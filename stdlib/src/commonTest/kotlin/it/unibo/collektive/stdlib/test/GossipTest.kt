/*
 * Copyright (c) 2024, Danilo Pianini, Nicolas Farabegoli, Elisa Tronetti,
 * and all authors listed in the `build.gradle.kts` and the generated `pom.xml` file.
 *
 * This file is part of Collektive, and is distributed under the terms of the Apache License 2.0,
 * as described in the LICENSE file in this project's repository's top directory.
 */

package it.unibo.collektive.stdlib.test

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import it.unibo.collektive.stdlib.SelfStabilizingGossip.gossipMax
import it.unibo.collektive.stdlib.SelfStabilizingGossip.gossipMin
import it.unibo.collektive.testing.Environment
import it.unibo.collektive.testing.mooreGrid

class GossipTest : StringSpec({

    // A stable gossip means that every device of the network has the same value
    fun <Value> Environment<Value>.gossipIsStable(): Boolean =
        status().values.distinct().size == 1

    fun <Value> Environment<Value>.gossipResult(): Value =
        status().values.distinct().first()

    fun squareMooreGridWithGossip(size: Int, max: Boolean = true) =
        mooreGrid<Int>(size, size, { _, _ -> Int.MAX_VALUE }) {
            if (max) {
                gossipMax(localId) // gossip the max localID in the network
            } else {
                gossipMin(localId) // gossip the min localID in the network
            }
        }.apply {
            nodes.size shouldBe size * size
            val initial = status().values.distinct()
            initial.size shouldBe 1
            check(initial.first() == Int.MAX_VALUE) {
                "Initial status is not `Int.MAX_VALUE`, but it is $initial (${initial::class.simpleName})"
            }
        }

    fun linearMooreGridWithGossip(size: Int, max: Boolean = true) =
        mooreGrid<Int>(size, 1, { _, _ -> Int.MAX_VALUE }) {
            if (max) {
                gossipMax(localId) // gossip the max localID in the network
            } else {
                gossipMin(localId) // gossip the min localID in the network
            }
        }.apply {
            nodes.size shouldBe size
            val initial = status().values.distinct()
            initial.size shouldBe 1
            check(initial.first() == Int.MAX_VALUE) {
                "Initial status is not `Int.MAX_VALUE`, but it is $initial (${initial::class.simpleName})"
            }
        }

    "gossipMax in a square moore grid stabilizes after 2 reverse cycles" {
        val size = 5
        val environment: Environment<Int> = squareMooreGridWithGossip(size)
        environment.cycleInOrder()
        val firstRound = environment.status()
        // initially there should be different values as many are the devices in the network
        firstRound.values.distinct().size shouldBe (size * size)
        // in this implementation the initial value for each device is its own ID
        firstRound.forEach { (id, value) -> value shouldBe id.toDouble() }
        // status at first cycle
        environment.gossipIsStable() shouldBe false
        environment.cycleInReverseOrder()
        // status at second cycle
        environment.gossipIsStable() shouldBe true
        environment.gossipResult() shouldBe 24
    }

    "gossipMax in the best case stabilizes in one cycle" {
        val size = 5
        val environment: Environment<Int> = squareMooreGridWithGossip(size)
        environment.cycleInReverseOrder()
        environment.gossipIsStable() shouldBe true
        environment.gossipResult() shouldBe 24
    }

    "gossipMax in the worst case stabilizes in the network diameter cycles" {
        val size = 10
        val environment: Environment<Int> = linearMooreGridWithGossip(size)
        repeat(times = size - 1) {
            environment.cycleInOrder()
            environment.gossipIsStable() shouldBe false
        }
        environment.cycleInOrder()
        environment.gossipIsStable() shouldBe true
        environment.gossipResult() shouldBe 9
    }

    "gossipMin in the best case stabilizes in one cycle" {
        val size = 5
        val environment: Environment<Int> = squareMooreGridWithGossip(size, max = false)
        environment.cycleInOrder()
        environment.gossipIsStable() shouldBe true
        environment.gossipResult() shouldBe 0
    }

    "gossipMin in the worst case stabilizes in the network diameter cycles" {
        val size = 10
        val environment: Environment<Int> = linearMooreGridWithGossip(size, max = false)
        repeat(times = size - 1) {
            environment.cycleInReverseOrder()
            environment.gossipIsStable() shouldBe false
        }
        environment.cycleInOrder()
        environment.gossipIsStable() shouldBe true
        environment.gossipResult() shouldBe 0
    }

    "gossipMin in a square moore grid stabilizes after 2 reverse cycles" {
        val size = 5
        val environment: Environment<Int> = squareMooreGridWithGossip(size, max = false)
        environment.cycleInReverseOrder()
        val firstRound = environment.status()
        // initially there should be different values as many are the devices in the network
        firstRound.values.distinct().size shouldBe (size * size)
        // in this implementation the initial value for each device is its own ID
        firstRound.forEach { (id, value) -> value shouldBe id.toDouble() }
        // status at first cycle
        environment.gossipIsStable() shouldBe false
        environment.cycleInOrder()
        // status at second cycle
        environment.gossipIsStable() shouldBe true
        environment.gossipResult() shouldBe 0
    }
})
