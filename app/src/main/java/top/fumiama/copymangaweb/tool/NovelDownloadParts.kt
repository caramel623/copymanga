package top.fumiama.copymangaweb.tool

import java.io.File

object NovelDownloadParts {
    fun prepare(directory: File, traditional: Boolean, volumeIds: List<String>) {
        val marker = File(directory, ".resume-mode")
        val expectedMode = buildString {
            append(traditional)
            volumeIds.forEach { append('\n').append(it.length).append(':').append(it) }
        }
        if (directory.exists() && (!marker.isFile || marker.readText() != expectedMode)) {
            check(directory.deleteRecursively()) { "Unable to reset novel download parts: $directory" }
        }
        check(directory.isDirectory || directory.mkdirs()) { "Unable to create novel download parts: $directory" }
        if (!marker.isFile) marker.writeText(expectedMode)
    }
}
