package org.scotthamilton.wiidroid.bluetooth

import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
@RequiresApi(Build.VERSION_CODES.P)
class BluetoothHidProxyManager: BluetoothProfile.ServiceListener {
    companion object {
        private const val TAG = "BluetoothHidProxy"
    }
    private var hidDeviceProxy: BluetoothHidDevice? = null
//    private val sdpRecord by lazy {
//        BluetoothHidDeviceAppSdpSettings(
//            "Pixel HID1",
//            "Mobile BController",
//            "bla",
//            0,
//            DescriptorCollection.MOUSE_KEYBOARD_COMBO
//        )
//    }
//    private val qosOut by lazy {
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
            BluetoothProfile.HID_DEVICE
        ) == true) {
            Log.d(TAG, "Hid proxy successfully acquired")
        } else {
            Log.d(TAG, "error, failed to acquire Hid proxy")
        }
    }
    @RequiresPermission(value="android.permission.BLUETOOTH_CONNECT")
    fun connectDevice(device: BluetoothDevice): Boolean {
        val proxy = hidDeviceProxy
        return if (proxy == null) {
            Log.d(TAG,"error, can't connect device ${device.name}, hid proxy is null")
            false
        } else {
            proxy.connect(device)
        }
    }

    @RequiresPermission(value="android.permission.BLUETOOTH_CONNECT")
    fun connectedDevice(): List<BluetoothDevice>? =
        hidDeviceProxy?.connectedDevices

    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
        when(profile) {
            BluetoothProfile.HID_DEVICE -> {
                Log.d(TAG, "HID device connected")
                hidDeviceProxy = proxy as? BluetoothHidDevice
                if (hidDeviceProxy == null) {
                    Log.d(TAG, "error, provided BluetoothHidDevice is invalid")
                } else {
                    Log.d(TAG, "got a working BluetoothHidDevice")
                }
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

}

