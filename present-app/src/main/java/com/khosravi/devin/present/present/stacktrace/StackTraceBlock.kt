package com.khosravi.devin.present.present.stacktrace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.uikit.text.CodeBlock
import com.khosravi.devin.present.uikit.text.chunkForDisplay
import com.khosravi.devin.present.uikit.text.codeTextStyle
import com.khosravi.devin.present.uikit.theme.DevinTheme
import com.khosravi.devin.present.uikit.theme.spacing

/**
 * The stack trace of a log, parsed: root cause up front, then each section's headline and frames.
 * Colours follow an IDE: the exception type and `Caused by:` in the error colour, app frames (see [AppFrameMatcher])
 * at full contrast with their file:line in the primary colour, library frames muted, framework frames faded.
 * Runs of framework frames and `... N more` fold into a row that expands on tap. Meant to sit inside the detail screen's vertical scroll and SelectionContainer.
 */
@Composable
fun StackTraceBlock(trace: StackTrace, appPackage: String?, onCopy: () -> Unit) {
    val matcher = remember(trace, appPackage) { AppFrameMatcher(appPackage, trace) }
    DisableSelection {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = MaterialTheme.spacing.small)
        ) {
            Text(
                text = stringResource(R.string.field_stack_trace),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopy) {
                Icon(
                    painterResource(R.drawable.baseline_content_copy_24),
                    contentDescription = stringResource(R.string.stack_trace_copy)
                )
            }
        }
    }
    trace.rootCause?.let { RootCause(it, matcher) }
    CodeBlock(modifier = Modifier.padding(top = MaterialTheme.spacing.small)) {
        Headline(trace.sections.first())
        // One horizontal scroll for all frames, so long frames don't wrap and stay aligned while scrolling.
        Column(
            modifier = Modifier
                .padding(top = MaterialTheme.spacing.small)
                .horizontalScroll(rememberScrollState())
        ) {
            trace.sections.forEachIndexed { index, section ->
                if (index > 0) SectionHeader(section)
                val enclosing = remember(trace, section) { trace.enclosingOf(section) }
                SectionLines(section, enclosing, matcher)
            }
        }
    }
}

@Composable
private fun RootCause(section: StackTraceSection, matcher: AppFrameMatcher) {
    val firstAppFrame = remember(section, matcher) { section.frames.firstOrNull(matcher::isApp) }
    Column(
        modifier = Modifier
            .padding(top = MaterialTheme.spacing.small)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp)
    ) {
        DisableSelection {
            Text(
                text = stringResource(R.string.stack_trace_root_cause),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
        Headline(section, typeColor = MaterialTheme.colorScheme.onErrorContainer, messageColor = MaterialTheme.colorScheme.onErrorContainer)
        firstAppFrame?.let {
            Text(
                text = "at ${it.text}",
                style = codeTextStyle,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = MaterialTheme.spacing.xs)
            )
        }
    }
}

/** The exception type (bold, [typeColor]) over its message; the message is chunked because it can be very long. */
@Composable
private fun Headline(
    section: StackTraceSection,
    typeColor: Color = MaterialTheme.colorScheme.error,
    messageColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(text = section.type, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = typeColor)
    val message = section.message ?: return
    val chunks = remember(message) { chunkForDisplay(message) }
    chunks.forEachIndexed { index, chunk ->
        val display = if (index < chunks.lastIndex) chunk.removeSuffix("\n") else chunk
        Text(text = display, style = MaterialTheme.typography.bodyMedium, color = messageColor)
    }
}

@Composable
private fun SectionHeader(section: StackTraceSection) {
    val label = stringResource(
        if (section.kind == StackTraceSection.Kind.SUPPRESSED) R.string.stack_trace_suppressed else R.string.stack_trace_caused_by
    )
    val error = MaterialTheme.colorScheme.error
    val onSurface = MaterialTheme.colorScheme.onSurface
    val text = remember(label, section, error, onSurface) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = error, fontWeight = FontWeight.Bold)) { append("$label: ") }
            withStyle(SpanStyle(color = onSurface, fontWeight = FontWeight.Bold)) { append(section.type) }
            section.message?.let { withStyle(SpanStyle(color = onSurface)) { append(": $it") } }
        }
    }
    Text(
        text = text,
        style = codeTextStyle,
        softWrap = false,
        modifier = Modifier.padding(top = MaterialTheme.spacing.small, start = indent(section.depth))
    )
}

/** What a section shows, after folding framework runs. */
private sealed interface FoldedLine {
    class Frame(val frame: StackTraceLine.Frame, val isApp: Boolean, val isFramework: Boolean) : FoldedLine
    class FrameworkRun(val frames: List<StackTraceLine.Frame>) : FoldedLine
    class More(val count: Int) : FoldedLine
}

