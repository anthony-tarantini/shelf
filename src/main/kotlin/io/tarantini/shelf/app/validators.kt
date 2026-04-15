@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.app

import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object IdValidator {
    @OptIn(ExperimentalUuidApi::class)
    context(_: RaiseContext)
    inline fun <T> create(
        id: String?,
        emptyErr: AppError,
        invalidErr: AppError,
        creator: (Uuid) -> T,
    ) {
        ensureNotNull(id) { emptyErr }
        ensure(id.isNotEmpty()) { emptyErr }
        val uuid = Uuid.parseOrNull(id) ?: raise(invalidErr)
        creator(uuid)
    }
}
