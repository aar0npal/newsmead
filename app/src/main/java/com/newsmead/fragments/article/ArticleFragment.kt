package com.newsmead.fragments.article

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.ViewCompat
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.newsmead.R
import com.newsmead.activities.GazeTestActivity
import com.newsmead.activities.ReadingValidationActivity
import com.newsmead.custom.CustomDividerItemDecoration
import com.newsmead.data.DataHelper
import com.newsmead.data.DatabaseHelper
import com.newsmead.data.FirebaseHelper
import com.newsmead.data.StudyConfig
import com.newsmead.activities.ReadingBaselineActivity
import com.newsmead.gaze.ReadingFeaturePipeline
import com.newsmead.gaze.ParticipantReadingBaseline
import com.newsmead.gaze.reading.ArticleReadingSession
import com.newsmead.gaze.mahalanobis.*
import com.newsmead.research.MahalanobisSessionLog
import com.newsmead.research.MahalanobisJson
import com.newsmead.gaze.AdaptiveScaffoldController
import com.newsmead.gaze.GazeOverlayView
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.mgazenet.MgazeNetCalibrationStore
import com.newsmead.gaze.scroll.ArticleAutoScrollController
import com.newsmead.gaze.GazeProvider
import com.newsmead.gaze.getVisibleRectOnScreen
import com.newsmead.gaze.GazeTargetStabilizer
import com.newsmead.gaze.LineAoiMapper
import com.newsmead.gaze.ReadingStateInferencer
import com.newsmead.gaze.ReadingMeasurementLog
import com.newsmead.gaze.ScaffoldLevel
import com.newsmead.gaze.ScaffoldUpdate
import com.newsmead.gaze.TextTarget
import com.newsmead.gaze.aid.ReadingAidControlGate
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.newsmead.data.news.NewsDataApi
import com.newsmead.logging.Analytics
import com.newsmead.logging.AppLog
import com.newsmead.research.ResearchSession
import com.newsmead.databinding.FragmentArticleBinding
import com.newsmead.fragments.layouts.BottomSheetDialogSaveFragment
import com.newsmead.models.Article
import com.newsmead.models.SavedList
import com.newsmead.recyclerviews.feed.ArticleSimplifiedAdapter
import com.newsmead.recyclerviews.feed.clickListener
import kotlinx.coroutines.launch
import java.util.Locale


class ArticleFragment() : Fragment(), clickListener, TextToSpeech.OnInitListener {
    private lateinit var binding: FragmentArticleBinding
    private lateinit var adapter: ArticleSimplifiedAdapter
    private lateinit var savedLists: ArrayList<SavedList>
    private lateinit var textToSpeech: TextToSpeech
    private var isTranslated = false
    // Body currently shown; translate's revert restores it.
    private var displayedBody = ""

    // Reading-session bookkeeping for the research log. openedAtMs uses the
    // monotonic clock so a clock change mid-article cannot produce a negative
    // or wildly inflated dwell time.
    private var loggedArticleId: String? = null
    private var articleForUsage: Article? = null
    private var openedAtElapsedMs = 0L
    private var deepestScrollPct = 0
    private var lastScrollLogMs = 0L
    private var language = "english"
    private var gazeProvider: GazeProvider? = null
    private var articleGazeSink: GazeProvider.OnGaze? = null
    private var gazeOverlay: GazeOverlayView? = null
    private var launchedCalibration = false
    private var gazeRestartNotBeforeMs = 0L
    private var calibrationRestartPending = false
    private var resumeGazeAfterStop = false
    private var features: ReadingFeaturePipeline? = null
    private var temporal: MahalanobisTemporalController? = null
    private var activeBaseline: ParticipantReadingBaseline.Entry? = null
    private var detectorLog: MahalanobisSessionLog? = null
    private val detectorHandler = Handler(Looper.getMainLooper())
    private var lastDetectorDebug = "No scoring update yet."
    private var readingLineCount = 0
    private var aidsEnabled = true
    private var autoScrollEnabled = false
    private var scaffoldController: AdaptiveScaffoldController? = null
    private var currentTextTarget = TextTarget.INVALID
    private var lastAoiLogMs = 0L
    private var readingMeasurement: ReadingMeasurementLog? = null
    private var readingSession: ArticleReadingSession? = null
    private var articleBodyReady = false
    private var articleTextSizeSp = ArticleTypography.DEFAULT_SIZE_SP
    private var textResizePending = false
    private var textResizeToken = 0
    private var readingAidControls: AlertDialog? = null
    private var articleAutoScroll: ArticleAutoScrollController? = null
    private var articleOptionsDialog: AlertDialog? = null
    private val readerControlGate = ReadingAidControlGate()
    private val readingGeometryListener = ViewTreeObserver.OnPreDrawListener {
        articleAutoScroll?.onPreDraw()
        readingSession?.checkGeometry()
        true
    }
    private val measurementGeometryListener = ViewTreeObserver.OnPreDrawListener {
        readingMeasurement?.checkGeometry()
        true
    }
    private enum class ColorMode { LIGHT, DARK, SEPIA }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("ArticleFragment", "onCreateView: ArticleFragment started")
        binding = FragmentArticleBinding.inflate(inflater, container, false)
        articleBodyReady = false
        isTranslated = false
        setupReadingAidEntry()

        // Disable the Read Aloud button until the TextToSpeech engine is initialized
        binding.btnReadAloudArticle.isEnabled = false
        binding.btnReadAloudArticle.isClickable = false

        // Initialize TextToSpeech with Filipino language
        textToSpeech = TextToSpeech(requireContext(), this, "com.google.android.tts")

        // Initialize RecyclerView
        adapter = ArticleSimplifiedAdapter(arrayListOf(), this)
        binding.rvArticleRecommended.adapter = adapter

        // Set layout manager
        val layoutManager = LinearLayoutManager(context)
        layoutManager.orientation = LinearLayoutManager.VERTICAL
        binding.rvArticleRecommended.layoutManager = layoutManager

        // Add divider
        val customDivider = CustomDividerItemDecoration(context, R.drawable.line_divider)
        binding.rvArticleRecommended.addItemDecoration(customDivider)

        // Receive parcelable from ArticleActivity
        var article = arguments?.getParcelable<Article>("article") ?: Article(
            "Article Source",
            "Article Source Image",
            "Article Title",
            "",
            "Article Date",
            "Article Body",
            "Article Category",
            "Article Language",
            "Article Read Time",
            "url",
            "0"
        )

        // If article is null from ArticleActivity, then receive from it's own arguments
        if (article.title == "Article Title") {
            val args = ArticleFragmentArgs.fromBundle(requireArguments())
            article = args.articleItem
        }

