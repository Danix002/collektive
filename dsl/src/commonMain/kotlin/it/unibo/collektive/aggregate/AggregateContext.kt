package it.unibo.collektive.aggregate

import it.unibo.collektive.ID
import it.unibo.collektive.field.Field
import it.unibo.collektive.messages.AnisotropicMessage
import it.unibo.collektive.messages.InboundMessage
import it.unibo.collektive.messages.IsotropicMessage
import it.unibo.collektive.messages.OutboundMessage
import it.unibo.collektive.networking.Network
import it.unibo.collektive.runUntil
import it.unibo.collektive.singleCycle
import it.unibo.collektive.stack.Path
import it.unibo.collektive.stack.Stack
import it.unibo.collektive.state.State

/**
 * Context for managing aggregate computation.
 * It represents the [localId] of the device, the [messages] received from the neighbours,
 * and the [previousState] of the device.
 */
class AggregateContext(
    private val localId: ID,
    private val messages: Set<InboundMessage>,
    private val previousState: Set<State<*>>,
) {

    private val stack = Stack<Any>()
    private var state = setOf<State<*>>()
    private var toBeSent = setOf<OutboundMessage>()

    /**
     * Messages to send to the other nodes.
     */
    fun messagesToSend(): Set<OutboundMessage> = toBeSent

    /**
     * Return the current state of the device as a new state.
     */
    fun newState(): Set<State<*>> = state

    /**
     * This function computes the local value of e_i, substituting variable n with the nvalue w of
     * messages received from neighbours, using the local value of e_i ([initial]) as a default.
     * The exchange returns the neighbouring or local value v_r from the evaluation of e_r applied to the [body].
     * e_s evaluates to a nvalue w_s consisting of local values to be sent to neighbour devices δ′,
     * which will use their corresponding w_s(δ') as soon as they wake up and perform their next execution round.
     *
     * Often, expressions e_r and e_s coincide, so this function provides a shorthand for exchange(e_i, (n) => (e, e)).
     *
     * ## Example
     * ```
     * exchange(0){ f ->
     *  f.mapField { _, v -> if (v % 2 == 0) v + 1 else v * 2 }
     * }
     * ```
     * The result of the exchange function is a field with as messages a map with key the id of devices across the
     * network and the result of the computation passed as relative local values.
     */
    fun <X, Y> exchange(initial: X, body: (Field<X>) -> Field<Y>): Field<Y> {
        val messages = messagesAt<X>(stack.currentPath())
        val previous = stateAt<X>(stack.currentPath()) ?: initial
        val subject = Field(localId, messages + (localId to previous))
        return body(subject).also { field ->
            toBeSent = toBeSent + IsotropicMessage(localId, mapOf(stack.currentPath() to field.local))
            if (messages.isNotEmpty()) {
                field.toMap().filterNot { it.key == localId }.map { (id, value) ->
                    val old = toBeSent
                        .filterIsInstance<AnisotropicMessage>()
                        .firstOrNull { it.senderId == localId && it.receiverId == id }
                        ?: AnisotropicMessage(localId, id, mapOf(stack.currentPath() to value))
                    toBeSent = (
                        toBeSent
                            .filterNot {
                                it is AnisotropicMessage && it.senderId == localId && it.receiverId == id
                            } + AnisotropicMessage(localId, id, old.message + (stack.currentPath() to value))
                        ).toSet()
                }
            }
            state = state
                .filterNot { stack.currentPath() == it.path }
                .toSet() + State(stack.currentPath(), field.local)
        }
    }

    /**
     * Iteratively updates the value of the input expression [repeat] at each device using the last computed value or
     * the [initial].
     */
    fun <X, Y> repeating(initial: X, repeat: (X) -> Y): Y {
        val res = stateAt<X>(stack.currentPath())?.let { repeat(it) } ?: repeat(initial)
        state = state.filterNot { stack.currentPath() == it.path }.toSet() + State(stack.currentPath(), res)
        return res
    }

    /**
     * Alignment function that pushes in the stack the pivot, executes the body and pop the last
     * element of the stack after it is called.
     * Returns the body's return element.
     */
    fun <R> alignedOn(pivot: Any?, body: () -> R): R {
        stack.alignRaw(pivot)
        return body().also { stack.dealign() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> messagesAt(path: Path): Map<ID, T> = messages
        .filter { it.messages.containsKey(path) }
        .associate { it.senderId to it.messages[path] as T }

    @Suppress("UNCHECKED_CAST")
    private fun <T> stateAt(path: Path): T? = previousState.firstOrNull { it.path == path }?.value as? T
}

/**
 * Aggregate program entry point which computes a single iteration of a device [localId], taking as parameters
 * the previous [state], the [messages] received from the neighbours and the [init] with AggregateContext
 * object receiver that provides the aggregate constructs.
 */
fun <X> aggregate(
    localId: ID,
    messages: Set<InboundMessage> = emptySet(),
    state: Set<State<*>> = emptySet(),
    init: AggregateContext.() -> X,
) = singleCycle(localId, messages, state, compute = init)

/**
 * Aggregate program entry point which computes multiple iterations of a device [localId],
 * over a [condition] and a [network] of devices, with the lambda [init] with AggregateContext
 * object receiver that provides the aggregate constructs.
 */
fun <X> aggregate(
    localId: ID,
    condition: () -> Boolean,
    network: Network,
    init: AggregateContext.() -> X,
) = runUntil(localId, condition, network, compute = init)