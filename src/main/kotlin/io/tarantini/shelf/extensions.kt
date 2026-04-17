@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf

import arrow.core.raise.catch
import io.tarantini.shelf.app.AppError
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.datetime.LocalDate

typealias RaiseContext = arrow.core.raise.Raise<AppError>

fun String.toYearOrNull() = catch({ LocalDate.parse(this).year }) { null }
