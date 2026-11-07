package com.afmc.find.tv

import com.afmc.find.tv.core.Links
import com.afmc.find.tv.core.Ranking
import com.afmc.find.tv.model.TorrentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingTest {

    @Test
    fun canonicalNormalisesSeasonAndResolution() {
        assertEquals("s01", Ranking.canonical("第1季"))
        assertEquals("e05", Ranking.canonical("第5集"))
        assertEquals("2160p", Ranking.canonical("4K"))
        assertTrue(Ranking.canonical("流浪地球2 2160p").startsWith("流浪地球2 2160p"))
    }

    @Test
    fun exactTitleScoresTierZero() {
        val row = TorrentResult(
            title = "流浪地球2 2023 2160p BluRay x265",
            source = "test",
            magnet = "magnet:?xt=urn:btih:" + "a".repeat(40),
        )
        assertEquals(0, Ranking.matchTier("流浪地球2 2023 2160p", row.title))
        assertTrue(Ranking.score("流浪地球2 2023 2160p", row, 1_700_000_000L) > 0)
    }

    @Test
    fun missingYearIsTierOne() {
        val row = TorrentResult(title = "流浪地球2 2160p BluRay", source = "test")
        assertEquals(1, Ranking.matchTier("流浪地球2 2023 2160p", row.title))
    }

    @Test
    fun unrelatedTitleIsTierThree() {
        assertEquals(3, Ranking.matchTier("流浪地球2", "Ubuntu 22.04 LTS"))
    }

    @Test
    fun seedersRaiseScore() {
        val base = TorrentResult(title = "Blender 4.0", source = "a")
        val seeded = base.copy(seeders = 500)
        val now = 1_700_000_000L
        assertTrue(Ranking.score("Blender 4.0", seeded, now) > Ranking.score("Blender 4.0", base, now))
    }

    @Test
    fun dedupeKeepsHealthiestCopy() {
        val hash = "b".repeat(40)
        val weak = TorrentResult(title = "Blender", source = "a", hash = hash, seeders = 2)
        val strong = TorrentResult(title = "Blender", source = "b", hash = hash, seeders = 90)
        val deduped = Ranking.dedupe(listOf(weak, strong))
        assertEquals(1, deduped.size)
        assertEquals("b", deduped[0].source)
    }

    @Test
    fun magnetHashAcceptsHexAndBuildsMagnet() {
        val hash = "C12FE1C06BAC9B7E8C1E1D3CF00C0B3A6E9A7B52"
        assertNotNull(Links.normalizeHash(hash))
        assertNotNull(Links.magnetHash("magnet:?xt=urn:btih:$hash&dn=test"))
        val magnet = Links.magnetFor(hash, "流浪地球 2")
        assertNotNull(magnet)
        assertTrue(magnet!!.startsWith("magnet:?xt=urn:btih:" + hash.lowercase()))
    }
}
