package org.grails.web.mapping

import grails.artefact.Artefact
import grails.testing.web.UrlMappingsUnitTest
import spock.lang.Specification

class RootUrlMappingTests extends Specification implements UrlMappingsUnitTest<StoreUrlMappings> {


    def testMappingToController() {
        when:
        def template = '<g:link controller="store">Show the time !</g:link>'
        String output = applyTemplate(template)

        then:
        output == '<a href="/">Show the time !</a>'
    }

}

@Artefact("UrlMappings")
class StoreUrlMappings {
    static mappings = {
        "/"(controller:"store")
        "/$controller/$action?/$id?"{
            constraints {
                // apply constraints here
            }
        }
        "/"(view:"/index")
        "500"(view:'/error')
    }
}

@Artefact("Controller")
class StoreController {

    def index = { }

    def showTime = {
        render "${new Date()}"
    }
}
