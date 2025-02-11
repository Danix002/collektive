/*
 * Copyright (c) 2025, Danilo Pianini, Nicolas Farabegoli, Elisa Tronetti,
 * and all authors listed in the `build.gradle.kts` and the generated `pom.xml` file.
 *
 * This file is part of Collektive, and is distributed under the terms of the Apache License 2.0,
 * as described in the LICENSE file in this project's repository's top directory.
 */

package it.unibo.collektive.networking

import it.unibo.collektive.path.Path
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.StringFormat
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class SerializerNetworkTest(private val serializer: SerialFormat = Json) : Mailbox<Int> {
    val messages = mutableMapOf<Int, Message<Int, Any?>>()
    private val factory = object : SerializedMessageFactory<Int, Any?>(serializer) {}

    fun serializeAndSend(id: Int): String {
        require(messages[id] is SerializedMessage<Int>) { "Message $id is not serialized" }
        return when (serializer) {
            is StringFormat -> serializer.encodeToString(messages[id] as SerializedMessage<Int>)
            is BinaryFormat -> serializer.encodeToByteArray(messages[id] as SerializedMessage<Int>).decodeToString()
            else -> error("Unsupported serializer")
        }
    }

    fun deserializeAndReceive(serialized: String) {
        val message =
            when (serializer) {
                is StringFormat -> serializer.decodeFromString<SerializedMessage<Int>>(serialized)
                is BinaryFormat ->
                    serializer.decodeFromByteArray<SerializedMessage<Int>>(
                        serialized.encodeToByteArray(),
                    )
                else -> error("Unsupported serializer")
            }
        deliverableReceived(message)
    }

    override fun deliverableFor(
        id: Int,
        outboundMessage: OutboundEnvelope<Int>,
    ) {
        messages[id] = outboundMessage.prepareMessageFor(id, factory)
    }

    override fun deliverableReceived(message: Message<Int, *>) {
        messages[message.senderId] = message
    }

    @OptIn(InternalSerializationApi::class)
    override fun currentInbound(): NeighborsData<Int> =
        object : NeighborsData<Int> {
            override val neighbors: Set<Int> get() = messages.keys

            override fun <Value> dataAt(
                path: Path,
                kClass: KSerializer<Value>,
            ): Map<Int, Value> =
                messages
                    .mapValues { (_, message) ->
                        require(message.sharedData.all { it.value is ByteArray }) {
                            "Message ${message.senderId} is not serialized"
                        }
                        message.sharedData.getOrElse(path) { NoValue }
                    }.filterValues { it != NoValue }
                    .mapValues { (_, payload) ->
                        val byteArrayPayload = payload as ByteArray
                        when (serializer) {
                            is StringFormat -> serializer.decodeFromString(kClass, byteArrayPayload.decodeToString())
                            is BinaryFormat -> serializer.decodeFromByteArray(kClass, byteArrayPayload)
                            else -> error("Unsupported serializer")
                        }
                    }
        }

    private object NoValue
}
