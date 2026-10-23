package top.fumiama.copymangaweb.tool

import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipFile

class EpubChapterReader(private val epub: File) {
    fun chapterHrefs(): List<String> = ZipFile(epub).use { zip ->
        val opf = zip.getInputStream(zip.getEntry("OEPF/content.opf"))
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val manifest = Regex("<item id=\"([^\"]+)\" href=\"([^\"]+)\"")
            .findAll(opf)
            .associate { it.groupValues[1] to it.groupValues[2] }
        Regex("<itemref idref=\"([^\"]+)\"/>")
            .findAll(opf)
            .map { "OEPF/${manifest.getValue(it.groupValues[1])}" }
            .toList()
    }

    fun readChapter(entryName: String): String = ZipFile(epub).use { zip ->
        val entry = zip.getEntry(entryName) ?: throw FileNotFoundException(entryName)
        val content = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        Regex("<body\\b[^>]*>(.*?)</body>", setOf(RegexOption.DOT_MATCHES_ALL))
            .find(content)
            ?.groupValues
            ?.get(1)
            ?: throw IllegalArgumentException("Chapter has no body element: $entryName")
    }
}
