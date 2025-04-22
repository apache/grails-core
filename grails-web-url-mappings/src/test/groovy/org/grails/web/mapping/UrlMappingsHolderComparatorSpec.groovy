package org.grails.web.mapping

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import org.grails.support.MockApplicationContext
import spock.lang.Issue
import spock.lang.Specification

/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author graemerocher
 */
class UrlMappingsHolderComparatorSpec extends Specification {


    @Issue('https://github.com/apache/grails-core/issues/665')
    void "Test that RegexUrlMapping doesn't violate its contract"() {
        when:"Url mappings are parsed"
            def ctx = new MockApplicationContext()
            ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
            def evaluator = new DefaultUrlMappingEvaluator(ctx)
            def mappings = evaluator.evaluateMappings(ComplexUrlMappings.mappings)

        then:
            new DefaultUrlMappingsHolder(mappings)
    }

}

class ComplexUrlMappings {

    static mappings = {
        "/rest/apikey"( resources: "apiKeyRest", includes: ['index', 'show'] )
        "/rest/campaign"( resources: "campaign", includes: ['index', 'show', 'update'] )
        "/rest/category"( resources: "category", includes: ['index', 'show', 'update'] )
        "/rest/eventtype"( resources: "eventTypeRest", includes: ['index', 'show', 'save', 'update'] )
        "/rest/mailing"( resources: "mailingRest", includes: ['index', 'show', 'save', 'update'] )
        "/rest/receivedmail"( resources: "receivedMailRest", includes: ['index', 'show'] )
        "/rest/usersettings"( resources: "userSettings", includes: ['show', 'update'] )
//
//
        "/rest/qualitycheck"( resources: "qualityCheck", includes: ['show'] )
        "/rest/testmail"( controller: "testmail", action: "testmail", method: "POST" )
        "/rest/preview"( controller: "mailingPreview", action: "preview", method: "POST" )
        "/rest/activate"( controller: "mailingActivate", action: "activate", method: "POST" )
//
        "/rest/logincheck"( resources: "loginCheck", includes: ['index'] )
        "/rest/login"(controller: "ajaxLogin", action: "login", method: "POST")
        "/rest/loginsuccess"(controller: "ajaxLogin", action: "success")
        "/rest/logindenied"(controller: "ajaxLogin", action: "denied")
        "/rest/logout"(controller: "ajaxLogout", action: "logout")
        "/rest/logoutsuccess"(controller: "ajaxLogout", action: "success")

        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/"(view:"/index")
        "500"(view:'/error')
        "404"(view:'/notFound')
    }
}
