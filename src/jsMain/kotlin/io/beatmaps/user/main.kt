package io.beatmaps.user

import Axios
import AxiosRequestConfig
import generateConfig
import io.beatmaps.common.Config
import io.beatmaps.api.UserDetail
import io.beatmaps.common.formatTime
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.beatmapTable
import io.beatmaps.index.modal
import io.beatmaps.setPageTitle
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.js.onClickFunction
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.w3c.dom.get
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.createRef
import react.dom.*
import react.ref
import react.router.dom.RouteResultHistory
import react.setState

external interface ProfilePageProps : RProps {
    var history: RouteResultHistory
    var userId: Int?
}

data class ProfilePageState(var loading: Boolean = false, var startup: Boolean = false, var userDetail: UserDetail? = null, var wip: Boolean = false) : RState

@JsExport
class ProfilePage : RComponent<ProfilePageProps, ProfilePageState>() {
    private val modalRef = createRef<ModalComponent>()

    override fun componentDidMount() {
        setPageTitle("Profile")

        setState {
            wip = props.userId == null
            startup = true
        }

        loadState()
    }

    private fun loadState() {
        setState {
            loading = true
        }

        val url = props.userId?.let { "${Config.apibase}/users/id/$it" } ?: "/api/users/me"

        Axios.get<UserDetail>(
            url,
            generateConfig<String, UserDetail>()
        ).then {
            setPageTitle("Profile - ${it.data.name}")
            setState {
                userDetail = it.data
                loading = false
            }
        }.catch {
            // Cancelled request
        }
    }

    override fun RBuilder.render() {
        val loggedInLocal = window["userId"] as Int?
        modal {
            ref = modalRef
        }
        div ("row") {
            div ("col-md-4 mb-3") {
                div ("card") {
                    div("card-body") {
                        div("d-flex flex-column align-items-center text-center") {
                            img("Profile Image", state.userDetail?.avatar, classes = "rounded-circle") {
                                attrs.width = "150"
                                attrs.height = "150"
                            }
                            div("mt-3") {
                                h4 {
                                    +(state.userDetail?.name ?: "")
                                }
                                /*p("text-muted mb-1") {
                                    +"Subheading"
                                }*/
                            }
                        }
                    }
                }
            }
            div ("col-md-8 mb-3") {
                div ("card user-badges") {
                    div("card-body") {
                        state.userDetail?.stats?.let {
                            a("${Config.apibase}/users/id/${state.userDetail?.id ?: 0}/playlist", "_blank", "btn btn-secondary") {
                                attrs.attributes["download"] = ""
                                i("fas fa-list") { }
                                +"Playlist"
                            }

                            +"Maps: ${it.totalMaps}, Upvotes: ${it.totalUpvotes}, Downvotes: ${it.totalDownvotes}"
                            br {  }
                            +"Average BPM: ${it.avgBpm}, Average Score: ${it.avgScore}%, "
                            +"Average Duration: ${it.avgDuration.formatTime()}"
                            it.diffStats?.let { ds ->
                                br { }
                                b {
                                    +"Easy: "
                                }
                                +"${ds.easy}"
                                b {
                                    +", Normal: "
                                }
                                +"${ds.normal}"
                                b {
                                    +", Hard: "
                                }
                                +"${ds.hard}"
                                b {
                                    +", Expert: "
                                }
                                +"${ds.expert}"
                                b {
                                    +", Expert+: "
                                }
                                +"${ds.expertPlus}"
                            }
                        }
                        /*img("100 Maps", "https://cdn.discordapp.com/avatars/98334361564246016/01ade7513a63215bb7937d217b766da3.png", classes = "rounded-circle mx-2") {
                            attrs.width = "75"
                            attrs.height = "75"
                        }
                        img("100 Maps", "https://cdn.discordapp.com/avatars/98334361564246016/01ade7513a63215bb7937d217b766da3.png", classes = "rounded-circle mx-2") {
                            attrs.width = "75"
                            attrs.height = "75"
                        }
                        img("100 Maps", "https://cdn.discordapp.com/avatars/98334361564246016/01ade7513a63215bb7937d217b766da3.png", classes = "rounded-circle mx-2") {
                            attrs.width = "75"
                            attrs.height = "75"
                        }*/
                    }
                }
                if (props.userId == null) {
                    ul("nav nav-tabs") {
                        li("nav-item") {
                            a("#", classes = "nav-link" + if (!state.wip) " active" else "") {
                                attrs.onClickFunction = {
                                    it.preventDefault()
                                    setState {
                                        wip = false
                                    }
                                }
                                +"Published"
                            }
                        }
                        li("nav-item") {
                            a("#", classes = "nav-link" + if (state.wip) " active" else "") {
                                attrs.onClickFunction = {
                                    it.preventDefault()
                                    setState {
                                        wip = true
                                    }
                                }
                                +"WIP"
                            }
                        }
                    }
                }
            }
        }
        if (state.startup) {
            beatmapTable {
                user = props.userId ?: loggedInLocal ?: 0
                modal = modalRef
                wip = state.wip
                history = props.history
            }
        }
    }
}