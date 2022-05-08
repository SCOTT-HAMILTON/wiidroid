package org.scotthamilton.wiidroid.bluetooth.utils

import android.bluetooth.BluetoothSocket
import android.content.Context.USB_SERVICE
import android.hardware.usb.UsbManager
import androidx.activity.ComponentActivity
import androidx.compose.material.SnackbarHostState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.scotthamilton.wiidroid.bluetooth.ActivityPermManager
import org.scotthamilton.wiidroid.bluetooth.ActivityPermManagerImpl
import org.scotthamilton.wiidroid.bluetooth.ScannedDevice
import org.scotthamilton.wiidroid.bluetooth.WiimoteManager
import java.lang.IllegalArgumentException

class WiimoteManagerUsbImpl(
    p: ActivityPermManager = ActivityPermManagerImpl()
): WiimoteManager, ActivityPermManager by p {
    private lateinit var snackbarHostState: SnackbarHostState
    private lateinit var activity: ComponentActivity
    private lateinit var usbManager: UsbManager
    private var onScanResultsCallback: ((wiimotes: Set<ScannedDevice>) -> Unit)? = null
    private var onScanEndCallback: (() -> Unit)? = null

    override fun setup(activity: ComponentActivity) {
        this.activity = activity
        usbManager = activity.getSystemService(USB_SERVICE) as? UsbManager ?:
            throw IllegalArgumentException("usb service returned null manager")
    }

    override fun cleanUp() { }

    override fun tryStartScan() {
        activity.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                onScanResultsCallback?.invoke(
                    usbManager.deviceList.map {
                        ScannedDevice(
                            name = it.key,
                            mac_address = it.value.deviceId.toString(),
                            device = it.value
                        )
                    }.toSet()
                )
                onScanEndCallback?.invoke()
            }
        }
    }
    override fun hasBluetooth(): Boolean = true
    override fun setComposeDeps(
        snackbarHostState: SnackbarHostState,
    ) {
        this.snackbarHostState = snackbarHostState
    }
    override fun setOnScanResults(callback: (wiimotes: Set<ScannedDevice>) -> Unit) {
        onScanResultsCallback = callback
    }
    override fun setOnScanEnded(callback: () -> Unit) {
        onScanEndCallback = callback
    }

    override fun connectWiimote(device: ScannedDevice, syncPairing: Boolean): BluetoothSocket? {
        return null
    }
}