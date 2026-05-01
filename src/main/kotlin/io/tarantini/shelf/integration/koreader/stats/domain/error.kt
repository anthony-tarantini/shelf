package io.tarantini.shelf.integration.koreader.stats.domain

import io.tarantini.shelf.app.AppError

sealed interface KoreaderStatsError : AppError

data object EmptyKoreaderBookId : KoreaderStatsError

data object InvalidKoreaderBookId : KoreaderStatsError

data object InvalidStatsSqliteFile : KoreaderStatsError

data object KoreaderStatsBookNotFound : KoreaderStatsError

data object InvalidStatsDateRange : KoreaderStatsError
