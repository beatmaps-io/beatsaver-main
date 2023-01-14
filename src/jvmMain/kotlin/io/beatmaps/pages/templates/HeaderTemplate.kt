package io.beatmaps.pages.templates

import io.beatmaps.api.ReviewConstants
import io.beatmaps.common.Config
import io.beatmaps.login.Session
import io.ktor.server.html.Template
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

val bannerEnabled = System.getenv("BANNER_ENABLED") == "true"

class HeaderTemplate(private val s: Session?) : Template<FlowContent> {
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
                                a("/test", classes = "nav-link auto-router") {
                                    +"Testplays"
                                }
                            }
                        }
                        if (s?.admin == true || (s?.curator == true && ReviewConstants.COMMENTS_ENABLED)) {
                            li("nav-item dropdown") {
                                a("#", classes = "nav-link dropdown-toggle") {
                                    +"Mod"
                                }
                                div("dropdown-menu") {
                                    if (s.admin) {
                                        a("/modlog", classes = "dropdown-item auto-router") {
                                            +"ModLog"
                                        }
                                    }
                                    a("/modreview", classes = "dropdown-item auto-router") {
                                        +"Reviews"
                                    }
                                }
                            }
                        }
                        li("nav-item") {
                            a("/mappers", classes = "nav-link auto-router") {
                                +"Mappers"
                            }
                        }
                        li("nav-item") {
                            a("/playlists", classes = "nav-link auto-router") {
                                +"Playlists"
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
                                a("${Config.apiBase(true)}/docs/", classes = "dropdown-item") {
                                    +"API Docs"
                                }
                            }
                        }
                    }

                    ul("navbar-nav") {
                        if (s == null) {
                            li("nav-item") {
                                a("/login", classes = "nav-link auto-router") {
                                    +"Login"
                                }
                            }
                        } else {
                            li("nav-item") {
                                a("/alerts", classes = "nav-link auto-router") {
                                    i("fas fa-bell")
                                    small("alert-count") {
                                        id = "alert-count"
                                        val count = s.alerts ?: 0
                                        attributes["data-count"] = count.toString()
                                        if (count < 10) +count.toString() else +"9+"
                                    }
                                }
                            }
                            li("nav-item dropdown") {
                                a("#", classes = "nav-link dropdown-toggle no-caret") {
                                    i("fas fa-plus-square")
                                }
                                div("dropdown-menu") {
                                    if (!s.suspended) {
                                        a("/upload", classes = "dropdown-item auto-router") {
                                            +"Upload Map"
                                        }
                                        div("dropdown-divider") {}
                                    }
                                    a("/playlists/new", classes = "dropdown-item auto-router") {
                                        +"Create Playlist"
                                    }
                                }
                            }
                            li("nav-item dropdown") {
                                a("/profile", classes = "nav-link dropdown-toggle") {
                                    +(s.uniqueName ?: s.userName)
                                }
                                div("dropdown-menu") {
                                    a("/profile", classes = "dropdown-item auto-router") {
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
        if (bannerEnabled) {
            div("navbar navbar-expand-lg navbar-light bg-warning") {
                id = "site-notice"
                div("container flex-nowrap") {
                    div("navbar-text flex-grow-1 text-center") {
                        +"VOTE NOW for 2022 BeastSaber Mapping Award winners! Voting is open from January 1-14. "
                        a("https://forms.gle/PuTdd2SWGYCgYFp67", target = "_blank") {
                            +"Vote here"
                        }
                    }
                    button(type = ButtonType.button, classes = "btn-close btn-close-white") { }
                }
            }
        }
    }
}