private fun foldLines(section: StackTraceSection, matcher: AppFrameMatcher): List<FoldedLine> {
    val rows = mutableListOf<FoldedLine>()
    val run = mutableListOf<StackTraceLine.Frame>()
    fun flushRun() {
        if (run.size >= MIN_FOLDED_FRAMES) {
            rows += FoldedLine.FrameworkRun(run.toList())
        } else {
            run.forEach { rows += FoldedLine.Frame(it, isApp = false, isFramework = true) }
        }
        run.clear()
    }
    for (line in section.lines) {
        when (line) {
            is StackTraceLine.Frame -> {
                val isApp = matcher.isApp(line)
                if (!isApp && line.isFramework()) {
                    run += line
                } else {
                    flushRun()
                    rows += FoldedLine.Frame(line, isApp = isApp, isFramework = false)
                }
            }
            is StackTraceLine.More -> {
                flushRun()
                rows += FoldedLine.More(line.count)
            }
        }
    }
    flushRun()
    return rows
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SectionLines(section: StackTraceSection, enclosing: StackTraceSection?, matcher: AppFrameMatcher) {
    val rows = remember(section, matcher) { foldLines(section, matcher) }
    val start = indent(section.depth + 1)
    rows.forEach { row ->
        when (row) {
            is FoldedLine.Frame -> FrameText(row.frame, row.isApp, row.isFramework, start)
            is FoldedLine.FrameworkRun -> Foldable(
                label = pluralStringResource(R.plurals.stack_trace_framework_frames, row.frames.size, row.frames.size),
                start = start,
            ) {
                row.frames.forEach { FrameText(it, isApp = false, isFramework = true, start = start) }
            }
            is FoldedLine.More -> {
                // The elided frames are the enclosing trace's last [count] frames.
                val elided = enclosing?.frames?.takeLast(row.count).orEmpty()
                Foldable(
                    label = pluralStringResource(R.plurals.stack_trace_more_frames, row.count, row.count),
                    start = start,
                    expandable = elided.isNotEmpty(),
                ) {
                    elided.forEach { FrameText(it, matcher.isApp(it), it.isFramework(), start) }
                }
            }
        }
    }
}

@Composable
private fun FrameText(frame: StackTraceLine.Frame, isApp: Boolean, isFramework: Boolean, start: Dp) {
    val colors = MaterialTheme.colorScheme
    val text = remember(frame, isApp, isFramework, colors) {
        val faded = colors.onSurfaceVariant.copy(alpha = FRAMEWORK_ALPHA)
        val (methodStyle, locationStyle) = when {
            isApp -> SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Bold) to
                SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)
            isFramework -> SpanStyle(color = faded) to SpanStyle(color = faded)
            else -> SpanStyle(color = colors.onSurfaceVariant) to SpanStyle(color = colors.onSurfaceVariant)
        }
        val location = frame.text.substring(frame.qualifiedMethod.length)
        buildAnnotatedString {
            withStyle(SpanStyle(color = faded)) { append("at ") }
            withStyle(methodStyle) { append(frame.qualifiedMethod) }
            withStyle(locationStyle) { append(location) }
        }
    }
    Text(
        text = text,
        style = codeTextStyle,
        softWrap = false,
        modifier = Modifier.padding(start = start)
    )
}

@Composable
private fun Foldable(
    label: String,
    start: Dp,
    expandable: Boolean = true,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    DisableSelection {
        Text(
            text = if (!expandable) "… $label" else if (expanded) "▾ $label" else "▸ $label",
            style = codeTextStyle,
            color = MaterialTheme.colorScheme.primary,
            softWrap = false,
            modifier = Modifier
                .padding(start = start)
                .clip(RoundedCornerShape(4.dp))
                .then(if (expandable) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(vertical = 2.dp, horizontal = MaterialTheme.spacing.xs)
        )
    }
    if (expanded) content()
}

@Composable
private fun indent(depth: Int) = MaterialTheme.spacing.medium * depth

/** Fewer framework frames in a row than this are shown (dimmed) instead of folded. */
private const val MIN_FOLDED_FRAMES = 3

private const val FRAMEWORK_ALPHA = 0.55f

@Preview(widthDp = 400)
@Composable
private fun StackTraceBlockPreview() {
    val trace = parseStackTrace(
        """
        java.lang.IllegalStateException: Could not load profile
        	at com.example.app.ProfileViewModel.load(ProfileViewModel.kt:88)
        	at com.example.app.ProfileFragment.onViewCreated(ProfileFragment.kt:42)
        	at androidx.fragment.app.Fragment.performViewCreated(Fragment.java:3128)
        	at androidx.fragment.app.FragmentStateManager.createView(FragmentStateManager.java:552)
        	at android.os.Handler.dispatchMessage(Handler.java:106)
        	at android.os.Looper.loop(Looper.java:223)
        Caused by: java.lang.NullPointerException: user was null
        	at com.example.app.UserRepository.current(UserRepository.kt:17)
        	... 6 more
        """.trimIndent()
    )!!
    DevinTheme {
        Column { StackTraceBlock(trace, appPackage = "com.example.app", onCopy = {}) }
    }
}
