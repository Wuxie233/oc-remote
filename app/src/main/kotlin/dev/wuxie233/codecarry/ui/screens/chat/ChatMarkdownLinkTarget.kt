package dev.wuxie233.codecarry.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import java.net.URI

internal val LocalChatMarkdownWorkspaceCwd = compositionLocalOf<String?> { null }

internal sealed interface ChatMarkdownLinkTarget {
    data class Web(val url: String) : ChatMarkdownLinkTarget
    data class WorkspaceFile(val path: String) : ChatMarkdownLinkTarget
}

internal const val ChatMarkdownUrlAnnotationTag = "MARKDOWN_URL"
internal const val ChatMarkdownPathAnnotationTag = "CODECARRY_WORKSPACE_PATH"

private val AbsoluteUnixFilePath = Regex("""^/(?:[^/\s]+/)*[^/\s]+$""")
private val RelativeFileNameWithExtension = Regex("""^[A-Za-z0-9._-]+\.[A-Za-z][A-Za-z0-9._-]*$""")
private val BacktickAbsolutePath = Regex("""`(/[^`\s]+)`""")
private val AbsolutePathToken = Regex("""/[A-Za-z0-9._-][A-Za-z0-9._/-]*""")
private val RelativeFileToken = Regex("""[A-Za-z0-9._-]+\.[A-Za-z][A-Za-z0-9._-]*""")

internal fun classifyChatMarkdownLink(raw: String, cwd: String? = null): ChatMarkdownLinkTarget? {
    val href = unwrapMarkdownHref(raw) ?: return null
    classifyHttpUrl(href)?.let { return it }
    classifyFileUrl(href, cwd)?.let { return it }
    classifyAbsoluteUnixPath(href)?.let { return it }
    return classifyRelativeFileName(href, cwd)
}

internal fun annotateBareWorkspacePaths(
    text: androidx.compose.ui.text.AnnotatedString,
    cwd: String?,
    linkStyle: androidx.compose.ui.text.SpanStyle? = null,
): androidx.compose.ui.text.AnnotatedString {
    if (text.isEmpty()) return text
    val existing = coveredRanges(text)
    val matches = mutableListOf<Pair<IntRange, ChatMarkdownLinkTarget.WorkspaceFile>>()
    fun consider(candidate: String, range: IntRange) {
        val target = classifyChatMarkdownLink(candidate, cwd) as? ChatMarkdownLinkTarget.WorkspaceFile ?: return
        if (existing.any { it.overlaps(range) }) return
        if (matches.any { it.first.overlaps(range) }) return
        matches += range to target
    }
    for (match in BacktickAbsolutePath.findAll(text.text)) {
        val group = match.groups[1] ?: continue
        consider(group.value, group.range)
    }
    for (match in AbsolutePathToken.findAll(text.text)) {
        if (!isBareTokenBoundary(text.text, match.range)) continue
        consider(match.value, match.range)
    }
    for (match in RelativeFileToken.findAll(text.text)) {
        if (!isBareTokenBoundary(text.text, match.range)) continue
        consider(match.value, match.range)
    }
    if (matches.isEmpty()) return text
    return androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        matches.forEach { (range, target) ->
            addStringAnnotation(
                tag = ChatMarkdownPathAnnotationTag,
                annotation = target.path,
                start = range.first,
                end = range.last + 1,
            )
            addStringAnnotation(
                tag = ChatMarkdownUrlAnnotationTag,
                annotation = target.path,
                start = range.first,
                end = range.last + 1,
            )
            addStyle(
                style = linkStyle ?: androidx.compose.ui.text.SpanStyle(),
                start = range.first,
                end = range.last + 1,
            )
        }
    }
}

internal fun resolveChatMarkdownAnnotation(
    tag: String,
    value: String,
    cwd: String?,
): ChatMarkdownLinkTarget? = when (tag) {
    ChatMarkdownUrlAnnotationTag, ChatMarkdownPathAnnotationTag -> classifyChatMarkdownLink(value, cwd)
    else -> classifyChatMarkdownLink(value, cwd)
}

internal fun dispatchChatMarkdownLink(
    context: android.content.Context,
    raw: String,
    cwd: String?,
    onWorkspaceFile: (String) -> Unit,
) {
    when (val target = classifyChatMarkdownLink(raw, cwd)) {
        is ChatMarkdownLinkTarget.Web -> openMessageLink(context, target.url)
        is ChatMarkdownLinkTarget.WorkspaceFile -> onWorkspaceFile(target.path)
        null -> Unit
    }
}

