package io.tarantini.shelf.app

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import org.slf4j.Logger

class LogbackConfigTest :
    StringSpec({
        "logback selects JSON appender when LOG_JSON_ENABLED=true" {
            withJsonLoggingProperty("true") {
                val context = loadLogbackContext()
                appenderNames(context) shouldContain "STDOUT_JSON"
                appenderNames(context) shouldNotContain "STDOUT_LINE"
            }
        }

        "logback selects line appender when LOG_JSON_ENABLED=false" {
            withJsonLoggingProperty("false") {
                val context = loadLogbackContext()
                appenderNames(context) shouldContain "STDOUT_LINE"
                appenderNames(context) shouldNotContain "STDOUT_JSON"
            }
        }
    })

private fun withJsonLoggingProperty(value: String, block: () -> Unit) {
    val previous = System.getProperty(LOG_JSON_ENABLED_PROPERTY)
    try {
        System.setProperty(LOG_JSON_ENABLED_PROPERTY, value)
        block()
    } finally {
        if (previous == null) {
            System.clearProperty(LOG_JSON_ENABLED_PROPERTY)
        } else {
            System.setProperty(LOG_JSON_ENABLED_PROPERTY, previous)
        }
    }
}

private fun loadLogbackContext(): LoggerContext =
    LoggerContext().also { context ->
        val configurator = JoranConfigurator().apply { this.context = context }
        context.reset()
        val config =
            requireNotNull(LogbackConfigTest::class.java.classLoader.getResource("logback.xml"))
        configurator.doConfigure(config)
    }

private fun appenderNames(context: LoggerContext): List<String> =
    context
        .getLogger(Logger.ROOT_LOGGER_NAME)
        .iteratorForAppenders()
        .asSequence()
        .map { it.name }
        .toList()
