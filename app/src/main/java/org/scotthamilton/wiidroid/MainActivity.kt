package org.scotthamilton.wiidroid

import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.scotthamilton.wiidroid.bluetooth.BluetoothScannedDevice
import org.scotthamilton.wiidroid.bluetooth.WiimoteComponentActivity
import org.scotthamilton.wiidroid.bluetooth.WiimoteManager
import org.scotthamilton.wiidroid.bluetooth.WiimoteManagerImpl
import org.scotthamilton.wiidroid.ui.theme.WiidroidTheme
import org.scotthamilton.wiidroid.bluetooth.utils.hasBluetooth

data class CompositionData(
    val hasBluetooth: Boolean,
    val onStartScan: () -> Unit,
    val onConnectRequest: (BluetoothScannedDevice) -> Unit,
    val scannedWiimotes: SnapshotStateList<BluetoothScannedDevice>,
    val scanRunning: MutableState<Boolean>
)

@RequiresApi(Build.VERSION_CODES.P)
class MainActivity(m: WiimoteManager = WiimoteManagerImpl()) :
    WiimoteComponentActivity(m) {
    companion object {
        const val TAG = "MainActivity"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WiidroidTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val snackbarHostState = remember { SnackbarHostState() }
                    val coroutineScope = rememberCoroutineScope()
                    val scannedWiimotes = remember { mutableStateListOf<BluetoothScannedDevice>() }
                    val scanRunning = remember { mutableStateOf(false) }
                    setComposeDeps(snackbarHostState, coroutineScope)
                    setOnScanResults {
                        scannedWiimotes.clear()
                        scannedWiimotes.addAll(it)
                    }
                    setOnScanEnded {
                        scanRunning.value = false
                    }
                    val compositionData = CompositionData(
                        hasBluetooth = packageManager.hasBluetooth() && hasBluetooth(),
                        onStartScan = {
                            tryStartScan()
                        },
                        onConnectRequest = {
                            lifecycleScope.launch {
                                withContext(Dispatchers.Default) {
                                    connectWiimote(it.device, true)
                                }
                            }
                        },
                        scannedWiimotes = scannedWiimotes,
                        scanRunning = scanRunning
                    )
                    MainContent(data = compositionData)
                }
            }
        }
    }
}

@Composable
fun MainContent(data: CompositionData? = null) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (data?.scanRunning?.value == true) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.TopEnd)
                    .padding(5.dp),
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(modifier = Modifier.fillMaxWidth().height(1.dp))
            Text(
                text = if (data?.hasBluetooth == true) {
                    "We do have bluetooth !"
                } else {
                    "Sorry, we don't have bluetooth"
                }
            )
            Button(
                onClick = {
                    data?.scanRunning?.value = true
                    data?.onStartScan?.invoke()
                },
                enabled = data?.scanRunning?.value == false
            ) {
                Text("Let's find some wiimotes")
            }
            Spacer(modifier = Modifier.fillMaxWidth().height(10.dp))
            val wiimotes = data?.scannedWiimotes?.asIterable()?.toList()
            LazyColumn(
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.8f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(wiimotes?.size ?: 0) { index ->
                    val device = wiimotes?.get(index)
                    Button(
                        onClick = {
                            Log.d(MainActivity.TAG, "user asked to connect to device $device")
                            device?.let {
                                data.onConnectRequest.invoke(it)
                            }
                        }
                    ) {
                        Text(device?.toString() ?: "Invalid device")
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    WiidroidTheme {
        MainContent()
    }
}