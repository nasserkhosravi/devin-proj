package com.khosravi.devin.present.present

import com.khosravi.devin.present.R
import com.khosravi.devin.present.filter.CustomFilterCriteria
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.FilterUiData
import com.khosravi.devin.present.filter.IndexFilterItem
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.tool.NotEmptyString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterMenuEntriesTest {

    private val actions = mutableListOf<FilterChipAction>()
    private val selected = mutableListOf<TagFilterItem>()

    private fun entries(filter: FilterItem, withSelection: Boolean = true) =
        filterMenuEntries(filter, { actions += it }, if (withSelection) { t -> selected += t } else null)

    private fun custom(pinned: Boolean = false) =
        CustomFilterItem(FilterUiData("c", NotEmptyString("c"), pinned), CustomFilterCriteria(listOf("a"), null))

    @Test
    fun `index filter has no entries`() {
        assertTrue(entries(IndexFilterItem.instance).isEmpty())
    }

    @Test
    fun `unpinned tag filter offers pin, share and select multiple`() {
        val labels = entries(TagFilterItem("net", isPinned = false)).map { it.label }

        assertEquals(
            listOf(R.string.menu_action_pin, R.string.menu_action_share_as_json, R.string.menu_action_select_multiple),
            labels,
        )
    }

    @Test
    fun `pinned tag filter offers unpin`() {
        assertEquals(R.string.menu_action_unpin, entries(TagFilterItem("net", isPinned = true)).first().label)
    }

    @Test
    fun `tag filter without a selection callback omits select multiple`() {
        val labels = entries(TagFilterItem("net", false), withSelection = false).map { it.label }

        assertEquals(listOf(R.string.menu_action_pin, R.string.menu_action_share_as_json), labels)
    }

    @Test
    fun `custom filter offers pin, edit and remove but not share or select`() {
        val labels = entries(custom()).map { it.label }

        assertEquals(listOf(R.string.menu_action_pin, R.string.menu_action_edit, R.string.menu_action_remove), labels)
    }

    @Test
    fun `pinned custom filter offers unpin first`() {
        assertEquals(R.string.menu_action_unpin, entries(custom(pinned = true)).first().label)
    }

    @Test
    fun `entries dispatch the matching action for their filter`() {
        val tag = TagFilterItem("net", false)
        entries(tag).forEach { it.onClick() }

        assertEquals(listOf<FilterChipAction>(FilterChipAction.TogglePin(tag), FilterChipAction.ShareAsJson(tag)), actions)
        assertEquals(listOf(tag), selected)

        actions.clear()
        val custom = custom()
        entries(custom).forEach { it.onClick() }
        assertEquals(
            listOf(FilterChipAction.TogglePin(custom), FilterChipAction.Edit(custom), FilterChipAction.Remove(listOf(custom))),
            actions,
        )
    }

    @Test
    fun `create filter entry invokes its callback`() {
        var clicked = 0
        val entry = createFilterMenuEntry { clicked++ }

        entry.onClick()

        assertEquals(R.string.menu_create_filter, entry.label)
        assertEquals(1, clicked)
    }
}
