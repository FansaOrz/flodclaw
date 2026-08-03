package com.foldpods.bluetooth.reconnect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.foldsuite.core.Outcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class BondReconnectHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    @SuppressLint("MissingPermission")
    suspend fun assistReconnect(preferredAddress: String? = null): Outcome<String> =
        withContext(Dispatchers.Main) {
            val bt = adapter ?: return@withContext Outcome.Err("蓝牙不可用")
            if (!bt.isEnabled) return@withContext Outcome.Err("请先打开蓝牙")

            val bonded = bt.bondedDevices.orEmpty()
            val target = preferredAddress?.let { addr ->
                bonded.firstOrNull { it.address.equals(addr, ignoreCase = true) }
            } ?: bonded.firstOrNull { looksLikeAirPods(it) }

            if (target == null) {
                return@withContext Outcome.Err("未找到已配对的 AirPods，请先在系统蓝牙中配对")
            }

            val ok = connectA2dp(bt, target)
            if (ok) Outcome.Ok(target.address)
            else Outcome.Err("系统拒绝自动连接，请下拉状态栏手动点选设备")
        }

    @SuppressLint("MissingPermission")
    private suspend fun connectA2dp(adapter: BluetoothAdapter, device: BluetoothDevice): Boolean =
        suspendCancellableCoroutine { cont ->
            val proxyListener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile != BluetoothProfile.A2DP) return
                    val connected = runCatching {
                        val method = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                        method.invoke(proxy, device) as Boolean
                    }.getOrDefault(false)
                    adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                    if (cont.isActive) cont.resume(connected)
                }

                override fun onServiceDisconnected(profile: Int) = Unit
            }
            val bound = adapter.getProfileProxy(context, proxyListener, BluetoothProfile.A2DP)
            if (!bound && cont.isActive) cont.resume(false)
            cont.invokeOnCancellation {
                // best-effort; proxy may already be closed
            }
        }

    @SuppressLint("MissingPermission")
    private fun looksLikeAirPods(device: BluetoothDevice): Boolean {
        val name = device.name?.lowercase().orEmpty()
        return name.contains("airpods") || name.contains("airpod")
    }
}
