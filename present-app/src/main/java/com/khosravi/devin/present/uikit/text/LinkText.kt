package com.khosravi.devin.present.uikit.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpOffset
import com.khosravi.devin.present.setClipboardSafe
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.khosravi.devin.present.R

private const val LINK_TAG = "link"

/** `scheme://...` (custom schemes like `myapp://` count) or `www....`, up to the next whitespace or quote/bracket. */
private val LINK_REGEX = Regex("""(?:[A-Za-z][A-Za-z0-9+.\-]*://|www\.)[^\s<>"'`]+""")

/** Punctuation that usually ends a sentence rather than a link. */
private const val TRAILING_PUNCTUATION = ".,;:!?)]}"

/** Links found in [text] as (range, target uri), without trailing punctuation. */
internal fun findLinks(text: String): List<Pair<IntRange, String>> =
    LINK_REGEX.findAll(text).mapNotNull { match ->
        val value = match.value.trimEnd { it in TRAILING_PUNCTUATION }
        // A bare scheme ("http://") or "www." is not a link.
        if (value.length <= match.value.indexOfFirst { it == '/' || it == '.' } + 2) return@mapNotNull null
        val range = match.range.first until match.range.first + value.length
        range to if (value.startsWith("www.")) "https://$value" else value
    }.toList()

/** [text] with every link underlined in [linkColor] and tagged for [LinkText] to open. */
internal fun linkifiedString(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    findLinks(text).forEach { (range, uri) ->
        addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), range.first, range.last + 1)
        addStringAnnotation(LINK_TAG, uri, range.first, range.last + 1)
    }
}

/** The uri of the link covering [offset], or null. The end of a link's range is exclusive. */
internal fun AnnotatedString.linkAt(offset: Int): String? =
    getStringAnnotations(LINK_TAG, 0, length).firstOrNull { offset >= it.start && offset < it.end }?.item

/** From Android 13 the system confirms a clipboard copy itself, so the app's own confirmation would be a second one. */
internal fun needsCopyConfirmation(sdkInt: Int): Boolean = sdkInt < Build.VERSION_CODES.TIRAMISU

/** Opens [uri] with whatever handles it; custom-scheme links may have no handler. */
internal fun openLink(context: Context, uri: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.link_no_handler, Toast.LENGTH_SHORT).show()
    }
}

private data class LinkMenu(val uri: String, val anchor: DpOffset)

/** Text whose links are highlighted and, with [clickable], show an Open/Copy menu on tap. */
@Composable
fun LinkText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    clickable: Boolean = true,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) { linkifiedString(text, linkColor) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var menu by remember { mutableStateOf<LinkMenu?>(null) }
    Box(modifier = modifier) {
        ClickableText(
            text = annotated,
            style = style.copy(color = if (style.color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else style.color),
            onTextLayout = { textLayout = it },
            onClick = { offset ->
                val uri = annotated.linkAt(offset)
                val layout = textLayout
                if (clickable && uri != null && layout != null) {
                    // The menu opens under the tapped character.
                    val box = layout.getBoundingBox(offset)
                    menu = LinkMenu(uri, with(density) { DpOffset(box.left.toDp(), box.bottom.toDp() - layout.size.height.toDp()) })
                }
            },
        )
        val current = menu
        DropdownMenu(
            expanded = current != null,
            onDismissRequest = { menu = null },
            offset = current?.anchor ?: DpOffset.Zero,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.link_menu_open)) },
                onClick = {
                    menu = null
                    current?.let { openLink(context, it.uri) }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.link_menu_copy)) },
                onClick = {
                    menu = null
                    if (current != null && context.setClipboardSafe(current.uri) && needsCopyConfirmation(Build.VERSION.SDK_INT)) {
                        Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }
}
