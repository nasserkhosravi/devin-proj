package com.khosravi.devin.present.uikit.component

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

abstract class EndlessScrollListener(
    private val layoutManager: LinearLayoutManager,
    private val visibleThreshold: Int = 5
) : RecyclerView.OnScrollListener() {

    private var loading = false
    private var previousTotalItemCount = 0
    private var currentPage = 0
    private var isLastPage = false

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        if (dy <= 0 || isLastPage) return  // Only trigger on scroll down & if more pages exist

        val totalItemCount = layoutManager.itemCount
        val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

        if (!loading && lastVisibleItemPosition + visibleThreshold >= totalItemCount) {
            loading = true
            currentPage++
            onLoadMore(currentPage, totalItemCount)
        }
    }

    fun resetState() {
        currentPage = 0
        previousTotalItemCount = 0
        loading = false
        isLastPage = false
    }

    /**
     * Call this after loading more data is complete.
     */
    fun setLoaded(isLastPage: Boolean = false) {
        loading = false
        this.isLastPage = isLastPage
        previousTotalItemCount = layoutManager.itemCount
    }

    abstract fun onLoadMore(page: Int, totalItemsCount: Int)
}
