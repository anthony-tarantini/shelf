package io.tarantini.shelf.app

import arrow.fx.coroutines.ResourceScope
import kotlinx.serialization.json.Json

suspend fun ResourceScope.processResource(pb: ProcessBuilder): Process =
    install({ pb.start() }) { process, _ ->
        if (process.isAlive) {
            process.destroy()
        }
    }

val jsonParser = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
