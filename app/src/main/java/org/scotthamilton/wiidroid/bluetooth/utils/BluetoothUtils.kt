package org.scotthamilton.wiidroid.bluetooth.utils

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import org.scotthamilton.wiidroid.bluetooth.WiimoteManager

enum class WiimoteProtocolePCMChannels(channel: Int) {
    ControlChannel(0x11),
    DataChannel(0x13),
}

fun PackageManager.hasBluetooth(): Boolean =
    hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)

fun WiimoteManager.registerForActionFound(context: ContextWrapper) {
    val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
    context.registerReceiver(getActionFoundReceiver(), filter)
}

fun WiimoteManager.unregisterForActionFound(context: ContextWrapper) {
    context.unregisterReceiver(getActionFoundReceiver())
}

fun WiimoteManager.registerForScanStarted(context: ContextWrapper) {
    val filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
    context.registerReceiver(getScanStartedReceiver(), filter)
}

fun WiimoteManager.unregisterForScanStarted(context: ContextWrapper) {
    context.unregisterReceiver(getScanStartedReceiver())
}

fun WiimoteManager.registerForScanFinished(context: ContextWrapper) {
    val filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
    context.registerReceiver(getScanFinishedReceiver(), filter)
}

fun WiimoteManager.unregisterForScanFinished(context: ContextWrapper) {
    context.unregisterReceiver(getScanFinishedReceiver())
}

fun WiimoteManager.registerForStateChanged(context: ContextWrapper) {
    val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
    context.registerReceiver(getBtStateChangedReceiver(), filter)
}

fun WiimoteManager.unregisterForStateChanged(context: ContextWrapper) {
    context.unregisterReceiver(getBtStateChangedReceiver())
}

fun WiimoteManager.onCreateWiimoteSetup(activity: ComponentActivity) {
    setup(activity)
    registerForActionFound(activity)
    registerForScanStarted(activity)
    registerForScanFinished(activity)
    registerForStateChanged(activity)
}

fun WiimoteManager.onDestroyWiimoteCleanup(activity: ComponentActivity) {
    unregisterForActionFound(activity)
    unregisterForScanStarted(activity)
    unregisterForScanFinished(activity)
    unregisterForStateChanged(activity)
}

fun deviceNameIsWiimote(deviceName: String) =
    deviceName.startsWith("Nintendo RVL")