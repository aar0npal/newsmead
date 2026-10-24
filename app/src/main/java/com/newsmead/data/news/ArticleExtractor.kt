package com.newsmead.data.news

import net.dankito.readability4j.Readability4J
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Extracts full article text from the publisher's page, since NewsData's free
 * plan only gives a summary. Candidates: Readability4J, JSON-LD `articleBody`,
 * and Next.js `__NEXT_DATA__` (ABS-CBN). Returns null below [MIN_WORDS].
 * [fetchHtml] blocks; call it off the main thread.
 */
object ArticleExtractor {

    const val MIN_WORDS = 80

    private const val TIMEOUT_MS = 10_000
    private const val MAX_PAGE_BYTES = 4 * 1024 * 1024

    // Several publishers reject Java's default agent.
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0 Mobile Safari/537.36"

    private const val BLOCKS = "p, h2, h3, h4, blockquote, li"
    private val HEADINGS = setOf("h2", "h3", "h4")

    // Ads, bylines, newsletter prompts, share links.
    private val boilerplate = Regex(
        "^\\W*(advertisement|advertisment|ads?|sponsored|read more|read also|also read|related|" +
            "related stories|related articles|recommended|trending|subscribe|follow us|" +
            "click here|share this|download the .* app|get the latest|stay updated|" +
            "for more news|published|updated|make this your preferred source|" +
            "your subscription|haven't received our newsletter|sign up|" +
            "photo|file photo|image source)\\b.*" +
            "|.*(\\ball rights reserved|read more:?)\\s*$" +
            "|^\\s*©.*",
        RegexOption.IGNORE_CASE
    )

    // Near the end of the body, everything from here on is footer.
    private val footerStart = Regex(
        "securedrop|secure messaging|first-hand accounts from people in the know|haven't received our newsletter|your subscription (could not|has been)|" +
            "all rights reserved",
        RegexOption.IGNORE_CASE
    )

    /** Downloads [url] and extracts its article text, or returns null. */
    fun fetchFullText(url: String): String? {
        val html = fetchHtml(url) ?: return null
        return extract(url, html)
    }

    fun fetchHtml(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "text/html")
            connection.setRequestProperty("Accept-Language", "en-PH,en;q=0.9,fil;q=0.8")
            if (connection.responseCode !in 200..299) return null
            if (connection.contentType?.contains("html", ignoreCase = true) == false) return null

            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_PAGE_BYTES) return null
                }
            }
            val charset = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE)
                .find(connection.contentType ?: "")?.groupValues?.get(1) ?: "UTF-8"
            out.toString(runCatching { charset(charset).name() }.getOrDefault("UTF-8"))
        } finally {
            connection.disconnect()
        }
    }

    /** Article text from a downloaded page, paragraphs separated by blank lines. */
    fun extract(url: String, html: String): String? {
        val document = try {
            Jsoup.parse(html, url)
        } catch (e: Exception) {
            return null
        }

        val readability = runCatching { fromReadability(url, html) }.getOrNull()?.let(::clean)
        val structured = listOfNotNull(
            runCatching { fromJsonLd(document) }.getOrNull(),
            runCatching { fromNextData(document) }.getOrNull(),
        ).map(::clean)

        // Prefer Readability (keeps paragraphs) unless it clearly missed the body.
        val longest = (structured + listOfNotNull(readability)).maxByOrNull(::wordCount)
        val best = if (readability != null && longest != null &&
            wordCount(readability) >= wordCount(longest) * 0.6
        ) readability else longest
        return best?.takeIf { wordCount(it) >= MIN_WORDS }
    }

    fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotBlank() }

    // ---- candidates: each returns a list of (isHeading, text) blocks --------

    private fun fromReadability(url: String, html: String): List<Pair<Boolean, String>>? {
        val content = Readability4J(url, html).parse().articleContent ?: return null
        return blocksOf(content)
    }

    private fun fromJsonLd(document: Document): List<Pair<Boolean, String>>? {
        val body = document.select("script[type=application/ld+json]")
            .mapNotNull { script -> findArticleBody(parseJson(script.data())) }
            .maxByOrNull { it.length }
            ?: return null
        return if (body.contains("<p", ignoreCase = true)) {
            blocksOf(Jsoup.parseBodyFragment(body).body())
        } else {
            body.split(Regex("\\n\\s*\\n|\\r?\\n")).map { false to it.trim() }
        }
    }

    private fun fromNextData(document: Document): List<Pair<Boolean, String>>? {
        val data = document.selectFirst("script#__NEXT_DATA__")?.data() ?: return null
        val html = longestBodyHtml(parseJson(data)) ?: return null
        return blocksOf(Jsoup.parseBodyFragment(html).body())
    }

    private fun blocksOf(root: Element): List<Pair<Boolean, String>> {
        val blocks = root.select(BLOCKS)
        val matched = blocks.toHashSet()
        return blocks
            .filter { block -> block.parents().none { it in matched } }
            .filterNot { it.tagName() == "li" && it.isInsideLinkList() }
            .map { (it.tagName() in HEADINGS) to it.text() }
    }

    // ---- cleanup ------------------------------------------------------------

    private fun clean(blocks: List<Pair<Boolean, String>>): String {
        val lines = blocks
            .map { (heading, text) -> heading to text.replace(' ', ' ').trim() }
            .filter { (_, text) -> text.isNotEmpty() }

        // Cut footers starting in the last quarter.
        val cut = lines.indexOfFirst { footerStart.containsMatchIn(it.second) }
            .takeIf { it >= 0 && it >= lines.size * 3 / 4 } ?: lines.size

        return lines.take(cut)
            .filterNot { (_, text) -> boilerplate.matches(text) }
            // Teasers for other stories
            .filterNot { (_, text) -> text.endsWith("…") }
            // Captions and bylines; h2-h4 subheadings are kept
            .filterNot { (heading, text) -> !heading && isCaption(text) }
            .map { it.second }
            .distinct()
            .joinToString("\n\n")
    }

    private fun isCaption(text: String): Boolean =
        wordCount(text) <= 5 && !text.trimEnd().last().let { it in ".!?\"”’)':;" }

    // ---- JSON helpers ---------------------------------------------------------

    private fun parseJson(text: String): Any? {
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("{") -> JSONObject(trimmed)
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> null
        }
    }

    private fun findArticleBody(node: Any?): String? = when (node) {
        is JSONObject -> node.optString("articleBody").takeIf { it.isNotBlank() }
            ?: node.keys().asSequence().mapNotNull { findArticleBody(node.opt(it)) }.firstOrNull()
        is JSONArray -> (0 until node.length()).asSequence()
            .mapNotNull { findArticleBody(node.opt(it)) }.firstOrNull()
        else -> null
    }

    // Longest HTML under a body/content key; related blurbs are shorter.
    private fun longestBodyHtml(node: Any?, key: String = ""): String? = when (node) {
        is JSONObject -> node.keys().asSequence()
            .mapNotNull { longestBodyHtml(node.opt(it), it) }.maxByOrNull { it.length }
        is JSONArray -> (0 until node.length()).asSequence()
            .mapNotNull { longestBodyHtml(node.opt(it), key) }.maxByOrNull { it.length }
        is String -> node.takeIf {
            key.contains(Regex("body|content", RegexOption.IGNORE_CASE)) &&
                it.contains("<p", ignoreCase = true)
        }
        else -> null
    }

    // Link-only lists are navigation.
    private fun Element.isInsideLinkList(): Boolean {
        val list = parent() ?: return false
        return list.children().all { item ->
            val linkText = item.select("a").text().length
            linkText > 0 && linkText >= item.text().length * 0.8
        }
    }
}
