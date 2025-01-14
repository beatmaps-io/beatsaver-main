import io.beatmaps.user.changeEmailPage
import io.beatmaps.user.forgotPage
import io.beatmaps.user.loginPage
import io.beatmaps.user.oauth.authorizePage
import io.beatmaps.user.pickUsernamePage
import io.beatmaps.user.resetPage
import io.beatmaps.user.signupPage

@JsExport
val changeEmail = changeEmailPage

@JsExport
val forgot = forgotPage

@JsExport
val login = loginPage

@JsExport
val register = signupPage

@JsExport
val username = pickUsernamePage

@JsExport
val reset = resetPage

@JsExport
val quest = io.beatmaps.quest.quest

@JsExport
val authorize = authorizePage
