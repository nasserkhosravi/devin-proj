package com.khosravi.devin.present.present.stacktrace

import com.google.gson.JsonParser
import com.khosravi.devin.read.DevinLogFlagsApi

/**
 * A stack trace parsed from the text of `Log.getStackTraceString` (what the write side stores under
 * `DevinLogFlagsApi.KEY_LOG_THROWABLE`). Built by [parseStackTrace].
 */
class StackTrace(
    /** The original text, trimmed; what "Copy stack trace" copies. */
    val raw: String,
    /** The thrown exception first, then its `Caused by:` and `Suppressed:` sections in printed order. */
    val sections: List<StackTraceSection>,
) {
    /** The deepest `Caused by:` of the thrown exception, or null when it has no cause. */
    val rootCause: StackTraceSection?
        get() = sections.lastOrNull { it.kind == StackTraceSection.Kind.CAUSED_BY && it.depth == 0 }

    /**
     * The section whose trailing frames a `... N more` row of [section] refers to: the trace it is the cause of,
     * or for a suppressed exception the trace it was suppressed in.
     */
    fun enclosingOf(section: StackTraceSection): StackTraceSection? {
        val index = sections.indexOf(section)
        val depth = if (section.kind == StackTraceSection.Kind.SUPPRESSED) section.depth - 1 else section.depth
        return (index - 1 downTo 0).map { sections[it] }.firstOrNull { it.depth == depth }
    }
}

class StackTraceSection(
    val kind: Kind,
    /** Leading tabs of the section's first line; nested `Suppressed:` sections (and their causes) are indented. */
    val depth: Int,
    /** Exception class, e.g. `java.lang.IllegalStateException`. */
    val type: String,
    /** Exception message, possibly multi-line; null when the exception has none. */
    val message: String?,
    val lines: List<StackTraceLine>,
) {
    enum class Kind { THROWN, CAUSED_BY, SUPPRESSED }

    val headline: String get() = if (message == null) type else "$type: $message"

    val frames: List<StackTraceLine.Frame> get() = lines.filterIsInstance<StackTraceLine.Frame>()
}

sealed interface StackTraceLine {
    /** `at com.example.Foo.bar(Foo.kt:42)`; [text] is the line without the leading `at `. */
    class Frame(val text: String) : StackTraceLine {
        /** Fully qualified class name (including the method), e.g. `com.example.Foo.bar`. */
        val qualifiedMethod: String = text.substringBefore('(')
    }

    /** `... N more`: the last [count] frames are the same as the enclosing trace's. */
    class More(val count: Int) : StackTraceLine
}

private const val FRAME_PREFIX = "at "
private const val CAUSED_BY_PREFIX = "Caused by: "
private const val SUPPRESSED_PREFIX = "Suppressed: "
private val MORE_LINE = Regex("""\.\.\. (\d+) more""")

/**
 * Parses [text] into a [StackTrace], or returns null when it doesn't look like one (no exception headline,
 * no frames, or a line that is neither a frame, a `... N more` nor a section header after frames started),
 * so callers can fall back to showing the plain text.
 */
fun parseStackTrace(text: String): StackTrace? {
    val raw = text.trim()
    if (raw.isEmpty()) return null
    val sections = mutableListOf<StackTraceSection>()

    var kind = StackTraceSection.Kind.THROWN
    var depth = 0
    var headline = StringBuilder()
    var lines = mutableListOf<StackTraceLine>()

    fun closeSection(): Boolean {
        val head = headline.toString()
        val type = head.substringBefore(": ").trim()
        if (type.isEmpty() || type.contains(' ')) return false
        val message = if (head.contains(": ")) head.substringAfter(": ") else null
        sections += StackTraceSection(kind, depth, type, message, lines)
        return true
    }

    for ((index, line) in raw.lines().withIndex()) {
        val trimmed = line.trim()
        val tabs = line.takeWhile { it == '\t' }.length
        when {
            index == 0 -> headline.append(trimmed)
            trimmed.startsWith(FRAME_PREFIX) -> lines += StackTraceLine.Frame(trimmed.removePrefix(FRAME_PREFIX))
            MORE_LINE.matches(trimmed) -> lines += StackTraceLine.More(MORE_LINE.matchEntire(trimmed)!!.groupValues[1].toInt())
            trimmed.startsWith(CAUSED_BY_PREFIX) || trimmed.startsWith(SUPPRESSED_PREFIX) -> {
                if (!closeSection()) return null
                val isCause = trimmed.startsWith(CAUSED_BY_PREFIX)
                kind = if (isCause) StackTraceSection.Kind.CAUSED_BY else StackTraceSection.Kind.SUPPRESSED
                depth = tabs
                headline = StringBuilder(trimmed.removePrefix(if (isCause) CAUSED_BY_PREFIX else SUPPRESSED_PREFIX))
                lines = mutableListOf()
            }
            // A multi-line exception message continues the headline until the first frame.
            lines.isEmpty() -> headline.append('\n').append(line)
            else -> return null
        }
    }
    if (!closeSection()) return null
    if (sections.none { it.frames.isNotEmpty() }) return null
    return StackTrace(raw, sections)
}

/** Packages of the platform, the JDK and Kotlin; their frames are dimmed and folded. */
private val FRAMEWORK_PREFIXES = listOf(
    "android.", "androidx.", "java.", "javax.", "kotlin.", "kotlinx.", "dalvik.", "sun.", "libcore.",
    "com.android.", "jdk.internal.",
)

fun StackTraceLine.Frame.isFramework(): Boolean = FRAMEWORK_PREFIXES.any { qualifiedMethod.startsWith(it) }

/**
 * Decides which frames are the client app's own code. Frames under [appPackage] when any frame of the trace is;
 * otherwise (no package known, or the code lives under a different package than the application id) every
 * non-framework frame.
 */
class AppFrameMatcher(appPackage: String?, trace: StackTrace) {
    private val prefix = appPackage?.let { "$it." }?.takeIf { p ->
        trace.sections.any { section -> section.frames.any { it.qualifiedMethod.startsWith(p) } }
    }

    fun isApp(frame: StackTraceLine.Frame): Boolean =
        if (prefix != null) frame.qualifiedMethod.startsWith(prefix) else !frame.isFramework()
}

/** A log's meta split in two: the parsed [stackTrace], and [meta], what is left (null when nothing is). */
class MetaWithStackTrace(val meta: String?, val stackTrace: StackTrace)

/**
 * Takes the stack trace (`DevinLogFlagsApi.KEY_LOG_THROWABLE`) out of a log's [meta] JSON. Returns null when there is
 * none, or it doesn't parse, so the meta is shown as-is.
 */
fun extractStackTrace(meta: String?): MetaWithStackTrace? {
    if (meta == null) return null
    val json = runCatching { JsonParser.parseString(meta) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
        ?: return null
    val throwable = json.get(DevinLogFlagsApi.KEY_LOG_THROWABLE)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: return null
    val trace = parseStackTrace(throwable) ?: return null
    json.remove(DevinLogFlagsApi.KEY_LOG_THROWABLE)
    return MetaWithStackTrace(json.takeIf { it.size() > 0 }?.toString(), trace)
}
