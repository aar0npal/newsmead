package com.newsmead

import com.newsmead.data.news.ArticleExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleExtractorTest {

    private val url = "https://news.example.ph/2026/09/27/story"

    // A paragraph of ~40 words, so a handful of them clear MIN_WORDS.
    private fun sentence(n: Int) =
        "Paragraph $n reports that the provincial government approved a new budget for flood " +
            "control projects after weeks of debate, and officials said construction on the " +
            "first dikes along the river would begin before the rainy season arrives next year."

    private fun page(body: String, head: String = "") = """
        <html><head><title>Story</title>$head</head><body>
        <nav><ul><li><a href="/">Home</a></li><li><a href="/news">News</a></li></ul></nav>
        <article>
          <h1>Budget approved</h1>
          $body
        </article>
        <footer><p>© 2026 Example News. All Rights Reserved.</p></footer>
        </body></html>
    """.trimIndent()

    @Test
    fun keepsBodyAndDropsBoilerplate() {
        val html = page("""
            <p>Published September 27, 2026 9:00 AM PHT</p>
            <p>${sentence(1)}</p>
            <p>STAR / File</p>
            <p>ADVERTISEMENT</p>
            <h2>What happens next</h2>
            <p>${sentence(2)}</p>
            <p>${sentence(3)}</p>
            <p>Government borrowings hit a record high by the end of July, mostly going…</p>
            <p>Read more:</p>
        """)
        val text = ArticleExtractor.extract(url, html)
        assertNotNull(text)
        val paragraphs = text!!.split("\n\n")
        assertEquals(sentence(1), paragraphs.first())
        assertTrue(paragraphs.contains("What happens next")) // real subheading kept
        assertEquals(sentence(3), paragraphs.last())
        listOf("Published", "STAR / File", "ADVERTISEMENT", "borrowings", "Read more", "©")
            .forEach { junk -> assertFalse(junk, text.contains(junk)) }
    }

    @Test
    fun tooShortIsRejected() {
        assertNull(ArticleExtractor.extract(url, page("<p>Photos: fun travel moments.</p>")))
    }

    @Test
    fun cutsFooterNearTheEnd() {
        val html = page("""
            <p>${sentence(1)}</p><p>${sentence(2)}</p><p>${sentence(3)}</p><p>${sentence(4)}</p>
            <p>SecureDrop</p>
            <p>If you can safely use the tor network you can send messages to our newsroom.</p>
        """)
        val text = ArticleExtractor.extract(url, html)!!
        assertFalse(text.contains("tor network"))
        assertTrue(text.endsWith(sentence(4)))
    }

    @Test
    fun fallsBackToNextDataWhenHtmlHasNoBody() {
        // JavaScript-rendered pages (ABS-CBN) ship the body only in page data.
        val bodyHtml = (1..4).joinToString("") { "<p>${sentence(it)}</p>" }.replace("\"", "\\\"")
        val html = """
            <html><body><h1>Budget approved</h1><div id="app"></div>
            <script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"article":{"title":"Budget approved","body_html":"$bodyHtml"},
             "related":[{"body_html":"<p>Short teaser.</p>"}]}}}
            </script></body></html>
        """.trimIndent()
        val text = ArticleExtractor.extract(url, html)
        assertNotNull(text)
        assertEquals(4, text!!.split("\n\n").size)
        assertTrue(text.startsWith("Paragraph 1"))
    }

    @Test
    fun usesJsonLdArticleBody() {
        val body = (1..3).joinToString("\\n\\n") { sentence(it) }
        val html = """
            <html><head><script type="application/ld+json">
            {"@type":"NewsArticle","headline":"Budget approved","articleBody":"$body"}
            </script></head><body><h1>Budget approved</h1><div class="app"></div></body></html>
        """.trimIndent()
        val text = ArticleExtractor.extract(url, html)
        assertNotNull(text)
        assertEquals(3, text!!.split("\n\n").size)
    }

    @Test
    fun prefersParagraphedReadabilityOverUnbrokenJsonLd() {
        // JSON-LD bodies are often one block without breaks; keep the page's paragraphs.
        val oneBlock = (1..4).joinToString(" ") { sentence(it) }
        val html = page(
            (1..4).joinToString("") { "<p>${sentence(it)}</p>" },
            head = """<script type="application/ld+json">{"@type":"NewsArticle","articleBody":"$oneBlock"}</script>"""
        )
        assertEquals(4, ArticleExtractor.extract(url, html)!!.split("\n\n").size)
    }
}
