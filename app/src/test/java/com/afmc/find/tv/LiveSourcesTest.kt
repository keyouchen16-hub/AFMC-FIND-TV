package com.afmc.find.tv

import com.afmc.find.tv.core.Links
import com.afmc.find.tv.core.SearchSource
import com.afmc.find.tv.sources.BitSearchSource
import com.afmc.find.tv.sources.DmhySource
import com.afmc.find.tv.sources.KnabenSource
import com.afmc.find.tv.sources.NyaaSource
import com.afmc.find.tv.sources.PirateBaySource
import com.afmc.find.tv.sources.TorrentsCsvSource
import org.junit.Assume
import org.junit.Test

/**
 * Live checks against the real public endpoints. Skipped when the machine has no network.
 * These are the same calls the TV app makes; they prove the parsers still match the services.
 */
class LiveSourcesTest {

    private val sources: List<SearchSource> = listOf(
        KnabenSource(),
        BitSearchSource(),
        PirateBaySource(),
        NyaaSource(),
        DmhySource(),
        TorrentsCsvSource(),
    )

    private fun networkAvailable(): Boolean = try {
        val socket = java.net.Socket()
        socket.connect(java.net.InetSocketAddress("api.knaben.org", 443), 5000)
        socket.close()
        true
    } catch (_: Exception) {
        false
    }

    @Test
    fun publicSourcesReturnUsableRows() {
        Assume.assumeTrue("no network", networkAvailable())

        var alive = 0
        val failures = ArrayList<String>()
        for (source in sources) {
            val rows = try {
                source.search("ubuntu")
            } catch (t: Throwable) {
                failures.add("${source.name}: ${t.message}")
                continue
            }
            println("${source.name} -> ${rows.size} rows")
            if (rows.isEmpty()) {
                failures.add("${source.name}: empty")
                continue
            }
            alive++
            for (row in rows) {
                check(row.title.isNotBlank()) { "${source.name} blank title" }
                check(Links.magnetHash(row.magnet) != null) { "${source.name} bad magnet for ${row.title}" }
            }
        }

        println("alive sources: $alive/${sources.size}; failures: $failures")
        check(alive >= 2) { "expected at least 2 reachable sources, failures=$failures" }
    }
}
