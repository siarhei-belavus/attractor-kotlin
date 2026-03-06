package attractor.cli

enum class OutputFormat { TEXT, JSON }

data class CliContext(
    val baseUrl: String = "http://localhost:7070",
    val outputFormat: OutputFormat = OutputFormat.TEXT
)
