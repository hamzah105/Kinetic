package dev.kinetic.core.agent

import java.util.UUID

fun interface IdGenerator {
    fun nextId(prefix: String): String
}

class UuidIdGenerator : IdGenerator {
    override fun nextId(prefix: String): String = "$prefix-${UUID.randomUUID()}"
}

