package org.scotthamilton.wiidroid.bluetooth

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.material.SnackbarHostState
import androidx.core.content.ContextCompat.getSystemService
import kotlinx.coroutines.*
import org.scotthamilton.wiidroid.R
import org.scotthamilton.wiidroid.bluetooth.utils.deviceNameIsWiimote

interface WiimoteManager {
    fun setup(activity: ComponentActivity)
    fun bluetoothEnablingRequestCallback(r: ActivityResult)
    fun tryStartScan(snackbarHostState: SnackbarHostState,
                     coroutineScope: CoroutineScope)
    fun hasBluetooth(): Boolean
    fun getActionFoundReceiver(): BroadcastReceiver
    fun getScanStartedReceiver(): BroadcastReceiver
    fun getScanFinishedReceiver(): BroadcastReceiver
    fun getBtStateChangedReceiver(): BroadcastReceiver

    fun setOnScanResults(callback: (wiimotes: Set<BluetoothScannedDevice>) -> Unit)
    fun setOnScanEnded(callback: () -> Unit)
}

class WiimoteManagerImpl(
    p: ActivityPermManager = ActivityPermManagerImpl(),
    s: BluetoothScanManager = BluetoothScanManagerImpl()
) : WiimoteManager, ActivityPermManager by p, BluetoothScanManager by s {
    companion object {
        private const val TAG = "WiimoteManagerImpl"
    }
    private lateinit var activity: ComponentActivity
    private lateinit var requestEnablingBluetoothLauncher: ActivityResultLauncher<Intent>
    private var onScanResultsCallback: ((wiimotes: Set<BluetoothScannedDevice>) -> Unit)? = null
    private var onScanEndedCallback: (() -> Unit)? = null
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var singleshotOnBluetoothEnabled: (()->Unit)? = null
    private val actionFoundReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val deviceName = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        hasPerm(BLUETOOTH_CONNECT)) {
                        device?.name
                    } else {
                        Log.d(TAG, "can't access found device name, " +
                                    "BLUETOOTH_CONNECT_PERM_MISSING")
                        null
                    }
                    val deviceMacAddress = device?.address // MAC address
                    val scannedDevice = BluetoothScannedDevice(
                        deviceName ?: "",
                        deviceMacAddress ?: "",
                    )
                    Log.d(TAG,"scan found device $scannedDevice")
                    if (scannedDevice.name != "" && scannedDevice.mac_address != "") {
                        if (deviceNameIsWiimote(scannedDevice.name)) {
                            Log.d(TAG, "found Wiimote $scannedDevice")
                            scanFoundWiimote(scannedDevice)
                            onScanResultsCallback?.invoke(currentlyFoundWiimotes())
                        }
                    }
                }
            }
        }
    }
    private val scanStartedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    startScan()
                    Log.d(TAG,"bluetooth scan started")
                }
            }
        }
    }
    private val scanFinishedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    val foundWiimotes = endScan()
                    Log.d(TAG,"bluetooth scan ended, found $foundWiimotes")
                    onScanResultsCallback?.invoke(foundWiimotes)
                    onScanEndedCallback?.invoke()
                }
            }
        }
    }
    private val stateChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val previousState = intent.extras?.get(BluetoothAdapter.EXTRA_PREVIOUS_STATE)
                    val state = intent.extras?.get(BluetoothAdapter.EXTRA_STATE)
                    if (previousState != BluetoothAdapter.STATE_ON
                        && state == BluetoothAdapter.STATE_ON) {
                        singleshotOnBluetoothEnabled?.let {
                            singleshotOnBluetoothEnabled?.invoke()
                            singleshotOnBluetoothEnabled = null
                        }
                        Log.d(TAG,"bluetooth got enabled")
                    } else if (previousState == BluetoothAdapter.STATE_ON
                        && state != BluetoothAdapter.STATE_ON) {
                        Log.d(TAG,"bluetooth got disabled")
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun setup (activity: ComponentActivity) {
        this.activity = activity
        requestEnablingBluetoothLauncher = this.activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) {
            bluetoothEnablingRequestCallback(it)
        }
        bluetoothManager = getSystemService(this.activity, BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter
        setupPermManager(activity)
        configurePerm(BLUETOOTH_CONNECT,
            activity.getString(R.string.bluetooth_connect_perm_rationale))
        configurePerm(BLUETOOTH_SCAN,
            activity.getString(R.string.bluetooth_scan_perm_rationale))
    }

    override fun bluetoothEnablingRequestCallback (r: ActivityResult) {
        if (r.resultCode == ComponentActivity.RESULT_OK) {
            Log.d(TAG, "user enabled bluetooth")
        } else {
            Log.d(TAG, "user refused to enable bluetooth")
            bluetoothManager = null
            bluetoothAdapter = null
        }
    }
    @SuppressLint("InlinedApi")
    override fun tryStartScan(
        snackbarHostState: SnackbarHostState,
        coroutineScope: CoroutineScope
    ) {
        val rationaleLauncher = ComposeSnackbarRationale(
            snackbarHostState,
            coroutineScope,
            activity
        )
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                runWithPermissionOrIf(
                    BLUETOOTH_CONNECT,
                    rationaleLauncher, {
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                    }
                ) {
                    if (bluetoothAdapter?.isEnabled == true) {
                        Log.d(TAG, "bluetooth is enabled, starting discovery")
                        runWithPermissionOrIf(
                            BLUETOOTH_CONNECT,
                            rationaleLauncher, {
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                            }
                        ) {
                            startBluetoothDiscovery(snackbarHostState, coroutineScope)
                        }
                    } else {
                        Log.d(
                            TAG,
                            "bluetooth is not enabled, requesting before starting discovery"
                        )
                        singleshotOnBluetoothEnabled = {
                            Log.d(TAG, "user enabled bluetooth, starting discovery")
                            startBluetoothDiscovery(snackbarHostState, coroutineScope)
                        }
                        requestEnablingBluetooth()
                    }
                }
            }
        }
    }

    override fun hasBluetooth(): Boolean = bluetoothAdapter != null
    override fun getActionFoundReceiver(): BroadcastReceiver = actionFoundReceiver
    override fun getScanStartedReceiver(): BroadcastReceiver = scanStartedReceiver
    override fun getScanFinishedReceiver(): BroadcastReceiver = scanFinishedReceiver
    override fun getBtStateChangedReceiver(): BroadcastReceiver = stateChangedReceiver
    override fun setOnScanResults(callback: (wiimotes: Set<BluetoothScannedDevice>) -> Unit) {
        onScanResultsCallback = callback
    }
    override fun setOnScanEnded(callback: () -> Unit) { onScanEndedCallback = callback }

    private fun requestEnablingBluetooth() {
        requestEnablingBluetoothLauncher.launch(bluetoothEnablingRequestIntent())
    }

    private fun makeComposeRationaleShower(
        snackbarHostState: SnackbarHostState,
        coroutineScope: CoroutineScope
    ): RationaleShower =
        ComposeSnackbarRationale(
            snackbarHostState,
            coroutineScope,
            activity
        )

    @SuppressLint("MissingPermission", "InlinedApi")
    private fun startBluetoothDiscovery(
        snackbarHostState: SnackbarHostState,
        coroutineScope: CoroutineScope
    ) {
        val rationaleShower = makeComposeRationaleShower(snackbarHostState, coroutineScope)
        runWithPermissionOrIf(
            BLUETOOTH_SCAN,
            rationaleShower, {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            }
        ) {
            bluetoothAdapter?.startDiscovery()
        }
    }

    private fun bluetoothEnablingRequestIntent(): Intent =
        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

    private fun previouslyConnectedDevices(): Set<BluetoothDevice>? =
        setOf()

    @SuppressLint("MissingPermission")
    private fun pairedDevices(): Set<BluetoothDevice>? {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasPerm(BLUETOOTH_CONNECT)) {
            bluetoothAdapter?.bondedDevices
        } else {
            Log.d(TAG,
                "can't find available paired devices, BLUETOOTH_CONNECT_PERM_MISSING")
            null
        }
    }
}