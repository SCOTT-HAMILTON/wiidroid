package org.scotthamilton.wiidroid.bluetooth

import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothDevice.*
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
import androidx.annotation.RequiresPermission
import androidx.compose.material.SnackbarHostState
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.scotthamilton.wiidroid.R
import org.scotthamilton.wiidroid.bluetooth.utils.*
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

interface WiimoteManager {
    fun setup(activity: ComponentActivity)
    fun bluetoothEnablingRequestCallback(r: ActivityResult)
    fun tryStartScan()
    fun hasBluetooth(): Boolean
    fun getBluetoothBroadcastReceiver(): BroadcastReceiver

    fun setComposeDeps(snackbarHostState: SnackbarHostState, coroutineScope: CoroutineScope)
    fun setOnScanResults(callback: (wiimotes: Set<BluetoothScannedDevice>) -> Unit)
    fun setOnScanEnded(callback: () -> Unit)
    fun connectWiimote(device: BluetoothDevice, syncPairing: Boolean): BluetoothSocket?
}
@RequiresApi(Build.VERSION_CODES.P)
class WiimoteManagerImpl(
    p: ActivityPermManager = ActivityPermManagerImpl(),
    s: BluetoothScanManager = BluetoothScanManagerImpl()
) : WiimoteManager, ActivityPermManager by p, BluetoothScanManager by s {
    companion object {
        const val TAG = "WiimoteManagerImpl"
    }
    private val hidProxyManager = BluetoothHidProxyManager()
    private val pairingRequestJobsConsumer = AsyncJobConsumer<String>()
    private val bondStatusChangeJobsConsumer = AsyncJobConsumer<String>()
    private val sdpUuidJobsConsumer = AsyncJobConsumer<String>()
    private lateinit var activity: ComponentActivity
    private lateinit var requestEnablingBluetoothLauncher: ActivityResultLauncher<Intent>
    private lateinit var composeSnackbarHostState: SnackbarHostState
    private lateinit var composeCoroutineScope: CoroutineScope
    private var onScanResultsCallback: ((wiimotes: Set<BluetoothScannedDevice>) -> Unit)? = null
    private var onScanEndedCallback: (() -> Unit)? = null
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var singleshotOnBluetoothEnabled: (()->Unit)? = null
    private val bluetoothBroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                ACTION_FOUND -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
                    if (device != null) {
                        val scannedDevice = device.toScannedDevice(this@WiimoteManagerImpl)
                        Log.d(TAG, "scan found device $scannedDevice")
                        if (scannedDevice.name != "" && scannedDevice.mac_address != "") {
                            if (deviceNameIsWiimote(scannedDevice.name)) {
                                Log.d(TAG, "found Wiimote $scannedDevice")
                                scanFoundWiimote(scannedDevice)
                                onScanResultsCallback?.invoke(currentlyFoundWiimotes())
                            }
                        }
                    }
                }
                ACTION_PAIRING_REQUEST -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
                    if (device != null) {
                        val scannedDevice = device.toScannedDevice(this@WiimoteManagerImpl)
                        pairingRequestJobsConsumer.consumeJobs(scannedDevice.mac_address)
                            ?.let { jobs ->
                                activity.lifecycleScope.launch {
                                    withContext(Dispatchers.IO) {
                                        jobs.forEach { job ->
                                            val pin = job.task() as? ByteArray?
                                            if (pin == null) {
                                                Log.d(TAG, "error, invalid PIN=" +
                                                        "`${pin?.toPinStr()}`")
                                                job.channel.send(false)
                                            } else {
                                                device.setPin(pin)
                                                Log.d(TAG, "pin set device $scannedDevice " +
                                                        "pairing pin=${pin.toPinStr()}"
                                                )
                                                job.channel.send(true)
                                            }
                                        }
                                    }
                                }
                            }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d(TAG, "bluetooth scan started")
                    startScan()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    val foundWiimotes = endScan()
                    Log.d(TAG, "bluetooth scan ended, found $foundWiimotes")
                    onScanResultsCallback?.invoke(foundWiimotes)
                    onScanEndedCallback?.invoke()
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val previousState = intent.extras?.get(BluetoothAdapter.EXTRA_PREVIOUS_STATE)
                    val state = intent.extras?.get(BluetoothAdapter.EXTRA_STATE)
                    if (previousState != BluetoothAdapter.STATE_ON
                        && state == BluetoothAdapter.STATE_ON
                    ) {
                        singleshotOnBluetoothEnabled?.let {
                            singleshotOnBluetoothEnabled?.invoke()
                            singleshotOnBluetoothEnabled = null
                        }
                        Log.d(TAG, "bluetooth got enabled")
                    } else if (previousState == BluetoothAdapter.STATE_ON
                        && state != BluetoothAdapter.STATE_ON
                    ) {
                        Log.d(TAG, "bluetooth got disabled")
                    }
                }
                ACTION_BOND_STATE_CHANGED -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
                    val previousState =
                        intent.getIntExtra(
                            EXTRA_PREVIOUS_BOND_STATE, BOND_NONE
                        )
                    val newState =
                        intent.getIntExtra(EXTRA_BOND_STATE, BOND_NONE)
                    if (device != null) {
                        val scannedDevice = device.toScannedDevice(this@WiimoteManagerImpl)
                        val bonded = previousState != BOND_BONDED && newState == BOND_BONDED
                        val unbonded = previousState != BOND_NONE && newState == BOND_NONE
                        if (bonded || unbonded) {
                            bondStatusChangeJobsConsumer.consumeJobs(scannedDevice.mac_address)
                                ?.let { jobs ->
                                    activity.lifecycleScope.launch {
                                        jobs.forEach { job ->
                                            job.task()
                                            job.channel.send(bonded)
                                        }
                                    }
                                }
                        }
                        Log.d(
                            TAG, "bond state for device $scannedDevice changed from " +
                                    "previous=$previousState to now=$newState"
                        )
                    }
                }
                ACTION_UUID -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
                    val uuids = intent.getParcelableArrayExtra(EXTRA_UUID)?.map {
                        it.toString()
                    }
                    if (device != null && uuids != null) {
                        val scannedDevice = device.toScannedDevice(this@WiimoteManagerImpl)
//                        Log.d(TAG,"found SDP Uuid for device $scannedDevice, uuid=`$uuids`")
                        val jobHandlers = sdpUuidJobsConsumer.consumeJobs(scannedDevice.mac_address)
                        runBlocking {
                            jobHandlers?.forEach { jobHandler ->
                                jobHandler.channel.send(uuids)
                            }
                        }
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
        configurePerm(BLUETOOTH_ADMIN, "Needs to be bluetooth admin to scann lol.")
        configurePerm(ACCESS_FINE_LOCATION, "Stupidly, fine location is needed.")
        configurePerm(ACCESS_COARSE_LOCATION, "Stupidly, coarse location is needed.")
        hidProxyManager.setup(bluetoothAdapter, activity)
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
    override fun tryStartScan() {
        val rationaleLauncher = ComposeSnackbarRationale(
            composeSnackbarHostState,
            composeCoroutineScope,
            activity
        )
        activity.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runWithPermissionOrIfAsync(
                    BLUETOOTH_CONNECT,
                    rationaleLauncher, {
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                    }
                ) {
                    if (bluetoothAdapter?.isEnabled == true) {
                        Log.d(TAG, "bluetooth is enabled, starting discovery")
                        runWithPermissionOrIfAsync(
                            BLUETOOTH_CONNECT,
                            rationaleLauncher, {
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                            }
                        ) {
                            startBluetoothDiscovery()
                        }
                    } else {
                        Log.d(TAG,
                            "bluetooth is not enabled, requesting before starting discovery"
                        )
                        singleshotOnBluetoothEnabled = {
                            Log.d(TAG, "user enabled bluetooth, starting discovery")
                            startBluetoothDiscovery()
                        }
                        requestEnablingBluetooth()
                    }
                }
            }
        }
    }

    override fun hasBluetooth(): Boolean = bluetoothAdapter != null
    override fun getBluetoothBroadcastReceiver(): BroadcastReceiver = bluetoothBroadcastReceiver

    override fun setComposeDeps(
        snackbarHostState: SnackbarHostState,
        coroutineScope: CoroutineScope
    ) {
        composeCoroutineScope = coroutineScope
        composeSnackbarHostState = snackbarHostState
    }

    override fun setOnScanResults(callback: (wiimotes: Set<BluetoothScannedDevice>) -> Unit) {
        onScanResultsCallback = callback
    }
    override fun setOnScanEnded(callback: () -> Unit) { onScanEndedCallback = callback }
    @OptIn(ExperimentalTime::class)
    @SuppressLint("MissingPermission", "InlinedApi", "HardwareIds")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun connectWiimote(device: BluetoothDevice, syncPairing: Boolean): BluetoothSocket? =
        runBlockingWithPermissionOrIf(
            BLUETOOTH_CONNECT,
            makeComposeRationaleShower(), {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            }
        ) {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Still 1")
                bluetoothAdapter?.cancelDiscovery()
                Log.d(TAG, "Still 2")
                val pairedDevices = pairedDevices()
                Log.d(TAG, "Still 3")
                val isAlreadyPaired =
                    pairedDevices?.let { it.find { it.address == device.address } != null } == true
                val paired = if (!isAlreadyPaired) {
                    Log.d(TAG,"device isn't paired, pairing ${device.name}")
                    pairDevice(device, syncPairing)
                } else {
                    Log.d(TAG,"device is already paired, connecting ${device.name}")
                   true
                }
                if (paired) {
                    Log.d(TAG, "HidDeviceProxy has ${hidProxyManager.connectedDevice()?.size} " +
                            "connected devices")
                    if (hidProxyManager.connectDevice(device)) {
                        Log.d(TAG, "HidProxy successfully connected to device ${device.name}")
                    } else {
                        Log.d(TAG, "HidProxy failed to connect to device $device")
                    }
                }
                Log.d(TAG, "Still 5")
                null
            }
        }

    @OptIn(ExperimentalTime::class)
    @RequiresPermission(value="android.permission.BLUETOOTH_CONNECT")
    private fun pairDevice(device: BluetoothDevice, syncPairing: Boolean): Boolean {
        val pin = if (syncPairing) {
            "8C:DE:E6:70:6C:88".macAddressToByteList().reversedArray()
        } else {
            device.address.macAddressToByteList().reversedArray()
        }
        val pinJob = pairingRequestJobsConsumer.registerJob(device.address) { pin }
        val bondJob = bondStatusChangeJobsConsumer.registerJob(device.address) { }
        return if (device.createBond()) {
            Log.d(TAG, "pairing with device ${device.name} started.")
            val pinned = pinJob.waitResult(Duration.seconds(60))
            Log.d(TAG, "is device ${device.name} pinned ? $pinned")
            val bonded = bondJob.waitResult(Duration.seconds(60)) as Boolean?
            Log.d(TAG, "is device ${device.name} paired ? $bonded")
            bonded == true
        } else {
            Log.d(TAG, "failed to initiate pairing with ${device.name}.")
            false
        }
    }

    private fun requestEnablingBluetooth() {
        requestEnablingBluetoothLauncher.launch(bluetoothEnablingRequestIntent())
    }

    private fun makeComposeRationaleShower(): RationaleShower =
        ComposeSnackbarRationale(
            composeSnackbarHostState,
            composeCoroutineScope,
            activity
        )

    @SuppressLint("MissingPermission", "InlinedApi")
    private fun startBluetoothDiscovery() =
        activity.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                pairedDevices()?.forEach {
                    if (deviceNameIsWiimote(it.name)) {
                        scanFoundWiimote(it.toScannedDevice(this@WiimoteManagerImpl))
                        onScanResultsCallback?.invoke(currentlyFoundWiimotes())
                    }
                }
