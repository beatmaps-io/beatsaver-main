package io.beatmaps.pages

import io.ktor.server.html.Template
import kotlinx.html.BODY
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.hr
import kotlinx.html.id
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.span

class DMCAPageTemplate(val nonce: String?) : Template<BODY> {
    override fun BODY.apply() {
        main("container") {
            div("jumbotron") {
                h1 {
                    +"DMCA policy"
                }
                p {
                    +"This Digital Millennium Copyright Act policy (\"Policy\") applies to the beatsaver.com website (\"Website\" or \"Service\") and any of its related products and services (collectively, \"Services\") and outlines how this Website operator (\"Operator\", \"we\", \"us\" or \"our\") addresses copyright infringement notifications and how you (\"you\" or \"your\") may submit a copyright infringement complaint."
                }
                p {
                    +"Protection of intellectual property is of utmost importance to us and we ask our users and their authorized agents to do the same. It is our policy to expeditiously respond to clear notifications of alleged copyright infringement that comply with the United States Digital Millennium Copyright Act (\"DMCA\") of 1998, the text of which can be found at the U.S. Copyright Office "
                    a("https://www.copyright.gov", "_blank") {
                        +"website"
                    }
                    +"."
                }
                hr("my-4") {}
                h2 {
                    +"What to consider before submitting a copyright complaint"
                }
                p {
                    +"Please note that if you are unsure whether the material you are reporting is in fact infringing, you may wish to contact an attorney before filing a notification with us."
                }
                p {
                    +"The DMCA requires you to provide your personal information in the copyright infringement notification. If you are concerned about the privacy of your personal information, you may wish to "
                    a("https://www.copyrighted.com/professional-takedowns", "_blank") {
                        +"hire an agent"
                    }
                    +" to report infringing material for you."
                }
                hr("my-4") {}
                h2 {
                    +"Notifications of infringement"
                }
                p {
                    +"If you are a copyright owner or an agent thereof, and you believe that any material available on our Services infringes your copyrights, then you may submit a written copyright infringement notification (\"Notification\") using the contact details below pursuant to the DMCA. All such Notifications must comply with the DMCA requirements. You may refer to a "
                    a("https://www.websitepolicies.com/create/dmca-takedown-notice", "_blank") {
                        +"DMCA takedown notice generator"
                    }
                    +" or other similar services to avoid making mistake and ensure compliance of your Notification."
                }
                p {
                    +"Filing a DMCA complaint is the start of a pre-defined legal process. Your complaint will be reviewed for accuracy, validity, and completeness. If your complaint has satisfied these requirements, our response may include the removal or restriction of access to allegedly infringing material as well as a permanent termination of repeat infringers’ accounts."
                }
                p {
                    +"If we remove or restrict access to materials or terminate an account in response to a Notification of alleged infringement, we will make a good faith effort to contact the affected user with information concerning the removal or restriction of access."
                }
                p {
                    +"Notwithstanding anything to the contrary contained in any portion of this Policy, the Operator reserves the right to take no action upon receipt of a DMCA copyright infringement notification if it fails to comply with all the requirements of the DMCA for such notifications."
                }
                hr("my-4") {}
                h2 {
                    +"Changes and amendments"
                }
                p {
                    +"We reserve the right to modify this Policy or its terms relating to the Website and Services at any time, effective upon posting of an updated version of this Policy on the Website. When we do, we will revise the updated date at the bottom of this page."
                }
                hr("my-4") {}
                h2 {
                    +"Reporting copyright infringement"
                }
                p {
                    +"If you would like to notify us of the infringing material or activity, you may send an email to "
                    span("text-info") {
                        +"dmca@beatsaver.com"
                    }
                }
                hr("my-4") {}
                p("text-muted") {
                    +"This document was last updated on January 11, 2021"
                }
            }
        }
        div {
            id = "root"
        }
        jsTags(nonce)
    }
}
