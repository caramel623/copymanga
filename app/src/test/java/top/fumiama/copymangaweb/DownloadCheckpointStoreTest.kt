package top.fumiama.copymangaweb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.fumiama.copymangaweb.tool.DownloadCheckpointStore
import java.nio.file.Files

class DownloadCheckpointStoreTest {
    @Test
    fun checkpointPersistsUrlsAndCompletedPages() {
        val root = Files.createTempDirectory("copymanga-checkpoint").toFile()
        try {
            val store = DownloadCheckpointStore(root)
            val urls = arrayOf("https://example/1.jpg", "https://example/2.jpg")
            val checkpoint = store.prepareDownload("chapter", "comic", "comic-01", urls, true, null)

            store.stageImage("chapter", 0).writeBytes(byteArrayOf(1, 2, 3))
            store.markCompleted(checkpoint, 0)

            val restored = DownloadCheckpointStore(root).load("chapter")!!
            assertArrayEquals(urls, restored.imageUrls)
            assertTrue(restored.completed[0])
            assertFalse(restored.completed[1])
            assertEquals("comic-01", restored.outputName)
            assertTrue(store.stageImage("chapter", 0).isFile)

            store.clear("chapter")
            assertNull(store.load("chapter"))
            assertFalse(store.stageDirectory("chapter").listFiles()?.isNotEmpty() == true)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun changedUrlsResetStagedProgressButCompletedCheckpointRemainsPendingUntilFinalized() {
        val root = Files.createTempDirectory("copymanga-checkpoint-reset").toFile()
        try {
            val store = DownloadCheckpointStore(root)
            val first = store.storeUrls("chapter", "comic", arrayOf("https://example/1.jpg"))
            store.stageImage("chapter", 0).writeBytes(byteArrayOf(1))
            store.markCompleted(first, 0)

            assertEquals(1, store.pendingForComic("comic").size)

            val changed = store.storeUrls(
                "chapter",
                "comic",
                arrayOf("https://example/new-1.jpg", "https://example/new-2.jpg")
            )
            assertEquals(2, changed.completed.size)
            assertFalse(changed.completed.any { it })
            assertFalse(store.stageImage("chapter", 0).isFile)
        } finally {
            root.deleteRecursively()
        }
    }
}
