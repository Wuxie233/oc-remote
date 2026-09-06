package dev.wuxie233.codecarry.ui.screens.codex

import dev.wuxie233.codecarry.data.codex.CodexServerRequest
import dev.wuxie233.codecarry.data.codex.CodexToolUserInputQuestion
import dev.wuxie233.codecarry.data.codex.requestKey
import dev.wuxie233.codecarry.ui.screens.chat.ChatResponseDockItem
import dev.wuxie233.codecarry.ui.screens.chat.ChatResponseDockKind

internal fun CodexToolUserInputQuestion.allowsCustomAnswer(): Boolean = isOther || options.isEmpty()

internal fun CodexToolUserInputQuestion.requiresExplicitSubmit(): Boolean =
    multiple || allowsCustomAnswer()

internal fun codexUserInputNeedsExplicitSubmit(
    questions: List<CodexToolUserInputQuestion>,
): Boolean = questions.size != 1 || questions.first().requiresExplicitSubmit()

internal fun codexUserInputAllowsInstantSubmit(
    questions: List<CodexToolUserInputQuestion>,
): Boolean = !codexUserInputNeedsExplicitSubmit(questions)

internal fun codexUserInputDraftComplete(
    questions: List<CodexToolUserInputQuestion>,
    answers: Map<String, List<String>>,
): Boolean = questions.isNotEmpty() && questions.all { question ->
    answers[question.id].orEmpty().any { it.isNotBlank() }
}

internal fun codexUserInputAnswerPayload(
    questions: List<CodexToolUserInputQuestion>,
    answers: Map<String, List<String>>,
): Map<String, List<String>> = questions.associate { question ->
    question.id to answers[question.id].orEmpty().map(String::trim).filter(String::isNotBlank)
}

internal fun codexInstantOptionAnswer(
    questions: List<CodexToolUserInputQuestion>,
    questionId: String,
    optionLabel: String,
): Map<String, List<String>>? {
    if (!codexUserInputAllowsInstantSubmit(questions)) return null
    val question = questions.singleOrNull() ?: return null
    if (question.id != questionId) return null
    if (question.options.none { it.label == optionLabel }) return null
    return mapOf(question.id to listOf(optionLabel))
}

internal fun codexInstantCustomAnswer(
    questions: List<CodexToolUserInputQuestion>,
    questionId: String,
    customText: String,
): Map<String, List<String>>? {
    if (!codexUserInputAllowsInstantSubmit(questions)) return null
    val question = questions.singleOrNull() ?: return null
    if (question.id != questionId || !question.allowsCustomAnswer()) return null
    val trimmed = customText.trim()
    if (trimmed.isBlank()) return null
    return mapOf(question.id to listOf(trimmed))
}

internal fun buildCodexResponseDockItems(
    pendingRequests: List<CodexServerRequest>,
): List<ChatResponseDockItem> = pendingRequests.map { request ->
    ChatResponseDockItem(
        kind = if (request.approval != null) {
            ChatResponseDockKind.Permission
        } else {
            ChatResponseDockKind.Question
        },
        ownershipId = request.id.requestKey(),
    )
}

internal fun ChatResponseDockItem.codexRequest(
    pendingRequests: List<CodexServerRequest>,
): CodexServerRequest? = pendingRequests.firstOrNull { request ->
    request.id.requestKey() == ownershipId &&
        when (kind) {
            ChatResponseDockKind.Permission -> request.approval != null
            ChatResponseDockKind.Question -> request.approval == null
            ChatResponseDockKind.Retry -> false
        }
}

internal fun codexRequestUnlockToken(
    requestKey: String,
    requestErrors: Map<String, String>,
): Int {
    val error = requestErrors[requestKey] ?: return 0
    val hashed = "$requestKey:$error".hashCode()
    return if (hashed == 0) 1 else hashed
}