//                Log.d(TAG, "already paired devices: " +
//                        "${pairedDevices()?.map {it.toScannedDevice(this@WiimoteManagerImpl)}}")
                runWithPermissionsOrIfAsync(
                    listOf(BLUETOOTH_ADMIN, BLUETOOTH_CONNECT, BLUETOOTH_SCAN, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION),
                    makeComposeRationaleShower(), {
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                    }
                ) {
                    bluetoothAdapter?.let {
                        Log.d(TAG, "hasPerm(BLUETOOTH_SCAN)=${hasPerm(BLUETOOTH_SCAN)}, " +
                                "hasPerm(ACCESS_FINE_LOCATION)=${hasPerm(ACCESS_FINE_LOCATION)}, " +
                                "hasPerm(ACCESS_COARSE_LOCATION)=${hasPerm(ACCESS_COARSE_LOCATION)}, " +
                                "hasPerm(BLUETOOTH_CONNECT)=${hasPerm(BLUETOOTH_CONNECT)}, " +
                                "hasPerm(BLUETOOTH_ADMIN)=${hasPerm(BLUETOOTH_ADMIN)}")
                        Log.d(TAG,"starting bluetooth discovery, ")
                        if (it.startDiscovery()) {
                            Log.d(TAG,"bluetooth discovery successfully started")
                        } else {
                            Log.d(TAG,"bluetooth discovery failed to start")
                            onScanEndedCallback?.invoke()
                        }
                    }
                }
            }
        }

    private fun bluetoothEnablingRequestIntent(): Intent =
        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

    private fun previouslyConnectedDevices(): Set<BluetoothDevice>? =
        setOf()

    @SuppressLint("MissingPermission")
    private fun pairedDevices(): Set<BluetoothDevice>? =
        runBlockingWithPermissionOrIf(BLUETOOTH_CONNECT, makeComposeRationaleShower(),
            { Build.VERSION.SDK_INT < Build.VERSION_CODES.S }) {
            bluetoothAdapter?.bondedDevices
        }
}
