package org.scotthamilton.wiidroid.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import org.scotthamilton.wiidroid.bluetooth.reflection.utils.bluetoothHidHostConnect

@RequiresApi(Build.VERSION_CODES.P)
class BluetoothHidProxyManager: BluetoothProfile.ServiceListener {
    companion object {
        private const val TAG = "BluetoothHidProxy"
    }
    // Should be of type BluetoothHidHost once API gets unhidden
    private var hidHostProxy: Any? = null
//    private val sdpRecord by lazy {
//        BluetoothHidDeviceAppSdpSettings(
//            "Wiidroid",
//            "Droid wiimotehost",
//            "wiidroid",
//            BluetoothHidDevice.SUBCLASS2_REMOTE_CONTROL,
//            WiimoteHIDDescriptors
//        )
//    }
//    private val qosIn by lazy {
//        BluetoothHidDeviceAppQosSettings(
//            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
//            800,
//            9,
//            0,
//            11250,
//            BluetoothHidDeviceAppQosSettings.MAX
//        )
//    }
    fun setup(adapter: BluetoothAdapter?, context: Context) {
        if (adapter?.getProfileProxy(
            context,
            this,
            HID_HOST
        ) == true) {
            Log.d(TAG, "Hid proxy successfully acquired")
        } else {
            Log.d(TAG, "error, failed to acquire Hid proxy")
        }
    }
    @RequiresPermission(allOf=[
        "android.permission.BLUETOOTH_PRIVILEGED",
        "android.permission.BLUETOOTH_CONNECT"]
    )
    fun connectDevice(device: BluetoothDevice): Boolean {
        val proxy = hidHostProxy
        return if (proxy == null) {
            Log.d(TAG,"error, can't connect device ${device.name}, hid proxy is null")
            false
        } else {
            if (bluetoothHidHostConnect(device, hidHostProxy!!)) {
                Log.d(TAG, "HID successfully connected to device ${device.name}")
//                if (proxy.sendReport(device, 0xa2, listOf(0x12, 0x00, 0x30).toGoodByteArray())) {
//                    Log.d(TAG, "successfully sent report to device ${device.name}")
//                } else {
//                    Log.d(TAG, "failed to send report to device ${device.name}")
//                }
                true
            } else {
                Log.d(TAG, "failed to connect to HID device ${device.name}")
                false
            }
//            if (proxy.connect(device)) {
//                Log.d(TAG, "HID successfully connected to device ${device.name}")
//                if (proxy.sendReport(device, 0xa2, listOf(0x12, 0x00, 0x30).toGoodByteArray())) {
//                    Log.d(TAG, "successfully sent report to device ${device.name}")
//                } else {
//                    Log.d(TAG, "failed to send report to device ${device.name}")
//                }
//                true
//            } else {
//                Log.d(TAG, "failed to connect to HID device ${device.name}")
//                false
//            }
        }
    }

    @RequiresPermission(value="android.permission.BLUETOOTH_CONNECT")
    fun connectedDevice(): List<BluetoothDevice>? =
        null // TODO: fix
//        hidHostProxy?.connectedDevices

    @SuppressLint("MissingPermission")
    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
        when(profile) {
            HID_HOST -> {
                Log.d(TAG, "HID host connected")
                hidHostProxy = proxy
//                if (hidHostProxy == null) {
//                    Log.d(TAG, "error, provided BluetoothHidDevice is invalid")
//                } else {
//                    hidHostProxy?.registerApp(
//                        sdpRecord,
//                        qosIn,
//                        null,
//                        Executors.newSingleThreadExecutor(),
//                        this
//                    )
//                    Log.d(TAG, "got a working BluetoothHidDevice")
//                }
            }
            else -> Log.d(TAG, "")
        }
    }
    override fun onServiceDisconnected(profile: Int) {
        when(profile) {
            BluetoothProfile.HID_DEVICE ->
                Log.d(TAG, "HID device disconnected")
            else ->
                Log.d(TAG, "HID device disconnected but profile isn't HID_DEVICE !!! " +
                        "profile=$profile")
        }
    }
//    @RequiresPermission(value="android.permission.BLUETOOTH_CONNECT")
//    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
//        Log.d(TAG, "appStatusChanged pluggedDevice=${pluggedDevice?.name}," +
//                "registered = $registered")
//        super.onAppStatusChanged(pluggedDevice, registered)
//    }
//    @RequiresPermission(value="android.permission.BLUETOOTH_CONNECT")
//    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
//        val stateStr = when (state) {
//            BluetoothProfile.STATE_CONNECTED -> "STATE_CONNECTED"
//            BluetoothProfile.STATE_CONNECTING -> "STATE_CONNECTING"
//            BluetoothProfile.STATE_DISCONNECTED -> "STATE_DISCONNECTED"
//            BluetoothProfile.STATE_DISCONNECTING -> "STATE_DISCONNECTING"
//            else -> "UNKNOWN_STATE_$state"
//        }
//        Log.d(TAG, "connection state changed, device: ${device?.name}, " +
//                "state=$stateStr")
//        super.onConnectionStateChanged(device, state)
//    }
//    @RequiresPermission(value="android.permission.BLUETOOTH_CONNECT")
//    override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
//        Log.d(TAG, "get report, device: ${device?.name}, " +
//                "type=$type, id=$id, bufferSize=$bufferSize")
//        super.onGetReport(device, type, id, bufferSize)
//    }
//    @RequiresPermission(value="android.permission.BLUETOOTH_CONNECT")
//    override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
//        Log.d(TAG, "interrupt data, device: ${device?.name}, " +
//                "reportId=$reportId, data=$data")
//        super.onInterruptData(device, reportId, data)
//    }
//    @RequiresPermission(value="android.permission.BLUETOOTH_CONNECT")
//    override fun onSetProtocol(device: BluetoothDevice?, protocol: Byte) {
//        Log.d(TAG, "set protocol, device: ${device?.name}, " +
//                "protocol=$protocol")
//        super.onSetProtocol(device, protocol)
//    }
//    @RequiresPermission(value="android.permission.BLUETOOTH_CONNECT")
//    override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
//        Log.d(TAG, "set report, device: ${device?.name}, " +
//                "type=$type, id=$id, data=$data")
//        super.onSetReport(device, type, id, data)
//    }
//    @RequiresPermission(value="android.permission.BLUETOOTH_CONNECT")
//    override fun onVirtualCableUnplug(device: BluetoothDevice?) {
//        Log.d(TAG, "virtual cable unplugged, device=${device?.name}")
//        super.onVirtualCableUnplug(device)
//    }

}

