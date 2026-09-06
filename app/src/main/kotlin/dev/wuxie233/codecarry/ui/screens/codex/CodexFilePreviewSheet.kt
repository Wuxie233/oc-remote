package dev.wuxie233.codecarry.ui.screens.codex

import androidx.compose.runtime.Composable
import dev.wuxie233.codecarry.ui.screens.chat.ChatFilePreviewSheet
import dev.wuxie233.codecarry.ui.screens.chat.ChatFilePreviewState

data class CodexFilePreviewState(
    val path: String,
    val isLoading: Boolean = true,
    val contents: String? = null,
    val error: String? = null,
)

@Composable
internal fun CodexFilePreviewSheet(
    preview: CodexFilePreviewState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    ChatFilePreviewSheet(
        preview = ChatFilePreviewState(
            path = preview.path,
            isLoading = preview.isLoading,
            contents = preview.contents,
            error = preview.error,
        ),
        onDismiss = onDismiss,
        onRetry = onRetry,
    )
}
