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
                    listOf(
                        "Music distribution rights",
                        "Rights to the beatmap data (Notes and Lighting)",
                        "Rights to publish any additional content included in the uploaded zip",
                        "Permission to grant BeatSaver distribution rights to all files contained in the uploaded zip"
                    ).forEach {
                        li {
                            +it
                        }
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
                    +"You must declare truthfully whether automated note generation tools were used in the creation of the uploaded content. Misrepresenting your upload by declaring it as human-made when an external auto-generation tool such as BeatSage was used in all or part of the mapping process will result in the removal of the upload and a warning issued. Repeat infractions may lead to account restrictions and/or termination."
                }
                p {
                    +"Automated lighting generated from human placed notes such as those generated from Lightmap or lolighter are the only exception to this policy."
                }
                p {
                    +"Maps that contain AI or Auto-Generated Content may be subject to, at BeatSaver’s discretion, a 90-day retention period after which the content is removed."
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
                    +"The following apply to all user-generated content (beatmap upload, titles, descriptions, etc.):"
                }
                ul {
                    listOf(
                        "Do not advertise or mention any content creation services, monetization, or donation methods. (Ko-fi, Patreon, Commissions, etc.)",
                        "Harassment and Hate speech are not allowed",
                        "Human (or animated) bodies are not inherently offensive. Cover art should stick to “PG13-rated” themes and imagery to remain fully visible",
                        "Overtly sexual and excessively violent imagery (\"R-rated\"), such as profile pictures or a map’s cover and any additional images included in the zip, needs to be censored or blurred",
                        "Explicit Adult content (18+ only) is prohibited",
                        "Do not distribute viruses, malware, or phish others by any means such as exploits or links"
                    ).forEach {
                        li {
                            +it
                        }
                    }
                }
                p {
                    +"The following apply to the content in a beatmap zip upload, map title, and description:"
                }
                ul {
                    listOf(
                        "Explicit lyrics or profanities in song audio or map descriptions are only allowed if they are not harmful to the mentioned race, gender, nationality, sexuality, etc. in their intent",
                        "Map titles or descriptions should be free of excessively sexual or violent language"
                    ).forEach {
                        li {
                            +it
                        }
                    }
                }
                p {
                    +"The following apply to the content in user-generated reviews / replies:"
                }
                ul {
                    listOf(
                        "Reviews exist to help other users find content that might appeal to them. They should be well-intentioned and constructive with the recommendation selection generally matching the body of the review.",
                        "Do not leave reviews on your own maps (self-reviews) or maps that you have not played",
                        "Do not use personal attacks and/or harassment against mappers or other community members. Remember that there are real people on the other side of the maps on this site.",
                        "Reviews that only reference the song audio and not the map (\"This song is terrible\", \"I hate anime\"), where it's clear that the reviewer was attempting a map that was too difficult for their skill level, or that only include random characters/keyboard spam will be removed."
                    ).forEach {
                        li {
                            +it
                        }
                    }
                }
                p {
                    +"Content that violates these terms will be edited or removed and repeated violation may result in the suspension of review privileges or account access."
                }
                p {
                    +"If you discover content you believe violates these guidelines you can submit a report on the site or in the `#report-content` channel in the BeatSaver Discord. The BeatSaver team has the final say in determining what does and does not violate these guidelines."
                }
                hr("my-4") {}
                h3 {
                    +"Limits"
                }
                p {
                    +"Established limits are as follows:"
                }
                ul {
                    listOf(
                        "15 MiB maximum zip file size",
                        "50 MiB maximum size for an individual file in unzipped form",
                        "Map revision updates for unpublished works in progress are limited to two uploads every 12 hours"
                    ).forEach {
                        li {
                            +it
                        }
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
                    +"Prohibited Behaviors"
                }
                p {
                    +"Activities during your interaction with the site should not be abusive to the site or community. Behaviors in violation of this include, but are not limited to"
                }
                ul {
                    listOf(
                        "Excessive beatmap uploads and/or deletions",
                        "Creating multiple accounts to manipulate the site's review system ratings",
                        "Posting misleading reviews",
                        "\"Reserving\" map keys / ids",
                        "Commercializing content available on the site"
                    ).forEach {
                        li {
                            +it
                        }
                    }
                }
                h3 {
                    +"Prohibited Usernames"
                }
                p {
                    +"Usernames that are not permitted are as follows:"
                }
                ul {
                    listOf(
                        "Impersonation of another user or famous person",
                        "Imply they represent BeatSaver or organization they are not affiliated with",
                        "Imply they have special permissions such as site administrator, staff, moderator, etc.",
                        "Contain profanities in any language",
                        "Intended to offend a particular race, gender, nationality, sexuality, etc."
                    ).forEach {
                        li {
                            +it
                        }
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
                    +"Reasons that would result in your account being restricted or terminated include, but are not limited to:"
                }
                ul {
                    listOf(
                        "Excessive content theft",
                        "Multiple warnings of policy violations",
                        "Malicious uploads, map titles, descriptions, or activity",
                        "Excessive policy violations"
                    ).forEach {
                        li {
                            +it
                        }
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
                    +"This document was last updated on January 6, 2025"
                }
            }
        }
        div {
            id = "root"
        }
        jsTags()
    }
}
