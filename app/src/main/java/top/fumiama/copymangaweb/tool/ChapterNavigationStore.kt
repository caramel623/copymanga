package top.fumiama.copymangaweb.tool

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.Properties

data class ChapterNavigationState(
    val originUrl: String? = null,
    val selectionUrl: String? = null,
    val selectionPath: String? = null,
    val comicSlug: String? = null,
    val latestChapterUrl: String? = null,
    val phase: String? = null
)

/** Temporary navigation state used only while the user is inside a comic flow. */
class ChapterNavigationStore(context: Context) {
    private val atomicFile = AtomicFile(File(context.cacheDir, FILE_NAME))

    @Synchronized
    fun read(): ChapterNavigationState {
        val properties = readProperties()
        return ChapterNavigationState(
            originUrl = properties.value(KEY_ORIGIN_URL),
            selectionUrl = properties.value(KEY_SELECTION_URL),
            selectionPath = properties.value(KEY_SELECTION_PATH),
            comicSlug = properties.value(KEY_COMIC_SLUG),
            latestChapterUrl = properties.value(KEY_CHAPTER_URL),
            phase = properties.value(KEY_PHASE)
        )
    }

    @Synchronized
    fun recordEntry(originUrl: String?, selectionUrl: String, comicSlug: String) {
        writeProperties(Properties().apply {
            putValue(KEY_ORIGIN_URL, originUrl)
            putValue(KEY_SELECTION_URL, selectionUrl)
            putValue(KEY_COMIC_SLUG, comicSlug)
            putValue(KEY_PHASE, PHASE_SELECTION)
        })
    }

    @Synchronized
    fun recordChapter(chapterUrl: String, selectionPath: String? = null) {
        val properties = readProperties()
        properties.putValue(KEY_CHAPTER_URL, chapterUrl)
        if (selectionPath != null) properties.putValue(KEY_SELECTION_PATH, selectionPath)
        properties.putValue(KEY_PHASE, PHASE_READING)
        writeProperties(properties)
    }

    @Synchronized
    fun markReturningToSelection() = updatePhase(PHASE_RETURNING_SELECTION)

    @Synchronized
    fun markReturningToOrigin() = updatePhase(PHASE_RETURNING_ORIGIN)

    @Synchronized
    fun clear() {
        atomicFile.delete()
    }

    private fun updatePhase(phase: String) {
        val properties = readProperties()
        if (properties.isEmpty) return
        properties.putValue(KEY_PHASE, phase)
        writeProperties(properties)
    }

    private fun readProperties(): Properties = Properties().also { properties ->
        runCatching {
            atomicFile.openRead().use { properties.loadFromXML(it) }
        }
    }

    private fun writeProperties(properties: Properties) {
        var output = atomicFile.startWrite()
        try {
            properties.storeToXML(output, "Temporary chapter navigation state", "UTF-8")
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    private fun Properties.value(key: String): String? =
        getProperty(key)?.takeIf { it.isNotBlank() }

    private fun Properties.putValue(key: String, value: String?) {
        if (value.isNullOrBlank()) remove(key) else setProperty(key, value)
    }

    companion object {
        const val FILE_NAME = "chapter-navigation-state.xml"
        private const val KEY_ORIGIN_URL = "origin_url"
        private const val KEY_SELECTION_URL = "selection_url"
        private const val KEY_SELECTION_PATH = "selection_path"
        private const val KEY_COMIC_SLUG = "comic_slug"
        private const val KEY_CHAPTER_URL = "latest_chapter_url"
        private const val KEY_PHASE = "phase"
        private const val PHASE_SELECTION = "selection"
        private const val PHASE_READING = "reading"
        private const val PHASE_RETURNING_SELECTION = "returning_selection"
        private const val PHASE_RETURNING_ORIGIN = "returning_origin"
    }
}
