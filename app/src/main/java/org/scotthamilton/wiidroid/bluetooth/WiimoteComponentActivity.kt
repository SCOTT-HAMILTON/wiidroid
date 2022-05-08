package org.scotthamilton.wiidroid.bluetooth

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi

open class WiimoteComponentActivity<W : WiimoteManager>(private val m: W) :
    ComponentActivity(), WiimoteManager by m {
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setup(this)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onDestroy() {
        cleanUp()
        super.onDestroy()
    }
}