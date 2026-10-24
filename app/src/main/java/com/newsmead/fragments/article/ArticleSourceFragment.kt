package com.newsmead.fragments.article

import android.os.Bundle
import android.util.Log
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
import com.newsmead.databinding.FragmentArticleSourceBinding
import com.newsmead.models.Article
import com.newsmead.recyclerviews.feed.ArticleAdapter
import com.newsmead.recyclerviews.feed.clickListener
import kotlinx.coroutines.launch

class ArticleSourceFragment: Fragment(), clickListener {
    val args: ArticleSourceFragmentArgs by navArgs()
    private lateinit var binding: FragmentArticleSourceBinding
    private lateinit var adapter: ArticleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentArticleSourceBinding.inflate(inflater, container, false)

        // Start shimmer
        binding.shimmerSource.startShimmer()

        // Change logo of ivSourceLogo
        val context = binding.root.context
        val sourceImage = DataHelper.sourceImageMap(args.author)
        DataHelper.loadSourceImage(binding.ivSourceLogo, sourceImage)
        // Change text of tvSourceName to sourceName
        binding.tvSourceName.text = args.author

        Log.d("ArticleSourceFragment", "onCreateView: Binding: ${binding.root}")

        adapter = ArticleAdapter(arrayListOf(), this)
        binding.rvSourceArticles.adapter = adapter

        val layoutManager = LinearLayoutManager(context)
        layoutManager.orientation = LinearLayoutManager.VERTICAL
        binding.rvSourceArticles.layoutManager = layoutManager

        // Add divider between items
        val customDividerItemDecoration = CustomDividerItemDecoration(context, R.drawable.line_divider)
        binding.rvSourceArticles.addItemDecoration(customDividerItemDecoration)

        // Fill chips
        val chipData: ArrayList<String> = DataHelper.loadCategoryData()

        // Add all to start
        chipData.add(0, "All")

        binding.cgCategory.removeAllViews()
        for (category in chipData) {
            val chip = ChipSearchBinding.inflate(inflater, container, false).root
            chip.text = category
            binding.cgCategory.addView(chip)

            // Set onClickListener for each chip
            chip.setOnClickListener {
                updateToCategory(category)
            }
        }

        // Back button to go back to previous fragment
        binding.btnArticleRecommendedBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        lifecycleScope.launch {
            DataHelper.loadArticleData(context, source=DataHelper.reverseSourceNameMap(args.author)) {
                if(FirebaseHelper.isNetworkAvailable(context)) {
                    binding.shimmerSource.stopShimmer()
                    binding.shimmerSource.visibility = View.GONE
                    adapter.updateData(it)
                }
            }
        }

        return binding.root
    }

    fun updateToCategory(category: String) {
        // Uncheck all chips except for the one that was clicked
        for (i in 0 until binding.cgCategory.childCount) {
            val chip = binding.cgCategory.getChildAt(i) as Chip
            if (chip.text != category) {
                chip.isChecked = false
            }
        }

        lifecycleScope.launch {
            val category = if (category == "All") null else category.lowercase()
            DataHelper.loadArticleData(
                context,
                source=DataHelper.reverseSourceNameMap(args.author),
                category=category) {
                adapter.updateData(it)
            }
        }
    }
    override fun onItemClicked(article: Article) {
        // Action
        val action = ArticleSourceFragmentDirections.actionArticleSourceFragmentToArticleFragment(
            article
        )

        Navigation.findNavController(requireView()).navigate(action)
    }
}