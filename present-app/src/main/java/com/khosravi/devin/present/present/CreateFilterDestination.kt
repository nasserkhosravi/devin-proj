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
 * The "create custom filter" dialog ([FilterDialog]) as a NavHost dialog, so it and its fields survive rotation.
 * Open it with [navigateToCreateFilter]; the preselected tags travel in the destination's arguments Bundle.
 */
fun NavGraphBuilder.createFilterDestination(
    loadAvailableTags: suspend () -> List<String>,
    onConfirm: (CustomFilterItem) -> Unit,
) {
    dialog(
        CREATE_FILTER_ROUTE,
        arguments = listOf(navArgument(ARG_PRESELECTED_TAGS) { type = NavType.StringArrayType; nullable = true; defaultValue = null }),
    ) { entry ->
        val preselected = entry.arguments?.getStringArray(ARG_PRESELECTED_TAGS)?.toList().orEmpty()
        CreateFilterRoute(preselected, loadAvailableTags, onConfirm)
    }
}

@Composable
private fun CreateFilterRoute(
    preselected: List<String>,
    loadAvailableTags: suspend () -> List<String>,
    onConfirm: (CustomFilterItem) -> Unit,
) {
    val availableTags by produceState<List<String>?>(initialValue = null) { value = loadAvailableTags() }
    FilterDialog(availableTags = availableTags, preselectedTags = preselected, onConfirm = onConfirm)
}

/** Opens [createFilterDestination] with [preselectedTags] checked. Navigates by id so the tags go in a Bundle, not the URI. */
fun NavController.navigateToCreateFilter(preselectedTags: List<String>) {
    val destination = requireNotNull(graph.findNode(CREATE_FILTER_ROUTE)) {
        "createFilter destination is not registered in the nav graph"
    }
    navigate(destination.id, bundleOf(ARG_PRESELECTED_TAGS to preselectedTags.toTypedArray()))
}

private const val ARG_PRESELECTED_TAGS = "preselectedTags"
const val CREATE_FILTER_ROUTE = "createFilter?$ARG_PRESELECTED_TAGS={$ARG_PRESELECTED_TAGS}"
