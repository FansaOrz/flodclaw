package com.foldpods.bluetooth.reconnect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.foldpods.domain.ClassicHeadset
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 监听 A2DP / HFP 当前已连接设备。
 * AirPods 的经典地址可能与 BLE 邻近广播地址不同，故单独暴露供选主设备使用。
 */
@Singleton
class ClassicConnectionMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    @SuppressLint("MissingPermission")
    fun observeConnected(): Flow<List<ClassicHeadset>> = callbackFlow {
        val a2dpDevices = linkedSetOf<String>()
        val hfpDevices = linkedSetOf<String>()
        val names = mutableMapOf<String, String?>()

        fun emitMerged() {
            val all = (a2dpDevices + hfpDevices).distinct()
            trySend(
                all.map { addr ->
                    val device = runCatching { adapter?.getRemoteDevice(addr) }.getOrNull()
                    ClassicHeadset(
                        address = addr,
                        name = names[addr] ?: device?.name,
                        a2dpConnected = a2dpDevices.any { it.equals(addr, true) },
                        hfpConnected = hfpDevices.any { it.equals(addr, true) },
                        systemBatteryPercent = device?.systemBatteryPercent(),
                    )
                },
            )
        }

        fun remember(device: BluetoothDevice?) {
            if (device == null) return
            names[device.address] = device.name
        }

        var a2dpProxy: BluetoothProfile? = null
        var hfpProxy: BluetoothProfile? = null

        fun refreshFromProxies() {
            a2dpProxy?.connectedDevices.orEmpty().forEach { d ->
                remember(d)
                a2dpDevices.add(d.address)
            }
            hfpProxy?.connectedDevices.orEmpty().forEach { d ->
                remember(d)
                hfpDevices.add(d.address)
            }
            // Drop stale entries not reported by proxies when both are bound
            if (a2dpProxy != null) {
                a2dpDevices.retainAll { addr ->
                    a2dpProxy!!.connectedDevices.orEmpty().any { it.address.equals(addr, true) }
                }
            }
            if (hfpProxy != null) {
                hfpDevices.retainAll { addr ->
                    hfpProxy!!.connectedDevices.orEmpty().any { it.address.equals(addr, true) }
                }
            }
            emitMerged()
        }

        val profileListener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                when (profile) {
                    BluetoothProfile.A2DP -> a2dpProxy = proxy
                    BluetoothProfile.HEADSET -> hfpProxy = proxy
                }
                refreshFromProxies()
            }

            override fun onServiceDisconnected(profile: Int) {
                when (profile) {
                    BluetoothProfile.A2DP -> a2dpProxy = null
                    BluetoothProfile.HEADSET -> hfpProxy = null
                }
            }
        }

        val bt = adapter
        bt?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
        bt?.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        val device = intent.parcelableDevice()
                        remember(device)
                        refreshFromProxies()
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val addr = intent.parcelableDevice()?.address
                        if (addr != null) {
                            a2dpDevices.removeAll { it.equals(addr, true) }
                            hfpDevices.removeAll { it.equals(addr, true) }
                        }
                        refreshFromProxies()
                    }
                    BluetoothA2dpConnection.ACTION -> {
                        val device = intent.parcelableDevice()
                        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                        remember(device)
                        val addr = device?.address
                        if (addr != null) {
                            if (state == BluetoothProfile.STATE_CONNECTED) a2dpDevices.add(addr)
                            else a2dpDevices.removeAll { it.equals(addr, true) }
                        }
                        emitMerged()
                    }
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                        val device = intent.parcelableDevice()
                        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                        remember(device)
                        val addr = device?.address
                        if (addr != null) {
                            if (state == BluetoothProfile.STATE_CONNECTED) hfpDevices.add(addr)
                            else hfpDevices.removeAll { it.equals(addr, true) }
                        }
                        emitMerged()
                    }
                    ACTION_BATTERY_LEVEL_CHANGED -> emitMerged()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dpConnection.ACTION)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(ACTION_BATTERY_LEVEL_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }

        emitMerged()
        val poll = launch {
            while (isActive) {
                delay(2_000)
                refreshFromProxies()
            }
        }

        awaitClose {
            poll.cancel()
            runCatching { context.unregisterReceiver(receiver) }
            a2dpProxy?.let { bt?.closeProfileProxy(BluetoothProfile.A2DP, it) }
            hfpProxy?.let { bt?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
        }
    }
}

/** A2DP 连接状态广播 action（隐藏常量，各 OEM 稳定）。 */
private object BluetoothA2dpConnection {
    const val ACTION = "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
}

private const val ACTION_BATTERY_LEVEL_CHANGED =
    "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"

@SuppressLint("MissingPermission")
private fun BluetoothDevice.systemBatteryPercent(): Int? {
    val level = runCatching {
        val method = BluetoothDevice::class.java.getMethod("getBatteryLevel")
        method.invoke(this) as Int
    }.getOrDefault(-1)
    return level.takeIf { it in 0..100 }
}

@Suppress("DEPRECATION")
private fun Intent.parcelableDevice(): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