@Composable
internal fun rememberChatMarkdownUriHandler(
    cwd: String?,
    onWorkspaceFile: (String) -> Unit,
): UriHandler {
    val context = LocalContext.current
    val latestCwd = rememberUpdatedState(cwd)
    val latestOpen = rememberUpdatedState(onWorkspaceFile)
    return remember(context) {
        object : UriHandler {
            override fun openUri(uri: String) {
                dispatchChatMarkdownLink(
                    context = context,
                    raw = uri,
                    cwd = latestCwd.value,
                    onWorkspaceFile = latestOpen.value,
                )
            }
        }
    }
}

@Composable
internal fun ChatMarkdownLinkEnvironment(
    cwd: String?,
    onOpenWorkspaceFile: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val handler = rememberChatMarkdownUriHandler(cwd, onOpenWorkspaceFile)
    CompositionLocalProvider(
        LocalUriHandler provides handler,
        LocalChatMarkdownWorkspaceCwd provides cwd,
        content = content,
    )
}

private fun unwrapMarkdownHref(raw: String): String? {
    var value = raw.trim()
    if (value.isEmpty()) return null
    if (value.startsWith("<") && value.endsWith(">") && value.length >= 2) {
        value = value.substring(1, value.length - 1).trim()
    }
    if ((value.startsWith("`") && value.endsWith("`") && value.length >= 2) ||
        (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) ||
        (value.startsWith("'") && value.endsWith("'") && value.length >= 2)
    ) {
        value = value.substring(1, value.length - 1).trim()
    }
    return value.takeIf { it.isNotEmpty() }
}

private fun classifyHttpUrl(href: String): ChatMarkdownLinkTarget.Web? {
    val uri = runCatching { URI(href) }.getOrNull() ?: return null
    val scheme = uri.scheme ?: return null
    if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
        return null
    }
    return ChatMarkdownLinkTarget.Web(href)
}

private fun classifyFileUrl(href: String, cwd: String?): ChatMarkdownLinkTarget.WorkspaceFile? {
    val uri = runCatching { URI(href) }.getOrNull() ?: return null
    if (!uri.scheme.equals("file", ignoreCase = true)) return null
    val path = uri.path?.takeIf { it.isNotBlank() } ?: return null
    return classifyAbsoluteUnixPath(path) ?: classifyRelativeFileName(path.trimStart('/'), cwd)
}

private fun classifyAbsoluteUnixPath(href: String): ChatMarkdownLinkTarget.WorkspaceFile? {
    if (!AbsoluteUnixFilePath.matches(href)) return null
    if (href.contains("://")) return null
    val name = href.substringAfterLast('/')
    if (name.isEmpty() || name == "." || name == "..") return null
    return ChatMarkdownLinkTarget.WorkspaceFile(href)
}

private fun classifyRelativeFileName(href: String, cwd: String?): ChatMarkdownLinkTarget.WorkspaceFile? {
    if (href.startsWith("/") || href.contains("://") || href.contains('\\')) return null
    if (!RelativeFileNameWithExtension.matches(href)) return null
    val directory = cwd?.trim()?.trimEnd('/')?.takeIf { it.startsWith('/') } ?: return null
    return ChatMarkdownLinkTarget.WorkspaceFile("$directory/$href")
}

private fun coveredRanges(text: androidx.compose.ui.text.AnnotatedString): List<IntRange> {
    val url = text.getStringAnnotations(ChatMarkdownUrlAnnotationTag, 0, text.length).map { it.start until it.end }
    val path = text.getStringAnnotations(ChatMarkdownPathAnnotationTag, 0, text.length).map { it.start until it.end }
    return url + path
}

private fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last

private fun isBareTokenBoundary(text: String, range: IntRange): Boolean {
    val before = range.first.takeIf { it > 0 }?.let { text[it - 1] }
    val after = range.last.plus(1).takeIf { it < text.length }?.let { text[it] }
    return !isPathTokenChar(before) && !isPathTokenChar(after)
}

private fun isPathTokenChar(char: Char?): Boolean =
    char != null && (char.isLetterOrDigit() || char == '_' || char == '.' || char == '/' || char == '`')