        // Safeargs bundle
        Log.d("ArticleFragment", "onCreateView: newsId: ${article.newsId}")
        Log.d("ArticleFragment", "onCreateView: title: ${article.title}")
        Log.d("ArticleFragment", "onCreateView: date: ${article.date}")
        Log.d("ArticleFragment", "onCreateView: source: ${article.source}")
        Log.d("ArticleFragment", "onCreateView: sourceImage: ${article.sourceImage}")
        Log.d("ArticleFragment", "onCreateView: imageURL: ${article.imageURL}")
        Log.d("ArticleFragment", "onCreateView: readTime: ${article.readTime}")
        Log.d("ArticleFragment", "onCreateView: url: ${article.url}")


        // Add to history
        FirebaseHelper.addArticleToHistory(requireContext(), article)


        articleForUsage = article

        // Set article title
        binding.tvArticleHeadline.text = article.title

        // Set article category in title case
        binding.tvCategory.text = article.category.replaceFirstChar { it.uppercase() }

        // Set article source
        binding.tvSource.text = article.source
        binding.btnArticleRecommendations.text = "Show more from " + article.source
        val context = binding.root.context
        DataHelper.loadSourceImage(binding.ivSourceImage, article.sourceImage)

        // Set article read time
        val readTime = article.readTime //+ " min read"
        binding.tvArticleMinRead.text = readTime

