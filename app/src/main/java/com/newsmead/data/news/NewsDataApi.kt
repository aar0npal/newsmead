package com.newsmead.data.news

import com.newsmead.models.Article
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/**
 * URL building and parsing for the NewsData.io `latest` endpoint. No Android
 * classes, so it is unit-testable; the request is made in DataHelper.
 * Free plan: 200 credits/day, 10 articles/request, no full `content`.
 */
object NewsDataApi {

    const val BASE_URL = "https://newsdata.io/api/1/latest"

    /** Free plan maximum. */
    const val MAX_PAGE_SIZE = 10

    enum class Region {
        /** `country=ph` */
        LOCAL,

        /** Top non-PH publishers. */
        INTERNATIONAL,

        /** No country filter (search). */
        ALL;

        companion object {
            fun fromPreference(value: String?): Region =
                values().firstOrNull { it.name == value } ?: LOCAL
        }
    }

    // App source key -> NewsData source_id(s). TV5 is not on NewsData.
    private val knownSourceIds = mapOf(
        "gmanews" to "gmanetwork,gmanetwork_ph",
        "inquirer" to "inquirer",
        "philstar" to "philstar",
        "manilabulletin" to "mb",
        "abantenews" to "abante_ph,abante",
    )
    private val knownSourceKeys = knownSourceIds.entries
        .flatMap { (key, ids) -> ids.split(",").map { it to key } }
        .toMap()

    // Display name -> id / icon seen in responses, for "Show more from".
    private val seenSourceIds = ConcurrentHashMap<String, String>()
    private val seenSourceIcons = ConcurrentHashMap<String, String>()

    data class Query(
        val region: Region = Region.LOCAL,
        val language: String? = null,
        val category: String? = null,
        val source: String? = null,
        val searchText: String? = null,
        val size: Int? = null,
        val page: String? = null,
    )

    data class Page(
        val articles: List<Article>,
        val nextPage: String?,
    )

    class ApiException(message: String, val code: String?) : Exception(message)

    fun buildUrl(apiKey: String, query: Query): String {
        val params = linkedMapOf<String, String>()
        params["apikey"] = apiKey

        val sourceId = query.source?.takeIf { it.isNotBlank() }?.let { resolveSourceId(it) }

        // NewsData rejects mismatched country + domain, so skip region.
        val international = sourceId == null && query.region == Region.INTERNATIONAL
        val (category, categoryKeyword) = mapCategory(query.category)

        if (sourceId != null) {
            params["domain"] = sourceId
        } else {
            when (query.region) {
                Region.LOCAL -> params["country"] = "ph"
                Region.INTERNATIONAL -> {
                    params["excludecountry"] = "ph"
                    params["prioritydomain"] = "top"
                }
                Region.ALL -> {}
            }
        }

        val language = languageCode(query.language)
        params["language"] = when {
            // No Filipino world news exists, so allow English.
            language == "tl" && international -> "tl,en"
            language != null -> language
            else -> "en,tl"
        }

        when {
            category != null -> params["category"] = category
            international && categoryKeyword == null -> params["category"] = "world"
        }

        val q = listOfNotNull(
            query.searchText?.trim()?.takeIf { it.isNotEmpty() },
            categoryKeyword
        ).joinToString(" AND ")
        if (q.isNotEmpty()) params["q"] = q

        val size = (query.size ?: MAX_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
        params["size"] = size.toString()
        params["removeduplicate"] = "1"
        query.page?.let { params["page"] = it }

        return BASE_URL + "?" + params.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
    }

    /** Same URL with the key masked, safe for logcat. */
    fun redact(url: String): String = url.replace(Regex("apikey=[^&]*"), "apikey=***")

    /** App language labels ("English", "filipino", ...) -> NewsData codes. */
    fun languageCode(language: String?): String? = when (language?.lowercase()) {
        null, "" -> null
        "english", "en" -> "en"
        "filipino", "tagalog", "tl" -> "tl"
        else -> null
    }

    private fun languageLabel(code: String?): String = when (code?.lowercase()) {
        "tl", "tagalog", "filipino" -> "Filipino"
        else -> "English"
    }

    /** Chip -> (NewsData category, keyword); "opinion" is a keyword. */
    private fun mapCategory(category: String?): Pair<String?, String?> =
        when (category?.lowercase()) {
            null, "", "all" -> null to null
            "news" -> "top" to null
            "opinion" -> null to "opinion"
            "sports", "technology", "lifestyle", "business", "entertainment",
            "politics", "world", "health", "science" -> category.lowercase() to null
            else -> null to null
        }

    private fun categoryLabel(categories: JSONArray?): String {
        val first = categories?.optString(0)?.takeIf { it.isNotEmpty() } ?: return "News"
        return when (first) {
            "top", "domestic", "other" -> "News"
            else -> first.replaceFirstChar { it.uppercase() }
        }
    }

    /** Short key, raw NewsData id, or a display name seen before. */
    fun resolveSourceId(source: String): String =
        knownSourceIds[source] ?: seenSourceIds[source] ?: source

    /** Icon URL for a source name seen in an earlier response, if any. */
    fun sourceIconFor(name: String): String? = seenSourceIcons[name]

    fun parse(
        json: JSONObject,
        sourceNameMap: (String) -> String,
        sourceImageMap: (String) -> String,
    ): Page {
        if (json.optString("status") != "success") {
            val results = json.optJSONObject("results")
            throw ApiException(
                results?.optString("message")?.takeIf { it.isNotEmpty() }
                    ?: "NewsData request failed",
                results?.optString("code")
            )
        }

        val results = json.optJSONArray("results") ?: JSONArray()
        val articles = ArrayList<Article>(results.length())
        val seenIds = HashSet<String>()
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val article = parseArticle(item, sourceNameMap, sourceImageMap) ?: continue
            if (seenIds.add(article.newsId)) articles.add(article)
        }
        return Page(articles, json.optStringOrNull("nextPage"))
    }

