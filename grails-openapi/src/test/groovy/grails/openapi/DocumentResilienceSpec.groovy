/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.openapi

import io.swagger.v3.oas.models.OpenAPI

import grails.artefact.Artefact
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.validation.Validateable
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.util.WebUtils

import spock.lang.Specification

class DocumentResilienceSpec extends Specification {

    void setup() {
        WebUtils.clearGrailsWebRequest()
    }

    void 'a class that cannot be described does not take the document with it'() {
        given:
        def openApi = new OpenAPI()

        when: 'swagger-core cannot introspect one command object'
        customizer().contribute(openApi, null)

        then: 'the document is still served'
        noExceptionThrown()

        and: 'the operations are still described'
        openApi.paths['/undescribable'].post
        openApi.paths['/sound'].post

        and: 'and the class that could be described still is'
        openApi.components.schemas.containsKey('SoundCommand')
        !openApi.components.schemas.containsKey('UndescribableCommand')

        and: 'the operation that referred to the undescribable class refers to nothing instead'
        openApi.paths['/undescribable'].post.requestBody == null ||
                openApi.paths['/undescribable'].post.requestBody
                        .content['application/json'].schema == null
    }

    void 'a class whose constraints cannot be read is described without them'() {
        given:
        def openApi = new OpenAPI()

        when: 'one command object throws while its constraints are read'
        customizer().contribute(openApi, null)

        then:
        openApi.paths['/exploding'].post.requestBody.content['application/json'].schema.$ref ==
                '#/components/schemas/ExplodingCommand'
        openApi.components.schemas['ExplodingCommand'].properties.keySet() == ['ok'] as Set
        !openApi.components.schemas['ExplodingCommand'].required

        and: 'with what Grails declares of it that could be read'
        openApi.components.schemas['ExplodingCommand'].xml.name == 'explodingCommand'
    }

    void 'no reference anywhere in the document is left unresolved'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)

        then: 'across the paths as well as the schemas, which is where a dropped type shows'
        Set defined = openApi.components?.schemas?.keySet() ?: [] as Set
        Set referenced = (groovy.json.JsonOutput.toJson([openApi.paths, openApi.components?.schemas]) =~
                /#\/components\/schemas\/(\w+)/).collect { it[1] } as Set
        (referenced - defined).isEmpty()
    }

    void 'describes the element type of a map and a nested collection'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)

        then: 'a generic argument is followed, so no reference is left undefined'
        openApi.components.schemas.containsKey('InnerCommand')

        and:
        def defined = openApi.components.schemas.keySet()
        def referenced = (groovy.json.JsonOutput.toJson(openApi.components.schemas) =~
                /#\/components\/schemas\/(\w+)/).collect { it[1] } as Set
        (referenced - defined).isEmpty()
    }

    private static GrailsOpenApiGenerator customizer() {
        def application = new DefaultGrailsApplication(ExplodingController, SoundController, UndescribableController)
                .tap { it.initialise() }
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, application)
        def holder = new DefaultUrlMappingsHolder(new DefaultUrlMappingEvaluator(ctx).evaluateMappings {
            post '/exploding'(controller: 'exploding', action: 'submit')
            post '/sound'(controller: 'sound', action: 'submit')
            post '/undescribable'(controller: 'undescribable', action: 'submit')
        })
        OpenApiFixture.generator(holder, application)
    }
}

class InnerCommand implements Validateable {
    String label
}

class SoundCommand implements Validateable {
    Map<String, InnerCommand> byKey
    List<List<InnerCommand>> nested
}

class ExplodingCommand implements Validateable {
    String ok

    static Map getConstraintsMap() {
        throw new IllegalStateException('the constraints cannot be evaluated')
    }
}

/**
 * A class Jackson, and so swagger-core, refuses: it cannot choose between the setters of amount.
 */
class UndescribableCommand {
    private Object amount

    Integer getAmount() { (Integer) amount }

    void setAmount(Integer amount) { this.amount = amount }

    void setAmount(Long amount) { this.amount = amount }
}

@Artefact('Controller')
class UndescribableController {
    def submit(UndescribableCommand cmd) { }
}

@Artefact('Controller')
class ExplodingController {
    def submit(ExplodingCommand cmd) { }
}

@Artefact('Controller')
class SoundController {
    def submit(SoundCommand cmd) { }
}
