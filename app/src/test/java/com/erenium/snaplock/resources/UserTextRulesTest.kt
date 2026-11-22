package com.erenium.snaplock.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class UserTextRulesTest {
    private val english = File("src/main/res/values/strings.xml")
    private val turkish = File("src/main/res/values-tr/strings.xml")

    private fun elements(file: File): List<Element> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("*")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun isLeaf(element: Element): Boolean =
        (0 until element.childNodes.length).none { element.childNodes.item(it).nodeType == Node.ELEMENT_NODE }

    private fun resourceNames(file: File): Set<String> =
        elements(file)
            .filter { it.parentNode === it.ownerDocument.documentElement }
            .map { it.tagName + ":" + it.getAttribute("name") }
            .toSet()

    @Test
    fun userFacingTextsHaveNoSemicolons() {
        listOf(english, turkish).forEach { file ->
            elements(file).filter(::isLeaf).forEach {
                assertTrue("${file.path}: ${it.textContent.take(60)}", ';' !in it.textContent)
            }
        }
    }

    @Test
    fun bothLanguagesTranslateTheSameResources() {
        assertEquals(resourceNames(english), resourceNames(turkish))
    }
}
