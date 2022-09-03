package io.beatmaps.pages

import io.ktor.server.html.Template
import kotlinx.html.BODY
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.hr
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.ul

class TOSPageTemplate : Template<BODY> {
    override fun BODY.apply() {
        main("container") {
            div("jumbotron") {
                h1 {
                    +"Content Policy"
                }
                hr("my-4") {}
                h3 {
                    +"Beatmap Rights"
                }
                p {
                    +"By submitting a beatmap to the site you confirm that you have all applicable rights to publish the beatmap, including but not limited to:"
                }
                ul {
                    li {
                        +"Music distribution rights"
                    }
                    li {
                        +"Rights to the beatmap data (Notes and Lighting)"
                    }
                    li {
                        +"Rights to publish any additional content included in the uploaded zip"
                    }
                    li {
                        +"Permission to grant BeatSaver distribution rights to all files contained in the uploaded zip"
                    }
                }
                p {
                    +"Republishing or use of another user's content posted on BeatSaver including derivative work without explicit written permission is in violation of this policy. Crediting users' work in the description is not enough."
                }
                p {
                    +"If you are a copyright owner or an agent thereof, and you believe that any material available on our Services infringes your copyrights, please visit our DMCA Policy for more information on filing a notification of infringement."
                }
                hr("my-4") {}
                h3 {
                    +"AI or Auto-Generated Content"
                }
                p {
                    +"You must declare truthfully whether automated note generation tools were used in the creation of the uploaded content. Misrepresenting your upload by declaring it as human-made when an external auto-generation tool such as BeatSage was used in all or part of the mapping process will result in the removal of the upload and a warning issued. Automated lighting generated from human placed notes such as those generated from Lightmap or lolighter are the only exception."
                }
                hr("my-4") {}
                h3 {
                    +"Works In Progress"
                }
                p {
                    +"You should only upload completed maps (from start to finish) to BeatSaver. You should not use the site to get testplays, send the map to other users directly or use the #testplays channel in the BSMG discord before uploading."
                }
                hr("my-4") {}
                h3 {
                    +"Content Guidelines"
                }
                p {
                    +"The following apply to the content in a beatmap zip upload, map title, and description:"
                }
                ul {
                    li {
                        +"Do not advertise or mention any content creation services, monetization, or donation methods. (Ko-fi, Patreon, Commissions, etc.)"
                    }
                    li {
                        +"Harassment and Hate speech are not allowed"
                    }
                    li {
                        +"Explicit lyrics or profanities in song audio are only allowed if they are not harmful to the mentioned race, gender, nationality, sexuality, etc. in their intent."
                    }
                    li {
                        +"Explicit imagery in the map’s cover and any additional images included in the zip needs to be censored."
                    }
                    li {
                        +"Do not distribute viruses, malware, or phish others by any means such as exploits or links."
                    }
                }
                hr("my-4") {}
                h3 {
                    +"Limits"
                }
                p {
                    +"Established limits are as follows:"
                }
                ul {
                    li {
                        +"15 MiB maximum zip file size"
                    }
                    li {
                        +"50 MiB maximum size for an individual file in unzipped form"
                    }
                    li {
                        +"Map revision updates for unpublished works in progress are limited to two uploads every 12 hours"
                    }
                }
                p {
                    +"Attempts to circumvent these limits may result in further restrictions or account termination."
                }
                hr("my-4") {}
                h1 {
                    +"Account Policy"
                }
                hr("my-4") {}
                h3 {
                    +"Prohibited Usernames"
                }
                p {
                    +"Usernames that are not permitted are as follows:"
                }
                ul {
                    li {
                        +"Impersonation of another user or famous person"
                    }
                    li {
                        +"Imply they represent BeatSaver or organization they are not affiliated with"
                    }
                    li {
                        +"Imply they have special permissions such as site administrator, staff, moderator, etc."
                    }
                    li {
                        +"Contain profanities in any language"
                    }
                    li {
                        +"Intended to offend a particular race, gender, nationality, sexuality, etc."
                    }
                }
                p {
                    +"Accounts in violation of this policy are subject to a name change, the account being disabled, or removal from the service.\n"
                }
                hr("my-4") {}
                h3 {
                    +"Account Restrictions and Termination"
                }
                p {
                    +"BeatSaver may restrict or terminate your account and access to the service at any time and for any reason. You may, as the result of termination, lose your account and all information and data associated."
                }
                p {
                    +"Some reasons that would result in your account being restricted or terminated are:"
                }
                ul {
                    li {
                        +"Excessive content theft"
                    }
                    li {
                        +"Multiple warnings of policy violations"
                    }
                    li {
                        +"Malicious uploads, map titles, or descriptions"
                    }
                    li {
                        +"Excessive policy violations"
                    }
                }
                hr("my-4") {}
                h3 {
                    +"Blockchain Technology"
                }
                p {
                    +"Using this service as part of any blockchain technology is not allowed. This includes, but is not limited to, minting content hosted on the site in a non-fungible token (NFT). Repeated violations are subject to content removal, account restrictions, or termination."
                }
                hr("my-4") {}
                h1 {
                    +"Changes and amendments"
                }
                p {
                    +"We reserve the right to modify these Policies or its terms relating to the Website and Services at any time, effective upon posting of an updated version of this Policy on the Website. When we do, we will revise the updated date at the bottom of this page."
                }
                hr("my-4") {}
                p("text-muted") {
                    +"This document was last updated on January 19, 2022"
                }
            }
        }
        div {
            id = "root"
        }
        script(src = "/static/modules.js") {}
        script(src = "/static/output.js") {}
    }
}
