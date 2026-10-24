package com.newsmead.fragments.layouts

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.newsmead.data.DatabaseHelper
import com.newsmead.data.FirebaseHelper
import com.newsmead.logging.Analytics
import com.newsmead.research.ResearchSession
import com.newsmead.data.PreloadedData
import com.newsmead.databinding.BottomSheetDialogSaveListBinding
import com.newsmead.models.Article
import com.newsmead.models.NewsArticle
import com.newsmead.models.SavedList
import com.newsmead.recyclerviews.dialog.SheetDialogAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BottomSheetDialogSaveFragment(
    private val article: Article,
    private val body: String
): BottomSheetDialogFragment(), listListener {

    private lateinit var binding: BottomSheetDialogSaveListBinding
    private lateinit var lists: ArrayList<SavedList>
    private var checkedLists: ArrayList<String> = ArrayList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        this.binding = BottomSheetDialogSaveListBinding.inflate(inflater, container, false)

        lists = PreloadedData.lists

        val adapter = SheetDialogAdapter(lists, this@BottomSheetDialogSaveFragment)
        binding.rvDialogList.adapter = adapter
        val layoutManager = LinearLayoutManager(context)
        layoutManager.orientation = LinearLayoutManager.VERTICAL
        binding.rvDialogList.layoutManager = layoutManager

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Check if article is already saved to a list
                val fbCheckedLists = FirebaseHelper.checkIfArticleSavedInLists(requireContext(), article.newsId)

                Log.d("BottomSheetDialogSaveFragment", "Checked lists: $fbCheckedLists")
                checkedLists.addAll(fbCheckedLists as ArrayList<String>)

                // Update UI on the main thread
                withContext(Dispatchers.Main) {
                    adapter.checkLists(checkedLists)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error checking lists", Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            }
        }

        binding.btnNewList.setOnClickListener {
            showNewListDialog()
        }

        binding.btnListDone.setOnClickListener {
            Log.d("BottomSheetDialogSaveFragment", "Saving List...")
            Log.d("BottomSheetDialogSaveFragment", "Checked lists: $checkedLists")

            lifecycleScope.launch {
                // Save details here
                if (checkedLists.isEmpty()) {
                    Toast.makeText(context, "No list selected", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d("BottomSheetDialogSaveFragment", "Saving article to lists...")

                    // Save article to checked lists
                    for (listId in checkedLists) {
                        Log.d("BottomSheetDialogSaveFragment", "Saving article ${article.newsId} to list $listId")
                        val article: Article = article
                        FirebaseHelper.addArticleToFireStoreList(
                            requireContext(), listId, article
                        )
                        Analytics.saveToList(article.newsId, listId)
                        ResearchSession.saveToList(article.newsId, listId)

                        // Add to local database IF listId is offlineArticle
                        if (listId == "offlineArticles") {
                            val offlineId = article.newsId
                            var offlineArticleContent = "Offline Article Content Goes Here (Did not save properly)"
                            if (body != "") {
                                offlineArticleContent = body
                            }

                            val offlineArticle = NewsArticle(
                                offlineId,
                                offlineArticleContent,
                                System.currentTimeMillis()
                            )

                            Log.d("BottomSheetDialogSaveFragment", "Saving offline article ${article.newsId} to offlineArticles")
                            DatabaseHelper.getNewsArticleDao().insertNewsArticle(offlineArticle)
                        }
                    }

                    // Remove article from unchecked lists
                    Log.d("BottomSheetDialogSaveFragment", "Removing article from unchecked lists... $checkedLists")
                    val uncheckedLists = lists.filter { !checkedLists.contains(it.id) }
                    Log.d("BottomSheetDialogSaveFragment", "Unchecked lists: $uncheckedLists")
                    for (list in uncheckedLists) {
                        FirebaseHelper.deleteArticleFromFirestoreList(
                            requireContext(), list.id, article.newsId
                        )
                        Analytics.removeFromList(article.newsId, list.id)
                        ResearchSession.removeFromList(article.newsId, list.id)

                        // Remove from local database IF listId is offlineArticle
                        if (list.id == "offlineArticle") {
                            Log.d("BottomSheetDialogSaveFragment", "Removing article ${article.newsId} from offlineArticle")
                            val offlineId = article.newsId

                            DatabaseHelper.getNewsArticleDao().deleteNewsArticle(offlineId)
                        }
                    }

                    Toast.makeText(context, "Article saved to ${checkedLists.size} lists", Toast.LENGTH_SHORT).show()
                }

                dismiss()
            }
        }


        return binding.root
    }

    private fun showNewListDialog() {
        val newListDialog = NewListDialog(requireContext()) { newListName ->
            lifecycleScope.launch {
                if (newListName == "") {
                    Toast.makeText(context, "List name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Add new list to database
                val result = FirebaseHelper.addListToFireStore(requireContext(), newListName)

                if (result != "") {
                    // Add new list to recyclerView
                    val adapter = binding.rvDialogList.adapter as SheetDialogAdapter
                    val newList = SavedList(result, newListName)

                    PreloadedData.lists.add(newList)
                    adapter.addNewList(newList)
                }
            }
        }
        newListDialog.show()
    }

    /**
     * Called when a list is checked or unchecked
     * @param listId Id of the list that was checked or unchecked
     */
    override fun onListChecked(listId: String) {
        // Add or remove listId from checkedLists
        if (checkedLists.contains(listId)) {
            checkedLists.remove(listId)
        } else {
            checkedLists.add(listId)
        }

        Log.d("BottomSheetDialogSaveFragment", "Checked lists now: $checkedLists")
    }
}