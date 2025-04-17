package org.grails.web.taglib

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class NamespacedTagAndActionConflictTests extends AbstractGrailsTagTests {

    @BeforeEach
    protected void onSetUp() {
        
        gcl.parseClass '''
class FeedsTagLib {
    static namespace = "feed"
    def rss = {
        out << "rss feed"
    }
}
@grails.artefact.Artefact('Controller')
class TestController {
    def feed = {
        "foo"
    }
    def test = {
        println "FEED IS $feed"
        // should favour local action of feed tag
        assert feed instanceof Closure
        render feed()
    }
}
'''
    }

    @Test
    void testTagLibNamespaceAndActionConflict() {
        def controllerClass = ga.getControllerClass("TestController").clazz

        def controller = controllerClass.newInstance()

        controller.test()

        assertEquals "foo", response.contentAsString
    }
}
