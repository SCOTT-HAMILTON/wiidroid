package org.scotthamilton.wiidroid.bluetooth.utils

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.ContextWrapper
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import org.scotthamilton.wiidroid.bluetooth.ActivityPermManager
import org.scotthamilton.wiidroid.bluetooth.ScannedDevice
import org.scotthamilton.wiidroid.bluetooth.WiimoteManagerImpl

enum class WiimoteProtocolePCMChannels(channel: Int) {
    ControlChannel(0x11),
    DataChannel(0x13),
}

fun PackageManager.hasBluetooth(): Boolean =
    hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)

@RequiresApi(Build.VERSION_CODES.S)
fun WiimoteManagerImpl.onCreateWiimoteSetup(activity: ComponentActivity) {
    setup(activity)
}

fun BluetoothDevice.reflectCreateInsecureL2capSocket(pcm: Int): BluetoothSocket? {
    val c = BluetoothDevice::class.java
    val m = c.getMethod("createL2capSocket", Int::class.java)
    return m.invoke(this, pcm) as? BluetoothSocket
//    val m = c.getMethod("createL2capCocSocket", Int::class.java, Int::class.java)
//    return m.invoke(this, BluetoothDevice.TRANSPORT_LE, pcm) as? BluetoothSocket
}

fun BluetoothDevice.reflectShowMethods(filter: String) {
    val c = BluetoothDevice::class.java
    val ms = c.declaredMethods.iterator().asSequence().toList().filter {
        filter.lowercase() in it.name.lowercase()
    }
    Log.d(WiimoteManagerImpl.TAG,"Found ${ms.size} methods: ${ms.map{it.name}}")
}

fun deviceNameIsWiimote(deviceName: String) =
    deviceName.startsWith("Nintendo RVL")

fun String.macAddressToByteList(): ByteArray =
    split(':').filter { it.length == 2}.map {
        it.toInt(16).toByte()
    }.toByteArray()

fun ByteArray.toPinStr(): String =
    joinToString(":") { String.format("%02X", it) }

fun List<Int>.toGoodByteArray(): ByteArray =
    map {it.toByte()}.toByteArray()

@SuppressLint("MissingPermission")
fun BluetoothDevice.toScannedDevice(p: ActivityPermManager): ScannedDevice {
    val deviceName = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        p.hasPerm(Manifest.permission.BLUETOOTH_CONNECT)) {
        name
    } else {
        Log.d(
            WiimoteManagerImpl.TAG, "can't access found device name, " +
                    "BLUETOOTH_CONNECT_PERM_MISSING")
        null
    }
    val deviceMacAddress = address // MAC address
    return ScannedDevice(
        deviceName ?: "",
        deviceMacAddress ?: "",
        this
    )
}