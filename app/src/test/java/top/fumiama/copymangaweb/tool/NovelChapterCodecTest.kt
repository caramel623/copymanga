package top.fumiama.copymangaweb.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelChapterCodecTest {
    @Test
    fun plainTextRoundTripsThroughStoredBody() {
        val raw = "第一段 & 符號\"引號\"\r\n\r\n第二段 <尖括號>\r\n繼續同一段"

        val stored = NovelChapterCodec.toStored("text/plain", raw)
        val restored = NovelChapterCodec.toPlainText(stored)

        assertTrue(NovelChapterCodec.isPlain(stored))
        assertEquals("第一段 & 符號\"引號\"\n\n第二段 <尖括號>\n繼續同一段", restored)
    }

    @Test
    fun plainTextStoredBodyIsXhtmlSafe() {
        val stored = NovelChapterCodec.toStored("text/plain", "a < b & c > d \"e\" 'f'")

        assertEquals(NovelChapterCodec.PLAIN_MARKER + "<p>a &lt; b &amp; c &gt; d &quot;e&quot; &apos;f&apos;</p>", stored)
    }

    @Test
    fun htmlResourceKeepsBodyContentWithoutMarker() {
        val raw = "<!DOCTYPE html><html><head><title>t</title></head><body><p>內文&amp;</p></body></html>"

        val stored = NovelChapterCodec.toStored("text/html; charset=utf-8", raw)

        assertFalse(NovelChapterCodec.isPlain(stored))
        assertEquals("<p>內文&amp;</p>", stored)
    }

    @Test
    fun htmlWithoutBodyTagStoredVerbatim() {
        val raw = "<p>無包裹的html</p>"

        assertEquals(raw, NovelChapterCodec.toStored("text/html", raw))
    }
}
