package io.beatmaps.util

import kotlin.js.Promise

fun <T> Promise<T>.orCatch(block: (Throwable) -> T) =
    then({ it }, block)

fun <T> Promise<Promise<T>>.orCatch(block: (Throwable) -> T) =
    then({ it }, block)
