package top.fumiama.copymangaweb.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class EpubBuilderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun finishCreatesFileWithCallerChosenExtension() {
        val output = temporaryFolder.root.resolve("book.epub")

        val result = builder(output).apply {
            addChapter("ch0001.xhtml", "第一章", "<p>內容</p>")
        }.finish()

        assertTrue(result.isFile)
        assertEquals("book.epub", result.name)
    }

    @Test
    fun mimetypeIsFirstStoredEntryWithRequiredContent() {
        val output = buildBook()

        ZipFile(output).use { zip ->
            val first = zip.entries().nextElement()
            assertEquals("mimetype", first.name)
            assertEquals(ZipEntry.STORED, first.method)
            assertEquals("application/epub+zip", zip.getInputStream(first).bufferedReader().use { it.readText() })
        }
    }

    @Test
    fun archiveContainsPackageNavigationAndChapterFiles() {
        val output = buildBook()

        ZipFile(output).use { zip ->
            assertTrue(zip.getEntry("META-INF/container.xml") != null)
            assertTrue(zip.getEntry("OEPF/content.opf") != null)
            assertTrue(zip.getEntry("OEPF/nav.ncx") != null)
            assertTrue(zip.getEntry("OEPF/ch0001.xhtml") != null)
            assertTrue(zip.getEntry("OEPF/ch0002.xhtml") != null)
        }
    }

    @Test
    fun readerReturnsChaptersInSpineOrderAndPreservesBodyContent() {
        val output = temporaryFolder.root.resolve("roundtrip.epub")
        val bodies = listOf("<p>第一章內容</p>", "<p>第二章，中文內容</p>")
        builder(output).apply {
            addChapter("ch0001.xhtml", "第一章", bodies[0])
            addChapter("ch0002.xhtml", "第二章", bodies[1])
        }.finish()
        val reader = EpubChapterReader(output)

        assertEquals(listOf("OEPF/ch0001.xhtml", "OEPF/ch0002.xhtml"), reader.chapterHrefs())
        assertEquals(bodies[0], reader.readChapter("OEPF/ch0001.xhtml"))
        assertEquals(bodies[1], reader.readChapter("OEPF/ch0002.xhtml"))
    }

    @Test
    fun xmlMetadataAndChapterTitlesAreEscapedWithoutChangingBody() {
        val output = temporaryFolder.root.resolve("escaped.epub")
        val specialTitle = "A&<>\"中文"
        val escapedTitle = "A&amp;&lt;&gt;&quot;中文"
        builder(output, identifier = "id&<>", title = specialTitle, author = "作者 & <編輯>").apply {
            addChapter("ch0001.xhtml", specialTitle, "<p>原樣 &amp; 中文</p>")
        }.finish()

        ZipFile(output).use { zip ->
            val opf = zip.getInputStream(zip.getEntry("OEPF/content.opf")).bufferedReader().use { it.readText() }
            val ncx = zip.getInputStream(zip.getEntry("OEPF/nav.ncx")).bufferedReader().use { it.readText() }
            val chapter = zip.getInputStream(zip.getEntry("OEPF/ch0001.xhtml")).bufferedReader().use { it.readText() }
            assertTrue(opf.contains("<dc:identifier id=\"bookid\">id&amp;&lt;&gt;</dc:identifier>"))
            assertTrue(opf.contains("<dc:creator>作者 &amp; &lt;編輯&gt;</dc:creator>"))
            assertTrue(opf.contains("<dc:title>$escapedTitle</dc:title>"))
            assertTrue(ncx.contains("<text>$escapedTitle</text>"))
            assertTrue(chapter.contains("<title>$escapedTitle</title>"))
            assertTrue(chapter.contains("<body><p>原樣 &amp; 中文</p></body>"))
        }
        assertEquals("<p>原樣 &amp; 中文</p>", EpubChapterReader(output).readChapter("OEPF/ch0001.xhtml"))
    }

    @Test
    fun finishingWithoutChaptersFails() {
        val output = temporaryFolder.root.resolve("empty.epub")
        val epubBuilder = builder(output)

        try {
            epubBuilder.finish()
            assertFalse("finish should reject an EPUB without chapters", true)
        } catch (_: IllegalStateException) {
            assertFalse(output.exists())
        }
    }

    @Test
    fun abortDiscardsPartialArchive() {
        val output = temporaryFolder.root.resolve("aborted.epub")
        val epubBuilder = builder(output).apply { addChapter("ch0001.xhtml", "第一章", "<p>內容</p>") }

        epubBuilder.abort()

        assertFalse(output.exists())
        assertFalse(File(output.absolutePath + ".part").exists())
    }

    @Test
    fun missingChapterEntryThrowsFileNotFoundException() {
        val output = buildBook()

        try {
            EpubChapterReader(output).readChapter("OEPF/missing.xhtml")
            assertFalse("missing chapter should throw", true)
        } catch (_: FileNotFoundException) {
            assertTrue(output.isFile)
        }
    }

    private fun buildBook(): java.io.File {
        val output = temporaryFolder.root.resolve("book.epub")
        builder(output).apply {
            addChapter("ch0001.xhtml", "第一章", "<p>內容一</p>")
            addChapter("ch0002.xhtml", "第二章", "<p>內容二</p>")
        }.finish()
        return output
    }

    private fun builder(
        output: java.io.File,
        identifier: String = "book-id",
        title: String = "書名",
        author: String = "作者"
    ) = EpubBuilder(output, identifier, title, author)
}
