package com.afmc.find.tv.core

import java.net.URLDecoder

/** Port of the desktop Links helper: hash normalisation and magnet parsing. */
object Links {
    private val HEX40 = Regex("^[a-fA-F0-9]{40}$")
    private val V2 = Regex("^1220[a-fA-F0-9]{64}$")
    private val BASE32 = Regex("^[A-Z2-7]{32}$", RegexOption.IGNORE_CASE)

    fun normalizeHash(value: String?): String? {
        var v = value?.trim().orEmpty()
        if (v.isEmpty()) return null
        if (HEX40.matches(v)) return "btih:" + v.lowercase()
        if (v.startsWith("btih:", ignoreCase = true)) return normalizeHash(v.substring(5))
        if (v.startsWith("btmh:", ignoreCase = true)) v = v.substring(5)
        if (V2.matches(v)) return "btmh:" + v.lowercase()
        if (!BASE32.matches(v)) return null
        val bytes = ArrayList<Byte>()
        var bits = 0
        var acc = 0
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        for (ch in v.uppercase()) {
            acc = (acc shl 5) or alphabet.indexOf(ch)
            bits += 5
            if (bits >= 8) {
                bits -= 8
                bytes.add(((acc shr bits) and 0xFF).toByte())
                acc = acc and ((1 shl bits) - 1)
            }
        }
        return "btih:" + bytes.joinToString("") { "%02x".format(it) }
    }

    fun magnetHash(value: String?): String? {
        if (value.isNullOrEmpty() || value.length > 16384) return null
        if (!value.startsWith("magnet:?", ignoreCase = true)) return null
        for (pair in value.substring(8).split('&')) {
            val parts = pair.split('=', limit = 2)
            if (parts.size != 2 || !parts[0].equals("xt", ignoreCase = true)) continue
            val xt = runCatching { URLDecoder.decode(parts[1], "UTF-8") }.getOrNull() ?: continue
            if (xt.startsWith("urn:btih:", ignoreCase = true)) {
                return normalizeHash(xt.substring(9))
            }
            if (xt.startsWith("urn:btmh:", ignoreCase = true) && V2.matches(xt.substring(9))) {
                return "btmh:" + xt.substring(9).lowercase()
            }
        }
        return null
    }

    fun hexOf(normalized: String?): String? = normalized?.substringAfter(':')?.lowercase()

    fun magnetFor(hash: String?, title: String): String? {
        val h = hexOf(normalizeHash(hash)) ?: return null
        if (h.length != 40) return null
        val dn = java.net.URLEncoder.encode(title, "UTF-8").replace("+", "%20")
        return "magnet:?xt=urn:btih:$h&dn=$dn&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce" +
            "&tr=udp%3A%2F%2Fopen.demonii.com%3A1337%2Fannounce" +
            "&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce"
    }
}
