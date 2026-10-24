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

    private lateinit var context: Context
    private lateinit var repository: FilterRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = FilterRepository(context)
    }

    private fun filter(title: String, tags: List<String> = listOf("net"), search: String? = "GET") = CustomFilterItem(
        FilterUiData(title, NotEmptyString(title), isPinned = false),
        CustomFilterCriteria(tags, search),
    )

    private fun saveInOrder(clientId: String, vararg filters: CustomFilterItem) {
        filters.forEach {
            assertTrue(repository.saveFilter(it, clientId))
            Thread.sleep(2) // filters are ordered by their save timestamp
        }
    }

    @Test
    fun `saved filters are returned for their client in save order`() {
        saveInOrder("a", filter("first"), filter("second", tags = listOf("db", "cache"), search = "SELECT"))

        val result = repository.getCustomFilterItemList("a").map { it as CustomFilterItem }

        assertEquals(listOf("first", "second"), result.map { it.id })
        assertEquals(CustomFilterCriteria(listOf("db", "cache"), "SELECT"), result[1].criteria)
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
        saveInOrder("a", filter("f", tags = listOf("old")), filter("f", tags = listOf("new")))

        val result = repository.getCustomFilterItemList("a")
        assertEquals(1, result.size)
        assertEquals(listOf("new"), (result[0] as CustomFilterItem).criteria.tags)
    }

    @Test
    fun `blank search text loads as null`() {
        saveInOrder("a", filter("f", search = null))

        assertEquals(null, (repository.getCustomFilterItemList("a").single() as CustomFilterItem).criteria.searchText)
    }

    @Test
    fun `entries without a client id or tags, or that don't parse, are skipped`() {
        saveInOrder("a", filter("good"))
        context.getSharedPreferences(PREF_FILTERS, Context.MODE_PRIVATE).edit()
            .putString("global", """{"_id":"global","_timestamp":1,"_CRITERIA":{"_TAGS":["net"]},"_UI":{"_TITLE":"global"}}""")
            .putString("noTags", """{"_id":"noTags","_client_id":"a","_timestamp":1,"_CRITERIA":{"_TAGS":[]},"_UI":{"_TITLE":"noTags"}}""")
            .putString("broken", "{not json")
            .commit()

        assertEquals(listOf("good"), repository.getCustomFilterItemList("a").map { it.id })
    }

    @Test
    fun `legacy single-tag filters and their pins are dropped once`() {
        context.getSharedPreferences(PREF_LEGACY_FILTERS, Context.MODE_PRIVATE).edit()
            .putString("old", """{"_id":"old","_client_id":"a","_timestamp":1,"_CRITERIA":{"_TAG":"net"},"_UI":{"_TITLE":"old"}}""")
            .commit()
        repository.pinTag("old")
        repository.pinTag("keep")

        assertTrue(repository.getCustomFilterItemList("a").isEmpty())
        assertTrue(context.getSharedPreferences(PREF_LEGACY_FILTERS, Context.MODE_PRIVATE).all.isEmpty())
        val pins = repository.getPinnedListIds()
        assertFalse("old" in pins)
        assertTrue("keep" in pins)

        // Already dropped: a pin added later with the same id is left alone.
        repository.pinTag("old")
        repository.getCustomFilterItemList("a")
        assertTrue("old" in repository.getPinnedListIds())
    }

    @Test
    fun `removeFilter and clearSync delete filters`() {
        val keep = filter("keep")
        val drop = filter("drop")
        saveInOrder("a", keep, drop)

        repository.saveAsPinned(drop)

        repository.removeFilter(drop)
        assertEquals(listOf("keep"), repository.getCustomFilterItemList("a").map { it.id })
        assertFalse("drop" in repository.getPinnedListIds())

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
        val userFilters: List<FilterItem> = listOf(filter("net", tags = listOf("api")))

        val result = repository.createTagFilterList(setOf("net", "db", "ui"), userFilters)

        assertEquals(setOf("db", "ui"), result.keys)
        assertTrue(result.getValue("db").ui.isPinned)
        assertFalse(result.getValue("ui").ui.isPinned)
        assertTrue(result.getValue("ui") is TagFilterItem)
    }

    private companion object {
        const val PREF_FILTERS = "filter_v2"
        const val PREF_LEGACY_FILTERS = "filter"
    }
}
