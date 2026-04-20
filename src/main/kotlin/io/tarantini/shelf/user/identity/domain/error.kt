package io.tarantini.shelf.user.identity.domain

import io.tarantini.shelf.app.AppError

sealed interface UserError : AppError

sealed interface UserPersistenceError : UserError

object UserNotFound : UserPersistenceError

object EmailAlreadyExists : UserPersistenceError

object UsernameAlreadyExists : UserPersistenceError

object SetupAlreadyComplete : UserPersistenceError

object IncorrectPassword : UserPersistenceError

sealed interface UserValidationError : UserError

object EmptyUserId : UserValidationError

object InvalidUserId : UserValidationError

object EmptyTokenId : UserValidationError

object InvalidTokenId : UserValidationError

object EmptyUsername : UserValidationError

object TooShortUsername : UserValidationError

object EmptyEmail : UserValidationError

object InvalidEmail : UserValidationError

object EmptyPassword : UserValidationError

object TooShortPassword : UserValidationError

sealed interface JwtError : UserError

object JwtGeneration : JwtError

object JwtInvalid : JwtError

object JwtMissing : JwtError
