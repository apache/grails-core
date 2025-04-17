package org.grails.web.pages

import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification
import grails.artefact.Artefact

/**
 *
 */
class AliasedTagPropertySpec extends Specification implements TagLibUnitTest<AliasedTagLib> {

    def "Test that a property assigned to a tag is also a tag"() {
        when:"We call a regular tag"
            def content = applyTemplate "<a:hello />"

        then:"the tag is invoked as per normal"
            content == "Hello"

        when:"We call an aliased version of the tag "
            content = applyTemplate '<a:hola spanish="true"/>'

        then:"The alias is also a valid tag"
            content == "Hola"

    }
}

@Artefact("TagLibrary")
class AliasedTagLib {
    static namespace = "a"

    def hello = { attrs, body ->
        out << (attrs.spanish ? "Hola" : "Hello")
    }

    def hola = hello
}
