package com.newsmead.data

import android.widget.Toast
import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Source
import com.newsmead.models.Article
import com.newsmead.models.SavedList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class FirebaseHelper {
    companion object {
        private var uid = FirebaseAuth.getInstance().currentUser?.uid ?: "null"

        fun getUid(): String {
            uid = FirebaseAuth.getInstance().currentUser?.uid ?: "null"
            return uid
        }

        fun isNetworkAvailable(context: Context): Boolean {
            // Offline study mode: article content is served from a bundled local
            // asset, so treat data as always "available". The feed screens only
            // render inside `if (isNetworkAvailable(...))`, so without this the
            // shimmer would spin forever on a device with no network.
            if (StudyConfig.OFFLINE_MODE) return true

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            return activeNetworkInfo != null && activeNetworkInfo.isConnected
        }

        fun getFirestoreInstance(): FirebaseFirestore {
            // Make offline persistence
            val firestore = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()

            firestore.firestoreSettings = settings

            return firestore
        }

        suspend fun getListsCollection(context: Context): ArrayList<SavedList>? = coroutineScope {
            if (uid == "null") {
                Toast.makeText(context, "Please login to view/create a list", Toast.LENGTH_SHORT).show()
                return@coroutineScope null
            }

            val firestore = getFirestoreInstance()
            val userListsRef = firestore.collection("users").document(uid).collection("lists")

            try {
                // Check if network is available
                val isNetworkAvailable = isNetworkAvailable(context)

                // Reorder to make readLater first
                val finalList = ArrayList<SavedList>()
                var readLater: SavedList? = null
                var offlineArticles: SavedList? = null

                // Use Source.CACHE if network is not available
                val documents = if (isNetworkAvailable) {
                    userListsRef.get().await()
                } else {
                    userListsRef.get(Source.CACHE).await()
                }

                val deferredList = documents.documents.map { document ->
                    async {
                        // Grab collection id
                        val listId = document.id

                        val title = document.data?.get("name").toString()

                        // Get number of articles in list under /articles
                        val numArticles = document.reference.collection("articles").get().await().size()

                        // Create SavedList object
                        val list = SavedList(listId, title, numArticles)
                        if (list.id == "readLater") {
                            readLater = list

                            // Skip
                            return@async null
                        } else if (list.id == "offlineArticles") {
                            offlineArticles = list
                            // Skip
                            return@async null
                        }

                        list
                    }
                }

                // Clean deferredList by removing nulls
                finalList.addAll(deferredList.awaitAll().filterNotNull())

                // Move the read later list at the top
                if (readLater != null) {
                    finalList.add(0, readLater!!)
                }

                // Move the offline articles list at the top
                if (offlineArticles != null) {
                    finalList.add(1, offlineArticles!!)
                }

                finalList
            } catch (exception: Exception) {
                // Handle failure
                null
            }
        }

        suspend fun getOfflineArticlesList(context: Context): List<Article> = getArticlesFromList(context, "offlineArticles")

        suspend fun getAllArticlesFromLists(context: Context): ArrayList<Article> = coroutineScope {
            if (uid == "null") {
                Toast.makeText(context, "Please login to view/create a list", Toast.LENGTH_SHORT).show()
                return@coroutineScope ArrayList<Article>()
            }

            val articles = ArrayList<Article>()

            val firestore = getFirestoreInstance()
            val userListsRef = firestore.collection("users").document(uid).collection("lists")

            try {
                // Check if network is available
                val isNetworkAvailable = isNetworkAvailable(context)

                // Use async to perform the nested query concurrently
                val deferredList = if (isNetworkAvailable) {
                    userListsRef.get().await().documents.map { listDocument ->
                        async {
                            val listId = listDocument.id
                            val listRef = userListsRef.document(listId).collection("articles")

                            val listArticles = listRef.get().await().documents.mapNotNull { articleDocument ->
                                Article(
                                    source = articleDocument.data?.get("source").toString(),
                                    sourceImage = articleDocument.data?.get("sourceImage").toString(),
                                    category = articleDocument.data?.get("category").toString(),
                                    newsId = articleDocument.data?.get("newsId").toString(),
                                    title = articleDocument.data?.get("title").toString(),
                                    imageURL = articleDocument.data?.get("imageURL").toString(),
                                    language = articleDocument.data?.get("language").toString(),
                                    readTime = articleDocument.data?.get("readTime").toString(),
                                    date = articleDocument.data?.get("date").toString(),
                                    body = articleDocument.data?.get("body").toString(),
                                    url = articleDocument.data?.get("url").toString()
                                )
                            }

                            // Remove duplicates
                            listArticles.distinctBy { article ->
                                article.newsId
                            }

                            // Convert to ArrayList
                            listArticles
                        }
                    }
                } else {
                    // If network is not available, use Source.CACHE
                    userListsRef.get(Source.CACHE).await().documents.map { listDocument ->
                        async {
                            val listId = listDocument.id
                            val listRef = userListsRef.document(listId).collection("articles")

                            val listArticles = listRef.get(Source.CACHE).await().documents.mapNotNull { articleDocument ->
                                Article(
                                    source = articleDocument.data?.get("source").toString(),
                                    sourceImage = articleDocument.data?.get("sourceImage").toString(),
                                    category = articleDocument.data?.get("category").toString(),
                                    newsId = articleDocument.data?.get("newsId").toString(),
                                    title = articleDocument.data?.get("title").toString(),
                                    imageURL = articleDocument.data?.get("imageURL").toString(),
                                    language = articleDocument.data?.get("language").toString(),
                                    readTime = articleDocument.data?.get("readTime").toString(),
                                    date = articleDocument.data?.get("date").toString(),
                                    body = articleDocument.data?.get("body").toString(),
                                    url = articleDocument.data?.get("url").toString()
                                )
                            }

                            // Remove duplicates
                            listArticles.distinctBy { article ->
                                article.newsId
                            }

                            // Convert to ArrayList
                            listArticles
                        }
                    }
                }

                // Add all articles to list
                articles.addAll(deferredList.awaitAll().flatten())

                return@coroutineScope articles
            } catch (exception: Exception) {
                // Handle failure
                throw exception
            }
        }

        suspend fun getListsAndArticles(context: Context): Pair<ArrayList<SavedList>, ArrayList<Article>> = coroutineScope {
            if (uid == "null") {
                // Toast.makeText(context, "Please login to view/create a list", Toast.LENGTH_SHORT).show()
                return@coroutineScope Pair(ArrayList<SavedList>(), ArrayList<Article>())
            }

            val lists = ArrayList<SavedList>()
            val articles = ArrayList<Article>()

            val firestore = getFirestoreInstance()
            val userListsRef = firestore.collection("users").document(uid).collection("lists")

            try {
                // Check if network is available
                val isNetworkAvailable = isNetworkAvailable(context)

                // Reorder to make readLater first
                var readLater: SavedList? = null
                var offlineArticles: SavedList? = null

                // Use Source.CACHE if network is not available
                val documents = if (isNetworkAvailable) {
                    userListsRef.get().await()
                } else {
                    userListsRef.get(Source.CACHE).await()
                }

                val deferredList = documents.documents.map { document ->
                    async {
                        // Grab collection id
                        val listId = document.id

                        val title = document.data?.get("name").toString()

                        // Get number of articles in list under /articles
                        val numArticles = document.reference.collection("articles").get().await().size()

                        // Create SavedList object
                        val list = SavedList(listId, title, numArticles)
                        if (list.id == "readLater") {
                            readLater = list

                            // Skip
                            return@async null
                        } else if (list.id == "offlineArticles") {
                            offlineArticles = list
                            // Skip
                            return@async null
                        }

                        list
                    }
                }

                // Clean deferredList by removing nulls
                lists.addAll(deferredList.awaitAll().filterNotNull())

                // Move the read later list at the top
                if (readLater != null) {
                    lists.add(0, readLater!!)
                }

                // Move the offline articles list at the top
                if (offlineArticles != null) {
                    lists.add(1, offlineArticles!!)
                }

                // Use async to perform the nested query concurrently
                val deferredList2 = if (isNetworkAvailable) {
                    userListsRef.get().await().documents.map { listDocument ->
                        async {
                            val listId = listDocument.id
                            val listRef = userListsRef.document(listId).collection("articles")

                            val listArticles = listRef.get().await().documents.mapNotNull { articleDocument ->
                                Article(
                                    source = articleDocument.data?.get("source").toString(),
                                    sourceImage = articleDocument.data?.get("sourceImage").toString(),
                                    category = articleDocument.data?.get("category").toString(),
                                    newsId = articleDocument.data?.get("newsId").toString(),
                                    title = articleDocument.data?.get("title").toString(),
                                    imageURL = articleDocument.data?.get("imageURL").toString(),
                                    language = articleDocument.data?.get("language").toString(),
                                    readTime = articleDocument.data?.get("readTime").toString(),
                                    date = articleDocument.data?.get("date").toString(),
                                    body = articleDocument.data?.get("body").toString(),
                                    url = articleDocument.data?.get("url").toString()
                                )
                            }

                            // Remove duplicates
                            listArticles.distinctBy { article ->
                                article.newsId
                            }

                            // Convert to ArrayList
                            listArticles
                        }
                    }
                } else {
                    // If network is not available, use Source.CACHE
                    userListsRef.get(Source.CACHE).await().documents.map { listDocument ->
                        async {
                            val listId = listDocument.id
                            val listRef = userListsRef.document(listId).collection("articles")

                            val listArticles = listRef.get(Source.CACHE)
                                .await().documents.mapNotNull { articleDocument ->
                                    Article(
                                        source = articleDocument.data?.get("source").toString(),
                                        sourceImage = articleDocument.data?.get("sourceImage").toString(),
                                        category = articleDocument.data?.get("category").toString(),
                                        newsId = articleDocument.data?.get("newsId").toString(),
                                        title = articleDocument.data?.get("title").toString(),
                                        imageURL = articleDocument.data?.get("imageURL").toString(),
                                        language = articleDocument.data?.get("language").toString(),
                                        readTime = articleDocument.data?.get("readTime").toString(),
                                        date = articleDocument.data?.get("date").toString(),
                                        body = articleDocument.data?.get("body").toString(),
                                        url = articleDocument.data?.get("url").toString()
                                    )
                                }
                            // Remove duplicates
                            listArticles.distinctBy { article ->
                                article.newsId
                            }

                            // Convert to ArrayList
                            listArticles

                        }
                    }
                }

                // Add all articles to list
                articles.addAll(deferredList2.awaitAll().flatten())

                return@coroutineScope Pair(lists, articles)
            } catch (exception: Exception) {
                // Handle failure
                throw exception
            }
        }

        suspend fun getArticleIdsFromList(context: Context, listId: String): List<String> = suspendCoroutine { continuation ->
            if (uid == "null") {
                Toast.makeText(context, "Please login to view/create a list", Toast.LENGTH_SHORT).show()
                continuation.resume(emptyList())
            }

            val articleIds = mutableListOf<String>()

            val firestore = getFirestoreInstance()
            val userListsRef = firestore.collection("users").document(uid).collection("lists")
            val listRef = userListsRef.document(listId).collection("articles")

            listRef.get()
                .addOnSuccessListener { documents ->
                    // Get article ids from list
                    articleIds.addAll(documents.mapNotNull { document ->
                        document.data["articleId"].toString()
                    })
                    continuation.resume(articleIds)
                }
                .addOnFailureListener { exception ->
                    // Handle failure
                    continuation.resumeWithException(exception)
                }
        }

        suspend fun getArticlesFromList(context: Context, listId: String): List<Article> = suspendCoroutine { continuation ->
            if (uid == "null") {
                Toast.makeText(context, "Please login to view/create a list", Toast.LENGTH_SHORT).show()
                continuation.resume(emptyList())
            }

            val articles = mutableListOf<Article>()

            val firestore = getFirestoreInstance()
            val userListsRef = firestore.collection("users").document(uid).collection("lists")
            val listRef = userListsRef.document(listId).collection("articles")

            listRef.get()
                .addOnSuccessListener { documents ->
                    // Parse documents into articles
                    articles.addAll(documents.mapNotNull { document ->
                        Article(
                            source = document.data["source"].toString(),
                            sourceImage = document.data["sourceImage"].toString(),
                            category = document.data["category"].toString(),
                            newsId = document.data["newsId"].toString(),
                            title = document.data["title"].toString(),
                            imageURL = document.data["imageURL"].toString(),
                            language = document.data["language"].toString(),
                            readTime = document.data["readTime"].toString(),
                            date = document.data["date"].toString(),
                            body = document.data["body"].toString(),
                            url = document.data["url"].toString()
                        )
                    })

                    continuation.resume(articles)
                }
                .addOnFailureListener { exception ->
                    // Handle failure
                    continuation.resumeWithException(exception)
                }
        }

        suspend fun getHistory(context: Context): List<Article> = coroutineScope {
            if (uid == "null") {
                Toast.makeText(context, "Please login to view history", Toast.LENGTH_SHORT).show()
                return@coroutineScope emptyList()
            }

            val articles = mutableListOf<Article>()

            val firestore = getFirestoreInstance()
            val userHistoryRef = firestore.collection("users").document(uid).collection("history")

            try {
                // Use async to perform the nested query concurrently
                // Store to deferredList based on viewed date
                val deferredList = userHistoryRef.orderBy(
                    "viewed", com.google.firebase.firestore.Query.Direction.DESCENDING
                ).get().await().documents.map { articleDocument ->
                    async {
                        Article(
                            source = articleDocument.data?.get("source").toString(),
                            sourceImage = articleDocument.data?.get("sourceImage").toString(),
                            category = articleDocument.data?.get("category").toString(),
                            newsId = articleDocument.data?.get("newsId").toString(),
                            title = articleDocument.data?.get("title").toString(),
                            imageURL = articleDocument.data?.get("imageURL").toString(),
                            language = articleDocument.data?.get("language").toString(),
                            readTime = articleDocument.data?.get("readTime").toString(),
                            date = articleDocument.data?.get("date").toString(),
                            body = articleDocument.data?.get("body").toString(),
                            url = articleDocument.data?.get("url").toString()
                        )
                    }
                }

                // Add all articles to list
                articles.addAll(deferredList.awaitAll())

                return@coroutineScope articles
            } catch (exception: Exception) {
                // Handle failure
                throw exception
            }
        }

        suspend fun checkIfArticleSavedInLists(context: Context, articleId: String): List<String> = coroutineScope {
            if (uid == "null") {
                Toast.makeText(context, "Please login to view/create a list", Toast.LENGTH_SHORT).show()
                return@coroutineScope emptyList()
            }

            val firestore = getFirestoreInstance()
            val userListsRef = firestore.collection("users").document(uid).collection("lists")

            try {
                // Use async to perform the nested query concurrently
                val lists = userListsRef.get().await().documents
                    .mapNotNull { listDocument ->
                        async {
                            val listId = listDocument.id
                            val listRef = userListsRef.document(listId).collection("articles")

                            val articleExists = listRef
                                .whereEqualTo("newsId", articleId)
                                .get()
                                .await()
                                .documents
                                .isNotEmpty()

                            if (articleExists) listId else null
                        }
                    }
                    .awaitAll()
                    .filterNotNull()

                return@coroutineScope lists
            } catch (exception: Exception) {
                // Handle failure
                throw exception
            }
        }


        /**
         * Adds a new user to Firestore with a "Read Later" and "Offline  Articles" list
         * @param email Email of the new user
         * @return Id of the new user; "" if error
         */
        fun addUserToFireStore(context: Context, email: String, name: String = "User"): String {
            var uid = FirebaseAuth.getInstance().currentUser?.uid
            val firestore = getFirestoreInstance()
            var parsedName = name

            if (uid == null) {
                Toast.makeText(context, "Error creating user", Toast.LENGTH_SHORT).show()
                return ""
            }

            if (name == "") { parsedName = "User" }

            val userRef = firestore.collection("users").document(uid)

            // Create new user document
            userRef.set(
                hashMapOf(
                    "email" to email,
                    "name" to parsedName
                )
            )
                .addOnSuccessListener {
                    // Add "Read Later" list to Firestore
                    val userListsRef = firestore.collection("users").document(uid!!).collection("lists")
                    val newListId = "readLater" // This is the id of the "Read Later" list

                    userListsRef.document(newListId).set(
                        hashMapOf(
                            "name" to "Read Later"
                        )
                    )

                    // Add "Offline Articles" list to Firestore
                    val newListId2 = "offlineArticles" // This is the id of the "Offline Articles" list

                    userListsRef.document(newListId2).set(
                        hashMapOf(
                            "name" to "Offline Articles"
                        )
                    )
                    // Handle Success
                    // Toast.makeText(context, "User created", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    // Handle Failure
                    Toast.makeText(context, "Error creating user", Toast.LENGTH_SHORT).show()
                    uid = ""
                }

            return uid as String
        }       

        
        /**
         * Adds preferred categories to Firestore
         * @param categories List of preferred categories of the user
         */
        fun addPreferredCategoriesToFireStore(requireContext: Context, categories: List<String>) {
            val categoriesPrefListRef = getFirestoreInstance()
                .collection("users")
                .document(getUid())
                .collection("preferences")
                .document("categories")

            // Add preferred categories to Firestore
            Log.d("FirebaseHelper", "Adding preferred categories $categories to $uid")
            categoriesPrefListRef.set(
                hashMapOf(
                    "categories" to categories
                )
            )
                .addOnSuccessListener {
                    // Handle Success
                }
                .addOnFailureListener {
                    // Handle Failure
                    Toast.makeText(requireContext, "Error adding preferred categories", Toast.LENGTH_SHORT).show()
                }
        }



        /**
         * Adds a new list to Firestore
         * @param newListName Name of the new list
         * @return Id of the new list; "" if error
         */
        suspend fun addListToFireStore(context: Context, newListName: String): String {
            if (uid == "null") {
                Toast.makeText(context, "Please login to create a list", Toast.LENGTH_SHORT).show()
                return ""
            }

            val userListsRef = getFirestoreInstance()
                .collection("users")
                .document(uid)
                .collection("lists")

            val newListId = userListsRef.document().id

            val deferred = CompletableDeferred<String>()

            // Create new list document
            userListsRef.document(newListId).set(
                hashMapOf(
                    "name" to newListName
                )
            )
                .addOnSuccessListener {
                    // Handle Success
                    Toast.makeText(context, "List created", Toast.LENGTH_SHORT).show()
                    deferred.complete(newListId)
                }
                .addOnFailureListener {
                    // Handle Failure
                    Toast.makeText(context, "Error creating list", Toast.LENGTH_SHORT).show()
                    deferred.complete("")
                }

            return deferred.await()
        }

        // /**
        //  * Adds an article id to a list in Firestore
        //  * @param listId Id of the list to add the article to
        //  * @param articleId Id of the article to add to the list
        //  */
        // fun addArticleIdToFireStoreList(requireContext: Context, listId: String, articleId: String) {
        //     if (uid == "null") {
        //         Toast.makeText(requireContext, "Please login to add to a list", Toast.LENGTH_SHORT).show()
        //         return
        //     }
        //     val userListsRef = getFirestoreInstance()
        //         .collection("users")
        //         .document(uid)
        //         .collection("lists")
        //     // Add article to list
        //     Log.d("FirebaseHelper", "Adding article $articleId to list $listId")
        //     userListsRef.document(listId).collection("articles").document(articleId).set(
        //         hashMapOf(
        //             "articleId" to articleId
        //         )
        //     )
        //         .addOnSuccessListener {
        //             // Handle Success
        //             Toast.makeText(requireContext, "Article added to list", Toast.LENGTH_SHORT).show()
        //         }
        //         .addOnFailureListener {
        //             // Handle Failure
        //             Toast.makeText(requireContext, "Error adding article to list", Toast.LENGTH_SHORT).show()
        //         }
        // }


        /**
         * Adds an article to a list in Firestore
         * @param listId Id of the list to add the article to
         * @param articleId Id of the article to add to the list
         */
        fun addArticleToFireStoreList(requireContext: Context, listId: String, article: Article) {
            if (uid == "null") {
                Toast.makeText(requireContext, "Please login to add to a list", Toast.LENGTH_SHORT).show()
                return
            }

            val userListsRef = getFirestoreInstance()
                .collection("users")
                .document(uid)
                .collection("lists")

            // Add article to list
            Log.d("FirebaseHelper", "Adding article ${article.newsId} to list $listId")
            userListsRef.document(listId).collection("articles").document(article.newsId).set(
                hashMapOf(
                    "newsId" to article.newsId,
                    "title" to article.title,
                    "imageURL" to article.imageURL,
                    "source" to article.source,
                    "sourceImage" to article.sourceImage,
                    "readTime" to article.readTime,
                    "body" to article.body,
                    "date" to article.date,
                    "url" to article.url
                )
            )
                .addOnSuccessListener {
                    // Handle Success
                }
                .addOnFailureListener {
                    // Handle Failure
                    Toast.makeText(requireContext, "Error adding article to list", Toast.LENGTH_SHORT).show()
                }
        }

        fun deleteArticleFromFirestoreList(requireContext: Context, listId: String, articleId: String) {
            if (uid == "null") {
                Toast.makeText(requireContext, "Please login to delete from a list", Toast.LENGTH_SHORT).show()
                return
            }

            val userListsRef = getFirestoreInstance()
                .collection("users")
                .document(uid)
                .collection("lists")

            // Delete article from list
            userListsRef.document(listId).collection("articles").document(articleId).delete()
                .addOnSuccessListener {
                    // Handle Success
                }
                .addOnFailureListener {
                    // Handle Failure
                    Toast.makeText(requireContext, "Error deleting article from list", Toast.LENGTH_SHORT).show()
                }
        }

        fun deleteArticlesFromFirestoreList(requireContext: Context, listId: String, articleIds: List<String>) {
            if (uid == "null") {
                Toast.makeText(requireContext, "Please login to delete from a list", Toast.LENGTH_SHORT).show()
                return
            }

            val userListsRef = getFirestoreInstance()
                .collection("users")
                .document(uid)
                .collection("lists")

            // Delete article from list
            Log.d("FirebaseHelper", "Deleting articles $articleIds from list $listId")
            for (articleId in articleIds) {
                userListsRef.document(listId).collection("articles").document(articleId).delete()
                    .addOnSuccessListener {
                    }
                    .addOnFailureListener {
                        // Handle Failure
                        Toast.makeText(requireContext, "Error deleting article from list", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        fun deleteList(requireContext: Context, listId: String) {
            if (uid == "null") {
                Toast.makeText(requireContext, "Please login to delete a list", Toast.LENGTH_SHORT).show()
                return
            }

            val userListsRef = getFirestoreInstance()
                .collection("users")
                .document(uid)
                .collection("lists")

            // Delete list
            Log.d("FirebaseHelper", "Deleting list $listId")
            userListsRef.document(listId).delete()
                .addOnSuccessListener {
                    // Handle Success
                }
                .addOnFailureListener {
                    // Handle Failure
                    Toast.makeText(requireContext, "Error deleting list", Toast.LENGTH_SHORT).show()
                }
        }

        fun renameList(requireContext: Context, listId: String, newName: String) {
            if (uid == "null") {
                Toast.makeText(requireContext, "Please login to rename a list", Toast.LENGTH_SHORT).show()
                return
            }

            val userListsRef = getFirestoreInstance()
                .collection("users")
                .document(uid)
                .collection("lists")

            // Rename list
            Log.d("FirebaseHelper", "Renaming list $listId to $newName")
            userListsRef.document(listId).update(
                hashMapOf(
                    "name" to newName
                ) as Map<String, Any>
            )
                .addOnSuccessListener {
                    // Handle Success
                    Toast.makeText(requireContext, "List renamed", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    // Handle Failure
                    Toast.makeText(requireContext, "Error renaming list", Toast.LENGTH_SHORT).show()
                }
        }

        /**
         * Called when viewing an article. Adds the article to the user's history
         * Note: History stores 50 articles max
         * @param article Article to add to history
         */
        fun addArticleToHistory(requireContext: Context, article: Article) {
            if (uid == "null") {
                Toast.makeText(requireContext, "Please login to add to history", Toast.LENGTH_SHORT).show()
                return
            }

            // Get current date and time to store in history
            val currentDateTime = System.currentTimeMillis()

            val userHistoryRef = getFirestoreInstance()
                .collection("users")
                .document(uid)
                .collection("history")

            // If article already exists in history, delete it
            userHistoryRef.document(article.newsId).delete()

            // Add article to history
            Log.d("FirebaseHelper", "Adding article ${article.newsId} to history")
            userHistoryRef.document(article.newsId).set(
                hashMapOf(
                    "newsId" to article.newsId,
                    "title" to article.title,
                    "imageURL" to article.imageURL,
                    "source" to article.source,
                    "sourceImage" to article.sourceImage,
                    "readTime" to article.readTime,
                    "body" to article.body,
                    "date" to article.date,
                    "category" to article.category,
                    "url" to article.url,
                    "viewed" to currentDateTime
                )
            )
                .addOnSuccessListener {
                    // Handle Success
                    // If history has more than 50 articles, delete the oldest one
                    userHistoryRef.orderBy("viewed").limit(50).get()
                        .addOnSuccessListener { documents ->
                            if (documents.size() > 50) {
                                // Delete oldest article
                                val oldestArticle = documents.documents[0]
                                userHistoryRef.document(oldestArticle.id).delete()
                            }
                        }
                }
                .addOnFailureListener {
                    // Handle Failure
                    Toast.makeText(requireContext, "Error adding article to history", Toast.LENGTH_SHORT).show()
                }
        }
    }
}