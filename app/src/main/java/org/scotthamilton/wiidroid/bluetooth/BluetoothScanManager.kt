package org.scotthamilton.wiidroid.bluetooth

import android.os.Parcelable
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

data class ScannedDevice(
    val name: String,
    val mac_address: String,
    val device: Parcelable
)

interface BluetoothScanManager {
    fun startScan()
    fun endScan(): Set<ScannedDevice>
    fun scanFoundWiimote(device: ScannedDevice)
    fun previousScanWiimotes(): Set<ScannedDevice>?
    fun currentlyFoundWiimotes(): Set<ScannedDevice>
}

class BluetoothScanManagerImpl : BluetoothScanManager {
    companion object {
        private const val TAG = "BluetoothScanManager"
    }
    private val foundWiimotesContext =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val foundWiimotes: MutableSet<ScannedDevice> = mutableSetOf()
    private var previousScanWiimotes: Set<ScannedDevice>? = null

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

    override fun endScan(): Set<ScannedDevice> =
        runOnFoundWiimotesContext {
            val found: Set<ScannedDevice> = foundWiimotes.asIterable().toList().toSet()
            foundWiimotes.clear()
            previousScanWiimotes = found
            return@runOnFoundWiimotesContext found
        }

    override fun scanFoundWiimote(device: ScannedDevice) {
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

    override fun previousScanWiimotes(): Set<ScannedDevice>? =
        previousScanWiimotes

    override fun currentlyFoundWiimotes(): Set<ScannedDevice> =
        runOnFoundWiimotesContext {
            foundWiimotes.asIterable().toList().toSet()
        }

}