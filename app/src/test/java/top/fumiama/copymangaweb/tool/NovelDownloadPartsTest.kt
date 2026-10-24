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
        NovelDownloadParts.prepare(directory, traditional = true, volumeIds = listOf("v1", "v2"))
        val chapter = File(directory, "ch0001.xhtml.txt").apply { writeText("converted") }

        NovelDownloadParts.prepare(directory, traditional = true, volumeIds = listOf("v1", "v2"))

        assertEquals("converted", chapter.readText())
    }

    @Test
    fun changedModeDiscardsPartsFromPreviousMode() {
        val directory = temporaryFolder.newFolder("parts")
        NovelDownloadParts.prepare(directory, traditional = true, volumeIds = listOf("v1", "v2"))
        File(directory, "ch0001.xhtml.txt").writeText("converted")

        NovelDownloadParts.prepare(directory, traditional = false, volumeIds = listOf("v1", "v2"))

        assertFalse(File(directory, "ch0001.xhtml.txt").exists())
        assertEquals("false\n2:v1\n2:v2", File(directory, ".resume-mode").readText())
    }

    @Test
    fun legacyPartsWithoutModeMarkerAreDiscarded() {
        val directory = temporaryFolder.newFolder("parts")
        val legacyPart = File(directory, "ch0001.xhtml.txt").apply { writeText("unknown mode") }

        NovelDownloadParts.prepare(directory, traditional = true, volumeIds = listOf("v1"))

        assertFalse(legacyPart.exists())
        assertTrue(File(directory, ".resume-mode").isFile)
    }

    @Test
    fun changedVolumeOrderDiscardsParts() {
        val directory = temporaryFolder.newFolder("parts")
        NovelDownloadParts.prepare(directory, traditional = true, volumeIds = listOf("v1", "v2"))
        File(directory, "ch0001.xhtml.txt").writeText("chapter v1")

        NovelDownloadParts.prepare(directory, traditional = true, volumeIds = listOf("v2", "v1"))

        assertFalse(File(directory, "ch0001.xhtml.txt").exists())
    }
}
