package com.khosravi.devin.present.present.http.items

import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.core.text.bold
import androidx.core.text.getSpans
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.withResumed
import com.khosravi.devin.present.R
import com.khosravi.devin.present.data.http.HttpLogDetailData
import com.khosravi.devin.present.databinding.ItemHttpDetailContentBinding
import com.khosravi.devin.present.present.http.HttpFormatUtils
import com.khosravi.devin.present.present.http.JsonConfigColor
import com.khosravi.devin.present.present.http.JsonSpanTextUtil
import com.khosravi.devin.present.present.http.highlightWithDefinedColors
import com.khosravi.devin.present.present.http.highlightWithDefinedColorsSubstring
import com.khosravi.devin.present.present.http.indicesOf
import com.khosravi.devin.present.tool.adapter.FastBindingItem
import com.khosravi.devin.present.tool.adapter.isEmpty
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.GenericItemAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class HttpDetailContentItemView(
    val data: HttpLogDetailData,
    override var identifier: Long,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val lifecycle: Lifecycle,
    private val jsonConfigColor: JsonConfigColor,
) : FastBindingItem<ItemHttpDetailContentBinding>() {

    private val itemAdapter = GenericItemAdapter()
    private val adapter = FastAdapter.with(itemAdapter)

    private var backgroundSpanColor: Int = Color.YELLOW
    private var foregroundSpanColor: Int = Color.RED
    private var backgroundSpanColorSearchItem: Int = Color.GREEN

    override val type: Int
        get() = identifier.toInt()

    private val isRequestMode: Boolean by lazy { identifier == R.id.vh_item_http_detail_request.toLong() }
    private val scrollableIndices = arrayListOf<SearchItemBodyLine>()
    private var currentSearchScrollIndex = -1
    private var currentSearchQuery: String = ""


    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemHttpDetailContentBinding {
        return ItemHttpDetailContentBinding.inflate(inflater, parent, false)
    }

    override fun bindView(binding: ItemHttpDetailContentBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)
        binding.run {
            rvContent.visibility = View.INVISIBLE
            cpiProgress.visibility = View.VISIBLE
            ivSearchDown.setOnClickListener {
                onSearchScrollerButtonClick(false, context)
            }
            ivSearchUp.setOnClickListener {
                onSearchScrollerButtonClick(true, context)
            }
            svText.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String): Boolean {
                    scrollableIndices.clear()
                    currentSearchQuery = newText
                    currentSearchScrollIndex = -1

                    if (newText.isNotBlank() && newText.length > NUMBER_OF_IGNORED_SYMBOLS) {
                        val listOfSearchQuery =
                            highlightQueryWithColors(
                                newText,
                                backgroundSpanColor,
                                foregroundSpanColor,
                            )
                        if (listOfSearchQuery.isNotEmpty()) {
                            scrollableIndices.addAll(listOfSearchQuery)
                        } else {
                            resetHighlight()
                            visibleSearchResultResult(false)
                        }
                    } else {
                        resetHighlight()
                        visibleSearchResultResult(false)
                    }

                    lifecycleScope.launch {
                        delay(DELAY_FOR_SEARCH_SCROLL)
                        lifecycle.withResumed {
                            if (scrollableIndices.isNotEmpty()) {
                                scrollToSearchedItemPosition(0)
                            } else {
                                currentSearchScrollIndex = -1
                            }
                        }
                    }
                    return true
                }

            })

            if (itemAdapter.isEmpty()) {
                val headers = if (isRequestMode) {
                    HttpFormatUtils.formatHeaders(context, data.requestHeaders)
                } else {
                    HttpFormatUtils.formatHeaders(context, data.responseHeaders)
                }

                val bodyLines = if (isRequestMode) {
                    toBodyItems(data.requestBody, data.requestBodyMimeType, context)
                } else toBodyItems(data.responseBody, data.responseBodyMimeType, context)

                itemAdapter.add(HttpHeaderItemView(headers))
                if (bodyLines.isEmpty()) {
                    svText.visibility = View.GONE
                    itemAdapter.add(emptyBodyItem(context))
                } else {
                    svText.visibility = View.VISIBLE
                    itemAdapter.add(bodyLines)
                }
            }

            rvContent.visibility = View.VISIBLE
            cpiProgress.visibility = View.GONE
            rvContent.adapter = adapter
        }
    }

    private fun ItemHttpDetailContentBinding.onSearchScrollerButtonClick(goNext: Boolean, context: Context) {
        // hide the keyboard if visible
//        val inputMethodManager = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
//        if (inputMethodManager.isAcceptingText) {
//            activity?.currentFocus?.clearFocus()
//            inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)
//        }

        if (scrollableIndices.isNotEmpty()) {
            val scrollToIndex =
                if (goNext) {
                    ((currentSearchScrollIndex + 1) % scrollableIndices.size)
                } else {
                    (abs(currentSearchScrollIndex - 1 + scrollableIndices.size) % scrollableIndices.size)
                }
            scrollToSearchedItemPosition(scrollToIndex)
        }
    }


    private fun toBodyItems(content: String?, contentType: String?, context: Context): List<HttpBodyItemView> {
        if (content.isNullOrEmpty()) return emptyList()

        val spanned = HttpFormatUtils.spanBody(jsonConfigColor, content, contentType, context)
        if (spanned.isEmpty()) return emptyList()

        return spanned.lines().map {
            val span = SpannableStringBuilder.valueOf(it)
            HttpBodyItemView(span)
        }
    }


    private fun emptyBodyItem(context: Context) =
        HttpBodyItemView(SpannableStringBuilder.valueOf(context.getString(R.string.msg_body_empty)))

    internal fun highlightQueryWithColors(
        newText: String,
        backgroundColor: Int,
        foregroundColor: Int,
    ): List<SearchItemBodyLine> {
        val listOfSearchItems = arrayListOf<SearchItemBodyLine>()
        itemAdapter.adapterItems.withIndex()
            .forEach { (index, item) ->
                if (item !is HttpBodyItemView) return@forEach
                val listOfOccurrences = item.line.indicesOf(newText)
                if (listOfOccurrences.isNotEmpty()) {
                    // storing the occurrences and their positions
                    listOfOccurrences.forEach {
                        listOfSearchItems.add(
                            SearchItemBodyLine(
                                indexBodyLine = index,
                                indexStartOfQuerySubString = it,
                            ),
                        )
                    }

                    // highlighting the occurrences
                    item.line.clearHighlightSpans()
                    item.line =
                        item.line.highlightWithDefinedColors(
                            newText,
                            listOfOccurrences,
                            backgroundColor,
                            foregroundColor,
                        )
                    adapter.notifyItemChanged(index)
                } else {
                    // Let's clear the spans if we haven't found the query string.
                    val removedSpansCount = item.line.clearHighlightSpans()
                    if (removedSpansCount > 0) {
                        adapter.notifyItemChanged(index)
                    }
                }
            }
        return listOfSearchItems
    }

    private fun ItemHttpDetailContentBinding.scrollToSearchedItemPosition(positionOfScrollableIndices: Int) {
        // reset the last searched item highlight if done
        scrollableIndices.getOrNull(currentSearchScrollIndex)?.let {
            highlightItemWithColorOnPosition(
                it.indexBodyLine,
                it.indexStartOfQuerySubString,
                currentSearchQuery,
                backgroundSpanColor,
                foregroundSpanColor,
            )
        }
        currentSearchScrollIndex = positionOfScrollableIndices
        val scrollTo = scrollableIndices.getOrNull(positionOfScrollableIndices)
        if (scrollTo != null) {
            // highlight the next navigated item and update toolbar summary text
            highlightItemWithColorOnPosition(
                scrollTo.indexBodyLine,
                scrollTo.indexStartOfQuerySubString,
                currentSearchQuery,
                backgroundSpanColorSearchItem,
                foregroundSpanColor,
            )
            updateToolbarText(scrollableIndices.size, positionOfScrollableIndices + 1)
            visibleSearchResultResult()

            rvContent.scrollToPosition(scrollTo.indexBodyLine)
            currentSearchScrollIndex = positionOfScrollableIndices
        }
    }

    private fun ItemHttpDetailContentBinding.visibleSearchResultResult(visible: Boolean = true) {
        cvgSearchResult.isVisible = visible
    }

    private fun ItemHttpDetailContentBinding.updateToolbarText(
        searchResultsCount: Int,
        currentIndex: Int = 1,
    ) {
        tvSearchFound.text =
            SpannableStringBuilder().apply {
                bold {
                    append("$currentIndex / $searchResultsCount")
                }
            }
    }

    private fun CharSequence.lines(): List<CharSequence> {
        val linesList = this.lineSequence().toList()
        val result = mutableListOf<CharSequence>()
        var lineIndex = 0
        for (index in linesList.indices) {
            result.add(subSequence(lineIndex, lineIndex + linesList[index].length))
            lineIndex += linesList[index].length + 1
        }
        if (result.isEmpty()) {
            result.add(subSequence(0, length))
        }
        return result
    }


    internal fun highlightItemWithColorOnPosition(
        position: Int,
        queryStartPosition: Int,
        queryText: String,
        backgroundColor: Int,
        foregroundColor: Int,
    ) {
        val item = itemAdapter.adapterItems.getOrNull(position) as? HttpBodyItemView
        if (item != null) {
            item.line =
                item.line.highlightWithDefinedColorsSubstring(
                    queryText,
                    queryStartPosition,
                    backgroundColor,
                    foregroundColor,
                )
            itemAdapter[position] = item
        }
    }

    internal fun resetHighlight() {
        itemAdapter.adapterItems.withIndex()
            .forEach { (index, item) ->
                if (item !is HttpBodyItemView) return@forEach
                val removedSpansCount = item.line.clearHighlightSpans()
                if (removedSpansCount > 0) {
                    itemAdapter[index] = item
                }
            }
    }

    /**
     * Clear span that created during search process
     * @return Number of spans that removed.
     */
    private fun SpannableStringBuilder.clearHighlightSpans(): Int {
        var removedSpansCount = 0
        val spanList = getSpans<Any>(0, length)
        for (span in spanList)
            if (span !is JsonSpanTextUtil.MyForegroundColorSpan) {
                removeSpan(span)
                removedSpansCount++
            }
        return removedSpansCount
    }

    companion object {
        private const val DELAY_FOR_SEARCH_SCROLL: Long = 600L
        private const val NUMBER_OF_IGNORED_SYMBOLS = 1
    }
}