package org.scotthamilton.wiidroid.bluetooth

import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.*
import java.lang.Thread.sleep
import kotlin.concurrent.thread

interface WiimoteHandler {
    fun attachBluetoothSocket(socket: BluetoothSocket, device: ScannedDevice)
    fun cleanupBluetoothSocket()
}

class WiimoteHandlerImpl : WiimoteHandler {
    companion object {
        private const val TAG = "WiimoteHandlerImpl"
    }
    private var socket: BluetoothSocket? = null
    private var device: ScannedDevice? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    override fun attachBluetoothSocket(socket: BluetoothSocket, device: ScannedDevice) {
        if (this.socket == null) {
            this.socket = socket
            this.device = device
            inputStream = socket.inputStream
            outputStream = socket.outputStream
            thread(
                start = true,
                isDaemon = false,
                name = "WiimoteHandlerImpl",
            ) {
                run()
            }
        } else {
            Log.d(TAG, "error, can't attach to wiimote socket, already attached to $device")
        }
    }
    fun run() {
        while (true) {
            inputStream?.let {
                val bytes = it.readBytes()
                Log.d(TAG, "device $device sent $bytes")
                sleep(250)
            }
        }
    }
    override fun cleanupBluetoothSocket() {
        socket?.close()
    }
}