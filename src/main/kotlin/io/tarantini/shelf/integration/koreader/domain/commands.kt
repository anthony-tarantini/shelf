package io.tarantini.shelf.integration.koreader.domain

data class KoreaderCreateUserCommand(val username: String, val authKey: String)

data class KoreaderProgressReadCommand(val documentId: String)

data class KoreaderProgressUpdateCommand(val payload: ProgressPayload)

fun toKoreaderCreateUserCommand(username: String, authKey: String): KoreaderCreateUserCommand? {
    val normalizedUsername = username.trim()
    val normalizedAuthKey = authKey.trim()
    if (normalizedUsername.isEmpty() || normalizedAuthKey.isEmpty()) return null
    return KoreaderCreateUserCommand(normalizedUsername, normalizedAuthKey)
}

fun toKoreaderProgressReadCommand(documentId: String?): KoreaderProgressReadCommand? {
    val normalized = documentId?.trim() ?: return null
    if (normalized.isEmpty()) return null
    return KoreaderProgressReadCommand(normalized)
}

fun ProgressPayload.toCommand(): KoreaderProgressUpdateCommand? =
    if (document.isNullOrBlank()) null else KoreaderProgressUpdateCommand(payload = this)
