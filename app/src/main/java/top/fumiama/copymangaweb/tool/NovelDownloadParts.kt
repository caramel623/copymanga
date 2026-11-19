package top.fumiama.copymangaweb.tool

import java.io.File

object NovelDownloadParts {
    fun prepare(directory: File, traditional: Boolean, volumeIds: List<String>) {
        val marker = File(directory, ".resume-mode")
        val expectedMode = buildString {
            append(traditional)
            volumeIds.forEach { append('\n').append(it.length).append(':').append(it) }
        }
        val existing = if (marker.isFile) marker.readText() else null
        if (directory.exists() && !continuesFrom(existing, expectedMode)) {
            check(directory.deleteRecursively()) { "Unable to reset novel download parts: $directory" }
        }
        check(directory.isDirectory || directory.mkdirs()) { "Unable to create novel download parts: $directory" }
        if (existing != expectedMode) {
            val tmp = File(directory, "${marker.name}.tmp")
            tmp.writeText(expectedMode)
            if (!tmp.renameTo(marker)) {
                marker.writeText(expectedMode)
                tmp.delete()
            }
        }
    }

    /** Keep old parts when the volume list only grew at the end, so an update downloads only new volumes. */
    private fun continuesFrom(existing: String?, expected: String): Boolean {
        if (existing == null) return false
        val old = existing.split('\n')
        val new = expected.split('\n')
        return old.size <= new.size && old.indices.all { old[it] == new[it] }
    }
}
