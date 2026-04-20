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
interface UserService {
    context(_: RaiseContext)
    suspend fun register(command: RegisterUserCommand): Pair<JwtToken, SavedUserRoot>

    context(_: RaiseContext)
    suspend fun isSetupComplete(): Boolean

    context(_: RaiseContext)
    suspend fun setup(command: SetupUserCommand): Pair<JwtToken, SavedUserRoot>

    context(_: RaiseContext, auth: JwtContext)
    suspend fun update(command: UpdateCurrentUserCommand): SavedUserRoot

    context(_: RaiseContext)
    suspend fun login(command: LoginUserCommand): Pair<JwtToken, SavedUserRoot>

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

@Suppress("TooManyFunctions")
fun userService(
    userQueries: UserQueries,
    jwtService: JwtService,
    mutationRepository: UserMutationRepository = userMutationRepository(userQueries),
): UserService = UserIdentityService(userQueries, jwtService, mutationRepository)

private class UserIdentityService(
    private val userQueries: UserQueries,
    private val jwtService: JwtService,
    private val mutationRepository: UserMutationRepository,
) : UserService {
    context(_: RaiseContext)
    override suspend fun register(command: RegisterUserCommand) =
        withContext(Dispatchers.IO) {
            val newUser = UserMutationDecider.decideRegistration(command, UserRole.USER)
            val user = mutationRepository.createUser(newUser)
            val token = jwtService.generateJwtToken(user.id.id)
            token to user
        }

    context(_: RaiseContext)
    override suspend fun isSetupComplete(): Boolean =
        withContext(Dispatchers.IO) { userQueries.countUsers() > 0 }

    context(_: RaiseContext)
    override suspend fun setup(command: SetupUserCommand): Pair<JwtToken, SavedUserRoot> =
        withContext(Dispatchers.IO) {
            ensure(userQueries.countUsers() == 0L) { SetupAlreadyComplete }

            val newUser =
                UserMutationDecider.decideRegistration(
                    RegisterUserCommand(command.email, command.username, command.password),
                    UserRole.ADMIN,
                )
            val user = mutationRepository.createUser(newUser)
            val token = jwtService.generateJwtToken(user.id.id)
            token to user
        }

    context(_: RaiseContext, auth: JwtContext)
    override suspend fun update(command: UpdateCurrentUserCommand) =
        withContext(Dispatchers.IO) {
            val existing = mutationRepository.getUserById(auth.userId)
            val updateUser = UserMutationDecider.decideUpdate(existing, command)
            mutationRepository.updateUser(updateUser)
        }

    context(_: RaiseContext)
    override suspend fun login(command: LoginUserCommand) =
        withContext(Dispatchers.IO) {
            val aggregate = mutationRepository.getUserByEmail(command.email)
            val hashedPassword = HashedPassword.create(command.password, aggregate.salt)
            ensure(hashedPassword.value contentEquals aggregate.hashedPassword.value) {
                IncorrectPassword
            }

            jwtService.generateJwtToken(aggregate.user.id.id) to aggregate.user
        }

    context(_: RaiseContext, auth: JwtContext)
    override suspend fun getCurrentUser(): SavedUserRoot =
        withContext(Dispatchers.IO) { mutationRepository.getUserById(auth.userId).user }

    context(_: RaiseContext)
    override suspend fun getUserById(userId: UserId) =
        withContext(Dispatchers.IO) { mutationRepository.getUserById(userId).user }

    context(_: RaiseContext)
    override suspend fun getUserByName(username: UserName) =
        withContext(Dispatchers.IO) { mutationRepository.getUserByUsername(username).user }

    context(_: RaiseContext)
    override suspend fun getAllUsers(): List<SavedUserRoot> =
        withContext(Dispatchers.IO) { userQueries.getAllUsers() }

    context(_: RaiseContext)
    override suspend fun updateRole(userId: UserId, role: UserRole): SavedUserRoot =
        withContext(Dispatchers.IO) {
            val existing = mutationRepository.getUserById(userId)
            val updateUser =
                UpdateUser(
                    existing.user.id.id,
                    existing.user.email,
                    existing.user.username,
                    role,
                    existing.salt,
                    existing.hashedPassword,
                )
            mutationRepository.updateUser(updateUser)
        }

    context(_: RaiseContext)
    override suspend fun deleteUserById(userId: UserId) =
        withContext(Dispatchers.IO) { mutationRepository.deleteUserById(userId) }
}
