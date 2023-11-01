package io.beatmaps.maps

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.UserDetail
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLInputElement
import react.Props
import react.dom.a
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.img
import react.dom.input
import react.dom.span
import react.fc
import react.useRef
import react.useState

external interface UserSearchProps : Props {
    var callback: ((UserDetail) -> Unit)?
    var excludeUsers: List<Int>?
    var disabled: Boolean?
    var addText: String?
}

val userSearch = fc<UserSearchProps> { props ->
    val (foundUsers, setFoundUsers) = useState<List<UserDetail>?>(null)
    val inputRef = useRef<HTMLInputElement>()

    form("", classes = "search") {
        input(InputType.search, classes = "form-control") {
            attrs.id = "collaborators"
            attrs.placeholder = "Add users"
            attrs.disabled = props.disabled == true
            ref = inputRef
        }

        button(type = ButtonType.submit, classes = "btn btn-primary") {
            attrs.onClickFunction = {
                it.preventDefault()
                inputRef.current?.value?.ifBlank { null }?.let { q ->
                    Axios.get<List<UserDetail>>(
                        "${Config.apibase}/users/search?q=$q",
                        generateConfig<String, List<UserDetail>>()
                    ).then { res -> setFoundUsers(res.data) }
                } ?: setFoundUsers(listOf())
            }
            +"Search"
        }
    }

    foundUsers?.filter {
        props.excludeUsers?.contains(it.id) != true
    }?.let { users ->
        div("search-results") {
            if (users.isNotEmpty()) {
                users.forEach { user ->
                    div("list-group-item user") {
                        span {
                            img(user.name, user.avatar) { }
                            +user.name
                        }

                        a(classes = "btn btn-success btn-sm") {
                            attrs.onClickFunction = {
                                props.callback?.invoke(user)
                            }
                            +(props.addText ?: "Invite")
                        }
                    }
                }
            } else {
                div("list-group-item text-center") {
                    +"No results"
                }
            }
        }
    }
}
