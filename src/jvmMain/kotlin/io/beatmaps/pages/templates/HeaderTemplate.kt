package io.beatmaps.pages.templates

import io.beatmaps.common.Config
import io.beatmaps.login.Session
import io.ktor.html.Template
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.i
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.small
import kotlinx.html.span
import kotlinx.html.title
import kotlinx.html.ul

class HeaderTemplate(private val s: Session?, private val showAlerts: Boolean) : Template<FlowContent> {
    override fun FlowContent.apply() {
        nav("navbar navbar-expand-lg fixed-top navbar-dark bg-primary") {
            div("container") {
                a("/", classes = "navbar-brand") {
                    id = "home-link"
                    img("BeatSaver", "/static/beatsaver_logo.svg") {
                        title = "BeatSaver"
                        height = "23px"
                    }
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

                    ul("navbar-nav me-auto") {
                        if (s?.testplay == true) {
                            li("nav-item") {
                                a("/test", classes = "nav-link") {
                                    +"Testplays"
                                }
                            }
                        }
                        if (s?.isAdmin() == true) {
                            li("nav-item") {
                                a("/modlog", classes = "nav-link") {
                                    id = "modlog-link"
                                    +"ModLog"
                                }
                            }
                        }
                        li("nav-item") {
                            a("/mappers", classes = "nav-link") {
                                id = "mappers-link"
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
                                a("https://discord.gg/rjVDapkMmj", classes = "dropdown-item") {
                                    +"BeatSaver Discord"
                                }
                                div("dropdown-divider") {}
                                a("/policy/dmca", classes = "dropdown-item") {
                                    +"DMCA Policy"
                                }
                                a("/policy/tos", classes = "dropdown-item") {
                                    +"Terms of Service"
                                }
                                a("/policy/privacy", classes = "dropdown-item") {
                                    +"Privacy Policy"
                                }
                                div("dropdown-divider") {}
                                a("https://github.com/beatmaps-io/beatsaver-main", classes = "dropdown-item") {
                                    +"GitHub"
                                }
                                a("${Config.apiremotebase}/docs/", classes = "dropdown-item") {
                                    +"API Docs"
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
                                a("/profile#alerts", classes = "nav-link") {
                                    i("fas fa-bell")
                                    if (s.alerts != null && s.alerts > 0 && showAlerts) {
                                        small("alert-count") {
                                            if (s.alerts < 10) +s.alerts.toString() else +"9+"
                                        }
                                    }
                                }
                            }
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
