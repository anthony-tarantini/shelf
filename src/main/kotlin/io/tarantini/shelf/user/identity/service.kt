package io.tarantini.shelf.user.identity

import arrow.core.raise.context.ensure
import arrow.core.raise.ensure
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.user.auth.JwtContext
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.JwtToken
import io.tarantini.shelf.user.identity.domain.*
import io.tarantini.shelf.user.identity.persistence.UserQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions")
fun userService(userQueries: UserQueries, jwtService: JwtService) =
    object : UserService {
        context(_: RaiseContext)
        override suspend fun register(input: NewUserRequest) =
            withContext(Dispatchers.IO) {
                val email = UserEmail(input.email)
                val username = UserName(input.username)
                val password = UserPassword(input.password)

                val salt = Salt.generate()
                val hashedPassword = HashedPassword.create(password, salt)

                val newUser = NewUser(email, username, UserRole.USER, salt, hashedPassword)
                val user = userQueries.createUser(newUser)
                val token = jwtService.generateJwtToken(user.id.id)
                token to user
            }

        context(_: RaiseContext)
        override suspend fun isSetupComplete(): Boolean =
            withContext(Dispatchers.IO) { userQueries.countUsers() > 0 }

        context(_: RaiseContext)
        override suspend fun setup(input: NewUserRequest): Pair<JwtToken, SavedUserRoot> =
            withContext(Dispatchers.IO) {
                ensure(userQueries.countUsers() == 0L) { SetupAlreadyComplete }

                val email = UserEmail(input.email)
                val username = UserName(input.username)
                val password = UserPassword(input.password)

                val salt = Salt.generate()
                val hashedPassword = HashedPassword.create(password, salt)

                val newUser = NewUser(email, username, UserRole.ADMIN, salt, hashedPassword)
                val user = userQueries.createUser(newUser)
                val token = jwtService.generateJwtToken(user.id.id)
                token to user
            }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun update(input: UpdateUserRequest) =
            withContext(Dispatchers.IO) {
                val existing = userQueries.getUserById(auth.userId)

                val email = input.email?.let { UserEmail(it) } ?: existing.user.email
                val username = input.username?.let { UserName(it) } ?: existing.user.username
                val role =
                    existing.user.role // Role updates should probably be restricted to admin API
                val salt = existing.salt
                val hashedPassword =
                    input.password?.let { HashedPassword.create(UserPassword(it), salt) }
                        ?: existing.hashedPassword

                val updateUser =
                    UpdateUser(auth.userId, email, username, role, salt, hashedPassword)
                userQueries.updateUser(updateUser)
            }

        context(_: RaiseContext)
        override suspend fun login(input: LoginUserRequest) =
            withContext(Dispatchers.IO) {
                val email = UserEmail(input.email)
                val password = UserPassword(input.password)

                val aggregate = userQueries.getUserByEmail(email)
                val hashedPassword = HashedPassword.create(password, aggregate.salt)
                ensure(hashedPassword.value contentEquals aggregate.hashedPassword.value) {
                    IncorrectPassword
                }

                jwtService.generateJwtToken(aggregate.user.id.id) to aggregate.user
            }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun getCurrentUser(): SavedUserRoot =
            withContext(Dispatchers.IO) { userQueries.getUserById(auth.userId).user }

        context(_: RaiseContext)
        override suspend fun getUserById(userId: UserId) =
            withContext(Dispatchers.IO) { userQueries.getUserById(userId).user }

        context(_: RaiseContext)
        override suspend fun getUserByName(username: UserName) =
            withContext(Dispatchers.IO) { userQueries.getUserByUsername(username).user }

        context(_: RaiseContext)
        override suspend fun getAllUsers(): List<SavedUserRoot> =
            withContext(Dispatchers.IO) { userQueries.getAllUsers() }

        context(_: RaiseContext)
        override suspend fun updateRole(userId: UserId, role: UserRole): SavedUserRoot =
            withContext(Dispatchers.IO) {
                val existing = userQueries.getUserById(userId)
                val updateUser =
                    UpdateUser(
                        existing.user.id.id,
                        existing.user.email,
                        existing.user.username,
                        role,
                        existing.salt,
                        existing.hashedPassword,
                    )
                userQueries.updateUser(updateUser)
            }

        context(_: RaiseContext)
        override suspend fun deleteUserById(userId: UserId) =
            withContext(Dispatchers.IO) { userQueries.deleteUserById(userId) }
    }

@Suppress("TooManyFunctions")
interface UserService {
    context(_: RaiseContext)
    suspend fun register(input: NewUserRequest): Pair<JwtToken, SavedUserRoot>

    context(_: RaiseContext)
    suspend fun isSetupComplete(): Boolean

    context(_: RaiseContext)
    suspend fun setup(input: NewUserRequest): Pair<JwtToken, SavedUserRoot>

    context(_: RaiseContext, auth: JwtContext)
    suspend fun update(input: UpdateUserRequest): SavedUserRoot

    context(_: RaiseContext)
    suspend fun login(input: LoginUserRequest): Pair<JwtToken, SavedUserRoot>

    context(_: RaiseContext, auth: JwtContext)
    suspend fun getCurrentUser(): SavedUserRoot

    context(_: RaiseContext)
    suspend fun getUserById(userId: UserId): SavedUserRoot

    context(_: RaiseContext)
    suspend fun getUserByName(username: UserName): SavedUserRoot

    context(_: RaiseContext)
    suspend fun getAllUsers(): List<SavedUserRoot>

    context(_: RaiseContext)
    suspend fun updateRole(userId: UserId, role: UserRole): SavedUserRoot

    context(_: RaiseContext)
    suspend fun deleteUserById(userId: UserId)
}
