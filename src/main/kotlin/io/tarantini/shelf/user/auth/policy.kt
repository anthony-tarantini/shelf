package io.tarantini.shelf.user.auth

import arrow.core.raise.context.ensure
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.AccessDenied
import io.tarantini.shelf.user.identity.domain.UserId
import io.tarantini.shelf.user.identity.domain.UserRole

sealed interface AccessPolicy {
    data object SharedAuthenticatedRead : AccessPolicy

    data object SharedAuthenticatedMutation : AccessPolicy

    data object AdminOnly : AccessPolicy

    data class UserOwned(val ownerId: UserId) : AccessPolicy
}

context(_: RaiseContext, auth: JwtContext)
fun requireAuthenticatedSharedAccess() = requirePolicy(AccessPolicy.SharedAuthenticatedRead)

context(_: RaiseContext, auth: JwtContext)
fun requireAuthenticatedSharedMutation() = requirePolicy(AccessPolicy.SharedAuthenticatedMutation)

context(_: RaiseContext, auth: JwtContext)
fun requireOwnership(ownerId: UserId) = requirePolicy(AccessPolicy.UserOwned(ownerId))

context(_: RaiseContext)
fun requireAdmin(role: UserRole) = requirePolicy(AccessPolicy.AdminOnly, role = role)

context(_: RaiseContext, auth: JwtContext)
private fun requirePolicy(policy: AccessPolicy, role: UserRole? = null) =
    requirePolicy(policy, auth.userId, role)

context(_: RaiseContext)
private fun requirePolicy(policy: AccessPolicy, userId: UserId? = null, role: UserRole? = null) {
    when (policy) {
        AccessPolicy.SharedAuthenticatedRead -> Unit
        AccessPolicy.SharedAuthenticatedMutation -> Unit
        AccessPolicy.AdminOnly -> ensure(role == UserRole.ADMIN) { AccessDenied }
        is AccessPolicy.UserOwned -> ensure(userId == policy.ownerId) { AccessDenied }
    }
}
