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
import grails.gorm.annotation.Entity
import grails.rest.RestfulController
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.util.WebUtils

import spock.lang.Specification

/**
 * A date, a UUID and a byte array are all described as strings, so a schema type is not what decides
 * whether a string constraint can be read from a property.
 */
class PropertyTypeSchemaSpec extends Specification {

    void setup() {
        WebUtils.clearGrailsWebRequest()
    }

    void 'describes a domain class whose properties are not all strings'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'the schema is present rather than dropped'
        openApi.components.schemas.containsKey('Record')

        and: 'with every property described'
        openApi.components.schemas['Record'].properties.keySet()
                .containsAll(['label', 'published', 'reference', 'payload', 'size'])
    }

    void 'leaves no reference unresolved when a property is not a string'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        Set defined = openApi.components?.schemas?.keySet() ?: [] as Set
        Set referenced = (groovy.json.JsonOutput.toJson([openApi.paths, openApi.components?.schemas]) =~
                /#\/components\/schemas\/(\w+)/).collect { it[1] } as Set
        (referenced - defined).isEmpty()
    }

    void 'still carries the string constraints on the properties that are strings'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        openApi.components.schemas['Record'].properties.label.maxLength == 30

        and: 'and the numeric ones on the numbers'
        openApi.components.schemas['Record'].properties.size.minimum == 1G
    }

    private static UrlMappingsOpenApiCustomizer customizer() {
        def application = new DefaultGrailsApplication(RecordController).tap { it.initialise() }
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, application)
        def holder = new DefaultUrlMappingsHolder(new DefaultUrlMappingEvaluator(ctx).evaluateMappings {
            '/records'(resources: 'record')
        })

        MappingContext context = new KeyValueMappingContext('test')
        context.addPersistentEntity(Record)
        context.setValidatorRegistry(new DefaultValidatorRegistry(context, new ConnectionSourceSettings()))

        new UrlMappingsOpenApiCustomizer(holder).tap {
            grailsApplication = application
            mappingContext = context
        }
    }
}

@Entity
class Record {
    String label
    Date published
    UUID reference
    byte[] payload
    Integer size

    static constraints = {
        label nullable: false, maxSize: 30
        published nullable: true
        reference nullable: true
        payload nullable: true
        size nullable: true, min: 1
    }
}

@Artefact('Controller')
class RecordController extends RestfulController<Record> {
    RecordController() { super(Record) }
}
