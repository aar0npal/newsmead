package com.newsmead.fragments.article

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.newsmead.R
import com.newsmead.activities.GazeCalibrationActivity
import com.newsmead.activities.GazeTestActivity
import com.newsmead.custom.CustomDividerItemDecoration
import com.newsmead.data.DataHelper
import com.newsmead.data.DatabaseHelper
import com.newsmead.data.FirebaseHelper
import com.newsmead.data.StudyConfig
import com.newsmead.gaze.CalibrationStore
import com.newsmead.gaze.GazeMapper
import com.newsmead.gaze.GazeOverlayView
import com.newsmead.gaze.GazeProvider
import com.newsmead.gaze.LineAoiMapper
import com.newsmead.gaze.LocalCalibratedGazeProvider
import com.newsmead.gaze.LocalGazeSources
import com.newsmead.gaze.ReadingStateInferencer
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
    private var language = "english"
    private var gazeProvider: GazeProvider? = null
    private var articleGazeSink: GazeProvider.OnGaze? = null
    private var gazeOverlay: GazeOverlayView? = null
    private var launchedCalibration = false
    private var rsiInferencer: ReadingStateInferencer? = null
    private enum class ColorMode { LIGHT, DARK, SEPIA }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("ArticleFragment", "onCreateView: ArticleFragment started")
        binding = FragmentArticleBinding.inflate(inflater, container, false)

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

        // Set article title
        binding.tvArticleHeadline.text = article.title

        // Set article category in title case
        binding.tvCategory.text = article.category.replaceFirstChar { it.uppercase() }

        // Set article source
        binding.tvSource.text = article.source
        binding.btnArticleRecommendations.text = "Show more from " + article.source
        val context = binding.root.context
        val resourceId = context.resources.getIdentifier(article.sourceImage, "drawable", context.packageName)
        binding.ivSourceImage.setImageResource(if (resourceId != 0) resourceId else R.drawable.sample_source_image)

        // Set article read time
        val readTime = article.readTime //+ " min read"
        binding.tvArticleMinRead.text = readTime

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
            binding.btnReadAloudArticle.isEnabled = false
            binding.btnReadAloudArticle.isClickable = false
            if (textToSpeech.isSpeaking && binding.btnReadAloudArticle.text == "Stop") {
                binding.btnReadAloudArticle.text = "Read Aloud"
                textToSpeech.stop()
            } else {
                var body = binding.tvArticleText.text.toString()
                Log.d("ArticleFragment", "tts-body: ${body.substring(0, 100)}")
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
                // Revert to original language
                binding.tvArticleHeadline.text = article.title
                binding.tvArticleText.text = article.body
                binding.btnTranslateArticle.text = "Filipino"
                isTranslated = false
                binding.btnTranslateArticle.isEnabled = true
                binding.btnTranslateArticle.isClickable = true
            }
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

        binding.btnRunGazeCalibration.setOnClickListener {
            launchGazeCalibration()
        }

        binding.btnRunGazeTest.setOnClickListener {
            launchGazeTest()
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

        addBottomAppBarListeners()

        // Study mode: lock the body font size and remove the +/- text-size controls
        // so line bounding boxes stay stable for gaze AOI mapping (Stage 4).
        // Applied in dp so the OS font-scale setting can't change the pixel size.
        if (StudyConfig.LOCK_ARTICLE_FONT_SIZE) {
            binding.tvArticleText.setTextSize(
                TypedValue.COMPLEX_UNIT_DIP, StudyConfig.ARTICLE_FONT_SIZE_DP
            )
            binding.llArticleBottomButtons.visibility = View.GONE
        }

        setupGazeDebug()

        // Loading article content from url
        lifecycleScope.launch {
            // Check if offline article
            val articleId = article.newsId
            val offlineArticle = DatabaseHelper.getNewsArticleDao().getNewsArticle(articleId)

            Log.d("ArticleFragment", "onCreateView: offlineArticle: $offlineArticle")

            if (offlineArticle?.articleBody?.isNotEmpty() == true) {
                // Offline article
                binding.tvArticleText.text = offlineArticle.articleBody
            } else {
                // Online article
                binding.tvArticleText.text = article.body

                // Set article image from link
                if (article.imageURL != "") {
                    Glide.with(this@ArticleFragment).load(article.imageURL).error(R.drawable.sample_article_image).into(binding.ivArticleFullImage)
                } else {
                    binding.ivSourceImage.setImageResource(R.drawable.sample_source_image)
                }
            }

            // Load Recommended Articles
            DataHelper.loadArticleData(context, pageSize = 7, language = language) { it ->
                // Check if the fragment is still attached to a context
                val ctx = context ?: return@loadArticleData

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

    // Don't forget to release TextToSpeech when your activity is destroyed
    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        rsiInferencer?.flush()
        gazeProvider?.stop()
        gazeOverlay = null
        super.onDestroy()
    }


    override fun onResume() {
        super.onResume()
        if (launchedCalibration) {
            launchedCalibration = false
            restartLiveGazeAfterCalibration()
        }
    }

    private fun launchGazeCalibration() {
        launchedCalibration = true
        rsiInferencer?.flush()
        gazeProvider?.stop()
        gazeProvider = null
        startActivity(Intent(requireContext(), GazeCalibrationActivity::class.java))
    }

    /** Stop live gaze (frees the camera), open the accuracy test, resume on return. */
    private fun launchGazeTest() {
        launchedCalibration = true
        rsiInferencer?.flush()
        gazeProvider?.stop()
        gazeProvider = null
        startActivity(Intent(requireContext(), GazeTestActivity::class.java))
    }

    private fun restartLiveGazeAfterCalibration() {
        if (StudyConfig.GAZE_TOUCH_VALIDATION) return
        val onGaze = articleGazeSink ?: return
        gazeProvider?.stop()
        gazeProvider = null
        attachLiveGaze(onGaze)
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

    fun convertPixelsToSp(px: Float): Float {
        return px / (requireContext().resources.displayMetrics.scaledDensity)
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
        val size = 2
        val minSizeLimit = 14
        val maxSizeLimit = 60

        // Increase font size when btnArticleTextLarger is clicked
        binding.btnArticleTextLarger.setOnClickListener {
            val newSize = convertPixelsToSp(binding.tvArticleText.textSize + size)
            if (newSize <= maxSizeLimit) {
                binding.tvArticleText.textSize = newSize
            }
            else{
                Toast.makeText(context, "Reached maximum text size", Toast.LENGTH_SHORT).show()
            }
        }

        // Decrease font size when btnArticleTextSmaller is clicked
        binding.btnArticleTextSmaller.setOnClickListener {
            val newSize = convertPixelsToSp(binding.tvArticleText.textSize) - size
            if (newSize > minSizeLimit) {
                binding.tvArticleText.textSize = newSize
            }
            else{
                Toast.makeText(context, "Reached minimum text size", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnArticleClrLight.setOnClickListener{ updateColors(ColorMode.LIGHT) }
        binding.btnArticleClrDark.setOnClickListener{ updateColors(ColorMode.DARK) }
        binding.btnArticleClrSepia.setOnClickListener{ updateColors(ColorMode.SEPIA) }
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
        if (!StudyConfig.GAZE_ENABLED) return

        val overlay = GazeOverlayView(requireContext())
        gazeOverlay = overlay
        (binding.root as ViewGroup).addView(
            overlay,
            ConstraintLayout.LayoutParams(0, 0).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            }
        )
        overlay.bringToFront()
        overlay.translationZ = 1000f
        val mapper = LineAoiMapper(binding.tvArticleText)
        rsiInferencer = createRsiInferencer()

        // Single gaze entry point (full-screen px). Both the touch-validation
        // source and the local calibrated gaze provider feed through here.
        val onGaze = GazeProvider.OnGaze { x, y ->
            overlay.setGazeScreen(x, y)
            val line = mapper.lineAt(y)
            val lineCount = mapper.lineCount
            Log.d("GazeAOI", "gaze=(${x.toInt()},${y.toInt()}) line=$line/$lineCount")
            rsiInferencer?.onLine(line, lineCount)
        }

        articleGazeSink = onGaze

        if (StudyConfig.GAZE_TOUCH_VALIDATION) {
            attachTouchValidation(onGaze)
        } else {
            attachLiveGaze(onGaze)
        }
    }

    private fun createRsiInferencer(): ReadingStateInferencer =
        ReadingStateInferencer(object : ReadingStateInferencer.Listener {
            override fun onLineSample(sample: ReadingStateInferencer.LineSample) {
                Log.d("GazeRSI", "line=${sample.lineIndex}/${sample.lineCount} t=${sample.timestampMs}")
            }

            override fun onFixation(event: ReadingStateInferencer.FixationEvent) {
                Log.d("GazeRSI", "fixation line=${event.lineIndex}/${event.lineCount} duration=${event.durationMs}ms")
            }

            override fun onDwell(event: ReadingStateInferencer.DwellEvent) {
                Log.d("GazeRSI", "dwell line=${event.lineIndex}/${event.lineCount} duration=${event.durationMs}ms")
            }

            override fun onRegression(event: ReadingStateInferencer.RegressionEvent) {
                Log.d("GazeRSI", "regression ${event.fromLine}->${event.toLine} t=${event.timestampMs}")
            }

            override fun onScore(score: ReadingStateInferencer.RsiScore) {
                Log.d(
                    "GazeRSI",
                    "score=${"%.1f".format(score.score)} " +
                        "reg=${"%.2f".format(score.regressionComponent)} " +
                        "dwell=${"%.2f".format(score.dwellComponent)} " +
                        "fix=${"%.2f".format(score.fixationComponent)} " +
                        "events=f${score.fixationCount}/d${score.totalDwellMs}ms/r${score.regressionCount}"
                )
            }
        })

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
     * the saved 16-point calibration. In live mode, do not silently substitute
     * touch input; missing/invalid calibration must be fixed before reading.
     */
    private fun attachLiveGaze(onGaze: GazeProvider.OnGaze) {
        val samples = CalibrationStore.load(requireContext())
        if (samples == null) {
            Log.w("GazeAOI", "No calibration found; live gaze not started")
            Toast.makeText(context, "Run gaze calibration before live reading", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val rawSource = LocalGazeSources.create(requireContext())
            rawSource.setOnFps { fps -> activity?.runOnUiThread { gazeOverlay?.setFps(fps) } }
            val provider = LocalCalibratedGazeProvider(
                mapper = GazeMapper(samples),
                rawSource = rawSource,
            )
            provider.setOnGaze { x, y -> activity?.runOnUiThread { onGaze.onGaze(x, y) } }
            provider.start(viewLifecycleOwner)
            gazeProvider = provider
            Log.i("GazeAOI", "Local calibrated gaze started with ${samples.size} calibration samples")
        } catch (e: Exception) {
            Log.e("GazeAOI", "Calibration fit failed; live gaze not started", e)
            Toast.makeText(context, "Gaze calibration is invalid; recalibrate", Toast.LENGTH_LONG).show()
        }
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
                binding.btnArticleTextLarger.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_btn_emphasis))
                changeColorOfTexts(ContextCompat.getColor(requireContext(), R.color.light_text))
                changeColorOfSubTexts(ContextCompat.getColor(requireContext(), R.color.light_subtext))
            }
            ColorMode.DARK -> {
                window.statusBarColor = ContextCompat.getColor(activity,R.color.dark)
                window.navigationBarColor = ContextCompat.getColor(activity,R.color.dark)

                changeColorOfBackgrounds(ContextCompat.getColor(requireContext(), R.color.dark))
                changeColorOfButtons(ContextCompat.getColor(requireContext(), R.color.dark_btn))
                binding.btnArticleTextLarger.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.dark_btn_emphasis))
                changeColorOfTexts(ContextCompat.getColor(requireContext(), R.color.dark_text))
                changeColorOfSubTexts(ContextCompat.getColor(requireContext(), R.color.dark_subtext))
            }
            ColorMode.SEPIA -> {
                window.statusBarColor = ContextCompat.getColor(activity,R.color.sepia)
                window.navigationBarColor = ContextCompat.getColor(activity,R.color.sepia)

                changeColorOfBackgrounds(ContextCompat.getColor(requireContext(), R.color.sepia))
                changeColorOfButtons(ContextCompat.getColor(requireContext(), R.color.sepia_btn))
                binding.btnArticleTextLarger.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.sepia_btn_emphasis))
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
        binding.btnArticleTextSmaller.setBackgroundColor(color)
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

        // Change icon colors
        binding.btnArticleBack.setColorFilter(color)
        binding.btnArticleShare.setColorFilter(color)
        binding.btnRunGazeCalibration.setColorFilter(color)

        binding.btnSaveList.iconTint = ColorStateList.valueOf(color)
        binding.btnTranslateArticle.iconTint = ColorStateList.valueOf(color)
        binding.btnReadAloudArticle.iconTint = ColorStateList.valueOf(color)

        binding.btnArticleTextLarger.setCompoundDrawableTintList(ColorStateList.valueOf(color))
        binding.btnArticleTextSmaller.setCompoundDrawableTintList(ColorStateList.valueOf(color))

        // Change the color of the Recommended Articles RecyclerView (source, title)
        adapter.changeTextColor(color)
    }

    override fun onItemClicked(article: Article) {
        // Action
        val action = ArticleFragmentDirections.actionArticleFragmentSelf(article)
        Navigation.findNavController(requireView()).navigate(action)
    }
}
