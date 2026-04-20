package io.tarantini.shelf.processing.epub

import io.tarantini.shelf.app.AppError

sealed interface EpubError : AppError

object InvalidContainerXML : EpubError

object MissingContainerXML : EpubError

object MissingOPF : EpubError

object InvalidOPF : EpubError
