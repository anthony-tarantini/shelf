@file:OptIn(ExperimentalSerializationApi::class)

package io.tarantini.shelf.app

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable data class Response<T>(val data: T)

@Serializable data class Request<T>(val data: T)

@Serializable
sealed interface PersistenceState {
    @Serializable object Unsaved : PersistenceState

    @Serializable object Persisted : PersistenceState
}

@Serializable(with = IdentitySerializer::class)
sealed interface Identity<out S : PersistenceState, out ID> {
    val valueOrNull: ID?

    @Serializable
    data object Unsaved : Identity<PersistenceState.Unsaved, Nothing> {
        override val valueOrNull: Nothing? = null
    }

    @Serializable
    data class Persisted<out ID>(val id: ID) : Identity<PersistenceState.Persisted, ID> {
        override val valueOrNull: ID = id
    }
}

val <ID> Identity<PersistenceState.Persisted, ID>.id: ID
    get() = (this as Identity.Persisted).id

class IdentitySerializer<S : PersistenceState, ID>(
    private val stateSerializer: KSerializer<S>,
    private val idSerializer: KSerializer<ID>,
) : KSerializer<Identity<S, ID>> {
    override val descriptor: SerialDescriptor = idSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Identity<S, ID>) {
        when (value) {
            is Identity.Persisted -> idSerializer.serialize(encoder, value.id)
            is Identity.Unsaved -> encoder.encodeNull()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun deserialize(decoder: Decoder): Identity<S, ID> {
        return if (decoder.decodeNotNullMark()) {
            Identity.Persisted(idSerializer.deserialize(decoder)) as Identity<S, ID>
        } else {
            Identity.Unsaved as Identity<S, ID>
        }
    }
}
