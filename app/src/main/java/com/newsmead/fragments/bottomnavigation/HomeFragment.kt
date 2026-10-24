package com.newsmead.fragments.bottomnavigation

import android.content.Intent
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.newsmead.R
import com.newsmead.activities.GazeCalibrationActivity
import com.newsmead.activities.ReadingBaselineActivity
import com.newsmead.custom.CustomDividerItemDecoration
import com.newsmead.data.DataHelper
import com.newsmead.data.news.NewsDataApi
import com.newsmead.databinding.FragmentHomeBinding
import com.newsmead.models.Article
import com.newsmead.recyclerviews.feed.ArticleAdapter
import com.newsmead.recyclerviews.feed.clickListener
class HomeFragment : Fragment(), clickListener {

    private lateinit var sharedPreferences: SharedPreferences
    private var language = "English"
    private var feedRequestGeneration = 0
    private lateinit var articleAdapter: ArticleAdapter
    private lateinit var viewBinding: FragmentHomeBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        this.viewBinding = FragmentHomeBinding.inflate(inflater, container, false)
        return this.viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPreferences = requireActivity().getSharedPreferences("language", MODE_PRIVATE)
        language = sharedPreferences.getString("language", "English") ?: "English"
        viewBinding.btnLanguage.text = getString(
            if (language == "Filipino") R.string.home_show_english else R.string.home_show_filipino
        )

        // Start shimmer
        viewBinding.shimmerFeed.startShimmer()

        // Set up Feed RecyclerView
        this.articleAdapter = ArticleAdapter(arrayListOf(), this)

        // Region toggle, persisted
        val region = DataHelper.getNewsRegion(requireContext())
        this.viewBinding.tgRegion.check(
            if (region == NewsDataApi.Region.INTERNATIONAL) R.id.btnRegionInternational
            else R.id.btnRegionLocal
        )
        this.viewBinding.tgRegion.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val selected = if (checkedId == R.id.btnRegionInternational)
                NewsDataApi.Region.INTERNATIONAL else NewsDataApi.Region.LOCAL
            DataHelper.setNewsRegion(requireContext(), selected)
            loadFeed(language)
        }

        // Load data and update the adapter when available
        loadFeed(language, showToast = false)

        this.viewBinding.rvFeed.adapter = articleAdapter
        val linearLayoutManager = LinearLayoutManager(context)
        linearLayoutManager.orientation = LinearLayoutManager.VERTICAL
        this.viewBinding.rvFeed.layoutManager = linearLayoutManager

        // Divider
        val customDivider = CustomDividerItemDecoration(context, R.drawable.line_divider)
        this.viewBinding.rvFeed.addItemDecoration(customDivider)

        // History Button
        this.viewBinding.btnHistory.setOnClickListener {
            // Action
            val action = HomeFragmentDirections.actionHomeFragmentToHistoryFragment()

            // Navigate
            Navigation.findNavController(requireView()).navigate(action)
        }

        // Gaze calibration button - launches the 16-point calibration screen
        this.viewBinding.btnGazeCalibration.setOnClickListener {
            startActivity(Intent(requireContext(), GazeCalibrationActivity::class.java))
        }
        viewBinding.btnReadingBaseline.setOnClickListener {
            startActivity(Intent(requireContext(), ReadingBaselineActivity::class.java))
        }

        // Language Button (Toggles between English and Filipino)
        this.viewBinding.btnLanguage.setOnClickListener {
            if (language == "English") {
                // Change string resource
                this.viewBinding.btnLanguage.text = getString(R.string.home_show_english)
                language = "Filipino"
                sharedPreferences.edit().putString("language", "Filipino").apply()

            } else {
                // Change string resource
                this.viewBinding.btnLanguage.text = getString(R.string.home_show_filipino)
                language = "English"
                sharedPreferences.edit().putString("language", "English").apply()
            }
            loadFeed(language)
        }
    }

    private fun loadFeed(language: String, showToast: Boolean = true) {
        val requestGeneration = ++feedRequestGeneration
        val requestView = viewBinding.root
        viewBinding.rvFeed.visibility = View.GONE
        viewBinding.shimmerFeed.startShimmer()
        viewBinding.shimmerFeed.visibility = View.VISIBLE

        DataHelper.loadArticleData(context, language = language) { articles ->
            // May arrive after leaving the screen.
            val ctx = context ?: return@loadArticleData
            if (view !== requestView || requestGeneration != feedRequestGeneration) return@loadArticleData

            // Stop the shimmer
            viewBinding.shimmerFeed.stopShimmer()
            viewBinding.shimmerFeed.visibility = View.GONE
            viewBinding.rvFeed.visibility = View.VISIBLE
            // Update the adapter with the retrieved articles
            articleAdapter.updateData(articles)
            // New feed: scroll to top.
            viewBinding.rvFeed.scrollToPosition(0)

            if (showToast && articles.isNotEmpty()) {
                val where = if (DataHelper.getNewsRegion(ctx) == NewsDataApi.Region.INTERNATIONAL)
                    "world" else "Philippine"
                Toast.makeText(ctx, "You're now seeing: $language $where news", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onItemClicked(article: Article) {
        // Action
        val action = HomeFragmentDirections.actionHomeFragmentToArticleActivityStart(
            article
        )

        // Navigate
        Navigation.findNavController(requireView()).navigate(action)
    }
}
