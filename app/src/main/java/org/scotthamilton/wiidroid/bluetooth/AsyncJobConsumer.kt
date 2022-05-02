package org.scotthamilton.wiidroid.bluetooth

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.time.Duration
import java.util.concurrent.Executors
import kotlin.time.ExperimentalTime

data class JobHandler(val channel: Channel<Any?>, val task: () -> Any?)

@OptIn(ExperimentalTime::class)
fun JobHandler.waitResult(timeout: Duration): Any? =
    runBlocking {
        withTimeout(timeout) {
            channel.receive()
        }
    }

class AsyncJobConsumer<I>  {
    private val jobHandlersContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val jobHandlers: MutableMap<I, ArrayDeque<JobHandler>> = mutableMapOf()
    private fun <T> runOnJobHandlersContext(block: suspend CoroutineScope.() -> T) =
        runBlocking {
            withContext(jobHandlersContext) {
                block()
            }
        }
    fun registerJob(id: I, task: () -> Any?): JobHandler {
        val jobHandler = JobHandler(
            channel = Channel(),
            task = task
        )
        return runOnJobHandlersContext {
            val queue = jobHandlers[id].let {
                if (it != null) {
                    it.add(jobHandler)
                    it
                } else {
                    ArrayDeque(listOf(jobHandler))
                }
            }
            jobHandlers[id] = queue
            jobHandler
        }
    }
    fun consumeJobs(id: I): List<JobHandler>? =
        runOnJobHandlersContext {
            val jobs = jobHandlers[id]?.toTypedArray()?.toList()
            jobHandlers[id]?.clear()
            jobs
        }

}