import stack.Path
import stack.Stack
import util.switchIndexes

fun <X> singleCycle(
    localId: ID = IntId(),
    messages: Map<Path, Map<ID, *>> = emptyMap<Path, Map<ID, Any>>(),
    state: Map<Path, *> = emptyMap<Path, Any>(),
    compute: AggregateContext.() -> X
): AggregateContext.AggregateResult<X> {
    Stack.clearStack()
    return with(AggregateContext(localId, messages, state)) {
        AggregateContext.AggregateResult(compute(), messagesToSend(), newState()).also {
            Stack.clearStack()
        }
    }
}

fun <X> runUntil(
    condition: () -> Boolean,
    network: Network,
    compute: AggregateContext.() -> X): AggregateContext.AggregateResult<X> {
    val localId: ID = IntId()
    var state = emptyMap<Path, Any?>()
    var computed: AggregateContext.AggregateResult<X>? = null
    while (condition()) {
        val messages: Map<Path, Map<ID, *>> = network.receive().switchIndexes()
        computed = singleCycle(localId, messages, state, compute)
        state = computed.newState
        network.send(localId, computed.toSend)
    }
    return computed ?: throw IllegalStateException("The computation did not produce a result")
}