        // Label updates once loadFullText() finishes.
        if (article.url.startsWith("http")) {
            setOriginalLinkLabel(article, fullTextShown = false)
            binding.btnReadFullArticle.visibility = View.VISIBLE
            binding.btnReadFullArticle.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url)))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Set language
        language = article.language
        if(language.lowercase() == "english"){
            binding.btnTranslateArticle.text = "Filipino"
        }
        else{
            binding.btnTranslateArticle.text = "English"
        }

        // Read Aloud button
        binding.btnReadAloudArticle.setOnClickListener {
            if (readingMeasurement != null) return@setOnClickListener
            binding.btnReadAloudArticle.isEnabled = false
            binding.btnReadAloudArticle.isClickable = false
            if (textToSpeech.isSpeaking && binding.btnReadAloudArticle.text == "Stop") {
                binding.btnReadAloudArticle.text = "Read Aloud"
                textToSpeech.stop()
                logReadAloud(started = false)
            } else {
                logReadAloud(started = true)
                var body = binding.tvArticleText.text.toString()
                Log.d("ArticleFragment", "tts-body: ${body.take(100)}")
                if (body.isNotEmpty()) speak(body)
                else Toast.makeText(context, "No article to read", Toast.LENGTH_SHORT).show()
            }
            binding.btnReadAloudArticle.isEnabled = true
            binding.btnReadAloudArticle.isClickable = true
        }

        // If language is not english, hide the translate button
        if(language.lowercase() != "english"){
            binding.btnTranslateArticle.visibility = View.GONE
        }

        // Translate button
        binding.btnTranslateArticle.setOnClickListener {
            if (textToSpeech.isSpeaking) {
                binding.btnReadAloudArticle.isEnabled = false
                binding.btnReadAloudArticle.isClickable = false
                binding.btnReadAloudArticle.text = "Read Aloud"
                textToSpeech.stop()
                binding.btnReadAloudArticle.isEnabled = true
                binding.btnReadAloudArticle.isClickable = true
            }

            binding.btnTranslateArticle.isEnabled = false
            binding.btnTranslateArticle.isClickable = false
            binding.btnTranslateArticle.text = "Translating..."
            if (!isTranslated) {
                logTranslate(toFilipino = true, useGoogle = binding.switchUseGoogle.isChecked)
                // Translate article
                DataHelper.translateArticle(article.newsId, binding.switchUseGoogle.isChecked, requireContext()) { title, body ->
                    if(title.isNotEmpty() && body.isNotEmpty()) {
                        binding.tvArticleHeadline.text = title
                        binding.tvArticleText.text = body
                        binding.btnTranslateArticle.text = "English"
                        isTranslated = true
                    }
                    else{
                        Toast.makeText(context, "Translation unavailable", Toast.LENGTH_SHORT).show()
                        binding.btnTranslateArticle.text = "Filipino"
                    }
                    binding.btnTranslateArticle.isEnabled = true
                    binding.btnTranslateArticle.isClickable = true
                }
            } else {
                logTranslate(toFilipino = false, useGoogle = binding.switchUseGoogle.isChecked)
                // Revert to original language
                binding.tvArticleHeadline.text = article.title
                binding.tvArticleText.text = displayedBody
                binding.btnTranslateArticle.text = "Filipino"
                isTranslated = false
                binding.btnTranslateArticle.isEnabled = true
                binding.btnTranslateArticle.isClickable = true
            }
        }

        // Scroll depth, throttled: this is the highest-volume record type and
        // SessionLog's queue is bounded.
        binding.nsvArticleText.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (textResizePending) return@setOnScrollChangeListener
            val articleId = loggedArticleId ?: return@setOnScrollChangeListener
            val scroller = binding.nsvArticleText
            val content = scroller.getChildAt(0)?.height ?: 0
            val viewport = scroller.height
            val scrollable = (content - viewport).coerceAtLeast(1)
            val pct = ((scrollY * 100L) / scrollable).toInt().coerceIn(0, 100)
            if (pct > deepestScrollPct) deepestScrollPct = pct

            val now = SystemClock.elapsedRealtime()
            if (now - lastScrollLogMs < SCROLL_LOG_INTERVAL_MS) return@setOnScrollChangeListener
            lastScrollLogMs = now
            ResearchSession.articleScroll(articleId, scrollY, viewport, content,
                if (articleAutoScroll?.isMoving == true) "auto_scroll" else "manual_or_layout")
        }

        // When the user zooms in on the ZoomImageView, disable the NestedScrollView scrolling
        binding.ivArticleFullImage.setOnTouchListener { _, event ->
            when {
                event.action == MotionEvent.ACTION_UP -> {
                    // Enable scrolling on the NestedScrollView when the user lifts their finger
                    binding.nsvArticleText.setScrollingEnabled(true)
                }
                event.pointerCount > 1 -> {
                    // Disable scrolling on the NestedScrollView if there are at least two fingers touching the image
                    binding.nsvArticleText.setScrollingEnabled(false)
                }
            }
            false // Return false so the event is not consumed and continues to be propagated
        }

        // Bottom sheet dialog for saving articles
        binding.btnSaveList.setOnClickListener {
            binding.btnSaveList.isEnabled = false
            binding.btnSaveList.isClickable = false
            val bodyContent = binding.tvArticleText.text.toString()
            val bottomSheetDialogFragment = BottomSheetDialogSaveFragment(article, bodyContent)
            bottomSheetDialogFragment.show(requireActivity().supportFragmentManager, "save")
            // Re-enable the button after a 1 second to prevent multiple rapid clicks
            Handler(Looper.getMainLooper()).postDelayed({
                binding.btnSaveList.isEnabled = true
                binding.btnSaveList.isClickable = true
            }, 1000)
        }

        // Back button to go back to previous fragment
        binding.btnArticleBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnRunGazeTest.setOnClickListener {
            showGazeDiagnosticsMenu()
        }

        // Show more button to show more articles from source
        binding.btnArticleRecommendations.setOnClickListener {
            // Navigate to ArticleSourceFragment
            val action = ArticleFragmentDirections.actionArticleFragmentToArticleSourceFragment(
                article.source
            )

            Navigation.findNavController(binding.root).navigate(action)
        }

        // Share button to share article
        binding.btnArticleShare.setOnClickListener {
            binding.btnArticleShare.isEnabled = false
            binding.btnArticleShare.isClickable = false
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TITLE, "NewsMead")

                val articleTitle = article.title
                val articleURL = article.url
                val shareText = "$articleTitle\n$articleURL"

                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }

            val shareIntent = Intent.createChooser(sendIntent, "Share this article")
            startActivity(shareIntent)

            // Re-enable the button after a 2 seconds to prevent multiple rapid clicks
            Handler(Looper.getMainLooper()).postDelayed({
                binding.btnArticleShare.isEnabled = true
                binding.btnArticleShare.isClickable = true
            }, 2000)
        }

        articleTextSizeSp = ArticleTypography.normalizeSp(requireContext()
            .getSharedPreferences("article_reading_preferences", android.content.Context.MODE_PRIVATE)
            .getFloat("text_size_sp", ArticleTypography.DEFAULT_SIZE_SP))
        if (StudyConfig.LOCK_ARTICLE_FONT_SIZE) {
            binding.tvArticleText.setTextSize(
                TypedValue.COMPLEX_UNIT_DIP, StudyConfig.ARTICLE_FONT_SIZE_DP
            )
            binding.llArticleBottomButtons.visibility = View.GONE
        } else {
            ArticleTypography.apply(binding.tvArticleText, articleTextSizeSp)
            binding.llArticleBottomButtons.visibility = View.VISIBLE
        }
        addBottomAppBarListeners()
        updateTextSizeControls()

        // Loading article content from url
        val articleView = binding
        articleView.root.post {
            if (view !== articleView.root) return@post
            viewLifecycleOwner.lifecycleScope.launch {
                // Check if offline article
                val articleId = article.newsId
                val offlineArticle = DatabaseHelper.getNewsArticleDao().getNewsArticle(articleId)
                if (view !== articleView.root) return@launch

                Log.d("ArticleFragment", "onCreateView: offlineArticle: $offlineArticle")

                if (offlineArticle?.articleBody?.isNotEmpty() == true) {
                    // Offline article
                    displayedBody = offlineArticle.articleBody
                    binding.tvArticleText.text = displayedBody
                } else {
                    // Keep the summary visible while awaiting a stable final body.
                    displayedBody = article.body
                    binding.tvArticleText.text = displayedBody
                    if (!StudyConfig.OFFLINE_MODE) loadFullText(article, articleView)
                    if (view !== articleView.root) return@launch

                    // Set article image from link
                    if (article.imageURL != "") {
                        Glide.with(this@ArticleFragment).load(article.imageURL).error(R.drawable.sample_article_image).into(binding.ivArticleFullImage)
                    } else {
                        binding.ivArticleFullImage.visibility = View.GONE
                    }
                }

                // Article text must be final before TextView line/word geometry and
                // the participant-relative adaptation baseline begin.
                articleBodyReady = true
                updateTextSizeControls()
                resumeArticleGazeWhenReady()

                // Load Recommended Articles
                DataHelper.loadArticleData(context, pageSize = 7, language = language) { it ->
                    // Check if the fragment is still attached to a context
                    val ctx = context ?: return@loadArticleData
                    if (view !== articleView.root) return@loadArticleData

                    if (FirebaseHelper.isNetworkAvailable(ctx)) {
                        // remove article with the same newsId
                        val recommendedArticles = it.filter { it.newsId != article.newsId }
                        adapter.updateData(recommendedArticles)

                        // If no articles, hide recommendations
                        if (it.isEmpty()) {
                            binding.divider.visibility = View.GONE
                            binding.btnArticleRecommendations.visibility = View.GONE
                            binding.tvArticleRecommended.visibility = View.GONE
                        }

                    } else {
                        binding.divider.visibility = View.GONE
                        binding.btnArticleRecommendations.visibility = View.GONE
                        binding.tvArticleRecommended.visibility = View.GONE
                    }
                }

                // Load Saved Lists
                savedLists = ArrayList()

                val checkedLists = FirebaseHelper.checkIfArticleSavedInLists(requireContext(), article.newsId)
                if (view !== articleView.root) return@launch
                for (listId in checkedLists) {
                    val savedList = SavedList(listId, "List Name")
                    savedLists.add(savedList)
                }

                Log.d("ArticleFragment", "onCreateView: savedLists: $savedLists")

                if (savedLists.isNotEmpty()) {
                    Log.d("ArticleFragment", "onCreateView: savedLists is not empty")
                    // Change save button icon to filled
                    binding.btnSaveList.icon = ContextCompat.getDrawable(requireContext(), R.drawable.bookmark_filled_weight400)

                    // Change text to "Saved to list"
                    val savedText = "Saved to list"
                    binding.btnSaveList.text = savedText
                }
            }
        }

        return binding.root
    }
    // Implement TextToSpeech.OnInitListener
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language to Filipino (Tagalog)
            val result = textToSpeech.setLanguage(Locale("fil", "PH"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                installVoiceData()
            }
            for (voice in textToSpeech.voices) {
                // Log the voice name
                Log.d("ArticleFragment", "${voice.name} ${voice.quality} ${voice.isNetworkConnectionRequired} ${voice.features} ${voice.latency}")
                if(voice.name.contains("fil-ph-x-fie-local")) {
                    textToSpeech.setVoice(voice)
                    break
                }
            }

            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(p0: String?) {
                    // Change the Read Aloud button to "Stop" when TTS starts speaking
                    Handler(Looper.getMainLooper()).post {
                        binding.btnReadAloudArticle.text = "Stop"
                        Log.d("ArticleFragment", "TTS started")
                    }
                }

                override fun onDone(utteranceId: String) {
                    // Enable the Read Aloud button after the TTS is done speaking
                    Handler(Looper.getMainLooper()).post {
                        binding.btnReadAloudArticle.text = "Read Aloud"
                        binding.btnReadAloudArticle.isEnabled = true
                        binding.btnReadAloudArticle.isClickable = true
                        Log.d("ArticleFragment", "TTS done")
                    }
                }

                @Deprecated("Deprecated in API level 21")
                override fun onError(p0: String?){
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    // Show toast message when TTS encounters an error
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Read Aloud failed (${errorCode})", Toast.LENGTH_SHORT).show()
                    }
                }
            })
            binding.btnReadAloudArticle.isEnabled = true
            binding.btnReadAloudArticle.isClickable = true
        } else {
            // Upon TextToSpeech initialization failure, disable the Read Aloud button
            Toast.makeText(context, "Read Aloud is not available", Toast.LENGTH_SHORT).show()
        }
    }

    /** Await extraction before marking article geometry ready for gaze or measurement. */
    private suspend fun loadFullText(article: Article, articleView: FragmentArticleBinding) {
        if (!article.url.startsWith("http")) return
        articleView.btnReadFullArticle.isEnabled = false
        articleView.btnReadFullArticle.text = getString(R.string.article_loading_full)

        val fullText = DataHelper.loadFullText(article)
        if (view !== articleView.root) return

        articleView.btnReadFullArticle.isEnabled = true
        if (fullText != null) {
            // Preserve the extracted original even if translation finished first.
            displayedBody = fullText
            if (!isTranslated) {
                articleView.tvArticleText.text = displayedBody
                articleView.tvArticleMinRead.text = NewsDataApi.readTime(displayedBody)
            }
        }
        setOriginalLinkLabel(article, fullText != null && !isTranslated)
    }

    private fun setOriginalLinkLabel(article: Article, fullTextShown: Boolean) {
        binding.btnReadFullArticle.text = when {
            article.source.isEmpty() -> getString(R.string.article_read_full)
            fullTextShown -> getString(R.string.article_view_original_on, article.source)
            else -> getString(R.string.article_read_full_on, article.source)
        }
    }

    /**
     * Ask the current default engine to launch the matching INSTALL_TTS_DATA activity
     * so the required TTS files are properly installed.
     */
    private fun installVoiceData() {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.setPackage("com.google.android.tts")
        try {
            Log.v("TTS", "Installing voice data: " + intent.toUri(0))
            startActivity(intent)
        } catch (ex: ActivityNotFoundException) {
            Log.e("TTS", "Failed to install TTS data, no activity found for $intent)")
        }
    }

    private fun logReadAloud(started: Boolean) {
        val articleId = loggedArticleId ?: return
        Analytics.readAloud(started)
        ResearchSession.readAloud(articleId, started)
    }

    private fun logTranslate(toFilipino: Boolean, useGoogle: Boolean) {
        val articleId = loggedArticleId ?: return
        Analytics.translate(toFilipino, useGoogle)
        ResearchSession.translate(articleId, toFilipino, useGoogle)
    }

    /**
     * Closes the reading record. Fires from onStop rather than onDestroy so a
     * user who backgrounds the app without closing the article still produces
     * a dwell time instead of an open-ended record.
     */
    private fun closeArticleUsage() {
        val articleId = loggedArticleId
        if (articleId != null) {
            val dwellMs = SystemClock.elapsedRealtime() - openedAtElapsedMs
            Analytics.articleClose(articleId, dwellMs, deepestScrollPct)
            ResearchSession.articleClose(articleId, dwellMs, deepestScrollPct)
            AppLog.d("ArticleFragment", "article_close id=$articleId dwellMs=$dwellMs")
            loggedArticleId = null
        }
    }

    // Don't forget to release TextToSpeech when your activity is destroyed
    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        stopReadingSession("fragment_destroyed")
        gazeProvider?.stop()
        gazeOverlay?.clearScaffold()
        scaffoldController?.reset()
        scaffoldController = null
        currentTextTarget = TextTarget.INVALID
        gazeOverlay = null
        super.onDestroy()
    }


    override fun onStart() {
        super.onStart()
        val article = articleForUsage ?: return
        if (loggedArticleId != null) return
        loggedArticleId = article.newsId
        openedAtElapsedMs = SystemClock.elapsedRealtime()
        deepestScrollPct = 0
        lastScrollLogMs = 0L
        val openedFrom = ResearchSession.lastScreen() ?: "direct"
        Analytics.articleOpen(article.newsId, article.source, article.category, article.language, openedFrom)
        ResearchSession.articleOpen(article.newsId, article.source, article.category, article.language, openedFrom)
    }

    override fun onResume() {
        super.onResume()
        readingSession?.resume()
        if (launchedCalibration) {
            launchedCalibration = false
            calibrationRestartPending = true
            gazeRestartNotBeforeMs = SystemClock.elapsedRealtime() + GAZE_RESTART_DELAY_MS
        }
        resumeArticleGazeWhenReady()
    }

    /** Retain pending work across backgrounding; only dispatch against the current visible view. */
    private fun resumeArticleGazeWhenReady() {
        if (!StudyConfig.GAZE_ENABLED || !articleBodyReady || !isResumed ||
            launchedCalibration || gazeProvider != null) return
        val remainingDelay = (gazeRestartNotBeforeMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        if (remainingDelay > 0L) {
            val resumeView = binding.root
            // Keep the existing calibration camera-release delay on the monotonic clock.
            Handler(Looper.getMainLooper()).postDelayed({
                if (view === resumeView) resumeArticleGazeWhenReady()
            }, remainingDelay)
            return
        }
        val sink = articleGazeSink
        when {
            sink == null -> setupGazeDebug()
            calibrationRestartPending -> restartLiveGazeAfterCalibration()
            resumeGazeAfterStop -> attachLiveGaze(sink)
        }
        if (gazeProvider != null || StudyConfig.GAZE_TOUCH_VALIDATION) {
            calibrationRestartPending = false
            resumeGazeAfterStop = false
        }
    }

    override fun onPause() {
        articleAutoScroll?.interrupt("screen_paused")
        dismissReaderControls()
        // Keep a pending source-offset restore for this view's next pre-draw.
        // The capture gate stays closed to reading while paused, and the existing
        // lifecycle guard prevents the callback from resuming a paused runtime.
        // Destruction below invalidates callbacks belonging to a removed view.
        finishReadingMeasurement("screen_paused", restore = false)
        readingSession?.pause("screen_paused")
        super.onPause()
    }

    override fun onDestroyView() {
        articleAutoScroll?.close()
        articleAutoScroll = null
        binding.nsvArticleText.onUserTouch = null
        dismissReaderControls()
        cancelTextResize()
        stopReadingSession("article_view_destroyed")
        gazeProvider?.stop()
        gazeProvider = null
        articleGazeSink = null
        gazeOverlay = null
        resumeGazeAfterStop = false
        articleBodyReady = false
        articleForUsage = null
        super.onDestroyView()
    }

    override fun onStop() {
        closeArticleUsage()
        finishReadingMeasurement("screen_left", restore = false)
        resumeGazeAfterStop = resumeGazeAfterStop ||
            (gazeProvider != null && !StudyConfig.GAZE_TOUCH_VALIDATION)
        stopReadingSession("screen_left")
        gazeProvider?.stop(); gazeProvider = null
        gazeOverlay?.clearGaze()
        super.onStop()
    }

    /** Stop live gaze (frees the camera), open the accuracy test, resume on return. */
    private fun launchGazeTest() {
        launchedCalibration = true
        stopReadingSession("calibration_opened")
        gazeProvider?.stop()
        gazeProvider = null
        startActivity(Intent(requireContext(), GazeTestActivity::class.java))
    }

    private fun showGazeDiagnosticsMenu() {
        if (readingMeasurement != null) { finishReadingMeasurement("manual_stop"); return }
        if (articleOptionsDialog != null || readingAidControls != null || !openReaderControls("options")) return
        val items = mutableListOf("Reading support", "Gaze accuracy check", "Reading accuracy check", "Record measurement", "Export detector log", "Baseline reading")
        if (com.newsmead.BuildConfig.DEBUG) items.add("Detector diagnostics")
        articleOptionsDialog = AlertDialog.Builder(requireContext()).setTitle(R.string.article_options)
            .setItems(items.toTypedArray()) { _, which -> when (which) {
                0 -> showReadingAidControls()
                1 -> launchGazeTest()
                2 -> launchReadingValidation()
                3 -> showReadingMeasurementInstructions()
                4 -> saveReadingAidTrace()
                5 -> { stopReadingSession("baseline_opened"); gazeProvider?.stop(); gazeProvider = null
                    startActivity(Intent(requireContext(), ReadingBaselineActivity::class.java)) }
                6 -> AlertDialog.Builder(requireContext()).setTitle("Mahalanobis diagnostics")
                    .setMessage(lastDetectorDebug).setPositiveButton(android.R.string.ok, null).show()
            } }.setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { articleOptionsDialog = null; closeReaderControls("options") }.show()
    }

    private fun showReadingMeasurementInstructions() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.reading_measurement_option)
            .setMessage(R.string.reading_measurement_instructions)
            .setPositiveButton(R.string.reading_measurement_start) { _, _ -> startReadingMeasurement() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startReadingMeasurement() {
        if (!articleBodyReady) {
            Toast.makeText(context, getString(R.string.article_loading_full), Toast.LENGTH_SHORT).show()
            return
        }
        if (gazeProvider == null || StudyConfig.GAZE_TOUCH_VALIDATION || binding.tvArticleText.layout == null) {
            Toast.makeText(context, R.string.reading_measurement_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val fingerprint = MgazeNetCalibrationStore(requireContext()).fingerprint() ?: return
        articleAutoScroll?.pauseByReader("diagnostic_recording")
        lateinit var session: ReadingMeasurementLog
        try {
            session = ReadingMeasurementLog(requireContext(), binding.tvArticleText, fingerprint) { file, complete ->
                if (readingMeasurement === session) finishReadingMeasurement("writer_closed")
                Log.i("ReadingMeasurement", "${file.name}: complete=$complete")
                if (isAdded && isResumed) Toast.makeText(context,
                    if (complete) R.string.reading_measurement_saved else R.string.reading_measurement_failed,
                    Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("ReadingMeasurement", "Cannot open recording", e)
            Toast.makeText(context, R.string.reading_measurement_failed, Toast.LENGTH_LONG).show()
            return
        }
        readingMeasurement = session
        updateTextSizeControls()
        binding.btnArticleReadingAids.isEnabled = false
        readingSession?.boundary("diagnostic_mode_changed")
        textToSpeech.stop()
        binding.btnReadAloudArticle.isEnabled = false
        gazeOverlay?.setDotOnly(true)
        binding.root.viewTreeObserver.addOnPreDrawListener(measurementGeometryListener)
    }

    private fun finishReadingMeasurement(reason: String, restore: Boolean = true) {
        val session = readingMeasurement ?: return
        readingMeasurement = null
        updateTextSizeControls()
        binding.btnArticleReadingAids.isEnabled = true
        readingSession?.boundary("diagnostic_mode_changed")
        binding.root.viewTreeObserver.removeOnPreDrawListener(measurementGeometryListener)
        session.finish(reason)
        binding.btnReadAloudArticle.isEnabled = true
        resetAdaptiveReadingSession()
        gazeOverlay?.setDotOnly(false)
    }

    /** Stop live gaze, run the controlled reading protocol, then rebind on return. */
    private fun launchReadingValidation() {
        launchedCalibration = true
        stopReadingSession("reading_validation_opened")
        gazeProvider?.stop()
        gazeProvider = null
        startActivity(Intent(requireContext(), ReadingValidationActivity::class.java))
    }

    private fun restartLiveGazeAfterCalibration() {
        if (StudyConfig.GAZE_TOUCH_VALIDATION) return
        val onGaze = articleGazeSink ?: return
        stopReadingSession("provider_restarted")
        gazeProvider?.stop()
        gazeProvider = null
        resetAdaptiveReadingSession()
        attachLiveGaze(onGaze)
    }

    private fun resetAdaptiveReadingSession() {
        scaffoldController?.reset()?.let(::applyScaffoldUpdate)
        currentTextTarget = TextTarget.INVALID
        lastAoiLogMs = 0L
        Log.i("GazeScaffold", "Adaptive reading session reset")
    }
    // Function to convert text to speech
    private fun speak(text: String) {
        if(!isTranslated && language.lowercase() == "english"){
            textToSpeech.setLanguage(Locale.ENGLISH)
            textToSpeech.voice = textToSpeech.voices.find { it.name.contains("en-us-x-iom-local") }
        }
        else{
            textToSpeech.setLanguage(Locale("fil", "PH"))
            textToSpeech.voice = textToSpeech.voices.find { it.name.contains("fil-ph-x-fie-local") }
        }
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, TextToSpeech.ACTION_TTS_QUEUE_PROCESSING_COMPLETED)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Add bottom app bar logic (it must stick and then only disappear
        // once it reaches recommendations
        // binding.btnArticleRecommendations.post {
        //     val btnLocation = IntArray(2)
        //     binding.btnArticleRecommendations.getLocationOnScreen(btnLocation)
        //     val btnY = btnLocation[1]
        //     val nestedScrollView = binding.nsvArticleText
        //     val bottomAppBar = binding.bottomAppBar
        //     nestedScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
        //         Log.d("Y_COORDINATE", "btnY: $btnY scrollY: $scrollY")
        //         // Check if the scroll position is past the Y-coordinate of btnArticleRecommendations
        //         if (scrollY > btnY -1000) {
        //             Log.d("SCROLL", "Hiding Bottom App Bar")
        //             bottomAppBar.performHide()
        //         } else {
        //             bottomAppBar.performShow()
        //         }
        //     })
        // }
    }

    private fun addBottomAppBarListeners() {
        binding.btnArticleTextLarger.setOnClickListener { changeArticleTextSize(1) }
        binding.btnArticleTextSmaller.setOnClickListener { changeArticleTextSize(-1) }

        binding.btnArticleClrLight.setOnClickListener{ updateColors(ColorMode.LIGHT) }
        binding.btnArticleClrDark.setOnClickListener{ updateColors(ColorMode.DARK) }
        binding.btnArticleClrSepia.setOnClickListener{ updateColors(ColorMode.SEPIA) }
    }

    private fun updateTextSizeControls() {
        val enabled = articleBodyReady && readingMeasurement == null &&
            !StudyConfig.LOCK_ARTICLE_FONT_SIZE && !textResizePending
        binding.btnArticleTextSmaller.isEnabled = enabled && articleTextSizeSp > ArticleTypography.MIN_SIZE_SP
        binding.btnArticleTextLarger.isEnabled = enabled && articleTextSizeSp < ArticleTypography.MAX_SIZE_SP
        val description = getString(R.string.article_text_size_current, articleTextSizeSp.toInt())
        ViewCompat.setStateDescription(binding.btnArticleTextSmaller, description)
        ViewCompat.setStateDescription(binding.btnArticleTextLarger, description)
    }

    private fun changeArticleTextSize(direction: Int) {
        if (!isResumed || !articleBodyReady || textResizePending || readingMeasurement != null ||
            StudyConfig.LOCK_ARTICLE_FONT_SIZE) return
        val selected = ArticleTypography.stepSp(articleTextSizeSp, direction)
        if (selected == articleTextSizeSp) return
        val root = binding.root
        val body = binding.tvArticleText
        val layout = body.layout
        val oldScrollY = binding.nsvArticleText.scrollY
        val localY = oldScrollY - body.top - body.totalPaddingTop + body.scrollY
        val firstVisibleOffset = if (layout != null && localY >= 0 && oldScrollY < body.bottom)
            layout.getLineStart(layout.getLineForVertical(localY)) else null
        if (!openReaderControls("text_size")) return
        textResizePending = true
        val token = ++textResizeToken
        updateTextSizeControls()
        resetAdaptiveReadingSession()
        articleTextSizeSp = selected
        ArticleTypography.apply(body, selected)
        requireContext().getSharedPreferences("article_reading_preferences", android.content.Context.MODE_PRIVATE)
            .edit().putFloat("text_size_sp", selected).apply()
        root.doOnPreDraw {
            if (view !== root || token != textResizeToken || !textResizePending) return@doOnPreDraw
            val updated = body.layout
            if (firstVisibleOffset != null && updated != null) {
                val line = updated.getLineForOffset(firstVisibleOffset.coerceIn(0, body.text.length))
                binding.nsvArticleText.scrollTo(binding.nsvArticleText.scrollX,
                    body.top + body.totalPaddingTop - body.scrollY + updated.getLineTop(line))
            }
            readingSession?.checkGeometry()
            textResizePending = false
            closeReaderControls("text_size")
            updateTextSizeControls()
        }
    }

    private fun cancelTextResize() {
        if (!textResizePending) return
        textResizeToken++
        textResizePending = false
        closeReaderControls("text_size", allowResume = false)
        updateTextSizeControls()
    }

    /**
     * Stage 4 (touch-validation slice): attach the debug gaze layer to the
     * article view: the line-AOI mapper + a gaze-dot overlay. All gaze flows
     * through the single [GazeProvider.OnGaze] entry point below, so the live
     * local calibrated provider can drive it via `setOnGaze` with no changes here.
     * Touch validation proves line mapping before trusting gaze accuracy.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupGazeDebug() {
        if (!StudyConfig.GAZE_ENABLED || !articleBodyReady || !isResumed || launchedCalibration ||
            articleGazeSink != null || SystemClock.elapsedRealtime() < gazeRestartNotBeforeMs) return
        val overlay = GazeOverlayView(requireContext())
        gazeOverlay = overlay
        (binding.root as ViewGroup).addView(overlay, ConstraintLayout.LayoutParams(0, 0).apply {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        })
        overlay.translationZ = 1000f
        overlay.setDebugVisualsEnabled(StudyConfig.GAZE_DEBUG_VISUALS)
        features = ReadingFeaturePipeline(StudyConfig.MAHALANOBIS_CONFIG)
        scaffoldController = AdaptiveScaffoldController(StudyConfig.MAHALANOBIS_SCAFFOLD_CONFIG)
        val onGaze = GazeProvider.OnGaze { x, y -> overlay.setGazeScreen(x, y) }
        articleGazeSink = onGaze
        attachLiveGaze(onGaze)
    }

    private fun applyScaffoldUpdate(update: ScaffoldUpdate) {
        gazeOverlay?.setScaffoldState(binding.tvArticleText, update.state)
        update.targetReason?.let { reason ->
            detectorLog?.write(MahalanobisJson.scaffoldTarget(
                update.state,
                SystemClock.elapsedRealtime(),
                reason,
            ))
        }
    }



    /** Finger substitutes for gaze; returns false so the article still scrolls. */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouchValidation(onGaze: GazeProvider.OnGaze) {
        binding.nsvArticleText.setOnTouchListener { _, event ->
            onGaze.onGaze(event.rawX, event.rawY)
            false
        }
    }

    /**
     * Live gaze from the local phone tracker, mapped to the phone screen with
     * the saved 13-point calibration. In live mode, do not silently substitute
     * touch input; missing/invalid calibration must be fixed before reading.
     */
    private fun attachLiveGaze(onGaze: GazeProvider.OnGaze) {
        val store = MgazeNetCalibrationStore(requireContext())
        val issue = store.compatibilityIssue(binding.root)
        if (issue != null) { Toast.makeText(context, issue, Toast.LENGTH_LONG).show(); return }
        stopReadingSession("provider_replaced")
        val baseline = ParticipantReadingBaseline.current(ResearchSession.sessionId, store.fingerprint())
        activeBaseline = baseline
        val config = baseline?.profile?.config ?: StudyConfig.MAHALANOBIS_CONFIG
        features = ReadingFeaturePipeline(config)
        val scorer = MahalanobisScorer(baseline?.profile)
        temporal = MahalanobisTemporalController(config)
        detectorLog = MahalanobisSessionLog(requireContext(), "reading")
        detectorLog?.write(org.json.JSONObject().put("type", "reading_setup")
            .put("maxOutputAgeMs", StudyConfig.READING_MAX_OUTPUT_AGE_MS)
            .put("staticGeometryFallback", true)
            .put("autoScrollMotionExcluded", true)
            .put("scaffoldConfig", MahalanobisJson.scaffoldConfig(
                StudyConfig.MAHALANOBIS_SCAFFOLD_CONFIG)))
        baseline?.let {
            detectorLog?.write(MahalanobisJson.profile(it.profile).put("participantCode", it.participantCode)
                .put("baselineLogName", it.baselineLogName).put("calibrationFingerprint", it.calibrationFingerprint))
        }
        if (baseline == null) Toast.makeText(context, "Complete baseline reading from Home to enable adaptive support.", Toast.LENGTH_LONG).show()
        val session = ArticleReadingSession(binding.tvArticleText,
            onGeometry = { _, geometry -> readingLineCount = geometry?.lines?.size ?: 0 },
            onBoundary = { reason ->
                if (reason != "invalid_sample" && reason != "non_monotonic_capture") {
                    if (reason == "reading_scroll") features?.breakContinuity() else features?.boundary()
                    temporal?.finish()?.let { detectorLog?.write(MahalanobisJson.episode(it)) }
                    scaffoldController?.reset()?.let(::applyScaffoldUpdate)
                }
            }, onInput = { _, sample ->
                val at = sample.captureMs?.takeIf { it.isFinite() && it >= 0 }?.toLong()
                if (at != null) {
                    val raw = if (sample.eligible && sample.line != null)
                        TextTarget(sample.line, readingLineCount, sample.wordStartUtf16 ?: TextTarget.NO_OFFSET,
                            sample.wordEndUtf16 ?: TextTarget.NO_OFFSET) else TextTarget.INVALID
                    features?.sample(raw, sample.eligible, at)
                    currentTextTarget = features?.target ?: TextTarget.INVALID
                    scaffoldController?.onGazeTarget(raw, at)?.let(::applyScaffoldUpdate)
                }
            })
        readingSession = session
        binding.root.viewTreeObserver.addOnPreDrawListener(readingGeometryListener)
        session.checkGeometry()
        val provider = MgazeNetGazeProvider(requireContext(), binding.root, smoothForReading = true,
            rejectBlinks = true, maxOutputAgeMs = StudyConfig.READING_MAX_OUTPUT_AGE_MS)
        provider.onFps = { gazeOverlay?.setFps(it) }
        provider.onFailure = failure@{ message ->
            if (readingSession !== session) return@failure
            detectorLog?.write(org.json.JSONObject().put("type", "provider_failure").put("reason", message))
            stopReadingSession("provider_failed")
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        provider.onObservation = { observation ->
            if (readingSession === session) {
                articleAutoScroll?.onPreDraw()
                val scrollExclusion = articleAutoScroll?.exclusion(observation.captureMs)
                val excluded = readerControlGate.excludes(observation.captureMs) || readingMeasurement != null ||
                    (observation.x != null && observation.y != null && readingAidEntryContains(observation.x, observation.y)) ||
                    (::textToSpeech.isInitialized && textToSpeech.isSpeaking)
                val controllerInput = if (excluded) observation.copy(reason = "reader_controls") else observation
                val readingInput = if (scrollExclusion != null)
                    controllerInput.copy(reason = scrollExclusion) else controllerInput
                session.record(readingInput)
                articleAutoScroll?.onObservation(controllerInput)
            }
            readingMeasurement?.record(observation)
            if (observation.reason != "coordinate") gazeOverlay?.clearGaze()
        }
        provider.setOnGaze { x, y -> onGaze.onGaze(x, y) }
        gazeProvider = provider
        val pulse = object : Runnable {
            override fun run() {
                if (readingSession !== session || !isResumed) return
                val now = SystemClock.elapsedRealtime()
                val vector = features?.tick(now)
                if (vector != null) {
                    // A model from a different participant/session/calibration can never act.
                    val stillCurrent = baseline != null && ParticipantReadingBaseline.current(
                        ResearchSession.sessionId, store.fingerprint()) === baseline
                    val score = (if (stillCurrent) scorer else MahalanobisScorer(null)).score(vector)
                    val state = temporal!!.accept(score)
                    val update = scaffoldController!!.update(score, state, features!!.target,
                        aidsEnabled && !readerControlGate.active && readingMeasurement == null)
                    applyScaffoldUpdate(update)
                    val record = MahalanobisJson.update(score, state, baseline?.profile?.takeIf { stillCurrent },
                        update.state, config)
                    lastDetectorDebug = record.toString(2)
                    detectorLog?.write(record)
                    state.completedEpisode?.let { detectorLog?.write(MahalanobisJson.episode(it)) }
                }
                detectorHandler.postDelayed(this, config.updateIntervalMs)
            }
        }
        detectorHandler.post(pulse)
        provider.start(viewLifecycleOwner)
    }





    private fun stopReadingSession(reason: String) {
        dismissReaderControls()
        detectorHandler.removeCallbacksAndMessages(null)
        val session = readingSession
        readingSession = null
        binding.root.viewTreeObserver.removeOnPreDrawListener(readingGeometryListener)
        session?.close(reason)
        temporal?.finish()?.let { detectorLog?.write(MahalanobisJson.episode(it)) }
        detectorLog?.write(org.json.JSONObject().put("type", "session_end").put("reason", reason))
        detectorLog?.close()
        features?.boundary()
        scaffoldController?.reset()?.let(::applyScaffoldUpdate)
    }










    /** Fixed access in the existing bottom bar; article touches keep their normal behavior. */
    private fun setupReadingAidEntry() {
        binding.btnArticleReadingAids.setOnClickListener { showReadingAidControls() }
        articleAutoScroll = ArticleAutoScrollController(binding.tvArticleText, binding.nsvArticleText,
            binding.bottomAppBar, binding.articleAutoScrollTail,
            blocked = { when {
                !isResumed -> "screen_paused"
                !articleBodyReady -> "article_loading"
                gazeProvider == null -> "gaze_unavailable"
                readingMeasurement != null -> "diagnostic_recording"
                readerControlGate.active -> "reader_controls"
                textResizePending -> "text_resizing"
                ::textToSpeech.isInitialized && textToSpeech.isSpeaking -> "read_aloud"
                else -> null
            } },
            onBoundary = { moving, _, detail ->
                readingSession?.autoScrollMotion(moving, detail.optString("reason"))
            },
            onJournal = { _, _, _ -> },
            onState = { enabled, paused ->
                binding.btnArticleAutoScrollPause.visibility = if (enabled) View.VISIBLE else View.GONE
                binding.btnArticleAutoScrollPause.setText(if (paused) R.string.article_auto_scroll_resume
                    else R.string.article_auto_scroll_pause)
            })
        binding.nsvArticleText.onUserTouch = { event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN)
                articleAutoScroll?.pauseByReader("manual_touch")
        }
        binding.btnArticleAutoScrollPause.setOnClickListener {
            articleAutoScroll?.let { if (it.pausedByReader) it.resumeByReader() else it.pauseByReader() }
        }
    }

    private fun readingAidEntryContains(x: Float, y: Float): Boolean {
        val rect = Rect()
        return (binding.bottomAppBar.visibility == View.VISIBLE &&
            binding.bottomAppBar.getVisibleRectOnScreen(rect) && rect.contains(x.toInt(), y.toInt())) ||
            (binding.btnArticleAutoScrollPause.visibility == View.VISIBLE &&
                binding.btnArticleAutoScrollPause.getVisibleRectOnScreen(rect) && rect.contains(x.toInt(), y.toInt()))
    }

    private fun showReadingAidControls() {
        if (readingMeasurement != null || readingAidControls != null || !openReaderControls("aids")) return
        readingAidControls = AlertDialog.Builder(requireContext()).setTitle(R.string.reading_aids_option)
            .setMultiChoiceItems(arrayOf("Adaptive reading support", "Gaze auto-scroll"),
                booleanArrayOf(aidsEnabled, autoScrollEnabled)) { _, which, checked ->
                if (which == 0) aidsEnabled = checked else autoScrollEnabled = checked
                articleAutoScroll?.setEnabled(autoScrollEnabled)
                if (!aidsEnabled) gazeOverlay?.clearScaffold()
            }.setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener { readingAidControls = null; closeReaderControls("aids") }.show()
    }

    private fun openReaderControls(tag: String): Boolean {
        if (view == null) return false
        articleAutoScroll?.interrupt("reader_controls")
        readingSession?.boundary("reader_controls")
        readerControlGate.open(tag, SystemClock.elapsedRealtimeNanos() / 1e6)
        return true
    }

    private fun closeReaderControls(tag: String, allowResume: Boolean = true) {
        if (readerControlGate.close(tag, SystemClock.elapsedRealtimeNanos() / 1e6) && allowResume)
            readingSession?.boundary("reader_controls_closed")
    }

    /** Detach callbacks before dismissal so lifecycle cleanup cannot resume a paused runtime. */
    private fun dismissReaderControls() {
        val options = articleOptionsDialog
        articleOptionsDialog = null
        options?.setOnDismissListener(null)
        options?.dismiss()
        if (options != null) closeReaderControls("options", allowResume = false)
        val sheet = readingAidControls
        readingAidControls = null
        sheet?.setOnDismissListener(null)
        sheet?.dismiss()
        if (sheet != null) closeReaderControls("aids", allowResume = false)
    }

    private fun saveReadingAidTrace() {
        try { startActivity(MahalanobisSessionLog.shareAll(requireContext(), detectorLog)) }
        catch (e: Exception) { Toast.makeText(context, "Could not export detector log: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    /**
     * Updates the colors of the article fragment
     * @param color The color mode to update to
     */
    private fun updateColors(color: ColorMode) {
        val activity = requireActivity()
        val window = activity.window
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        when (color) {
            ColorMode.LIGHT -> {
                window.statusBarColor = ContextCompat.getColor(activity,R.color.light)
                window.navigationBarColor = ContextCompat.getColor(activity,R.color.light)

                changeColorOfBackgrounds(ContextCompat.getColor(requireContext(), R.color.light))
                changeColorOfButtons(ContextCompat.getColor(requireContext(), R.color.light_btn))
                changeColorOfTexts(ContextCompat.getColor(requireContext(), R.color.light_text))
                changeColorOfSubTexts(ContextCompat.getColor(requireContext(), R.color.light_subtext))
            }
            ColorMode.DARK -> {
                window.statusBarColor = ContextCompat.getColor(activity,R.color.dark)
                window.navigationBarColor = ContextCompat.getColor(activity,R.color.dark)

                changeColorOfBackgrounds(ContextCompat.getColor(requireContext(), R.color.dark))
                changeColorOfButtons(ContextCompat.getColor(requireContext(), R.color.dark_btn))
                changeColorOfTexts(ContextCompat.getColor(requireContext(), R.color.dark_text))
                changeColorOfSubTexts(ContextCompat.getColor(requireContext(), R.color.dark_subtext))
            }
            ColorMode.SEPIA -> {
                window.statusBarColor = ContextCompat.getColor(activity,R.color.sepia)
                window.navigationBarColor = ContextCompat.getColor(activity,R.color.sepia)

                changeColorOfBackgrounds(ContextCompat.getColor(requireContext(), R.color.sepia))
                changeColorOfButtons(ContextCompat.getColor(requireContext(), R.color.sepia_btn))
                changeColorOfTexts(ContextCompat.getColor(requireContext(), R.color.sepia_text))
                changeColorOfSubTexts(ContextCompat.getColor(requireContext(), R.color.sepia_subtext))
            }
        }
    }

    private fun changeColorOfBackgrounds(color: Int){
        binding.root.setBackgroundColor(color)
        binding.nsvArticleText.setBackgroundColor(color)
        binding.bottomAppBar.setBackgroundColor(color)
        binding.clArticleTopBar.setBackgroundColor(color)
        // Change the color of the Recommended Articles RecyclerView (background)
        adapter.changeBackgroundColor(color)
    }
    
    private fun changeColorOfButtons(color: Int) {
        binding.btnSaveList.setBackgroundColor(color)
        binding.btnTranslateArticle.setBackgroundColor(color)
        binding.btnReadAloudArticle.setBackgroundColor(color)
    }

    private fun changeColorOfSubTexts(color: Int){
        binding.tvArticleAuthor.setTextColor(color)
        binding.tvArticleMinRead.setTextColor(color)
        binding.tvArticleBy.setTextColor(color)
        binding.tvByDot.setTextColor(color)
        binding.tvBullet.setTextColor(color)
        // Change the color of the Recommended Articles RecyclerView (read time)
        adapter.changeSubTextColor(color)
    }

    private fun changeColorOfTexts(color: Int){
        binding.tvArticleText.setTextColor(color)
        binding.tvArticleHeadline.setTextColor(color)
        binding.tvArticleRecommended.setTextColor(color)
        binding.tvSource.setTextColor(color)
        binding.tvCategory.setTextColor(color)

        binding.btnArticleTextLarger.setTextColor(color)
        binding.btnArticleTextSmaller.setTextColor(color)
        binding.btnSaveList.setTextColor(color)
        binding.btnTranslateArticle.setTextColor(color)
        binding.btnReadAloudArticle.setTextColor(color)
        binding.btnArticleClrLight.setTextColor(color)
        binding.btnArticleClrDark.setTextColor(color)
        binding.btnArticleClrSepia.setTextColor(color)
        binding.btnArticleReadingAids.setTextColor(color)

        // Change icon colors
        binding.btnArticleBack.setColorFilter(color)
        binding.btnArticleShare.setColorFilter(color)

        binding.btnSaveList.iconTint = ColorStateList.valueOf(color)
        binding.btnTranslateArticle.iconTint = ColorStateList.valueOf(color)
        binding.btnReadAloudArticle.iconTint = ColorStateList.valueOf(color)

        binding.btnArticleTextLarger.iconTint = ColorStateList.valueOf(color)
        binding.btnArticleTextSmaller.iconTint = ColorStateList.valueOf(color)
        binding.btnArticleReadingAids.iconTint = ColorStateList.valueOf(color)

        // Change the color of the Recommended Articles RecyclerView (source, title)
        adapter.changeTextColor(color)
    }

    override fun onItemClicked(article: Article) {
        // Action
        val action = ArticleFragmentDirections.actionArticleFragmentSelf(article)
        Navigation.findNavController(requireView()).navigate(action)
    }

    companion object {
        private const val SCROLL_LOG_INTERVAL_MS = 250L
        /**
         * Delay before restarting live gaze after returning from the gaze
         * test/calibration activity, giving that activity time to finish its
         * onDestroy camera release before we rebind the shared CameraX camera.
         */
        private const val GAZE_RESTART_DELAY_MS = 800L
        private const val ARTICLE_DIAGNOSTIC_LOG_INTERVAL_MS = 1_000L

    }
}
