package com.newsmead.fragments.article

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.newsmead.custom.CustomDividerItemDecoration
import com.newsmead.data.DataHelper
import com.newsmead.R
import com.newsmead.data.FirebaseHelper
import com.newsmead.databinding.ChipSearchBinding
import com.newsmead.databinding.FragmentArticleSearchSourceBinding
import com.newsmead.models.Article
import com.newsmead.recyclerviews.feed.ArticleAdapter
import com.newsmead.recyclerviews.feed.clickListener
import kotlinx.coroutines.launch

class ArticleSearchSourceFragment: Fragment(), clickListener {
    private val args: ArticleSearchSourceFragmentArgs by navArgs()
    private lateinit var binding: FragmentArticleSearchSourceBinding
    private lateinit var adapter: ArticleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentArticleSearchSourceBinding.inflate(inflater, container, false)

        // Start shimmer
        binding.shimmerSearchSource.startShimmer()

        // Change text of tvSourceName to sourceName
        binding.tvSearchSourceName.text = args.sourceName
        // Change logo of ivSearchSourceLogo
        val context = binding.root.context
        val sourceImage = DataHelper.sourceImageMap(args.sourceName)
        DataHelper.loadSourceImage(binding.ivSearchSourceLogo, sourceImage)

        adapter = ArticleAdapter(arrayListOf(), this)
        binding.rvSearchSourceArticles.adapter = adapter

        val layoutManager = LinearLayoutManager(context)
        layoutManager.orientation = LinearLayoutManager.VERTICAL
        binding.rvSearchSourceArticles.layoutManager = layoutManager

        // Add divider between items
        val customDividerItemDecoration = CustomDividerItemDecoration(context, R.drawable.line_divider)
        binding.rvSearchSourceArticles.addItemDecoration(customDividerItemDecoration)

        // Fill chips
        val chipData: ArrayList<String> = DataHelper.loadCategoryData()

        // Add all to start
        chipData.add(0, "All")

        binding.cgSearchSourceCategory.removeAllViews()
        for (category in chipData) {
            val chip = ChipSearchBinding.inflate(inflater, container, false).root
            chip.text = category
            binding.cgSearchSourceCategory.addView(chip)

            // Set onClickListener for each chip
            chip.setOnClickListener {
                updateToCategory(category)
            }
        }

        // Back button to go back to previous fragment
        binding.btnArticleSearchRecommendedBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        lifecycleScope.launch {
            DataHelper.loadArticleData(context, source = DataHelper.reverseSourceNameMap(args.sourceName)) {
                if(FirebaseHelper.isNetworkAvailable(requireContext())) {
                    // Stop the shimmer
                    binding.shimmerSearchSource.stopShimmer()
                    binding.shimmerSearchSource.visibility = View.GONE
                    // Update the adapter with the retrieved articles
                    adapter.updateData(it)
                }
            }
        }

        return binding.root
    }

    fun updateToCategory(category: String) {
        // Uncheck all chips except for the one that was clicked
        for (i in 0 until binding.cgSearchSourceCategory.childCount) {
            val chip = binding.cgSearchSourceCategory.getChildAt(i) as Chip
            if (chip.text != category) {
                chip.isChecked = false
            }
        }

        lifecycleScope.launch {
            val category = if (category == "All") null else category.lowercase()
            DataHelper.loadArticleData(
                context,
                source=DataHelper.reverseSourceNameMap(args.sourceName),
                category=category) {
                adapter.updateData(it)
            }
        }
    }

    override fun onItemClicked(article: Article) {
        // Action
        val action = ArticleSearchSourceFragmentDirections.actionArticleSearchSourceFragmentToArticleActivityStart(
            article
        )

        // Navigate
        Navigation.findNavController(requireView()).navigate(action)
    }
}