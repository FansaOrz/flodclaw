package com.foldpods.bluetooth.aacp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 创建 BR/EDR L2CAP socket（AACP PSM 0x1001）。
 *
 * 对齐 CAPod [L2capSocketFactory]：
 * 1. 公开 [BluetoothSocketSettings] + TYPE_L2CAP（新系统栈；三星常拒绝 socketType=3）
 * 2. HiddenApiBypass + createInsecureL2capSocket / createL2capSocket
 */
@Singleton
class L2capSocketFactory @Inject constructor() {

    private val typeL2cap = 3

    @SuppressLint("MissingPermission")
    fun createSocket(device: BluetoothDevice, psm: Int): BluetoothSocket {
        require(psm > 0) { "Invalid PSM: $psm" }

        tryPublicApi(device, psm)?.let {
            Log.i(TAG, "L2CAP socket via BluetoothSocketSettings (TYPE_L2CAP)")
            return it
        }

        Log.i(TAG, "Public API unavailable — trying hidden create*L2capSocket")
        return createViaHiddenApi(device, psm)
    }

    private fun tryPublicApi(device: BluetoothDevice, psm: Int): BluetoothSocket? {
        return try {
            val settingsClass = Class.forName("android.bluetooth.BluetoothSocketSettings")
            val builderClass = Class.forName("android.bluetooth.BluetoothSocketSettings\$Builder")

            val builder = builderClass.getDeclaredConstructor().newInstance()
            builderClass.getMethod("setSocketType", Int::class.javaPrimitiveType)
                .invoke(builder, typeL2cap)
            builderClass.getMethod("setL2capPsm", Int::class.javaPrimitiveType)
                .invoke(builder, psm)
            builderClass.getMethod("setAuthenticationRequired", Boolean::class.javaPrimitiveType)
                .invoke(builder, false)
            builderClass.getMethod("setEncryptionRequired", Boolean::class.javaPrimitiveType)
                .invoke(builder, false)
            val settings = builderClass.getMethod("build").invoke(builder)

            val createMethod = BluetoothDevice::class.java.getMethod(
                "createUsingSocketSettings",
                settingsClass,
            )
            createMethod.invoke(device, settings) as BluetoothSocket
        } catch (_: ClassNotFoundException) {
            Log.d(TAG, "BluetoothSocketSettings not on this API level")
            null
        } catch (e: Exception) {
            val cause = if (e is InvocationTargetException) e.cause ?: e else e
            when (cause) {
                is IllegalArgumentException -> {
                    Log.d(TAG, "TYPE_L2CAP unsupported: ${cause.message}")
                    null
                }
                is SecurityException -> throw cause
                else -> {
                    Log.d(TAG, "BluetoothSocketSettings failed: ${cause.javaClass.simpleName}: ${cause.message}")
                    null
                }
            }
        }
    }

    private fun createViaHiddenApi(device: BluetoothDevice, psm: Int): BluetoothSocket {
        HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/")

        val errors = mutableListOf<String>()
        invokeCreate(device, "createInsecureL2capSocket", psm, errors)?.let { return it }
        invokeCreate(device, "createL2capSocket", psm, errors)?.let { return it }

        throw IOException(
            "create*L2capSocket failed: ${errors.joinToString(" | ").ifBlank { "no method" }}",
        )
    }

    private fun invokeCreate(
        device: BluetoothDevice,
        methodName: String,
        psm: Int,
        errors: MutableList<String>,
    ): BluetoothSocket? {
        return try {
            val method = BluetoothDevice::class.java.getDeclaredMethod(
                methodName,
                Int::class.javaPrimitiveType,
            )
            method.isAccessible = true
            val sock = method.invoke(device, psm) as BluetoothSocket
            Log.i(TAG, "L2CAP socket via $methodName")
            sock
        } catch (e: InvocationTargetException) {
            val root = e.cause ?: e
            errors += "$methodName: ${root.javaClass.simpleName}: ${root.message}"
            null
        } catch (e: ReflectiveOperationException) {
            errors += "$methodName: ${e.javaClass.simpleName}: ${e.message}"
            null
        }
    }

    companion object {
        private const val TAG = "FoldPods.L2cap"
    }
}
