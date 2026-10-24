package com.newsmead

import com.newsmead.data.news.NewsDataApi
import com.newsmead.data.news.NewsDataApi.Query
import com.newsmead.data.news.NewsDataApi.Region
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NewsDataApiTest {

    // Stand-ins for DataHelper.sourceNameMap / sourceImageMap
    private val names = { key: String -> if (key == "gmanews") "GMA News" else "" }
    private val images = { key: String -> if (key == "gmanews") "source_gmanews" else "sample_source_image" }

    private fun params(url: String): Map<String, String> =
        url.substringAfter("?").split("&").associate {
            val (k, v) = it.split("=", limit = 2)
            k to java.net.URLDecoder.decode(v, "UTF-8")
        }

    @Test
    fun localRegionFiltersToPhilippines() {
        val p = params(NewsDataApi.buildUrl("KEY", Query(region = Region.LOCAL, language = "Filipino")))
        assertEquals("KEY", p["apikey"])
        assertEquals("ph", p["country"])
        assertEquals("tl", p["language"])
        assertEquals("10", p["size"])
    }

    @Test
    fun internationalRegionUsesNonPhCountries() {
        val p = params(NewsDataApi.buildUrl("KEY", Query(region = Region.INTERNATIONAL, language = "english")))
        assertNull(p["country"])
        assertEquals("ph", p["excludecountry"])
        assertEquals("top", p["prioritydomain"])
        assertEquals("world", p["category"])
        assertEquals("en", p["language"])
    }

    @Test
    fun internationalKeepsChosenCategoryAndFallsBackToEnglish() {
        val p = params(NewsDataApi.buildUrl("KEY",
            Query(region = Region.INTERNATIONAL, language = "Filipino", category = "sports")))
        assertEquals("sports", p["category"])
        assertEquals("tl,en", p["language"])
    }

    @Test
    fun missingLanguageDefaultsToEnglishAndFilipino() {
        val p = params(NewsDataApi.buildUrl("KEY", Query(region = Region.ALL, searchText = "typhoon")))
        assertEquals("en,tl", p["language"])
    }

    @Test
    fun searchAndCategoryAreMapped() {
        val p = params(NewsDataApi.buildUrl("KEY",
            Query(region = Region.ALL, category = "news", searchText = "typhoon signal", size = 50)))
        assertNull(p["country"])
        assertEquals("top", p["category"])
        assertEquals("typhoon signal", p["q"])
        assertEquals("10", p["size"]) // capped at the free-plan maximum
    }

    @Test
    fun opinionBecomesKeyword() {
        val p = params(NewsDataApi.buildUrl("KEY", Query(category = "opinion", searchText = "budget")))
        assertNull(p["category"])
        assertEquals("budget AND opinion", p["q"])
    }

    @Test
    fun knownSourceReplacesCountry() {
        val p = params(NewsDataApi.buildUrl("KEY", Query(source = "gmanews")))
        assertEquals("gmanetwork,gmanetwork_ph", p["domain"])
        assertNull(p["country"])
    }

    @Test
    fun redactHidesKey() {
        val url = NewsDataApi.buildUrl("pub_secret", Query())
        assertFalse(NewsDataApi.redact(url).contains("pub_secret"))
    }

    @Test
    fun parsesFreePlanResponse() {
        val json = JSONObject("""
            {"status":"success","totalResults":2,"nextPage":"abc123","results":[
              {"article_id":"a1","title":"Senate passes budget","link":"https://www.gmanetwork.com/x",
               "description":"The Senate approved the budget on Friday.",
               "content":"ONLY AVAILABLE IN PAID PLANS","pubDate":"2026-09-27 03:15:00",
               "image_url":"https://img/x.jpg","source_id":"gmanetwork","source_name":"Gma Network",
               "source_icon":"https://icon/gma.png","language":"english","category":["top","politics"]},
              {"article_id":"a2","title":"Markets rally","link":"https://bbc.co.uk/y",
               "description":null,"content":null,"pubDate":"2026-09-26 22:00:00","image_url":null,
               "source_id":"bbc","source_name":"BBC","source_icon":"https://icon/bbc.png",
               "language":"tagalog","category":["business"]},
              {"article_id":"a1","title":"duplicate id is dropped","link":"https://dup"}
            ]}
        """.trimIndent())

        val page = NewsDataApi.parse(json, names, images)
        assertEquals("abc123", page.nextPage)
        assertEquals(2, page.articles.size)

        val gma = page.articles[0]
        assertEquals("GMA News", gma.source)            // app's own name wins for known sources
        assertEquals("source_gmanews", gma.sourceImage) // bundled logo
        assertEquals("The Senate approved the budget on Friday.", gma.body) // paid placeholder skipped
        assertEquals("News", gma.category)
        assertEquals("English", gma.language)
        assertEquals("a1", gma.newsId)
        assertEquals("1 min read", gma.readTime)
        assertTrue(gma.date.endsWith(", 2026"))

        val bbc = page.articles[1]
        assertEquals("BBC", bbc.source)
        assertEquals("https://icon/bbc.png", bbc.sourceImage) // remote logo for unknown sources
        assertEquals("Markets rally", bbc.body)               // falls back to the title
        assertEquals("", bbc.imageURL)
        assertEquals("Business", bbc.category)
        assertEquals("Filipino", bbc.language)

        // Names seen in a response can be used as source filters afterwards
        assertEquals("bbc", NewsDataApi.resolveSourceId("BBC"))
        assertEquals("https://icon/bbc.png", NewsDataApi.sourceIconFor("BBC"))
    }

    @Test
    fun errorResponseThrows() {
        val json = JSONObject("""{"status":"error","results":{"message":"API key invalid","code":"Unauthorized"}}""")
        try {
            NewsDataApi.parse(json, names, images)
            fail("expected ApiException")
        } catch (e: NewsDataApi.ApiException) {
            assertEquals("API key invalid", e.message)
            assertEquals("Unauthorized", e.code)
        }
    }

    @Test
    fun readTimeRoundsUp() {
        assertEquals("1 min read", NewsDataApi.readTime(""))
        assertEquals("2 min read", NewsDataApi.readTime("word ".repeat(201)))
    }
}
