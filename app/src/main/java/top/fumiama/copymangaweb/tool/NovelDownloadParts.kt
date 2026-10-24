package top.fumiama.copymangaweb.tool

import java.io.File

object NovelDownloadParts {
    fun prepare(directory: File, traditional: Boolean) {
        val modeFile = File(directory, ".traditional")
        val expectedMode = traditional.toString()
        if (directory.exists() && (!modeFile.isFile || modeFile.readText() != expectedMode)) {
            check(directory.deleteRecursively()) { "Unable to reset novel download parts: $directory" }
        }
        check(directory.isDirectory || directory.mkdirs()) { "Unable to create novel download parts: $directory" }
        if (!modeFile.isFile) modeFile.writeText(expectedMode)
    }
}
