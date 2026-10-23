package top.fumiama.copymangaweb.tool

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubBuilder(
    private val output: File,
    private val identifier: String,
    private val title: String,
    private val author: String,
) {
    private data class Chapter(val href: String, val title: String)

    private val part = File(output.absolutePath + ".part")
    private val chapters = mutableListOf<Chapter>()
    private val zip: ZipOutputStream
    private var finished = false

    init {
        val parent = output.absoluteFile.parentFile
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Unable to create output directory: $parent")
        }
        if (part.exists() && !part.delete()) {
            throw IOException("Unable to remove partial EPUB: $part")
        }
        zip = ZipOutputStream(FileOutputStream(part))
        try {
            val mimetype = "application/epub+zip".toByteArray(StandardCharsets.UTF_8)
            val crc = CRC32().apply { update(mimetype) }
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetype.size.toLong()
                this.crc = crc.value
            })
            zip.write(mimetype)
            zip.closeEntry()
        } catch (t: Throwable) {
            runCatching { zip.close() }
            part.delete()
            throw t
        }
    }

    fun addChapter(href: String, title: String, bodyXhtml: String) {
        check(!finished) { "EPUB has already been finished" }
        val chapter = Chapter(href, title)
        val content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" " +
            "\"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">" +
            "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>${escape(title)}</title></head>" +
            "<body>$bodyXhtml</body></html>"
        writeEntry("OEPF/$href", content)
        chapters.add(chapter)
    }

    fun finish(): File {
        check(!finished) { "EPUB has already been finished" }
        if (chapters.isEmpty()) {
            zip.close()
            finished = true
            part.delete()
            throw IllegalStateException("An EPUB must contain at least one chapter")
        }

        writeEntry("META-INF/container.xml", containerXml())
        writeEntry("OEPF/content.opf", contentOpf())
        writeEntry("OEPF/nav.ncx", navigationNcx())
        zip.close()
        finished = true

        if (output.exists() && !output.delete()) {
            throw IOException("Unable to replace output file: $output")
        }
        if (!part.renameTo(output)) {
            FileInputStream(part).use { input ->
                FileOutputStream(output).use { target -> input.copyTo(target) }
            }
            if (!part.delete()) {
                throw IOException("Unable to remove partial EPUB: $part")
            }
        }
        return output
    }

    fun abort() {
        runCatching { zip.close() }
        finished = true
        part.delete()
    }

    private fun writeEntry(name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun containerXml(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">" +
            "<rootfiles><rootfile full-path=\"OEPF/content.opf\" " +
            "media-type=\"application/oebps-package+xml\"/></rootfiles></container>"

    private fun contentOpf(): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        append("<package xmlns=\"http://www.idpf.org/2007/opf\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" version=\"2.0\" unique-identifier=\"bookid\">")
        append("<metadata><dc:identifier id=\"bookid\">${escape(identifier)}</dc:identifier>")
        append("<dc:title>${escape(title)}</dc:title><dc:creator>${escape(author)}</dc:creator>")
        append("<dc:language>zh</dc:language></metadata><manifest>")
        append("<item id=\"ncx\" href=\"nav.ncx\" media-type=\"application/x-dtbncx+xml\"/>")
        chapters.forEachIndexed { index, chapter ->
            append("<item id=\"chapter${index + 1}\" href=\"${escape(chapter.href)}\" " +
                "media-type=\"application/xhtml+xml\"/>")
        }
        append("</manifest><spine toc=\"ncx\">")
        chapters.forEachIndexed { index, _ -> append("<itemref idref=\"chapter${index + 1}\"/>") }
        append("</spine></package>")
    }

    private fun navigationNcx(): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        append("<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">")
        append("<head><meta name=\"dtb:uid\" content=\"${escape(identifier)}\"/></head>")
        append("<docTitle><text>${escape(title)}</text></docTitle><navMap>")
        chapters.forEachIndexed { index, chapter ->
            val order = index + 1
            append("<navPoint id=\"navPoint$order\" playOrder=\"$order\"><navLabel>")
            append("<text>${escape(chapter.title)}</text></navLabel>")
            append("<content src=\"${escape(chapter.href)}\"/></navPoint>")
        }
        append("</navMap></ncx>")
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            append(when (character) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&apos;"
                else -> character
            })
        }
    }
}
