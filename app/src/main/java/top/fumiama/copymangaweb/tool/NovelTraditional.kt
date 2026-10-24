package top.fumiama.copymangaweb.tool

import android.os.Build

object NovelTraditional {
    fun enabled(properties: PropertiesTools): Boolean =
        properties["ranobeTraditional"] != "false" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun convert(text: String): String {
        val type = Class.forName("android.icu.text.Transliterator")
        val converter = type.getMethod("getInstance", String::class.java).invoke(null, "Simplified-Traditional")
        return type.getMethod("transliterate", String::class.java).invoke(converter, text) as String
    }

    fun convertIfEnabled(properties: PropertiesTools, text: String): String =
        if (enabled(properties)) runCatching { convert(text) }.getOrDefault(text) else text
}
