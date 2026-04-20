package io.tarantini.shelf.app

internal const val LOG_JSON_ENABLED_PROPERTY: String = "LOG_JSON_ENABLED"

fun configureLogging(observability: Env.Observability) {
    System.setProperty(LOG_JSON_ENABLED_PROPERTY, observability.jsonLogsEnabled.toString())
}
