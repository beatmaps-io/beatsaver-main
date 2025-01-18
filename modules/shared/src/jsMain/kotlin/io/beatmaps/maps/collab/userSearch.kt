package io.beatmaps.maps.collab

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.UserDetail
import io.beatmaps.api.UserSearchResponse
import io.beatmaps.util.fcmemo
import react.ChildrenBuilder
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.span
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.ButtonType
import web.html.HTMLInputElement
import web.html.InputType
import kotlin.js.Promise

external interface UserSearchProps : Props {
    var callback: ((UserDetail) -> Unit)?
    var excludeUsers: List<Int>?
    var disabled: Boolean?
    var noForm: Boolean?
    var addText: String?
}

val userSearch = fcmemo<UserSearchProps>("userSearch") { props ->
    val (foundUsers, setFoundUsers) = useState<List<UserDetail>?>(null)
    val (loading, setLoading) = useState(false)
    val inputRef = useRef<HTMLInputElement>()

    fun ChildrenBuilder.formContent() {
        input {
            type = InputType.search
            className = ClassName("form-control")
            id = "collaborators"
            placeholder = "Add users"
            disabled = props.disabled == true
            ref = inputRef
        }

        button {
            type = ButtonType.submit
            className = ClassName("btn btn-primary")
            disabled = loading
            onClick = {
                it.preventDefault()
                setLoading(true)
                val q = inputRef.current?.value

                if (q.isNullOrBlank()) {
                    setFoundUsers(listOf())
                    Promise.resolve(null)
                } else {
                    Axios.get<UserSearchResponse>(
                        "${Config.apibase}/users/search/0?q=$q",
                        generateConfig<String, UserSearchResponse>()
                    ).then { res -> setFoundUsers(res.data.docs) }
                }.finally {
                    setLoading(false)
                }
            }
            +"Search"
        }
    }

    if (props.noForm == true) {
        div {
            className = ClassName("search")
            formContent()
        }
    } else {
        form {
            className = ClassName("search")
            formContent()
        }
    }

    foundUsers?.filter {
        props.excludeUsers?.contains(it.id) != true
    }?.take(10)?.let { users ->
        div {
            className = ClassName("search-results")
            if (users.isNotEmpty()) {
                users.forEach { user ->
                    div {
                        className = ClassName("list-group-item user")
                        span {
                            img {
                                alt = user.name
                                src = user.avatar
                            }
                            +user.name
                        }

                        a {
                            className = ClassName("btn btn-success btn-sm")
                            onClick = {
                                props.callback?.invoke(user)
                            }
                            +(props.addText ?: "Invite")
                        }
                    }
                }
            } else {
                div {
                    className = ClassName("list-group-item text-center")
                    +"No results"
                }
            }
        }
    }
}
