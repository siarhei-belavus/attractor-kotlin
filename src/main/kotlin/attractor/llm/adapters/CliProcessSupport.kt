package attractor.llm.adapters

import attractor.llm.ConfigurationError
import attractor.llm.FinishReason
import attractor.llm.LlmResponse
import attractor.llm.Message
import attractor.llm.ProviderError
import attractor.llm.Request
import attractor.llm.StreamEvent
import attractor.llm.StreamEventType
import attractor.llm.Usage
import attractor.runtime.ExecutionRuntime
import attractor.runtime.RuntimeMetrics
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

internal fun streamCliProcess(
    provider: String,
    commandTemplate: String,
    request: Request,
    runner: ProcessRunner
): Sequence<StreamEvent> {
    val args = buildArgs(commandTemplate, request)
    return sequence {
        val proc = try {
            runner.start(args)
        } catch (e: IOException) {
            val binary = commandTemplate.trim().split("\\s+".toRegex()).firstOrNull() ?: provider
            yield(
                StreamEvent(
                    StreamEventType.ERROR,
                    error = ConfigurationError("CLI binary '$binary' not found. Install it or add it to PATH, or switch to API mode in Settings.")
                )
            )
            return@sequence
        }

        yield(StreamEvent(StreamEventType.STREAM_START))

        val reader = proc.inputStream.bufferedReader()
        try {
            val buf = CharArray(256)
            var n: Int
            while (reader.read(buf).also { n = it } != -1) {
                val chunk = String(buf, 0, n)
                yield(StreamEvent(StreamEventType.TEXT_DELTA, delta = chunk))
            }
        } catch (e: InterruptedException) {
            terminateProcess(proc, provider, request.model, "interrupted")
            Thread.currentThread().interrupt()
            yield(StreamEvent(StreamEventType.ERROR, error = ProviderError("CLI interrupted during output read", provider)))
            return@sequence
        } finally {
            reader.close()
        }

        val exit = try {
            proc.waitFor()
        } catch (e: InterruptedException) {
            terminateProcess(proc, provider, request.model, "interrupted")
            Thread.currentThread().interrupt()
            yield(StreamEvent(StreamEventType.ERROR, error = ProviderError("CLI interrupted while waiting for process", provider)))
            return@sequence
        }

        if (exit != 0) {
            val stderr = proc.errorStream.bufferedReader().readText().take(2048)
            yield(StreamEvent(StreamEventType.ERROR,
                error = ProviderError("CLI exited with code $exit: $stderr", provider)))
            return@sequence
        }

        val finalMsg = Message.assistant("")
        val finalResponse = LlmResponse(
            id = UUID.randomUUID().toString(),
            model = request.model,
            provider = provider,
            message = finalMsg,
            finishReason = FinishReason("stop", "end"),
            usage = Usage.empty()
        )
        yield(StreamEvent(StreamEventType.FINISH,
            finishReason = FinishReason("stop", "end"),
            response = finalResponse))
    }
}

internal fun terminateProcess(process: Process, provider: String, model: String, reason: String) {
    if (!process.isAlive) return

    println("[attractor] subprocess_cancel provider=$provider model=$model reason=$reason phase=soft")
    process.destroy()

    val graceful = try {
        process.waitFor(ExecutionRuntime.cancelGraceMillis, TimeUnit.MILLISECONDS)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    if (graceful) return

    println("[attractor] subprocess_cancel provider=$provider model=$model reason=$reason phase=force")
    RuntimeMetrics.subprocessForceKills.incrementAndGet()
    process.destroyForcibly()
    runCatching { process.waitFor(ExecutionRuntime.cancelGraceMillis, TimeUnit.MILLISECONDS) }
}
