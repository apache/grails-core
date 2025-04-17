package org.grails.web.taglib

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue


class FormRenderingTagLibTests extends AbstractGrailsTagTests {

    @Test
    void testTimeZoneSelect() {
        def template = '<g:timeZoneSelect name="foo" locale="en_US"/>'

        def engine = appCtx.groovyPagesTemplateEngine

        assert engine
        def t = engine.createTemplate(template, "test_"+ System.currentTimeMillis())

        def w = t.make()

        def sw = new StringWriter()
        def out = new PrintWriter(sw)
        webRequest.out = out
        w.writeTo(out)

        def output = sw.toString()
        println output

        assertTrue output.startsWith('<select name="foo" id="foo" >')
        assertTrue output.contains('<option value="Pacific/Galapagos" >GALT, Galapagos Time -6:0.0 [Pacific/Galapagos]</option>')
        assertTrue (output.contains('<option value="US/Central" >CDT, Central Daylight Time -6:0.0 [US/Central]</option>') || output.contains('<option value="US/Central" >CST, Central Standard Time -6:0.0 [US/Central]</option>'))
        assertTrue output.endsWith('</select>')
    }

     void assertOutputEquals(expected, template, params = [:]) {
        def engine = appCtx.groovyPagesTemplateEngine

        assert engine
        def t = engine.createTemplate(template, "test_"+ System.currentTimeMillis())

        def w = t.make(params)

        def sw = new StringWriter()
        def out = new PrintWriter(sw)
        webRequest.out = out
        w.writeTo(out)

        assertEquals expected, sw.toString()
    }
}
