package io.beatmaps.browser.page

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.FilePayload
import io.beatmaps.common.MapTag

class UploadPage(page: Page) : PageBase(page) {
    val name = element("#name")
    val description = element("#description")
    fun tag(tag: MapTag) = element(".tags .badge-${tag.type.color}[title='${tag.human}']")
    val madeWithoutAI = element("#beatsage-no + label")
    val madeWithAI = element("#beatsage-yes + label")
    val dropzone = element(".dropzone")

    fun uploadMap(bytes: ByteArray) {
        val fileChooser = page.waitForFileChooser { dropzone.click() }
        fileChooser.setFiles(FilePayload("map.zip", "application/zip", bytes))
    }
}
