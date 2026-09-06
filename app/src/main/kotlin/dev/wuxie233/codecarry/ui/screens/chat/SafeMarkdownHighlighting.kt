package dev.wuxie233.codecarry.ui.screens.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.LocalMarkdownPadding
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.components.MarkdownComponent
import com.mikepenz.markdown.compose.elements.MarkdownCodeBackground
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.wuxie233.codecarry.R
import org.intellij.markdown.ast.ASTNode

val safeHighlightedCodeFence: MarkdownComponent = {
    SafeMarkdownHighlightedCodeFence(it.content, it.node)
}

val safeHighlightedCodeBlock: MarkdownComponent = {
    SafeMarkdownHighlightedCodeBlock(it.content, it.node)
}

@Composable
fun SafeMarkdownHighlightedCodeFence(
    content: String,
    node: ASTNode,
    highlights: Highlights.Builder = Highlights.Builder(),
) {
    MarkdownCodeFence(content, node) { code, language ->
        SafeMarkdownHighlightedCode(code, language, highlights)
    }
}

@Composable
fun SafeMarkdownHighlightedCodeBlock(
    content: String,
    node: ASTNode,
    highlights: Highlights.Builder = Highlights.Builder(),
) {
    MarkdownCodeBlock(content, node) { code, language ->
        SafeMarkdownHighlightedCode(code, language, highlights)
    }
}

@Composable
fun SafeMarkdownHighlightedCode(
    code: String,
    language: String?,
    highlights: Highlights.Builder = Highlights.Builder(),
    style: TextStyle = LocalMarkdownTypography.current.code,
) {
    val backgroundCodeColor = LocalMarkdownColors.current.codeBackground
    val codeTextColor = LocalMarkdownColors.current.codeText
    val codeBackgroundCornerSize = LocalMarkdownDimens.current.codeBackgroundCornerSize
    val codeBlockPadding = LocalMarkdownPadding.current.codeBlock
    val codeWordWrap = LocalCodeWordWrap.current
    val overflowTreatment = ChatOverflowPolicy.resolve(
        kind = ChatOverflowContentKind.CodeBlock,
        codeWordWrap = codeWordWrap,
    )
    val annotatedCode = remember(code, language, highlights, codeTextColor) {
        buildSafeHighlightedAnnotatedString(code, language, highlights, codeTextColor)
    }
    val clipboardManager = LocalClipboardManager.current
    val copyPayload = markdownCodeCopyPayload(code)

    MarkdownCodeBackground(
        color = backgroundCodeColor,
        shape = RoundedCornerShape(codeBackgroundCornerSize),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = if (overflowTreatment == ChatOverflowTreatment.Wrap) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth().chatCodeOverflow(codeWordWrap = codeWordWrap)
                }
            ) {
                SelectionContainer {
                    Text(
                        text = annotatedCode,
                        color = codeTextColor,
                        modifier = Modifier.padding(codeBlockPadding),
                        style = style,
                        softWrap = overflowTreatment == ChatOverflowTreatment.Wrap,
                    )
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                DisableSelection {
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(copyPayload)) },
                        modifier = Modifier.size(22.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.chat_copy),
                            modifier = Modifier.size(14.dp),
                            tint = codeTextColor.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clipboard payload for a rendered Markdown code block.
 * [innerCode] is the fence/indented body already extracted by the renderer
 * (no surrounding ``` / ~~~ markers).
 */
internal fun markdownCodeCopyPayload(innerCode: String): String = innerCode

internal fun extractMarkdownFenceInnerCode(markdown: String): String {
    val lines = markdown.lines()
    val startIndex = lines.indexOfFirst { markdownFenceStartRegex.matches(it) }
    if (startIndex < 0) return markdown
    val startMatch = markdownFenceStartRegex.matchEntire(lines[startIndex]) ?: return markdown
    val fenceMarker = startMatch.groupValues[1]
    val closesWith = fenceMarker.first().toString().repeat(fenceMarker.length)
    val codeLines = mutableListOf<String>()
    var lineIndex = startIndex + 1
    while (lineIndex < lines.size && lines[lineIndex].trim() != closesWith) {
        codeLines += lines[lineIndex]
        lineIndex++
    }
    return codeLines.joinToString("\n")
}

private val markdownFenceStartRegex = Regex("^\\s*(`{3,}|~{3,})\\s*([^\\s`]*)?.*$")

internal fun buildSafeHighlightedAnnotatedString(
    code: String,
    language: String?,
    highlightsBuilder: Highlights.Builder,
    codeTextColor: Color? = null,
): AnnotatedString {
    return runCatching {
        val syntaxLanguage = language?.let { SyntaxLanguage.getByName(it) }
        val codeHighlights = highlightsBuilder
            .code(code)
            .let { builder -> if (syntaxLanguage != null) builder.language(syntaxLanguage) else builder }
            .build()

        buildAnnotatedString {
            text(codeHighlights.getCode())

            codeHighlights.getHighlights()
                .filterIsInstance<ColorHighlight>()
                .forEach { highlight ->
                    addSafeStyle(
                        style = SpanStyle(
                            color = readableHighlightColor(
                                color = Color(highlight.rgb).copy(alpha = 1f),
                                codeTextColor = codeTextColor,
                            ),
                        ),
                        start = highlight.location.start,
                        end = highlight.location.end,
                        textLength = code.length,
                    )
                }

            codeHighlights.getHighlights()
                .filterIsInstance<BoldHighlight>()
                .forEach { highlight ->
                    addSafeStyle(
                        style = SpanStyle(fontWeight = FontWeight.Bold),
                        start = highlight.location.start,
                        end = highlight.location.end,
                        textLength = code.length,
                    )
                }
        }
    }.getOrDefault(AnnotatedString(code))
}

private fun readableHighlightColor(color: Color, codeTextColor: Color?): Color {
    if (codeTextColor == null) return color
    val darkCodeBackground = codeTextColor.luminance() > 0.5f
    return if (darkCodeBackground && color.luminance() < 0.24f) {
        codeTextColor.copy(alpha = 0.92f)
    } else {
        color
    }
}

private fun AnnotatedString.Builder.addSafeStyle(
    style: SpanStyle,
    start: Int,
    end: Int,
    textLength: Int,
) {
    if (start < 0 || end > textLength || start >= end) return
    addStyle(style = style, start = start, end = end)
}

private fun AnnotatedString.Builder.text(
    text: String,
    style: SpanStyle = SpanStyle(),
) = withStyle(style = style) {
    append(text)
}
