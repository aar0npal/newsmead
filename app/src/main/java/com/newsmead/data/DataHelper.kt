package com.newsmead.data

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.util.Log
import android.os.SystemClock
import android.util.LruCache
import android.widget.ImageView
import android.widget.Toast
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.newsmead.BuildConfig
import com.newsmead.R
import com.newsmead.data.news.ArticleExtractor
import com.newsmead.data.news.NewsDataApi
import com.newsmead.logging.AppLog
import com.newsmead.models.Article
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

import java.util.Locale
import kotlin.collections.ArrayList

object DataHelper {
    private const val TAG = "DataHelper"


    fun formatTitle(title: String): String {
        // Remove the source from the title
        return title.substring(0, title.indexOf(" - "))
    }
    fun formatDate(date: String): String {
        // Format date "2023-10-31T13:03:00Z" string to Mmm dd, yyyy
        // val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

        // Format date "2023-10-31 13:03:00" string to Mmm dd, yyyy
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

        val parsed = inputFormat.parse(date)
        return outputFormat.format(parsed)
    }

    // Source image mapping
    fun sourceImageMap(source: String): String {
        return when (source) {
            "gmanews", "GMA News" -> "source_gmanews"
            "inquirer", "INQUIRER.NET" -> "source_inquirer"
            "philstar", "Philstar" -> "source_philstar"
            "manilabulletin", "The Manila Bulletin" -> "source_manilabulletin"
            "news5", "TV5 News" -> "source_news5"
            "abantenews", "Abante News" -> "source_abantenews"
            // Other outlets: NewsData logo, if seen.
            else -> NewsDataApi.sourceIconFor(source) ?: "sample_source_image"
        }
    }

    // Source name mapping
    fun sourceNameMap(source: String): String {
        return when (source) {
            "gmanews" -> "GMA News"
            "inquirer" -> "INQUIRER.NET"
            "philstar" -> "Philstar"
            "manilabulletin" -> "The Manila Bulletin"
            "news5" -> "TV5 News"
            "abantenews" -> "Abante News"
            "study" -> "NewsMead" // Thesis-study articles: neutral in-app label
            else -> ""
        }
    }

    // Reverse source name mapping
    fun reverseSourceNameMap(fullName: String): String {
        return when (fullName) {
            "NewsMead" -> "study"
            "GMA News" -> "gmanews"
            "INQUIRER.NET" -> "inquirer"
            "Philstar" -> "philstar"
            "The Manila Bulletin" -> "manilabulletin"
            "TV5 News" -> "news5"
            "Abante News" -> "abantenews"
            // Unknown outlets pass through; NewsDataApi resolves the name.
            else -> fullName
        }
    }

