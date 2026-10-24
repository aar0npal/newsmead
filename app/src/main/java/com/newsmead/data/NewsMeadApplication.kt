package com.newsmead.data

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.newsmead.logging.AppLog
import com.newsmead.models.Article
import com.newsmead.research.ResearchSession
import com.newsmead.models.NewsArticle
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Application class to initialize the Room database. This will be called before any other
 * component is initialized.
 */
class NewsMeadApplication: Application() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        DatabaseHelper.initDatabase(this)

        // One research session per foreground period, rather than per process.
        // Android gives no reliable process-death callback, so a session tied
        // to the process would never get its session_end record written and
        // every file would be unterminated — which would waste the
        // dropped-record accounting SessionLog does.
        registerActivityLifecycleCallbacks(SessionLifecycle())
        AppLog.setAuthState(if (FirebaseHelper.getUid() == "null") "signed_out" else "signed_in")

        // Check if logged in
        // If logged in, then preload data
        if (FirebaseHelper.getUid() != "null") {
            GlobalScope.launch {
                val pairData = FirebaseHelper.getListsAndArticles(this@NewsMeadApplication)
                PreloadedData.updateSavedData(pairData)

                // Check if missing or outdated offline articles in local database
                val offlineData: List<Article> =
                    FirebaseHelper.getOfflineArticlesList(this@NewsMeadApplication)
                val offlineArticles: List<NewsArticle> =
                    DatabaseHelper.getNewsArticleDao().getAllNewsArticles()

                val dataIds: List<String> = offlineData.map { it.newsId }
                val articleIds: List<String> = offlineArticles.map { it.newsId }

                // 1 day = 86400000 milliseconds
                val timeLimit = 86400000
                val curTime = System.currentTimeMillis()

                // --- 1. Call API to get body of OUTDATED articles ---
                val outdatedArticles: List<NewsArticle> =
                    offlineArticles.filter { curTime - it.lastUpdated > timeLimit }

                for (outdated in outdatedArticles) {
                    val matchingFirebaseArticle = offlineData.find { it.newsId == outdated.newsId }
                    if (matchingFirebaseArticle != null) {
                        // Extract full text using your built-in DataHelper
                        val fullText = DataHelper.loadFullText(matchingFirebaseArticle)
                            ?: matchingFirebaseArticle.body

                        // Use .copy() to update fields because properties are 'val'
                        val updatedArticle = outdated.copy(
                            articleBody = fullText,
                            lastUpdated = curTime
                        )

                        // Update the database (Assumes insert with OnConflictStrategy.REPLACE or an update method exists)
                        DatabaseHelper.getNewsArticleDao().insertNewsArticle(updatedArticle)
                    }
                }

                // --- 2. Call API to get body of MISSING articles ---
                if (dataIds != articleIds) {
                    val missingIds: List<String> = dataIds.filter { !articleIds.contains(it) }
                    val missingArticles: List<Article> =
                        offlineData.filter { missingIds.contains(it.newsId) }

                    for (article in missingArticles) {
                        // Extract full text using your built-in DataHelper
                        val fullText = DataHelper.loadFullText(article) ?: article.body

                        // Instantiate EXACTLY matching your NewsArticle.kt file
                        val newsArticle = NewsArticle(
                            newsId = article.newsId,
                            articleBody = fullText,
                            lastUpdated = curTime
                        )
                        DatabaseHelper.getNewsArticleDao().insertNewsArticle(newsArticle)
                    }
                }
            }
        }
    }
    /**
     * Opens a research session when the app comes to the foreground and closes
     * it when the last activity stops, so every file ends with a session_end.
     */
    private inner class SessionLifecycle : ActivityLifecycleCallbacks {
        private val handler = Handler(Looper.getMainLooper())
        private var started = 0

        override fun onActivityStarted(activity: Activity) {
            if (started == 0 && !ResearchSession.isRunning) {
                // The participant code is set by whoever runs the study; left
                // unassigned the log is still valid, just not attributable.
                ResearchSession.start(this@NewsMeadApplication)
            }
            started++
        }

        override fun onActivityStopped(activity: Activity) {
            started--
            // A rotation stops and restarts the activity; without this guard
            // it would close the session and split the file in two.
            if (started != 0 || activity.isChangingConfigurations) return

            // Deferred rather than ended inline. This callback runs before the
            // hosted fragments' own onStop(), so ending here directly threw
            // away ArticleFragment's closing article_close record — and
            // discarded it silently, because the session was already gone by
            // the time the event arrived. Posting lets the rest of the
            // lifecycle dispatch finish first. The count is re-checked because
            // moving between two activities also passes through zero.
            handler.post {
                if (started == 0) ResearchSession.end("app_backgrounded")
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
