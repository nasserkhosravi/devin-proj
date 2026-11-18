package com.khosravi.devin.present.present

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import com.khosravi.devin.present.filter.CustomFilterItem

/**
 * The "create custom filter" / "edit custom filter" dialog ([FilterDialog]) as a NavHost dialog, so it and its fields survive rotation.
 * Open it with [navigateToCreateFilter] or [navigateToEditFilter]; the tags (and, when editing, the title and search
 * text) travel in the destination's arguments Bundle. [onConfirm] gets the original title when editing, else null.
 */
fun NavGraphBuilder.createFilterDestination(
    loadAvailableTags: suspend () -> List<String>,
    isTitleTaken: (String) -> Boolean,
    onConfirm: (CustomFilterItem, originalTitle: String?) -> Unit,
) {
    dialog(
        CREATE_FILTER_ROUTE,
        arguments = listOf(
            navArgument(ARG_PRESELECTED_TAGS) { type = NavType.StringArrayType; nullable = true; defaultValue = null },
            navArgument(ARG_EDIT_TITLE) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(ARG_EDIT_SEARCH_TEXT) { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { entry ->
        val preselected = entry.arguments?.getStringArray(ARG_PRESELECTED_TAGS)?.toList().orEmpty()
        val editTitle = entry.arguments?.getString(ARG_EDIT_TITLE)
        val editSearchText = entry.arguments?.getString(ARG_EDIT_SEARCH_TEXT)
        CreateFilterRoute(preselected, editTitle, editSearchText, loadAvailableTags, isTitleTaken, onConfirm)
    }
}

@Composable
private fun CreateFilterRoute(
    preselected: List<String>,
    editTitle: String?,
    editSearchText: String?,
    loadAvailableTags: suspend () -> List<String>,
    isTitleTaken: (String) -> Boolean,
    onConfirm: (CustomFilterItem, originalTitle: String?) -> Unit,
) {
    val availableTags by produceState<List<String>?>(initialValue = null) { value = loadAvailableTags() }
    FilterDialog(
        availableTags = availableTags,
        preselectedTags = preselected,
        onConfirm = { onConfirm(it, editTitle) },
        initialTitle = editTitle.orEmpty(),
        initialSearchText = editSearchText.orEmpty(),
        originalTitle = editTitle,
        isTitleTaken = isTitleTaken,
    )
}

/** Opens [createFilterDestination] with [preselectedTags] checked. Navigates by id so the tags go in a Bundle, not the URI. */
fun NavController.navigateToCreateFilter(preselectedTags: List<String>) {
    val destination = requireNotNull(graph.findNode(CREATE_FILTER_ROUTE)) {
        "createFilter destination is not registered in the nav graph"
    }
    navigate(destination.id, bundleOf(ARG_PRESELECTED_TAGS to preselectedTags.toTypedArray()))
}

/** Opens [createFilterDestination] to edit [item]: its title, tags and search text are filled in. */
fun NavController.navigateToEditFilter(item: CustomFilterItem) {
    val destination = requireNotNull(graph.findNode(CREATE_FILTER_ROUTE)) {
        "createFilter destination is not registered in the nav graph"
    }
    navigate(
        destination.id,
        bundleOf(
            ARG_PRESELECTED_TAGS to item.criteria.tags.toTypedArray(),
            ARG_EDIT_TITLE to item.id,
            ARG_EDIT_SEARCH_TEXT to item.criteria.searchText,
        )
    )
}

private const val ARG_PRESELECTED_TAGS = "preselectedTags"
private const val ARG_EDIT_TITLE = "editTitle"
private const val ARG_EDIT_SEARCH_TEXT = "editSearchText"
const val CREATE_FILTER_ROUTE =
    "createFilter?$ARG_PRESELECTED_TAGS={$ARG_PRESELECTED_TAGS}&$ARG_EDIT_TITLE={$ARG_EDIT_TITLE}&$ARG_EDIT_SEARCH_TEXT={$ARG_EDIT_SEARCH_TEXT}"
