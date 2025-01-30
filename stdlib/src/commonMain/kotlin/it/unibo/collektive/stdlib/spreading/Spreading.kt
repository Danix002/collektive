/*
 * Copyright (c) 2025, Danilo Pianini, Nicolas Farabegoli, Elisa Tronetti,
 * and all authors listed in the `build.gradle.kts` and the generated `pom.xml` file.
 *
 * This file is part of Collektive, and is distributed under the terms of the Apache License 2.0,
 * as described in the LICENSE file in this project's repository's top directory.
 */

package it.unibo.collektive.stdlib.spreading

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.operators.share
import it.unibo.collektive.field.Field
import it.unibo.collektive.field.operations.minBy
import it.unibo.collektive.stdlib.util.coerceIn
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * Compute the [Distance] from the closest [source], starting from [bottom] and up to [top].
 *
 * the [Distance] between neighboring devices is computed using the [metric] function,
 * the distance summation is governed by the [accumulateDistance] function.
 */
inline fun <ID : Any, Distance : Comparable<Distance>> Aggregate<ID>.distanceTo(
    source: Boolean,
    bottom: Distance,
    top: Distance,
    crossinline accumulateDistance: (Distance, Distance) -> Distance,
    crossinline metric: () -> Field<ID, Distance>,
): Distance =
    gradientCast(
        source = source,
        local = if (source) bottom else top,
        bottom = bottom,
        top = top,
        accumulateData = { neighborToSource, hereToNeighbor, _ ->
            accumulateDistance(neighborToSource, hereToNeighbor)
        },
        accumulateDistance,
        metric,
    )

/**
 * Computes the hop distance from the closest [source].
 */
fun <ID : Any> Aggregate<ID>.hopDistanceTo(source: Boolean): Int =
    distanceTo(source, 0, Int.MAX_VALUE, Int::plus) { neighboring(1) }

/**
 * Compute the distance from the closest [source], using [Double]s.
 *
 * The distance between neighboring devices is computed using the [metric] function,
 * and defaults to the hop distance.
 */
@JvmOverloads
inline fun <ID : Any> Aggregate<ID>.distanceTo(
    source: Boolean,
    crossinline metric: () -> Field<ID, Double> = { neighboring(1.0) },
): Double = distanceTo(source, 0.0, Double.POSITIVE_INFINITY, Double::plus, metric)

/**
 * Propagate [local] values across a spanning tree starting from the closest [source].
 *
 * If there are no sources and no neighbors, default to [local] value.
 * The [metric] function is used to compute the distance between devices in form of a field of [Distance]s.
 * [Distance]s must be in the [[bottom], [top]] range, [accumulateDistance] is used to sum distances.
 * [accumulateData] is used to modify data from neighbors on the fly, and defaults to the identity function.
 */
@JvmOverloads
inline fun <ID : Any, Value, Distance : Comparable<Distance>> Aggregate<ID>.gradientCast(
    source: Boolean,
    local: Value,
    bottom: Distance,
    top: Distance,
    crossinline accumulateData: (fromSource: Distance, toNeighbor: Distance, data: Value) -> Value =
        { _, _, data -> data },
    crossinline accumulateDistance: (fromSource: Distance, toNeighbor: Distance) -> Distance,
    crossinline metric: () -> Field<ID, Distance>,
): Value {
    val topValue = top to local
    return share(topValue) { neighborData ->
        val paths =
            neighborData.alignedMap(metric().coerceIn(bottom, top)) { (fromSource, data), toNeighbor ->
                val totalDistance = accumulateDistance(fromSource, toNeighbor).coerceIn(bottom, top)
                check(totalDistance >= fromSource && totalDistance >= toNeighbor) {
                    "The provided distance accumulation function violates the triangle inequality: " +
                        "accumulating $fromSource and $toNeighbor produced $totalDistance"
                }
                val newData = accumulateData(fromSource, toNeighbor, data)
                totalDistance to newData
            }
        when {
            source -> bottom to local
            else -> paths.minBy(base = topValue) { it.first } // sort by distance from the nearest source
        }
    }.second // return the data
}

/**
 * Propagate [local] values across a spanning tree starting from the closest [source].
 *
 * If there are no sources and no neighbors, default to [local] value.
 * The [metric] function is used to compute the distance between devices in form of a field of [Int]s.
 * [accumulateData] is used to modify data from neighbors on the fly, and defaults to the identity function.
 */
@JvmOverloads
@JvmName("gradientCastInt")
inline fun <ID : Any, Type> Aggregate<ID>.gradientCast(
    source: Boolean,
    local: Type,
    crossinline accumulateData: (fromSource: Int, toNeighbor: Int, data: Type) -> Type = { _, _, data -> data },
    crossinline metric: () -> Field<ID, Int> = { neighboring(1) },
): Type = gradientCast(source, local, Int.MIN_VALUE, Int.MAX_VALUE, accumulateData, Int::plus, metric)

/**
 * Propagate [local] values across a spanning tree starting from the closest [source].
 *
 * If there are no sources and no neighbors, default to [local] value.
 * The [metric] function is used to compute the distance between devices in form of a field of [Double]s.
 * [accumulateData] is used to modify data from neighbors on the fly, and defaults to the identity function.
 */
@JvmOverloads
@JvmName("gradientCastDouble")
inline fun <ID : Any, Type> Aggregate<ID>.gradientCast(
    source: Boolean,
    local: Type,
    crossinline accumulateData: (fromSource: Double, toNeighbor: Double, data: Type) -> Type = { _, _, data -> data },
    crossinline metric: () -> Field<ID, Double> = { neighboring(1.0) },
): Type = gradientCast(source, local, 0.0, Double.POSITIVE_INFINITY, accumulateData, Double::plus, metric)
