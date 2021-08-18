// ktlint-disable filename
package io.beatmaps

import kotlinx.html.id
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.dom.div

@JsExport
class NotFound : RComponent<RProps, RState>() {

    override fun RBuilder.render() {
        div {
            attrs.id = "notfound"
            +"Not found"
        }
    }
}
