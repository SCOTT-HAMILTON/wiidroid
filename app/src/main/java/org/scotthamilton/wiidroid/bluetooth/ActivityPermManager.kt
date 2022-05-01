package org.scotthamilton.wiidroid.bluetooth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.activity.ComponentActivity
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import kotlinx.coroutines.*
import org.scotthamilton.wiidroid.R
import java.util.concurrent.Executors

interface ActivityPermManager {
    fun setupPermManager(activity: ComponentActivity)
    fun configurePerm(perm: String, rationale: String)
    fun runWithPermissionOrIf(perm: String,
                              rationaleShower: RationaleShower,
                              predicate: ()->Boolean,
                              body: ()->Unit)
    fun hasPerm(perm: String): Boolean
}

interface RationaleShower {
    fun showRationaleWithAction(rationale: String, callback: (granted: Boolean)->Unit)
}

class ComposeSnackbarRationale(
    private val snackbarHostState: SnackbarHostState,
    private val coroutineScope: CoroutineScope,
    private val context: Context
) : RationaleShower {
    override fun showRationaleWithAction(rationale: String, callback: (accepted: Boolean) -> Unit) {
        coroutineScope.launch {
            withContext(Dispatchers.Main) {
                when (snackbarHostState.showSnackbar(
                    message = rationale,
                    actionLabel = context.getString(R.string.grant_text),
                    duration = SnackbarDuration.Long)
                ) {
                    SnackbarResult.ActionPerformed -> callback(true)
                    SnackbarResult.Dismissed -> callback(false)
                }
            }
        }
    }

}

typealias PermLauncher = ActivityResultLauncher<String>

class ActivityPermManagerImpl : ActivityPermManager {
    companion object {
        private const val TAG = "ActivityPermManagerImpl"
    }

    private val permRationales: MutableMap<String, String> = mutableMapOf()
    private val registeredPermissionsContext =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var registeredPermissions: MutableMap<String, PermLauncher> = mutableMapOf()
    private lateinit var activity: ComponentActivity

    override fun setupPermManager(activity: ComponentActivity) {
        this.activity = activity
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun runWithPermissionOrIf(
        perm: String,
        rationaleShower: RationaleShower,
        predicate: () -> Boolean,
        body: () -> Unit
    ) {
        if (predicate() || hasPerm(perm)) {
            body()
        } else if (!isPermRegistered(perm)) {
            Log.d(
                TAG, "error, can't ask permission $perm if launcher " +
                        "not already registered, try configurePerm($perm, <rationale>)"
            )
        } else {
            val permLauncher = findPermLauncher(perm)
            permLauncher?.let { launcher ->
                when {
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        perm
                    ) -> {
                        rationaleShower.showRationaleWithAction(
                            permRationales[perm] ?: ""
                        ) { accepted ->
                            if (accepted) {
                                launcher.launch(perm)
                            } else {
                                Log.d(TAG, "used refused to grant permission $perm")
                            }
                        }
                    }
                    else -> {
                        launcher.launch(perm)
                    }
                }
            }
        }
    }

    override fun configurePerm(perm: String, rationale: String) {
        addPermLauncher(perm, registerPermLauncher(perm))
        permRationales.put(perm, rationale)
    }

    private fun registerPermLauncher(perm: String): ActivityResultLauncher<String> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "permission $perm granted")
            } else {
                Log.d(TAG, "permission $perm denied")
            }
        }

    private fun isPermRegistered(perm: String): Boolean =
        runOnRegisteredPermissionsContext {
            registeredPermissions.containsKey(perm)
        }

    private fun addPermLauncher(perm: String, permLauncher: PermLauncher) =
       runOnRegisteredPermissionsContext {
            registeredPermissions.put(perm, permLauncher)
       }

    private fun findPermLauncher(perm: String): PermLauncher? =
        runOnRegisteredPermissionsContext {
            registeredPermissions[perm]
        }

    private fun <T> runOnRegisteredPermissionsContext(block: suspend CoroutineScope.() -> T) =
        runBlocking {
            withContext(registeredPermissionsContext) {
                block()
            }
        }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun hasPerm(perm: String): Boolean =
        ActivityCompat.checkSelfPermission(
            activity, perm
        ) == PackageManager.PERMISSION_GRANTED
}