package attractor.workspace

import java.io.File
import java.util.concurrent.TimeUnit

data class GitCommit(
    val hash: String,
    val shortHash: String,
    val subject: String,
    val date: String,
    val body: String = ""
)

data class GitSummary(
    val available: Boolean,
    val repoExists: Boolean = false,
    val branch: String = "",
    val commitCount: Int = 0,
    val lastCommit: GitCommit? = null,
    val dirty: Boolean = false,
    val trackedFiles: Int = 0,
    val recent: List<GitCommit> = emptyList()
)

object WorkspaceGit {

    private val gitAvailable: Boolean by lazy {
        runCatching {
            ProcessBuilder("git", "--version")
                .redirectErrorStream(true)
                .start()
                .waitFor(10, TimeUnit.SECONDS)
        }.getOrDefault(false)
    }

    private val gitignoreContent = """
        # macOS
        .DS_Store
        .AppleDouble
        ._*

        # Windows
        Thumbs.db
        Desktop.ini

        # Binaries and compiled output
        *.exe
        *.dll
        *.so
        *.dylib
        *.a
        *.o
        *.obj
        *.class
        *.jar
        *.war
        *.pyc
        *.pyo
        __pycache__/

        # Build output
        target/
        build/
        dist/
        out/
        bin/

        # Dependencies
        node_modules/
        vendor/

        # IDE / Editor
        .idea/
        .vscode/
        *.swp
        *.swo

        # Temp and logs
        *.log
        *.tmp
        *.temp
    """.trimIndent()

    /**
     * Initializes a git repository in [dir] if not already initialized.
     * Creates [dir] if it does not exist, writes a .gitignore, and sets a local
     * git identity so commits work without a global git config.
     * No-op if git is unavailable or [dir] cannot be created.
     */
    fun init(dir: String) {
        if (!gitAvailable) return
        val f = File(dir)
        f.mkdirs()
        if (!f.isDirectory) return
        if (File(f, ".git").exists()) return
        runCatching {
            ProcessBuilder("git", "init")
                .directory(f).redirectErrorStream(true).start()
                .waitFor(30, TimeUnit.SECONDS)
            ProcessBuilder("git", "config", "user.name", "Attractor")
                .directory(f).redirectErrorStream(true).start()
                .waitFor(10, TimeUnit.SECONDS)
            ProcessBuilder("git", "config", "user.email", "attractor@localhost")
                .directory(f).redirectErrorStream(true).start()
                .waitFor(10, TimeUnit.SECONDS)
            File(f, ".gitignore").writeText(gitignoreContent)
            // Commit .gitignore so the workspace starts clean; subsequent commitIfChanged()
            // calls will only see actual run outputs as new changes.
            ProcessBuilder("git", "add", ".gitignore")
                .directory(f).redirectErrorStream(true).start()
                .waitFor(10, TimeUnit.SECONDS)
            ProcessBuilder("git", "commit", "-m", "init: workspace repository")
                .directory(f).redirectErrorStream(true).start()
                .waitFor(30, TimeUnit.SECONDS)
        }
    }

    /**
     * Stages all workspace changes and commits if anything changed.
     * Calls [init] first as a guard (workspace may not have existed at the earlier init call).
     * No-op if git is unavailable, workspace has no .git, or there are no changes to commit.
     *
     * [prompt] is appended as the commit body (blank line + text) when non-blank, truncated to
     * 4000 characters so the commit message stays reasonable in size.
     */
    fun commitIfChanged(dir: String, message: String, prompt: String = "") {
        if (!gitAvailable) return
        init(dir)
        val f = File(dir)
        if (!File(f, ".git").exists()) return
        runCatching {
            ProcessBuilder("git", "add", "-A")
                .directory(f).redirectErrorStream(true).start()
                .waitFor(30, TimeUnit.SECONDS)
            val statusProc = ProcessBuilder("git", "status", "--porcelain")
                .directory(f).redirectErrorStream(true).start()
            val statusOut = statusProc.inputStream.bufferedReader().readText().trim()
            statusProc.waitFor(10, TimeUnit.SECONDS)
            if (statusOut.isEmpty()) return
            val fullMessage = if (prompt.isNotBlank()) {
                val truncated = prompt.trim().take(4000)
                "$message\n\n$truncated"
            } else {
                message
            }
            ProcessBuilder("git", "commit", "-m", fullMessage)
                .directory(f).redirectErrorStream(true).start()
                .waitFor(30, TimeUnit.SECONDS)
        }
    }

    /**
     * Returns a read-only summary of the git repository in [dir].
     * Uses tab-separated log format to avoid conflicts with commit subjects containing pipes.
     * All subprocess calls are non-fatal; degraded [GitSummary] is returned on any error.
     */
    fun summary(dir: String, recentLimit: Int = 20): GitSummary {
        if (!gitAvailable) return GitSummary(available = false)
        val f = File(dir)
        if (!File(f, ".git").exists()) return GitSummary(available = true, repoExists = false)
        return runCatching {
            fun run(vararg args: String): String {
                val proc = ProcessBuilder(*args)
                    .directory(f).redirectErrorStream(true).start()
                val out = proc.inputStream.bufferedReader().readText().trim()
                proc.waitFor(10, TimeUnit.SECONDS)
                return out
            }

            val branch = run("git", "branch", "--show-current")

            val commitCountStr = runCatching { run("git", "rev-list", "--count", "HEAD") }.getOrDefault("")
            val commitCount = commitCountStr.toIntOrNull() ?: 0

            // Fields separated by ASCII unit-separator (\u001f), commits terminated by null byte (\u0000).
            // Using %x1f/%x00 git escape sequences so no shell is involved.
            // Body (%b) may be empty or multi-line; trim() normalises trailing newlines.
            fun parseCommit(block: String): GitCommit? {
                val parts = block.split("\u001f", limit = 5)
                if (parts.size < 4) return null
                return GitCommit(
                    hash = parts[0].trim(),
                    shortHash = parts[1],
                    subject = parts[2],
                    date = parts[3],
                    body = if (parts.size >= 5) parts[4].trim() else ""
                )
            }

            val lastCommit = if (commitCount > 0) {
                runCatching {
                    parseCommit(run("git", "log", "-1", "--format=%H%x1f%h%x1f%s%x1f%ai%x1f%b"))
                }.getOrNull()
            } else null

            val dirty = run("git", "status", "--porcelain").isNotEmpty()

            val trackedFiles = runCatching {
                val proc = ProcessBuilder("git", "ls-files")
                    .directory(f).redirectErrorStream(true).start()
                val count = proc.inputStream.bufferedReader().lines().count().toInt()
                proc.waitFor(10, TimeUnit.SECONDS)
                count
            }.getOrDefault(0)

            val recent = if (commitCount > 0) {
                runCatching {
                    run("git", "log", "--format=%H%x1f%h%x1f%s%x1f%ai%x1f%b%x00", "-$recentLimit")
                        .split("\u0000")
                        .filter { it.isNotBlank() }
                        .mapNotNull { parseCommit(it) }
                }.getOrDefault(emptyList())
            } else emptyList()

            GitSummary(
                available = true,
                repoExists = true,
                branch = branch,
                commitCount = commitCount,
                lastCommit = lastCommit,
                dirty = dirty,
                trackedFiles = trackedFiles,
                recent = recent
            )
        }.getOrDefault(GitSummary(available = true, repoExists = true))
    }
}
