package org.grails.web.taglib

import grails.util.GrailsUtil
import org.junit.jupiter.api.Test

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class PluginTagLibTests extends AbstractGrailsTagTests {

    @Test
    void testPluginpath() {
        def template = '<plugin:path name="controllers" />'
        def grailsVersion = GrailsUtil.getGrailsVersion()
        assertOutputEquals("/plugins/controllers-$grailsVersion", template)
    }

    @Test
    void testPluginTagLib() {
        def template = '<plugin:isAvailable name="core">printme</plugin:isAvailable>'
        assertOutputEquals "printme", template

        template = '<plugin:isNotAvailable name="core">printme</plugin:isNotAvailable>'
        assertOutputEquals "", template

        template = '<plugin:isNotAvailable name="hibernate">printme</plugin:isNotAvailable>'
        assertOutputEquals "printme", template
    }
}
