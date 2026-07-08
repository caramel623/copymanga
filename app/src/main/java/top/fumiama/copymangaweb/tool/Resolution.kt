package top.fumiama.copymangaweb.tool

class Resolution(private val original: Regex) {
    fun wrap(u: String, width: Int = 1500) : String =
        if (width <= 0) u else u.replace(original, "c${width}x.")
}
