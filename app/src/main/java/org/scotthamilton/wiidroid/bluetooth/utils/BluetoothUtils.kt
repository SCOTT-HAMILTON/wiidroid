package org.scotthamilton.wiidroid.bluetooth.utils

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import org.scotthamilton.wiidroid.bluetooth.ActivityPermManager
import org.scotthamilton.wiidroid.bluetooth.BluetoothScannedDevice
import org.scotthamilton.wiidroid.bluetooth.WiimoteManager
import org.scotthamilton.wiidroid.bluetooth.WiimoteManagerImpl

enum class WiimoteProtocolePCMChannels(channel: Int) {
    ControlChannel(0x11),
    DataChannel(0x13),
}

fun PackageManager.hasBluetooth(): Boolean =
    hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)

fun WiimoteManager.registerBluetoothBroadcastReceiver(context: ContextWrapper) {
    val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
    filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
    filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
    filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
    filter.addAction(BluetoothDevice.ACTION_UUID)
    Log.d(WiimoteManagerImpl.TAG, "registered bluetooth broadcast receiver")
    context.registerReceiver(getBluetoothBroadcastReceiver(), filter)
}

fun WiimoteManager.unregisterBluetoothBroadcastReceiver(context: ContextWrapper) {
    Log.d(WiimoteManagerImpl.TAG, "unregistered bluetooth broadcast receiver")

    context.unregisterReceiver(getBluetoothBroadcastReceiver())
}

fun WiimoteManager.onCreateWiimoteSetup(activity: ComponentActivity) {
    setup(activity)
    registerBluetoothBroadcastReceiver(activity)
}

fun WiimoteManager.onDestroyWiimoteCleanup(activity: ComponentActivity) {
    unregisterBluetoothBroadcastReceiver(activity)
}

fun deviceNameIsWiimote(deviceName: String) =
    deviceName.startsWith("Nintendo RVL")

fun String.macAddressToByteList(): ByteArray =
    split(':').filter { it.length == 2}.map {
        it.toInt(16).toByte()
    }.toByteArray()

fun ByteArray.toPinStr(): String =
    joinToString(":") { String.format("%02X", it) }

@SuppressLint("MissingPermission")
fun BluetoothDevice.toScannedDevice(p: ActivityPermManager): BluetoothScannedDevice {
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
    return BluetoothScannedDevice(
        deviceName ?: "",
        deviceMacAddress ?: "",
        this
    )
}