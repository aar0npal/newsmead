package com.newsmead.recyclerviews.feed

import android.widget.CheckBox
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.newsmead.R
import com.newsmead.data.DataHelper
import com.newsmead.databinding.ItemListEditBinding
import com.newsmead.models.Article

class ArticleEditViewHolder(
    private val binding: ItemListEditBinding
): RecyclerView.ViewHolder(binding.root) {
    fun bindData(article: Article) {
        binding.tvArticleEditTitle.text = article.title
        binding.tvEditSource.text = article.source
        binding.tvArticleEditDate.text = article.date
        binding.tvEditReadTime.text = article.readTime
        if (article.imageURL != null && article.imageURL != "")
            Glide.with(binding.root).load(article.imageURL).error(R.drawable.sample_article_image).into(binding.ivArticleEditImage)
        else
            binding.ivArticleEditImage.setImageResource(R.drawable.sample_article_image)
        val context = binding.root.context
        DataHelper.loadSourceImage(binding.ivSourceEditImage, article.sourceImage)
    }

    fun getCardView(): CardView {
        return binding.cvArticleEditCard
    }

    fun getCheckBox(): CheckBox {
        return binding.cbArticleEdit
    }

    fun toggleCheckBox() {
        binding.cbArticleEdit.isChecked = !binding.cbArticleEdit.isChecked
    }
}