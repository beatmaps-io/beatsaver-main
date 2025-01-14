package io.beatmaps.util

import kotlin.js.Promise

fun <T> Promise<T>.orCatch(block: (Throwable) -> T) =
    then({ it }, block)

fun <T> Promise<Promise<T>>.orCatch(block: (Throwable) -> T) =
    then({ it }, block)

inline fun <T> T.applyIf(condition: Boolean, block: T.() -> T): T = if (condition) block(this) else this
