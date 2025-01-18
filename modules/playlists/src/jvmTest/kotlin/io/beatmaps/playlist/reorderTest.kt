package io.beatmaps.playlist

import com.appmattus.kotlinfixture.decorator.nullability.NeverNullStrategy
import com.appmattus.kotlinfixture.decorator.nullability.nullabilityStrategy
import com.appmattus.kotlinfixture.kotlinFixture
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapDetailWithOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals

class ReOrderTest {
    val fixture = kotlinFixture {
        nullabilityStrategy(NeverNullStrategy)
    }

    private fun List<Float>.toMaps() = map {
        MapDetailWithOrder(fixture<MapDetail>(), it)
    }

    @Test
    fun generatesCorrectNewOrderings() {
        val testData = mapOf(
            (listOf(1f, 2f, 3f) to (2 to 1)) to listOf(1f, 1.5f, 2f),
            (listOf(1f, 3f, 4f) to (2 to 1)) to listOf(1f, 2f, 3f),
            (listOf(0.25f, 0.5f, 1f) to (2 to 1)) to listOf(0.25f, 0.375f, 0.5f),
            (listOf(1f, 4f, 5f) to (2 to 1)) to listOf(1f, 2f, 4f),
            (listOf(1.1f, 4f, 5f) to (2 to 1)) to listOf(1.1f, 2f, 4f),
            (listOf(1.6f, 2.1f, 3f) to (2 to 1)) to listOf(1.6f, 2f, 2.1f),

            (listOf(1f, 2f, 3f) to (0 to 2)) to listOf(2f, 3f, 4f),
            (listOf(1f, 2f, 3.1f) to (0 to 2)) to listOf(2f, 3.1f, 4f),
            (listOf(1f, 2f, 2.1f) to (0 to 2)) to listOf(2f, 2.1f, 3f),

            (listOf(1f, 2f, 3f) to (2 to 0)) to listOf(0.5f, 1f, 2f),
            (listOf(0.5f, 2f, 3.1f) to (2 to 0)) to listOf(0.25f, 0.5f, 2f),
            (listOf(5f, 6f, 7.1f) to (2 to 0)) to listOf(1f, 5f, 6f)
        )

        testData.forEach { (idata, output) ->
            val (input, ends) = idata
            val (from, to) = ends

            val newOrder = reorderMaps(input.toMaps(), from, to)
            val sorted = newOrder?.sortedBy { it.order }
            assertContentEquals(newOrder, sorted, "Maps should be returned sorted")
            assertContentEquals(output, newOrder?.map { it.order }, "Ordering should match expected value ($output)")
        }
    }
}
