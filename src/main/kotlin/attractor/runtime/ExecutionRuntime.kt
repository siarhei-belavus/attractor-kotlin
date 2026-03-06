package attractor.runtime

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private fun envInt(key: String, default: Int): Int =
    System.getenv(key)?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: default

private fun envLong(key: String, default: Long): Long =
    System.getenv(key)?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: default

private fun envBool(key: String, default: Boolean): Boolean = when (System.getenv(key)?.trim()?.lowercase(Locale.getDefault())) {
    "1", "true", "yes", "on" -> true
    "0", "false", "no", "off" -> false
    else -> default
}

object ExecutionRuntime {
    val vthreadExecutionEnabled: Boolean = envBool("ATTRACTOR_VTHREAD_EXECUTION_ENABLED", false)
    val maxPendingRuns: Int = envInt("ATTRACTOR_MAX_PENDING_RUNS", 500)
    val maxActiveStages: Int = envInt("ATTRACTOR_MAX_ACTIVE_STAGES", 32)

    val maxActiveLlmByProvider: Map<String, Int> = mapOf(
        "openai" to envInt("ATTRACTOR_MAX_ACTIVE_LLM_OPENAI", 8),
        "anthropic" to envInt("ATTRACTOR_MAX_ACTIVE_LLM_ANTHROPIC", 8),
        "gemini" to envInt("ATTRACTOR_MAX_ACTIVE_LLM_GEMINI", 8),
        "copilot" to envInt("ATTRACTOR_MAX_ACTIVE_LLM_COPILOT", 8)
    )

    val cancelGraceMillis: Long = envLong("ATTRACTOR_SUBPROCESS_CANCEL_GRACE_MS", 1500L)

    val runExecutor = if (vthreadExecutionEnabled) {
        Executors.newVirtualThreadPerTaskExecutor()
    } else {
        Executors.newFixedThreadPool((Runtime.getRuntime().availableProcessors() * 2).coerceAtLeast(4))
    }

    // Separate executor for timeout-wrapped stage calls, so blocking waits do not tie platform threads.
    val timeoutExecutor = Executors.newVirtualThreadPerTaskExecutor()

    val stageLimiter = StageLimiter(maxActiveStages, maxActiveLlmByProvider)
}

class OverloadedExecutionException(message: String) : RuntimeException(message)

class StageLimiter(
    maxActiveStages: Int,
    providerMax: Map<String, Int>
) {
    private val global = Semaphore(maxActiveStages, true)
    private val llmByProvider = providerMax.mapValues { (_, limit) -> Semaphore(limit, true) }

    fun <T> withPermit(runId: String, nodeId: String, nodeType: String, provider: String?, block: () -> T): T {
        val queueStart = System.nanoTime()

        if (!global.tryAcquire()) {
            RuntimeMetrics.limiterSaturation.incrementAndGet()
            println("[attractor] limiter_saturated kind=global run_id=$runId node_id=$nodeId node_type=$nodeType")
            global.acquire()
        }

        val normalizedProvider = provider?.lowercase(Locale.getDefault())?.takeIf { it.isNotBlank() }
        val providerSemaphore = normalizedProvider?.let { llmByProvider[it] }
        if (providerSemaphore != null && !providerSemaphore.tryAcquire()) {
            RuntimeMetrics.limiterSaturation.incrementAndGet()
            println("[attractor] limiter_saturated kind=provider provider=$normalizedProvider run_id=$runId node_id=$nodeId")
            providerSemaphore.acquire()
        }

        val queueWaitNanos = System.nanoTime() - queueStart
        RuntimeMetrics.recordStageQueueWait(queueWaitNanos)
        RuntimeMetrics.activeStages.incrementAndGet()
        if (normalizedProvider != null && providerSemaphore != null) {
            RuntimeMetrics.activeStagesByProvider
                .computeIfAbsent(normalizedProvider) { AtomicInteger(0) }
                .incrementAndGet()
        }

        val start = System.nanoTime()
        try {
            return block()
        } finally {
            RuntimeMetrics.recordStageExecution(System.nanoTime() - start)
            RuntimeMetrics.activeStages.decrementAndGet()
            if (normalizedProvider != null && providerSemaphore != null) {
                RuntimeMetrics.activeStagesByProvider[normalizedProvider]?.decrementAndGet()
            }
            providerSemaphore?.release()
            global.release()
        }
    }
}

object RuntimeMetrics {
    val activeRuns = AtomicInteger(0)
    val queuedRuns = AtomicInteger(0)
    val activeStages = AtomicInteger(0)
    val activeStagesByProvider: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap()

    private val stageQueueWaitNanosTotal = AtomicLong(0)
    private val stageQueueWaitCount = AtomicLong(0)
    private val stageExecNanosTotal = AtomicLong(0)
    private val stageExecCount = AtomicLong(0)

    private val dbWriteNanosTotal = AtomicLong(0)
    private val dbWriteCount = AtomicLong(0)
    val dbWriteErrors = AtomicLong(0)

    val queueRejects = AtomicLong(0)
    val limiterSaturation = AtomicLong(0)
    val stageTimeouts = AtomicLong(0)
    val stageCancels = AtomicLong(0)
    val subprocessForceKills = AtomicLong(0)

    fun recordStageQueueWait(nanos: Long) {
        stageQueueWaitNanosTotal.addAndGet(nanos)
        stageQueueWaitCount.incrementAndGet()
    }

    fun recordStageExecution(nanos: Long) {
        stageExecNanosTotal.addAndGet(nanos)
        stageExecCount.incrementAndGet()
    }

    fun recordDbWrite(nanos: Long, ok: Boolean) {
        dbWriteNanosTotal.addAndGet(nanos)
        dbWriteCount.incrementAndGet()
        if (!ok) dbWriteErrors.incrementAndGet()
    }

    data class Snapshot(
        val activeRuns: Int,
        val queuedRuns: Int,
        val activeStages: Int,
        val activeStagesByProvider: Map<String, Int>,
        val stageQueueWaitAvgMs: Double,
        val stageExecAvgMs: Double,
        val dbWriteAvgMs: Double,
        val dbWriteErrors: Long,
        val queueRejects: Long,
        val limiterSaturation: Long,
        val stageTimeouts: Long,
        val stageCancels: Long,
        val subprocessForceKills: Long
    )

    fun snapshot(): Snapshot {
        val qCount = stageQueueWaitCount.get().coerceAtLeast(1)
        val eCount = stageExecCount.get().coerceAtLeast(1)
        val dbCount = dbWriteCount.get().coerceAtLeast(1)
        return Snapshot(
            activeRuns = activeRuns.get(),
            queuedRuns = queuedRuns.get(),
            activeStages = activeStages.get(),
            activeStagesByProvider = activeStagesByProvider.mapValues { it.value.get() },
            stageQueueWaitAvgMs = stageQueueWaitNanosTotal.get().toDouble() / qCount / 1_000_000.0,
            stageExecAvgMs = stageExecNanosTotal.get().toDouble() / eCount / 1_000_000.0,
            dbWriteAvgMs = dbWriteNanosTotal.get().toDouble() / dbCount / 1_000_000.0,
            dbWriteErrors = dbWriteErrors.get(),
            queueRejects = queueRejects.get(),
            limiterSaturation = limiterSaturation.get(),
            stageTimeouts = stageTimeouts.get(),
            stageCancels = stageCancels.get(),
            subprocessForceKills = subprocessForceKills.get()
        )
    }
}
