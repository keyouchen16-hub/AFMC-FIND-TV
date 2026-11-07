package com.afmc.find.tv

import com.afmc.find.tv.net.Rss
import com.afmc.find.tv.net.Sizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RssTest {

    private val torznabXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:torznab="http://torznab.com/schemas/2015/feed">
          <channel>
            <item>
              <title>Test.Movie.2024.1080p.BluRay.x264</title>
              <guid>https://indexer.example/1</guid>
              <link>https://indexer.example/download/1.torrent</link>
              <pubDate>Tue, 10 Oct 2023 12:00:00 +0000</pubDate>
              <torznab:attr name="seeders" value="42"/>
              <torznab:attr name="size" value="1073741824"/>
              <torznab:attr name="infohash" value="C12FE1C06BAC9B7E8C1E1D3CF00C0B3A6E9A7B52"/>
              <enclosure url="magnet:?xt=urn:btih:C12FE1C06BAC9B7E8C1E1D3CF00C0B3A6E9A7B52" type="application/x-bittorrent"/>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun parsesTorznabItemWithAttributes() {
        val items = Rss.items(torznabXml)
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("Test.Movie.2024.1080p.BluRay.x264", item.title)
        assertEquals("42", item.attrs["seeders"])
        assertEquals("1073741824", item.attrs["size"])
        assertEquals("C12FE1C06BAC9B7E8C1E1D3CF00C0B3A6E9A7B52", item.attrs["infohash"])
        assertNotNull(item.pubDate)
        assertTrue(item.enclosure!!.startsWith("magnet:"))
    }

    @Test
    fun parsesRfc822Date() {
        val epoch = Rss.parseDate("Tue, 10 Oct 2023 12:00:00 +0000")
        assertEquals(1696939200L, epoch)
    }

    @Test
    fun parsesIsoDate() {
        assertNotNull(Rss.parseDate("2023-10-10T12:00:00Z"))
        assertNotNull(Rss.parseDate("2023-10-10T12:00:00+08:00"))
    }

    @Test
    fun parsesHumanSizes() {
        assertEquals(1024L * 1024 * 1024, Sizes.parse("1.0GB"))
        assertEquals(1536L * 1024 * 1024, Sizes.parse("大小: 1.5 GiB"))
        assertEquals(700L * 1024 * 1024, Sizes.parse("[700MB]"))
    }
}
