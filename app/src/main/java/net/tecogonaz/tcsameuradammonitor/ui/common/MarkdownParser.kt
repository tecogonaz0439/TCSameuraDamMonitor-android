// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.AnnotatedString.Builder
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

internal val URL_REGEX = Regex("https?://[\\w./?=&%#@!+\\-~]+")
internal const val URL_ANNOTATION_TAG = "URL"

/**
 * 簡易的なMarkdown形式のテキスト（太字 `**`、インラインコード <code>`</code>、リンク、リスト `-`、見出し `#` など）を
 * 解析して Jetpack Compose UI として順次描画するComposable関数です。
 *
 * 主に「利用規約」や「プライバシーポリシー」などの文書表示に使用されます。
 *
 * @param text レンダリング対象のMarkdown形式テキスト文字列。
 * @param onOpenUrl テキスト内のURLリンクがクリックされたときに呼び出されるコールバック。
 */
@Composable
fun MarkdownBody(
    text: String,
    onOpenUrl: (String) -> Unit
) {
    val urlColor = MaterialTheme.colorScheme.primary
    val lines = text.split("\n")
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trimEnd()

        when {
            line.startsWith("### ") -> {
                val content = line.removePrefix("### ")
                Text(
                    text = parseInlineMarkdown(content, urlColor),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            line.startsWith("## ") -> {
                val content = line.removePrefix("## ")
                Text(
                    text = parseInlineMarkdown(content, urlColor),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            line.startsWith("# ") -> {
                val content = line.removePrefix("# ")
                Text(
                    text = parseInlineMarkdown(content, urlColor),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            line.startsWith("- ") -> {
                Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
                    Text("• ", style = MaterialTheme.typography.bodyMedium)
                    ClickableMarkdownText(
                        text = parseInlineMarkdown(line.removePrefix("- "), urlColor, onOpenUrl),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            line.startsWith("|") -> {
                ClickableMarkdownText(
                    text = parseInlineMarkdown(line, urlColor, onOpenUrl),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }

            line.startsWith("---") -> {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            line.isEmpty() -> {
                Spacer(Modifier.height(4.dp))
            }

            else -> {
                ClickableMarkdownText(
                    text = parseInlineMarkdown(line, urlColor, onOpenUrl),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                )
            }
        }
        i++
    }
}

/**
 * リンクなどのクリックアクションを含む、修飾済みのMarkdownテキストを描画するComposable関数です。
 *
 * @param text 修飾された [AnnotatedString]。
 * @param style テキストスタイル。
 * @param modifier このコンポーネントに適用する [Modifier]。
 */
@Composable
fun ClickableMarkdownText(
    text: AnnotatedString,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    BasicText(
        text = text,
        style = style,
        modifier = modifier.semantics { role = Role.Button }
    )
}

fun buildMarkdownAnnotated(
    text: String,
    urlColor: Color,
    onOpenUrl: ((String) -> Unit)? = null
): AnnotatedString {
    return buildAnnotatedString {
        appendMarkdownWithUrlAnnotations(text, urlColor, onOpenUrl)
    }
}

private fun Builder.appendMarkdownWithUrlAnnotations(
    text: String,
    urlColor: Color,
    onOpenUrl: ((String) -> Unit)? = null
) {
    var cursor = 0
    URL_REGEX.findAll(text).forEach { match ->
        appendInlineMarkdown(text.substring(cursor, match.range.first))
        val url = match.value
        if (onOpenUrl != null) {
            withLink(LinkAnnotation.Clickable(URL_ANNOTATION_TAG) { onOpenUrl(url) }) {
                withStyle(
                    SpanStyle(
                        color = urlColor,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(url)
                }
            }
        } else {
            pushStringAnnotation(URL_ANNOTATION_TAG, url)
            withStyle(
                SpanStyle(
                    color = urlColor,
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(url)
            }
            pop()
        }
        cursor = match.range.last + 1
    }
    appendInlineMarkdown(text.substring(cursor))
}

private fun Builder.appendInlineMarkdown(text: String) {
    var remaining = text
    while (remaining.isNotEmpty()) {
        val boldStart = remaining.indexOf("**")
        val codeStart = remaining.indexOf("`")
        val nextStart = listOf(boldStart, codeStart).filter { it >= 0 }.minOrNull()

        if (nextStart == null) {
            append(remaining)
            return
        }

        append(remaining.substring(0, nextStart))
        if (nextStart == boldStart) {
            val boldContent = remaining.substring(boldStart + 2)
            val boldEnd = boldContent.indexOf("**")
            if (boldEnd >= 0) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(boldContent.substring(0, boldEnd))
                }
                remaining = boldContent.substring(boldEnd + 2)
            } else {
                append("**")
                remaining = boldContent
            }
        } else {
            val codeContent = remaining.substring(codeStart + 1)
            val codeEnd = codeContent.indexOf("`")
            if (codeEnd >= 0) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    append(codeContent.substring(0, codeEnd))
                }
                remaining = codeContent.substring(codeEnd + 1)
            } else {
                append("`")
                remaining = codeContent
            }
        }
    }
}

/**
 * 太字やインラインコード、リンクなどのインラインMarkdown記述を含む文字列を解析し、
 * Composeで描画可能な [AnnotatedString] に変換するユーティリティ関数です。
 *
 * @param text 解析対象の文字列。
 * @param urlColor リンク（URL）の描画色。
 * @param onOpenUrl リンクをクリックした際に呼び出されるコールバック。
 * @return 装飾済みの [AnnotatedString]。
 */
fun parseInlineMarkdown(
    text: String,
    urlColor: Color = Color.Unspecified,
    onOpenUrl: ((String) -> Unit)? = null
): AnnotatedString {
    return buildMarkdownAnnotated(text, urlColor, onOpenUrl)
}
