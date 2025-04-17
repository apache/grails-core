package org.grails.web.pages

import grails.test.AbstractGrailsEnvChangingSpec
import grails.artefact.Artefact
import grails.testing.web.taglib.TagLibUnitTest

class OptionalTagBodySpec extends AbstractGrailsEnvChangingSpec implements TagLibUnitTest<CustomTagLib> {
    def "Test that the existence of a body can be tested with groovy truth"(grailsEnv) {
        when:
            changeGrailsEnv(grailsEnv)
            def content = applyTemplate("<a:myBody />")
            def content2 = applyTemplate("<a:myBody>Hello</a:myBody>")
        then:
            content == 'nobody'
            content2 == 'Hello'
        where:
            grailsEnv << AbstractGrailsEnvChangingSpec.grailsEnvs
    }
}

@Artefact("TagLibrary")
class CustomTagLib {

    static namespace = "a"

    def myBody = { attrs, body ->
        if (body) {
            out << body()
        } else {
            out << 'nobody'
        }
    }
}
