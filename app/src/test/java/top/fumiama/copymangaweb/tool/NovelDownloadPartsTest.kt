package top.fumiama.copymangaweb.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NovelDownloadPartsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sameModeKeepsCompletedPartsForResume() {
        val directory = temporaryFolder.newFolder("parts")
        NovelDownloadParts.prepare(directory, traditional = true)
        val chapter = File(directory, "ch0001.xhtml.txt").apply { writeText("converted") }

        NovelDownloadParts.prepare(directory, traditional = true)

        assertEquals("converted", chapter.readText())
    }

    @Test
    fun changedModeDiscardsPartsFromPreviousMode() {
        val directory = temporaryFolder.newFolder("parts")
        NovelDownloadParts.prepare(directory, traditional = true)
        File(directory, "ch0001.xhtml.txt").writeText("converted")

        NovelDownloadParts.prepare(directory, traditional = false)

        assertFalse(File(directory, "ch0001.xhtml.txt").exists())
        assertEquals("false", File(directory, ".traditional").readText())
    }

    @Test
    fun legacyPartsWithoutModeMarkerAreDiscarded() {
        val directory = temporaryFolder.newFolder("parts")
        val legacyPart = File(directory, "ch0001.xhtml.txt").apply { writeText("unknown mode") }

        NovelDownloadParts.prepare(directory, traditional = true)

        assertFalse(legacyPart.exists())
        assertTrue(File(directory, ".traditional").isFile)
    }
}
