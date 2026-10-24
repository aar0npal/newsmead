package com.newsmead.fragments.saved

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.newsmead.data.DatabaseHelper
import com.newsmead.data.FirebaseHelper
import com.newsmead.logging.Analytics
import com.newsmead.research.ResearchSession
import com.newsmead.databinding.FragmentSavedListEditBinding
import com.newsmead.fragments.layouts.listListener
import com.newsmead.models.Article
import com.newsmead.recyclerviews.feed.ArticleEditAdapter
import kotlinx.coroutines.launch

class SavedListEditFragment: Fragment(), listListener {
    private val args: SavedListEditFragmentArgs by navArgs()
    private lateinit var binding: FragmentSavedListEditBinding
    private val articleIds = ArrayList<String>()
    val data: ArrayList<Article> = ArrayList()

    private val checkedIds: ArrayList<String> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Print args
        Log.d("SavedListEditArticles", "ListId: ${args.listId} ListName: ${args.listName}")
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSavedListEditBinding.inflate(inflater, container, false)

        // Update name of list
        binding.tvSavedListEditName.text = args.listName

        val adapter = ArticleEditAdapter(data, this)
        binding.rvSavedEditArticles.adapter = adapter

        val layoutManager = LinearLayoutManager(context)
        layoutManager.orientation = LinearLayoutManager.VERTICAL
        binding.rvSavedEditArticles.layoutManager = layoutManager

        // Back button
        binding.btnSavedEditBack.setOnClickListener {
            // Simply pop the current fragment off the stack
            val navController = binding.root.findNavController()
            navController.popBackStack()
        }

        // Populate data with articles
        val articles: ArrayList<Article> = args.listArticlesParcel?.toCollection(ArrayList()) ?: ArrayList()
        for (article in articles) {
            data.add(article)
            articleIds.add(article.newsId)
        }

        // Save button
        binding.btnEditSave.setOnClickListener {
            // Save the checkedIds to the list
            Log.d("SavedListEditArticles", "CheckedIds: $checkedIds")

            // Delete articles from list
            FirebaseHelper.deleteArticlesFromFirestoreList(requireActivity(), args.listId, checkedIds)
            for (articleId in checkedIds) {
                Analytics.removeFromList(articleId, args.listId)
                ResearchSession.removeFromList(articleId, args.listId)
            }

            lifecycleScope.launch {
                // Remove from offline if list is offline
                if (args.listId == "offlineArticles") {
                    for (articleId in checkedIds) {
                        DatabaseHelper.getNewsArticleDao().deleteNewsArticle(articleId)
                    }
                }
            }

            // Simply pop the current fragment off the stack
            val navController = binding.root.findNavController()
            navController.popBackStack()
        }


        return binding.root
    }

    override fun onListChecked(listId: String) {
        Log.d("SavedListEditArticles", "ListId: $listId")
        // If checked, add to checkedIds
        // If unchecked, remove from checkedIds
        if (checkedIds.contains(listId)) {
            checkedIds.remove(listId)
        } else {
            checkedIds.add(listId)
        }

        // Disable button if no checkedIds
        if (checkedIds.size == 0) {
            binding.btnEditSave.isEnabled = false

            // Update delete button to number of checkedIds
            val newDeleteText = "Delete"
            binding.btnEditSave.text = newDeleteText
        } else {
            binding.btnEditSave.isEnabled = true

            // Update delete button to number of checkedIds
            val newDeleteText = "Delete (${checkedIds.size}) articles"
            binding.btnEditSave.text = newDeleteText
        }
    }
}