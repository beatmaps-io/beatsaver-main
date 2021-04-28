package io.beatmaps.maps.testplay

import io.beatmaps.api.MapDetail
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.modal
import io.beatmaps.maps.mapInfo
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.ref
import react.router.dom.RouteResultHistory

external interface TestplayProps : RProps {
    var mapInfo: MapDetail
    var isOwner: Boolean
    var loggedInId: Int?
    var refreshPage: () -> Unit
    var history: RouteResultHistory
}

@JsExport
class Testplay : RComponent<TestplayProps, RState>() {
    private val modalRef = createRef<ModalComponent>()

    override fun RBuilder.render() {
        modal {
            ref = modalRef
        }
        mapInfo {
            mapInfo = props.mapInfo
            isOwner = props.isOwner
            modal = modalRef
            reloadMap = props.refreshPage
            deleteMap = {
                props.history.push("/profile")
            }
        }
        timeline {
            mapInfo = props.mapInfo
            isOwner = props.isOwner
            loggedInId = props.loggedInId
            reloadMap = props.refreshPage
            modal = modalRef
            history = props.history
        }
    }
}

fun RBuilder.testplay(handler: TestplayProps.() -> Unit): ReactElement {
    return child(Testplay::class) {
        this.attrs(handler)
    }
}