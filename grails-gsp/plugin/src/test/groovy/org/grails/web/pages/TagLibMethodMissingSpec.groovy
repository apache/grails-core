package org.grails.web.pages

import grails.artefact.Artefact
import grails.test.AbstractGrailsEnvChangingSpec
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Unroll

class TagLibMethodMissingSpec extends AbstractGrailsEnvChangingSpec implements TagLibUnitTest<TagLibMethodMissingTagLib> {

    def setup() {
        mockArtefact(TagLibMethodMissingBTagLib)
    }

    @Unroll("template #template / expected content: #expectedContent / env: #grailsEnv")
    def "Test tag library method missing handling"(template, expectedContent, grailsEnv) {
        when:'We call a tag that invokes an existing tag in other TagLib'
            changeGrailsEnv(grailsEnv)
            def content = applyTemplate(template)
            def content2 = applyTemplate(template)
        then:"The expected result is rendered and when we call it a second time the cached version is used so we test that too"
            content == expectedContent
            content == content2
        where:
            [template, expectedContent, grailsEnv] <<  createCombinationsForGrailsEnvs([
                ['<a:zeroArguments />', 'ab'],
                ['${a.zeroArguments()}', 'ab'],
                ['a${g.renderErrors()}b', 'ab'],
                ['a${renderErrors()}b', 'ab'],
                ['<a:myLink/>', '<a href="/one/two"></a><a href="/foo/bar">Hello World</a>'],
                ['<a:myLink2/>', '<a href="/one/two"></a><a href="/foo/bar">Hello World</a>'],
                ['<a:bodyTag>hello</a:bodyTag>', 'hellohellohellohello'],
                ['hello ${other.printBody{"world"}}!', 'hello world!']
            ])
    }
}
@Artefact("TagLibrary")
class TagLibMethodMissingTagLib {
    static namespace = "a"

    def zeroArguments = { attrs, body ->
        out << 'a'
        g.renderErrors()
        renderErrors()
        out << 'b'
    }

    def myLink = { attrs, body ->
       out << g.link(controller:"one", action:"two")
       out << g.link(controller: "foo", action:"bar") {
           setLink(attrs, "World")
       }
    }

    def myLink2 = { attrs, body ->
        out << link(controller:"one", action:"two")
        out << link(controller: "foo", action:"bar") {
            setLink(attrs, "World")
        }
    }

    def bodyTag = { attrs, body ->
        out << other.printBody(body)
        out << other.printBody([:], body() as String)
        out << other.printBody([:], body())
        out << other.printBody{body()}
    }

    private setLink(attrs, name) {
        "Hello $name"
    }
}

@Artefact("TagLib")
class TagLibMethodMissingBTagLib {
    static namespace = "other"

    Closure printBody = { attrs, body ->
        out << body()
    }
}