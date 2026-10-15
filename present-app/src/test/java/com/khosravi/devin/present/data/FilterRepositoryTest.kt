package com.khosravi.devin.present.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.present.filter.CustomFilterCriteria
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.FilterUiData
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.tool.NotEmptyString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilterRepositoryTest {

    private lateinit var repository: FilterRepository

    @Before
    fun setUp() {
        repository = FilterRepository(ApplicationProvider.getApplicationContext<Context>())
    }

    private fun filter(title: String, tag: String? = "net", search: String? = "GET") = CustomFilterItem(
        FilterUiData(title, NotEmptyString(title), isPinned = false),
        CustomFilterCriteria(tag, search),
    )

    private fun saveInOrder(clientId: String, vararg filters: CustomFilterItem) {
        filters.forEach {
            assertTrue(repository.saveFilter(it, clientId))
            Thread.sleep(2) // filters are ordered by their save timestamp
        }
    }

    @Test
    fun `saved filters are returned for their client in save order`() {
        saveInOrder("a", filter("first"), filter("second", tag = "db", search = "SELECT"))

        val result = repository.getCustomFilterItemList("a").map { it as CustomFilterItem }

        assertEquals(listOf("first", "second"), result.map { it.id })
        assertEquals(CustomFilterCriteria("db", "SELECT"), result[1].criteria)
        assertFalse(result[1].ui.isPinned)
    }

    @Test
    fun `filters of other clients are not returned`() {
        saveInOrder("a", filter("mine"))
        saveInOrder("b", filter("theirs"))

        assertEquals(listOf("mine"), repository.getCustomFilterItemList("a").map { it.id })
    }

    @Test
    fun `saving with an existing title replaces the filter`() {
        saveInOrder("a", filter("f", tag = "old"), filter("f", tag = "new"))

        val result = repository.getCustomFilterItemList("a")
        assertEquals(1, result.size)
        assertEquals("new", (result[0] as CustomFilterItem).criteria.tag)
    }

    @Test
    fun `removeFilter and clearSync delete filters`() {
        val keep = filter("keep")
        val drop = filter("drop")
        saveInOrder("a", keep, drop)

        repository.removeFilter(drop)
        assertEquals(listOf("keep"), repository.getCustomFilterItemList("a").map { it.id })

        assertTrue(repository.clearSync())
        assertTrue(repository.getCustomFilterItemList("a").isEmpty())
    }

    @Test
    fun `pinning is reflected in loaded filters and can be undone`() {
        val item = filter("pinned")
        saveInOrder("a", item)
        // Default pins (e.g. okhttp) are seeded on first read; only the change from this item matters.
        val defaultPins = repository.getPinnedListIds().toSet()

        repository.saveAsPinned(item)
        assertEquals(defaultPins + "pinned", repository.getPinnedListIds().toSet())
        assertTrue(repository.getCustomFilterItemList("a").single().ui.isPinned)

        repository.removeAsPinned(item)
        assertEquals(defaultPins, repository.getPinnedListIds().toSet())
        assertFalse(repository.getCustomFilterItemList("a").single().ui.isPinned)
    }

    @Test
    fun `tag filters skip tags already used as custom filter ids and mark pinned tags`() {
        repository.saveAsPinned(TagFilterItem("db", isPinned = false))
        val userFilters: List<FilterItem> = listOf(filter("net"))

        val result = repository.createTagFilterList(setOf("net", "db", "ui"), userFilters)

        assertEquals(setOf("db", "ui"), result.keys)
        assertTrue(result.getValue("db").ui.isPinned)
        assertFalse(result.getValue("ui").ui.isPinned)
        assertTrue(result.getValue("ui") is TagFilterItem)
    }
}