    private fun parseArticle(
        item: JSONObject,
        sourceNameMap: (String) -> String,
        sourceImageMap: (String) -> String,
    ): Article? {
        val id = item.optStringOrNull("article_id") ?: return null
        val title = item.optStringOrNull("title") ?: return null
        val link = item.optStringOrNull("link") ?: ""

        val sourceId = item.optStringOrNull("source_id") ?: ""
        val knownKey = knownSourceKeys[sourceId]
        val sourceName = knownKey?.let(sourceNameMap)?.takeIf { it.isNotEmpty() }
            ?: item.optStringOrNull("source_name")
            ?: sourceId
        val icon = item.optStringOrNull("source_icon")

        if (sourceId.isNotEmpty()) seenSourceIds[sourceName] = sourceId
        if (icon != null) seenSourceIcons[sourceName] = icon

        // Bundled logo for built-in sources, else NewsData's icon URL.
        val sourceImage = knownKey?.let(sourceImageMap) ?: icon ?: "sample_source_image"

        val body = bodyOf(item, title)

        return Article(
            sourceName,
            sourceImage,
            title.trim(),
            item.optStringOrNull("image_url"),
            formatDate(item.optStringOrNull("pubDate")),
            body,
            categoryLabel(item.optJSONArray("category")),
            languageLabel(item.optStringOrNull("language")),
            readTime(body),
            link,
            id
        )
    }

    /** `content` is a paid-plan placeholder on the free plan; use the description. */
    fun bodyOf(item: JSONObject, title: String): String {
        val content = item.optStringOrNull("content")
            ?.takeUnless { it.contains("ONLY AVAILABLE IN", ignoreCase = true) }
        val description = item.optStringOrNull("description")
        return (content ?: description ?: title).trim()
    }

    fun readTime(body: String): String {
        val words = body.split(Regex("\\s+")).count { it.isNotBlank() }
        val minutes = ((words + 199) / 200).coerceAtLeast(1)
        return "$minutes min read"
    }

    /** "2026-09-27 03:15:00" (UTC) -> "Sep 27, 2026" in the device time zone. */
    fun formatDate(pubDate: String?): String {
        if (pubDate.isNullOrEmpty()) return ""
        return try {
            val input = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsed: Date = input.parse(pubDate) ?: return pubDate
            SimpleDateFormat("MMM dd, yyyy", Locale.US).format(parsed)
        } catch (e: Exception) {
            pubDate
        }
    }

    /** org.json turns JSON null into "null". */
    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }
}
