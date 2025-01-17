package io.beatmaps.maps.collab

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.UserDetail
import io.beatmaps.api.UserSearchResponse
import org.w3c.dom.HTMLInputElement
import react.ActionOrString
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.span
import react.fc
import react.useRef
import react.useState
import web.cssom.ClassName
import web.form.FormData
import web.html.ButtonType
import web.html.InputType
import kotlin.js.Promise

external interface UserSearchProps : Props {
    var callback: ((UserDetail) -> Unit)?
    var excludeUsers: List<Int>?
    var disabled: Boolean?
    var addText: String?
}

val userSearch = fc<UserSearchProps>("userSearch") { props ->
    val (foundUsers, setFoundUsers) = useState<List<UserDetail>?>(null)
    val (loading, setLoading) = useState(false)
    val inputRef = useRef<HTMLInputElement>()

    form {
        attrs.className = ClassName("search")
        input {
            attrs.type = InputType.search
            attrs.className = ClassName("form-control")
            attrs.id = "collaborators"
            attrs.placeholder = "Add users"
            attrs.disabled = props.disabled == true
            ref = inputRef
        }

        button {
            attrs.type = ButtonType.submit
            attrs.className = ClassName("btn btn-primary")
            attrs.disabled = loading
            attrs.onClick = {
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

    foundUsers?.filter {
        props.excludeUsers?.contains(it.id) != true
    }?.take(10)?.let { users ->
        div {
            attrs.className = ClassName("search-results")
            if (users.isNotEmpty()) {
                users.forEach { user ->
                    div {
                        attrs.className = ClassName("list-group-item user")
                        span {
                            img {
                                attrs.alt = user.name
                                attrs.src = user.avatar
                            }
                            +user.name
                        }

                        a {
                            attrs.className = ClassName("btn btn-success btn-sm")
                            attrs.onClick = {
                                props.callback?.invoke(user)
                            }
                            +(props.addText ?: "Invite")
                        }
                    }
                }
            } else {
                div {
                    attrs.className = ClassName("list-group-item text-center")
                    +"No results"
                }
            }
        }
    }
}
