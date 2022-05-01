package org.scotthamilton.wiidroid.bluetooth

import android.os.Bundle
import androidx.activity.ComponentActivity
import org.scotthamilton.wiidroid.bluetooth.utils.onCreateWiimoteSetup
import org.scotthamilton.wiidroid.bluetooth.utils.onDestroyWiimoteCleanup

open class WiimoteComponentActivity(m: WiimoteManager) : ComponentActivity(), WiimoteManager by m {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onCreateWiimoteSetup(this)
    }

    override fun onDestroy() {
        onDestroyWiimoteCleanup(this)
        super.onDestroy()
    }
}