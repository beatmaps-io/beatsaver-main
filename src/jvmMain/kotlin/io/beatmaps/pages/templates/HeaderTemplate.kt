package io.beatmaps.pages.templates

import io.beatmaps.login.Session
import io.ktor.html.Template
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.span
import kotlinx.html.ul

class HeaderTemplate(private val s: Session?) : Template<FlowContent> {
    override fun FlowContent.apply() {
        nav("navbar navbar-expand-lg fixed-top navbar-dark bg-primary") {
            div("container") {
                a("/", classes = "navbar-brand") {
                    id = "home-link"
                    +"BeatSaver"
                }
                button(classes = "navbar-toggler", type = ButtonType.button) {
                    id = "navbar-button"
                    attributes["data-toggle"] = "collapse"
                    attributes["data-target"] = "navbar"
                    attributes["aria-controls"] = "navbar"
                    attributes["aria-expanded"] = "false"
                    attributes["aria-label"] = "Toggle navigation"
                    span("navbar-toggler-icon") {}
                }

                div("collapse navbar-collapse") {
                    id = "navbar"

                    ul("navbar-nav mr-auto") {
                        if (s?.testplay == true) {
                            li("nav-item") {
                                a("/test", classes = "nav-link") {
                                    +"Testplays"
                                }
                            }
                        }
                        li("nav-item") {
                            a("/mappers", classes = "nav-link") {
                                +"Mappers"
                            }
                        }
                        li("nav-item dropdown") {
                            a("#", classes = "nav-link dropdown-toggle") {
                                +"Help"
                            }
                            div("dropdown-menu") {
                                a("https://bsmg.wiki", classes = "dropdown-item") {
                                    +"BSMG Wiki"
                                }
                                a("https://discord.gg/beatsabermods", classes = "dropdown-item") {
                                    +"BSMG Discord"
                                }
                                div("dropdown-divider") {}
                                a("/policy/dmca", classes = "dropdown-item") {
                                    +"DMCA Policy"
                                }
                            }
                        }
                    }

                    ul("navbar-nav") {
                        if (s == null) {
                            li("nav-item") {
                                a("/login", classes = "nav-link") {
                                    +"Login"
                                }
                            }
                        } else {
                            li("nav-item") {
                                a("/upload", classes = "nav-link") {
                                    +"Upload"
                                }
                            }
                            li("nav-item dropdown") {
                                a("/profile", classes = "nav-link dropdown-toggle") {
                                    +(s.uniqueName ?: s.userName)
                                }
                                div("dropdown-menu") {
                                    a("/profile", classes = "dropdown-item") {
                                        +"Profile"
                                    }
                                    a("/alerts", classes = "dropdown-item") {
                                        +"Alerts"
                                    }
                                    if (s.steamId == null) {
                                        a("/steam", classes = "dropdown-item") {
                                            +"Link steam account"
                                        }
                                    }
                                    div("dropdown-divider") {}
                                    a("/logout", classes = "dropdown-item") {
                                        +"Logout"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
