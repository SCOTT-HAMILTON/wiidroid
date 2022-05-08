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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.scotthamilton.wiidroid.R
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

interface ActivityPermManager {
    fun setupPermManager(activity: ComponentActivity)
    fun configurePerm(perm: String, rationale: String)
    fun <T> runBlockingWithPermissionOrIf(perm: String,
                                          rationaleShower: RationaleShower,
                                          predicate: ()->Boolean,
                                          body: suspend CoroutineScope.()->T): T?
    suspend fun <T> runWithPermissionOrIfAsync(perm: String,
                                               rationaleShower: RationaleShower,
                                               predicate: ()->Boolean,
                                               body: suspend CoroutineScope.()->T): Deferred<T?>

    fun <T> runBlockingWithPermissionsOrIf(perms: List<String>,
                                          rationaleShower: RationaleShower,
                                          predicate: ()->Boolean,
                                          body: suspend CoroutineScope.()->T): T?
    suspend fun <T> runWithPermissionsOrIfAsync(perms: List<String>,
                                               rationaleShower: RationaleShower,
                                               predicate: ()->Boolean,
                                               body: suspend CoroutineScope.()->T): Deferred<T?>
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

//data class FuturePermTask(val channel: Channel<Any?>, val future: ()->Any?)

class ActivityPermManagerImpl : ActivityPermManager {
    companion object {
        private const val TAG = "ActivityPermManagerImpl"
    }

    //    private val runnerWithPermFuturesContext =
//        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
//    private val runnerWithPermFutures: MutableMap<String, ArrayDeque<FuturePermTask>> = mutableMapOf()
    private val permJobsConsumer = AsyncJobConsumer<String>()
    private val permRationales: MutableMap<String, String> = mutableMapOf()
    private val registeredPermissionsContext =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var registeredPermissions: MutableMap<String, PermLauncher> = mutableMapOf()
    private lateinit var activity: ComponentActivity

    override fun setupPermManager(activity: ComponentActivity) {
        this.activity = activity
    }

    @OptIn(ExperimentalTime::class)
    @RequiresApi(Build.VERSION_CODES.S)
    override fun <T> runBlockingWithPermissionOrIf(
        perm: String,
        rationaleShower: RationaleShower,
        predicate: () -> Boolean,
        body: suspend CoroutineScope.() -> T
    ): T? = runBlocking {
        runWithPermissionOrIfAsync(perm, rationaleShower, predicate, body).await()
    }

    @OptIn(ExperimentalTime::class)
    @RequiresApi(Build.VERSION_CODES.S)
    override suspend fun <T> runWithPermissionOrIfAsync(
        perm: String,
        rationaleShower: RationaleShower,
        predicate: () -> Boolean,
        body: suspend CoroutineScope.() -> T
    ): Deferred<T?> = activity.lifecycleScope.async {
        withContext(Dispatchers.Default) {
            if (predicate() || hasPerm(perm)) {
                body()
            } else if (!isPermRegistered(perm)) {
                Log.d(
                    TAG, "error, can't ask permission $perm if launcher " +
                            "not already registered, try configurePerm($perm, <rationale>)"
                )
                null
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
                            val jobHandler = permJobsConsumer.registerJob(perm) {
                                runBlocking { body() }
                            }
                            async {
                                jobHandler.waitResult(timeout = Duration.Companion.seconds(20))
                                        as? T?
                            }
                        }
                    }
                }
                null
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun <T> runBlockingWithPermissionsOrIf(
        perms: List<String>,
        rationaleShower: RationaleShower,
        predicate: () -> Boolean,
        body: suspend CoroutineScope.() -> T
    ): T? = runBlocking {
        runWithPermissionsOrIfAsync(perms, rationaleShower, predicate, body).await()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override suspend fun <T> runWithPermissionsOrIfAsync(
        perms: List<String>,
        rationaleShower: RationaleShower,
        predicate: () -> Boolean,
        body: suspend CoroutineScope.() -> T
    ): Deferred<T?> {
        return when (perms.size) {
            0 -> activity.lifecycleScope.async { body() }
            1 -> runWithPermissionOrIfAsync(perms.first(), rationaleShower, predicate, body)
            else -> {
                val first = perms.first()
                val newperms = perms.drop(1)
                runWithPermissionOrIfAsync(
                    first, rationaleShower, predicate
                ) {
                    runWithPermissionsOrIfAsync(newperms, rationaleShower, predicate, body).await()
                }
            }
        }
    }

    override fun configurePerm(perm: String, rationale: String) {
        addPermLauncher(perm, registerPermLauncher(perm))
        permRationales[perm] = rationale
    }

    private fun registerPermLauncher(perm: String): ActivityResultLauncher<String> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            runBlocking {
                val runners = permJobsConsumer.consumeJobs(perm)
                withContext(Dispatchers.IO) {
                    if (isGranted) {
                        runners?.forEach { runner ->
                            val r = runBlocking {
                                runner.task()
                            }
                            runner.channel.send(r)
                        }
                        Log.d(TAG, "permission $perm granted")
                    } else {
                        runners?.forEach { runner ->
                            runner.channel.send(null)
                        }
                        Log.d(TAG, "permission $perm denied")
                    }
                }
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

    override fun hasPerm(perm: String): Boolean =
        ActivityCompat.checkSelfPermission(
            activity, perm
        ) == PackageManager.PERMISSION_GRANTED
}