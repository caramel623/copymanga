package top.fumiama.copymangaweb.tool

import android.os.Build

object NovelTraditional {
    fun enabled(properties: PropertiesTools): Boolean =
        properties["ranobeTraditional"] != "false" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun convert(text: String): String = runCatching {
        val type = Class.forName("android.icu.text.Transliterator")
        val converter = type.getMethod("getInstance", String::class.java).invoke(null, "Simplified-Traditional")
        type.getMethod("transliterate", String::class.java).invoke(converter, text) as String
    }.getOrDefault(text)
}
