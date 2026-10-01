package io.github.rolfwessels.cordlet.widget

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guard the drawable assets Glance displays in RemoteViews (which JVM tests cannot render). */
class WidgetArtworkTest {
    private val drawableDir = File("src/main/res/drawable")
    private val androidNs = "http://schemas.android.com/apk/res/android"

    @Test fun panelHasRoundedOutlineAndDarkFill() {
        val shape = xml("cordlet_widget_panel.xml").documentElement
        assertEquals("shape", shape.tagName)
        assertEquals("#181D27", resolveColor(shape.getElementsByTagName("solid").item(0).attributes.getNamedItemNS(androidNs, "color").nodeValue))
        assertEquals("16dp", shape.getElementsByTagName("corners").item(0).attributes.getNamedItemNS(androidNs, "radius").nodeValue)
        assertEquals("#2A3140", resolveColor(shape.getElementsByTagName("stroke").item(0).attributes.getNamedItemNS(androidNs, "color").nodeValue))
    }

    @Test fun microphoneIsAVectorGlyphNotAnEmoji() {
        val vector = xml("cordlet_widget_microphone.xml").documentElement
        assertEquals("vector", vector.tagName)
        assertTrue(vector.getElementsByTagName("path").length > 0)
        assertEquals("20dp", vector.getAttributeNS(androidNs, "width"))
    }

    @Test fun widgetUsesSharedPaletteResources() {
        val panel = xml("cordlet_widget_panel.xml").documentElement
        assertEquals("@color/cordlet_panel", panel.getElementsByTagName("solid").item(0).attributes.getNamedItemNS(androidNs, "color").nodeValue)
        assertEquals("@color/cordlet_border", panel.getElementsByTagName("stroke").item(0).attributes.getNamedItemNS(androidNs, "color").nodeValue)
    }

    private fun resolveColor(value: String): String {
        if (!value.startsWith("@color/")) return value
        val colors = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/colors.xml")).getElementsByTagName("color")
        return (0 until colors.length).map { colors.item(it) }.first {
            it.attributes.getNamedItem("name").nodeValue == value.removePrefix("@color/")
        }.textContent
    }

    private fun xml(name: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File(drawableDir, name))
}
