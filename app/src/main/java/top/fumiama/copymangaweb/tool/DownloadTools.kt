package top.fumiama.copymangaweb.tool

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

class DownloadTools {
    fun getHttpContent(u: String, refer: String? = null, ua: String? = null, cookie: String? = null): ByteArray? {
        Log.d("Mydl", "getHttp: $u")
        if (u.isBlank()) return null
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(u).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            refer?.let { connection?.setRequestProperty("referer", it) }
            ua?.let { connection?.setRequestProperty("User-agent", it) }
            cookie?.let { connection?.setRequestProperty("Cookie", it) }
            if (connection!!.responseCode !in 200..299) return null
            connection!!.inputStream.use { it.readBytes() }
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        } finally {
            connection?.disconnect()
        }
    }
}
