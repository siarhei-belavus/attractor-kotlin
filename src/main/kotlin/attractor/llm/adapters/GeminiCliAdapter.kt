package attractor.llm.adapters

import attractor.llm.*
import java.io.IOException
import java.util.UUID

/**
 * Google Gemini CLI-backed ProviderAdapter.
 * Invokes the `gemini` CLI binary (or a custom command template) via ProcessBuilder.
 * The command template supports `{prompt}` and `{model}` substitution.
 * Example template: "gemini --yolo --model {model} -p {prompt}"
 */
class GeminiCliAdapter(
    private val commandTemplate: String = "gemini --yolo --model {model} -p {prompt}",
    private val runner: ProcessRunner = DefaultProcessRunner
) : ProviderAdapter {

    override val name = "gemini"

    override fun initialize() {
        val binary = commandTemplate.trim().split("\\s+".toRegex()).firstOrNull() ?: "gemini"
        try {
            val proc = ProcessBuilder(binary, "--version")
                .redirectErrorStream(true)
                .start()
            val exit = proc.waitFor()
            if (exit != 0) throw ConfigurationError(
                "CLI binary '$binary' returned exit code $exit. Install it or switch to API mode in Settings."
            )
        } catch (e: IOException) {
            throw ConfigurationError(
                "CLI binary '$binary' not found. Install it or add it to PATH, or switch to API mode in Settings."
            )
        }
    }

    override fun complete(request: Request): LlmResponse {
        val fullText = StringBuilder()
        for (event in stream(request)) {
            if (event.type == StreamEventType.TEXT_DELTA && event.delta != null) {
                fullText.append(event.delta)
            }
            if (event.type == StreamEventType.ERROR && event.error != null) throw event.error
        }
        val msg = Message.assistant(fullText.toString())
        return LlmResponse(
            id = UUID.randomUUID().toString(),
            model = request.model,
            provider = "gemini",
            message = msg,
            finishReason = FinishReason("stop", "end"),
            usage = Usage.empty()
        )
    }

    override fun stream(request: Request): Sequence<StreamEvent> =
        streamCliProcess(name, commandTemplate, request, runner)
}
