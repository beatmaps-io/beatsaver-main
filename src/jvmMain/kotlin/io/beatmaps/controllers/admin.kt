@file:UseSerializers(OptionalPropertySerializer::class)

package io.beatmaps.controllers

import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.ModelClass
import io.beatmaps.common.OptionalProperty
import io.beatmaps.common.OptionalPropertySerializer
import io.beatmaps.common.util.paramInfo
import io.beatmaps.common.util.requireParams
import io.beatmaps.genericPage
import io.ktor.resources.Resource
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import kotlinx.serialization.UseSerializers

@Resource("/modlog")
class ModLog

@Resource("/modreview")
class ModReview

@Resource("/issues")
class IssuesController {
    @Resource("/{id}")
    data class Detail(
        @ModelClass(Int::class)
        val id: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @Ignore
        val api: IssuesController
    ) {
        init {
            requireParams(
                paramInfo(Detail::id)
            )
        }
    }
}

fun Route.adminController() {
    get<ModLog> {
        genericPage()
    }

    get<ModReview> {
        genericPage()
    }

    get<IssuesController> {
        genericPage()
    }

    get<IssuesController.Detail> {
        // We could include extra detail but this page is meant to be private
        genericPage()
    }
}
