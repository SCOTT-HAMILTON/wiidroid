package org.scotthamilton.wiidroid.bluetooth

import android.bluetooth.BluetoothDevice
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

data class BluetoothScannedDevice(
    val name: String,
    val mac_address: String,
    val device: BluetoothDevice
)

interface BluetoothScanManager {
    fun startScan()
    fun endScan(): Set<BluetoothScannedDevice>
    fun scanFoundWiimote(device: BluetoothScannedDevice)
    fun previousScanWiimotes(): Set<BluetoothScannedDevice>?
    fun currentlyFoundWiimotes(): Set<BluetoothScannedDevice>
}

class BluetoothScanManagerImpl : BluetoothScanManager {
    companion object {
        private const val TAG = "BluetoothScanManager"
    }
    private val foundWiimotesContext =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val foundWiimotes: MutableSet<BluetoothScannedDevice> = mutableSetOf()
    private var previousScanWiimotes: Set<BluetoothScannedDevice>? = null

    private fun <T> runOnFoundWiimotesContext(block: suspend CoroutineScope.() -> T) =
        runBlocking {
            withContext(foundWiimotesContext) {
                block()
            }
        }

    override fun startScan() =
        runOnFoundWiimotesContext {
            foundWiimotes.clear()
        }

    override fun endScan(): Set<BluetoothScannedDevice> =
        runOnFoundWiimotesContext {
            val found: Set<BluetoothScannedDevice> = foundWiimotes.asIterable().toList().toSet()
            foundWiimotes.clear()
            previousScanWiimotes = found
            return@runOnFoundWiimotesContext found
        }

    override fun scanFoundWiimote(device: BluetoothScannedDevice) {
        runOnFoundWiimotesContext {
            val isAlreadyFound = foundWiimotes.find {
                it.mac_address == device.mac_address && it.name == device.name
            } != null
            if (!isAlreadyFound) {
                foundWiimotes.add(device)
                Log.d(TAG, "scanFoundWiimote, device=$device, foundWiimotes=$foundWiimotes")
            } else {
                Log.d(TAG, "scanFoundWiimote, device already found=$device")
            }
        }
    }

    override fun previousScanWiimotes(): Set<BluetoothScannedDevice>? =
        previousScanWiimotes

    override fun currentlyFoundWiimotes(): Set<BluetoothScannedDevice> =
        runOnFoundWiimotesContext {
            foundWiimotes.asIterable().toList().toSet()
        }

}