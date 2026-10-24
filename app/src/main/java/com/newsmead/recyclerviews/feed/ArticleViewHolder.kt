package com.newsmead.recyclerviews.feed

import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.newsmead.R
import com.newsmead.data.DataHelper
import com.newsmead.databinding.ItemFeedArticleBinding
import com.newsmead.models.Article

class ArticleViewHolder(private val viewBinding: ItemFeedArticleBinding): RecyclerView.ViewHolder(viewBinding.root) {
    // Performs the binding of the article data to the views in the ViewHolder
    fun bindData(article: Article) {
        this.viewBinding.tvSource.text = article.source
        val context = viewBinding.root.context
        DataHelper.loadSourceImage(this.viewBinding.ivSourceImage, article.sourceImage)
        this.viewBinding.tvArticleTitle.text = article.title
        if (article.imageURL != null && article.imageURL != "")
            Glide.with(this.viewBinding.root).load(article.imageURL).error(R.drawable.sample_article_image).into(this.viewBinding.ivArticleImage)
        else
            this.viewBinding.ivArticleImage.setImageResource(R.drawable.sample_article_image)
        this.viewBinding.tvArticleDate.text = article.date
        this.viewBinding.tvReadTime.text = article.readTime
    }

    fun getCardView() = viewBinding.cvArticleCard
}