package top.fumiama.copymangaweb.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NovelDownloadStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun store() = NovelDownloadStore(
        File(temporaryFolder.root, "novel_downloads.json"),
        temporaryFolder.newFolder("novels")
    )

    private fun record(slug: String = "bqg_1") = NovelDownloadRecord(
        slug = slug, name = "書名", author = "作者", sizeBytes = 100, downloadedAt = 7,
        volumeIds = listOf("v1", "v2"), volumeNames = listOf("第一卷", "第二卷"), traditional = true
    )

    @Test
    fun markDownloadedRoundTripsRecordWithVolumeLists() {
        val store = store()

        store.markDownloaded(record())

        val loaded = store.recordFor("bqg_1")
        assertNotNull(loaded)
        assertEquals("書名", loaded!!.name)
        assertEquals(listOf("v1", "v2"), loaded.volumeIds)
        assertEquals(listOf("第一卷", "第二卷"), loaded.volumeNames)
        assertTrue(loaded.traditional)
    }

    @Test
    fun fileForReturnsEpubWhenItExists() {
        val store = store()
        store.markDownloaded(record())
        store.epubFile("bqg_1").writeText("x")

        assertEquals(store.epubFile("bqg_1"), store.fileFor("bqg_1"))
    }

    @Test
    fun fileForDropsRecordWhenFileMissing() {
        val store = store()
        store.markDownloaded(record())

        assertNull(store.fileFor("bqg_1"))
        assertNull(store.recordFor("bqg_1"))
    }

    @Test
    fun removeDownloadDeletesFileAndRecord() {
        val store = store()
        store.markDownloaded(record())
        store.epubFile("bqg_1").writeText("x")

        store.removeDownload("bqg_1")

        assertFalse(store.epubFile("bqg_1").exists())
        assertNull(store.recordFor("bqg_1"))
    }

    @Test
    fun corruptIndexReadsAsEmptyAndStillWritable() {
        val store = store()
        File(temporaryFolder.root, "novel_downloads.json").writeText("{not json")

        assertTrue(store.list().isEmpty())
        store.markDownloaded(record())
        assertNotNull(store.recordFor("bqg_1"))
    }

    @Test
    fun legacyRecordWithoutTraditionalFieldDefaultsToFalse() {
        val store = store()
        File(temporaryFolder.root, "novel_downloads.json").writeText(
            """[{"slug":"bqg_1","name":"書名","sizeBytes":100,"downloadedAt":7,"volumeIds":["v1"]}]"""
        )

        assertFalse(store.recordFor("bqg_1")!!.traditional)
    }

    @Test
    fun epubFileSanitizesSlug() {
        val store = store()

        val file = store.epubFile("../../evil")

        assertEquals(store.epubFile("x").parentFile, file.parentFile)
        assertFalse(file.name.contains("/"))
        assertFalse(file.name.contains("\\"))
    }
}
