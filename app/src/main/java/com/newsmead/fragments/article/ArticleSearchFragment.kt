package com.newsmead.fragments.article

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.newsmead.data.DataHelper
import com.newsmead.R
import com.newsmead.custom.CustomDividerItemDecoration
import com.newsmead.data.FirebaseHelper
import com.newsmead.logging.Analytics
import com.newsmead.research.ResearchSession
import com.newsmead.databinding.FragmentArticleSearchBinding
import com.newsmead.fragments.layouts.BottomSheetDialogSearchFilter
import com.newsmead.models.Article
import com.newsmead.recyclerviews.feed.ArticleAdapter
import com.newsmead.recyclerviews.feed.clickListener
import kotlinx.coroutines.launch

class ArticleSearchFragment : Fragment(), clickListener {
    private val args: ArticleSearchFragmentArgs by navArgs()
    private lateinit var binding: FragmentArticleSearchBinding
    private lateinit var adapter: ArticleAdapter
    private var categoryVal: String = ""
    private var sourceVal: String = ""
    private var languageVal: String = ""
    private var sortByVal: String = ""
    private var startDateVal: String = ""
    private var endDateVal: String = ""
    private var searchQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentArticleSearchBinding.inflate(
            inflater, container, false
        )

        // Start shimmer
        binding.shimmerSearch.startShimmer()

        // Receive search query from SearchFragment
        searchQuery = args.searchString

        // Change text to search query
        binding.tvSearchingForPrompt.text = searchQuery
        binding.svSearchBar.setQuery(searchQuery, false)

        // RecyclerView of Articles
        adapter = ArticleAdapter(arrayListOf(), this)

        binding.rvSearchArticles.adapter = adapter

        val layoutManager = LinearLayoutManager(context)
        layoutManager.orientation = LinearLayoutManager.VERTICAL
        binding.rvSearchArticles.layoutManager = layoutManager

        // Add divider between items
        val customDividerItemDecoration = CustomDividerItemDecoration(context, R.drawable.line_divider)
        binding.rvSearchArticles.addItemDecoration(customDividerItemDecoration)

        // Back button to go back to previous fragment
        // binding.btnArticleRecommendedBack.setOnClickListener {
        //     requireActivity().onBackPressedDispatcher.onBackPressed()
        // }

        // Dialog to show when user clicks on filter
        binding.btnSearchFilter.setOnClickListener {
            val bottomSheetDialogSearchFilter = BottomSheetDialogSearchFilter.newInstance(
                categoryVal,
                sourceVal,
                languageVal,
                sortByVal,
                startDateVal,
                endDateVal
            )

            bottomSheetDialogSearchFilter.onApplyFiltersListener = object : BottomSheetDialogSearchFilter.OnApplyFiltersListener {
                override fun onApplyFilters(category: String?, source: String, language: String, sortBy: String, startDate: String, endDate: String) {
                    categoryVal = category ?: ""
                    sourceVal = source
                    languageVal = language
                    sortByVal = sortBy
                    startDateVal = startDate
                    endDateVal = endDate
                    updateWithFilters()
                }
            }
            bottomSheetDialogSearchFilter.show(requireActivity().supportFragmentManager, "BottomSheetDialogSearchFilter")
        }

        // Search bar on submit or search
        binding.svSearchBar.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                Log.d("Search", "Query: $query")
                searchQuery = query ?: ""
                binding.tvSearchingForPrompt.text = searchQuery
                updateWithFilters()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return true
            }
        })

        lifecycleScope.launch {
            DataHelper.loadArticleData(context, searchText = searchQuery) {

                val language = if (languageVal == "") "english" else languageVal.lowercase()
                Analytics.search(searchQuery.length, language, it.size)
                ResearchSession.search(searchQuery.length, language, it.size)

                if(FirebaseHelper.isNetworkAvailable(requireContext())) {
                    // Stop the shimmer
                    binding.shimmerSearch.stopShimmer()
                    binding.shimmerSearch.visibility = View.GONE
                    // Update the adapter with the retrieved articles
                    binding.tvResultCount.text = "${it.size} results"
                    adapter.updateData(it)
                }
            }
        }

        // Inflate the layout for this fragment
        return binding.root
    }

    fun updateWithFilters() {
        val source = if (sourceVal == "") null else DataHelper.reverseSourceNameMap(sourceVal)
        val category = if (categoryVal == "All" || categoryVal == "") null else categoryVal.lowercase()
        val language = if (languageVal == "") "english" else languageVal.lowercase()
        val sortBy = sortByVal.lowercase()
        val startDate = if (startDateVal == "") null else startDateVal
        val endDate = if (endDateVal == "") null else endDateVal

        lifecycleScope.launch {
            DataHelper.loadArticleData(
                context,
                category=category,
                source=source,
                language=language,
                startDate=startDate,
                endDate=endDate,
                searchText=searchQuery) {
                binding.tvResultCount.text = "${it.size} results"
                adapter.updateData(it)

                // Logged once results are back, so result_count is real. Only

                Analytics.search(searchQuery.length, language, it.size)
                ResearchSession.search(searchQuery.length, language, it.size)
            }
        }
    }

    override fun onItemClicked(article: Article) {
        // Action
        val action = ArticleSearchFragmentDirections.actionArticleSearchFragmentToArticleActivityStart(
            article
        )

        // Navigate
        Navigation.findNavController(requireView()).navigate(action)
    }
}