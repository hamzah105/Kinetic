package dev.kinetic.app

/** Shared UI operation boundary; settings cannot overlap turns, recovery or context writes. */
internal fun externalCatalogIdle(recovered: Boolean, turn: Boolean, summary: Boolean,
    memory: Boolean, mcp: Boolean, appFunctions: Boolean): Boolean =
    recovered && !turn && !summary && !memory && !mcp && !appFunctions
