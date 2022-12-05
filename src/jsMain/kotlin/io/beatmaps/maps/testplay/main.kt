package io.beatmaps.maps.testplay

import io.beatmaps.api.MapDetail
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.modal
import io.beatmaps.maps.infoTable
import io.beatmaps.maps.mapInfo
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.ref
import react.router.dom.History

external interface TestplayProps : Props {
    var mapInfo: MapDetail
    var isOwner: Boolean
    var loggedInId: Int?
    var refreshPage: () -> Unit
    var history: History
    var updateMapinfo: (MapDetail) -> Unit
}

class Testplay : RComponent<TestplayProps, State>() {
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
            updateMapinfo = props.updateMapinfo
        }
        infoTable {
            map = props.mapInfo
            horizontal = true
            selected = null
            changeSelectedDiff = { }
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

fun RBuilder.testplay(handler: TestplayProps.() -> Unit) =
    child(Testplay::class) {
        this.attrs(handler)
    }