    fun translateArticle(id: String, useGoogle: Boolean, context: Context, callback: (String, String) -> Unit) {
        var url = "https://newsmead.southeastasia.cloudapp.azure.com/articles/translate/$id"
        if (useGoogle)
            url += "?service=google"
        val queue = Volley.newRequestQueue(context)
        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                val translatedTitle = response.getString("title")
                val translatedBody = response.getString("body")
                callback(translatedTitle, translatedBody)
            },
            { error ->
                Log.e("DataHelper", error.toString())
                callback("", "")
                return@JsonObjectRequest
            })
        queue.add(jsonObjectRequest)
    }

    // Shared queue; one per request leaked threads.
    private var requestQueue: RequestQueue? = null

    private fun queue(context: Context): RequestQueue =
        requestQueue ?: Volley.newRequestQueue(context.applicationContext).also { requestQueue = it }

    // Response cache to save the free plan's 200 requests/day.
    private const val CACHE_TTL_MS = 10 * 60 * 1000L
    private val responseCache = HashMap<String, Pair<Long, List<Article>>>()

    // Throttles error toasts to one per burst of requests.
    private var lastErrorToastMs = 0L

    private const val REGION_PREFS = "news_region"
    private const val REGION_KEY = "region"

    /** Home feed region. */
    fun getNewsRegion(context: Context): NewsDataApi.Region =
        NewsDataApi.Region.fromPreference(
            context.getSharedPreferences(REGION_PREFS, Context.MODE_PRIVATE)
                .getString(REGION_KEY, null)
        )

    fun setNewsRegion(context: Context, region: NewsDataApi.Region) {
        context.getSharedPreferences(REGION_PREFS, Context.MODE_PRIVATE)
            .edit().putString(REGION_KEY, region.name).apply()
    }

    /**
     * Loads NewsData articles into [callback] on the main thread; an empty list
     * and a toast on failure. Dates are filtered on the device; [page] is unused.
     */
    @Suppress("UNUSED_PARAMETER")
    fun loadArticleData(
        context: Context?,
        page: Int? = 1,
        source: String? = null,
        language: String? = null,
        category: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        searchText : String? = null,
        pageSize: Int? = null,
        region: NewsDataApi.Region? = null,
        callback: (List<Article>) -> Unit)
    {
        // Preserve the bundled study feed when explicitly selected.
        if (StudyConfig.OFFLINE_MODE) {
            loadLocalArticleData(
                context, page, source, language, category, searchText, pageSize, callback
            )
            return
        }

        if (context == null) return

        val apiKey = BuildConfig.NEWSDATA_API_KEY
        if (apiKey.isBlank()) {
            AppLog.w(TAG, "NEWSDATA_API_KEY is not set in local.properties; feed is empty")
            showError(context, "News feed not configured (missing NewsData API key)")
            callback(emptyList())
            return
        }

        val effectiveRegion = region
            ?: if (!searchText.isNullOrBlank()) NewsDataApi.Region.ALL else getNewsRegion(context)

        val url = NewsDataApi.buildUrl(
            apiKey,
            NewsDataApi.Query(
                region = effectiveRegion,
                language = language,
                category = category,
                source = source,
                searchText = searchText,
                size = pageSize,
            )
        )
        // No key or query text in logs.
        AppLog.d(TAG, "Fetching region=$effectiveRegion language=$language category=$category source=$source")

        val filter = { articles: List<Article> -> filterByDate(articles, startDate, endDate) }

        responseCache[url]?.let { (fetchedAt, cached) ->
            if (System.currentTimeMillis() - fetchedAt < CACHE_TTL_MS) {
                callback(filter(cached))
                return
            }
        }

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                val articles = try {
                    NewsDataApi.parse(response, ::sourceNameMap, ::sourceImageMap).articles
                } catch (e: NewsDataApi.ApiException) {
                    AppLog.e(TAG, "NewsData error ${e.code}: ${e.message}", e)
                    showError(context, "Couldn't load news: ${e.message}")
                    callback(emptyList())
                    return@JsonObjectRequest
                } catch (e: Exception) {
                    AppLog.e(TAG, "Unparseable NewsData response", e)
                    showError(context, "Couldn't load news")
                    callback(emptyList())
                    return@JsonObjectRequest
                }
                AppLog.d(TAG, "Fetched ${articles.size} articles")
                responseCache[url] = System.currentTimeMillis() to articles
                callback(filter(articles))
            },
            { error ->
                val status = error.networkResponse?.statusCode
                val message = when (status) {
                    401, 403 -> "News API key was rejected"
                    429 -> "Daily news limit reached, try again later"
                    null -> "No connection to the news service"
                    else -> "News service error ($status)"
                }
                AppLog.w(TAG, "NewsData request failed: status=$status", error)
                showError(context, message)
                callback(emptyList())
            })
        request.tag = TAG
        queue(context).add(request)
    }

    /**
     * Offline replacement for [loadArticleData]. Reads the bundled article set
     * (assets/[StudyConfig.ARTICLES_ASSET]) and applies the same filters the API
     * used to apply server-side (source / category / language / search / pageSize)
     * so every screen — home feed, search, by-source, by-category, recommended —
     * behaves the same, just against local data.
     *
     * Expected JSON (identical to the old API response):
     * {
     *   "articles": [
     *     {
     *       "source": "gmanews",                 // raw key -> logo + display name
     *       "title": "Headline text",
     *       "image_url": "file:///android_asset/study_images/a1.jpg",  // or "" or an http url
     *       "date": "2026-07-01 09:30:00",       // MUST be yyyy-MM-dd HH:mm:ss
     *       "body": "Full article body...",
     *       "category": "news",
     *       "language": "English",               // "English" or "Filipino"
     *       "read_time": "5 min read",
     *       "url": "https://example.com/original",
     *       "article_id": 1
     *     }
     *   ]
     * }
     */
    private fun loadLocalArticleData(
        context: Context?,
        page: Int?,
        source: String?,
        language: String?,
        category: String?,
        searchText: String?,
        pageSize: Int?,
        callback: (List<Article>) -> Unit
    ) {
        val articles = ArrayList<Article>()

        // No context, or a paginated request beyond the single local page:
        // return empty so any "load more" behaviour terminates cleanly.
        if (context == null || (page != null && page > 1)) {
            callback(articles)
            return
        }

        try {
            val jsonText = context.assets.open(StudyConfig.ARTICLES_ASSET)
                .bufferedReader().use { it.readText() }
            val data = JSONObject(jsonText).getJSONArray("articles")

            for (i in 0 until data.length()) {
                val a = data.getJSONObject(i)

                val rawSource = a.optString("source", "")
                val cat = a.optString("category", "")
                val lang = a.optString("language", "English")
                val title = a.optString("title", "")
                val body = a.optString("body", "")

                // Same filtering the API applied server-side.
                if (source != null && rawSource != source) continue
                if (category != null && cat.lowercase() != category.lowercase()) continue
                if (language != null && lang.lowercase() != language.lowercase()) continue
                if (!searchText.isNullOrEmpty() &&
                    !title.contains(searchText, ignoreCase = true) &&
                    !body.contains(searchText, ignoreCase = true)
                ) continue

                // Be forgiving about the date so a hand-edited entry can't crash
                // the whole feed: fall back to the raw string if it doesn't parse.
                val rawDate = a.optString("date", "")
                val date = try {
                    formatDate(rawDate)
                } catch (e: Exception) {
                    Log.w("DataHelper", "Unparseable date '$rawDate' in local articles")
                    rawDate
                }

                articles.add(
                    Article(
                        sourceNameMap(rawSource),
                        sourceImageMap(rawSource),
                        title,
                        a.optString("image_url", ""),
                        date,
                        body,
                        cat,
                        lang,
                        a.optString("read_time", ""),
                        a.optString("url", ""),
                        a.optInt("article_id", i + 1).toString()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("DataHelper", "Failed to load local articles from ${StudyConfig.ARTICLES_ASSET}", e)
        }

        // Honour pageSize as a cap (recommended list requests a small pageSize).
        val result = if (pageSize != null && pageSize < articles.size) {
            ArrayList(articles.subList(0, pageSize))
        } else {
            articles
        }
        callback(result)
    }



    // ----------------------------------------------------------------------------- //
    // ----------------------------- Data for Testing ------------------------------ //
    // ----------------------------------------------------------------------------- //

    fun loadArticleDataLatest(): ArrayList<Article> {
        val data = ArrayList<Article>()
        data.add(
            Article(
                "CNN Philippines",
                "PH secures over \$4.26-B investment deals from Marcos' Saudi Arabia visit",
                "Oct 20, 2023",
                "9 min read",
                "http://www.cnnphilippines.com/news/2023/10/20/marcos-says-15k-filipinos-to-benefit-with-saudi-deal.html"
            )
        )
        data.add(
            Article(
                "Philstar.com",
                "CHED to extend educational assistance to children of slain Filipinos in Israel",
                "Oct 20, 2023",
                "5 min read",
                "https://www.philstar.com/headlines/2023/10/20/2305252/ched-extend-educational-assistance-children-slain-filipinos-israel"
            )
        )
        data.add(
            Article(
                "ABS-CBN News",
                "Napoles found guilty, lawmaker acquitted in P20-M PDAF case",
                "Oct 20, 2023",
                "4 min read",
                "https://news.abs-cbn.com/news/10/20/23/napoles-found-guilty-lawmaker-acquitted-in-p20-m-pdaf-case"
            )
        )
        return data
    }

    private fun filterByDate(articles: List<Article>, startDate: String?, endDate: String?): List<Article> {
        if (startDate.isNullOrEmpty() && endDate.isNullOrEmpty()) return articles
        val input = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val display = java.text.SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val start = startDate?.let { runCatching { input.parse(it) }.getOrNull() }
        val end = endDate?.let { runCatching { input.parse(it) }.getOrNull() }
        return articles.filter { article ->
            val date = runCatching { display.parse(article.date) }.getOrNull() ?: return@filter true
            (start == null || !date.before(start)) && (end == null || !date.after(end))
        }
    }

    private fun showError(context: Context, message: String) {
        val now = System.currentTimeMillis()
        if (now - lastErrorToastMs < 3000) return
        lastErrorToastMs = now
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    // Extracted bodies by newsId, so reopening an article skips the download.
    private val fullTextCache = LruCache<String, String>(30)

    /** Publisher's full article text, or null if it can't be extracted. */
    suspend fun loadFullText(article: Article): String? {
        fullTextCache.get(article.newsId)?.let { return it }
        val started = SystemClock.elapsedRealtime()
        val text = withContext(Dispatchers.IO) {
            try {
                ArticleExtractor.fetchFullText(article.url)
            } catch (e: Exception) {
                AppLog.w(TAG, "Full text fetch failed for ${article.source}", e)
                null
            }
        }
        // No URL, title, or text in logs.
        AppLog.d(TAG, "Full text ${if (text != null) "extracted" else "unavailable"} " +
            "source=${article.source} ms=${SystemClock.elapsedRealtime() - started}")
        if (text != null) fullTextCache.put(article.newsId, text)
        return text
    }

    /** Source logo from a drawable name or a URL. */
    fun loadSourceImage(imageView: ImageView, sourceImage: String?) {
        if (sourceImage != null && sourceImage.startsWith("http")) {
            Glide.with(imageView).load(sourceImage)
                .placeholder(R.drawable.sample_source_image)
                .error(R.drawable.sample_source_image)
                .into(imageView)
            return
        }
        val context = imageView.context
        val resourceId = context.resources.getIdentifier(sourceImage ?: "", "drawable", context.packageName)
        imageView.setImageResource(if (resourceId != 0) resourceId else R.drawable.sample_source_image)
    }

    fun loadCategoryData(): ArrayList<String> {
        val data = ArrayList<String>()
        data.add("News")
        data.add("Opinion")
        data.add("Sports")
        data.add("Technology")
        data.add("Lifestyle")
        data.add("Business")
        data.add("Entertainment")
        return data
    }

    fun loadCategoryLongerData(): ArrayList<String> {
        val data = ArrayList<String>()
        data.add("News")
        data.add("Opinion")
        data.add("Sports")
        data.add("Technology")
        data.add("Lifestyle")
        data.add("Business")
        data.add("Entertainment")
        return data
    }

    fun loadSourcesData(): ArrayList<String> {
        val data = ArrayList<String>()
        data.add("GMA News")
        data.add("INQUIRER.NET")
        data.add("Philstar")
        data.add("The Manila Bulletin")
        // data.add("TV5 News")
        data.add("Abante News")
        return data
    }

    fun loadLanguagesData(): ArrayList<String> {
        val data = ArrayList<String>()
        data.add("Filipino")
        data.add("English")
        return data
    }

    fun getDateToday(): CharSequence {
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date())
    }

}
