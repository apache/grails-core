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
import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.annotations.media.Schema as SchemaAnnotation

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.util.WebUtils

import spock.lang.Specification

class PersistentEntitySchemaSpec extends Specification {

    void setup() {
        WebUtils.clearGrailsWebRequest()
    }

    void 'registers a schema for the documented resource and its request body'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        openApi.components.schemas.containsKey('Widget')
    }

    void 'defines a domain class reached only through an association'() {
        given:
        def openApi = new OpenAPI()

        when: 'only widgets are mapped, and Crate is reached from Widget'
        customizer().customise(openApi)

        then:
        openApi.components.schemas.containsKey('Crate')
    }

    void 'omits an unreferenced domain class entirely'() {
        given:
        def openApi = new OpenAPI()
        MappingContext context = new KeyValueMappingContext('test')
        context.addPersistentEntity(Widget)
        context.addPersistentEntity(Crate)
        context.addPersistentEntity(Orphan)
        context.setValidatorRegistry(new DefaultValidatorRegistry(context, new ConnectionSourceSettings()))

        when: 'Orphan has no mapping and nothing references it'
        new UrlMappingsOpenApiCustomizer(holder()).tap { mappingContext = context }.customise(openApi)

        then:
        !openApi.components.schemas.containsKey('Orphan')
    }

    void 'maps domain property types onto OpenAPI schema types'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        with(openApi.components.schemas['Widget'].properties) {
            id.type == 'integer'
            id.format == 'int64'
            name.type == 'string'
            weight.type == 'number'
            active.type == 'boolean'
        }
    }

    void 'references the associated schema rather than inlining it'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'a to-one association is a direct reference'
        openApi.components.schemas['Widget'].properties.crate.$ref == '#/components/schemas/Crate'

        and: 'a to-many association is an array of references'
        with(openApi.components.schemas['Crate'].properties.widgets) {
            type == 'array'
            items.$ref == '#/components/schemas/Widget'
        }
    }

    void 'derives required members from the constraints block'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'a property the constraints declare non-nullable is required'
        'name' in openApi.components.schemas['Widget'].required

        and: 'a nullable property is not'
        !('weight' in (openApi.components.schemas['Widget'].required ?: []))

        and: 'the generated version column is never required'
        !('version' in (openApi.components.schemas['Widget'].required ?: []))
    }

    void 'carries validation constraints across to the schema'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        with(openApi.components.schemas['Widget'].properties) {
            name.maxLength == 40
            color.enum == ['red', 'green']
            code.pattern == '[A-Z]{3}'
            contact.format == 'email'
        }
    }

    void 'marks the server assigned properties readOnly rather than defining a second schema'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'the identifier and version are present but not for a client to send'
        with(openApi.components.schemas['Widget'].properties) {
            id.readOnly
            version.readOnly

            and: 'an editable property is not marked'
            !name.readOnly
        }

        and: 'no second schema is defined for request bodies'
        openApi.components.schemas.keySet().every { !it.endsWith('Request') }
    }

    void 'documents a 404 on operations addressed by identifier'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'an instance operation can miss'
        openApi.paths['/widgets/{id}'].get.responses['404']

        and: 'a collection operation cannot'
        openApi.paths['/widgets'].get.responses['404'] == null
    }

    void 'responds with a collection schema for index and a single resource otherwise'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'index returns an array of the resource'
        with(openApi.paths['/widgets'].get.responses['200'].content['application/json'].schema) {
            type == 'array'
            items.$ref == '#/components/schemas/Widget'
        }

        and: 'show returns a single resource'
        openApi.paths['/widgets/{id}'].get.responses['200']
                .content['application/json'].schema.$ref == '#/components/schemas/Widget'
    }

    void 'documents a request body for methods that accept one'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        openApi.paths['/widgets'].post.requestBody
                .content['application/json'].schema.$ref == '#/components/schemas/Widget'

        and: 'a read-only method carries no request body'
        openApi.paths['/widgets'].get.requestBody == null
    }

    void 'omits schemas when the application has no mapping context'() {
        given:
        def openApi = new OpenAPI()
        def customizer = new UrlMappingsOpenApiCustomizer(holder())

        when:
        customizer.customise(openApi)

        then: 'paths are still documented'
        openApi.paths['/widgets'].get

        and: 'but nothing claims a schema'
        openApi.components?.schemas == null
        openApi.paths['/widgets'].get.responses['200'].content['application/json'].schema == null
    }

    void 'honors a Schema annotation on the domain class'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        openApi.components.schemas['Widget'].description == 'A widget in the catalogue'
    }

    void 'honors a Schema annotation on a property'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        with(openApi.components.schemas['Widget'].properties.name) {
            description == 'The name shown to a customer'
            example == 'Sprocket'
        }
    }

    void 'applies the GORM constraints over the annotated schema'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'the annotation supplies the prose'
        openApi.components.schemas['Widget'].properties.name.description == 'The name shown to a customer'

        and: 'while the constraints block still supplies the validation, which swagger-core cannot see'
        openApi.components.schemas['Widget'].properties.name.maxLength == 40
        'name' in openApi.components.schemas['Widget'].required
    }

    void 'omits the foreign key accessor GORM adds beside an association'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'the association describes the relationship'
        openApi.components.schemas['Widget'].properties.containsKey('crate')

        and: 'so the generated identifier accessor is redundant'
        !openApi.components.schemas['Widget'].properties.containsKey('crateId')
    }

    void 'describes the version even though swagger-core does not surface it'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        with(openApi.components.schemas['Widget'].properties.version) {
            it
            readOnly
        }
    }

    void 'produces the same schema for a second document'() {
        given: 'one customizer used for two documents, as a singleton bean is'
        def customizer = customizer()
        def first = new OpenAPI()
        def second = new OpenAPI()

        when:
        customizer.customise(first)
        customizer.customise(second)

        then: 'the constraints are applied once, not accumulated on a shared schema object'
        second.components.schemas['Widget'].properties.color.enum.toList() == ['red', 'green']
        second.components.schemas['Widget'].required.count { it == 'name' } == 1

        and: 'the two documents agree'
        second.components.schemas['Widget'].properties.color.enum.toList() ==
                first.components.schemas['Widget'].properties.color.enum.toList()
    }

    void 'keeps a zero bound, which Groovy treats as falsy'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'a min of zero is a real lower bound rather than an absent one'
        openApi.components.schemas['Widget'].properties.weight.minimum == 0G

        and: 'as is a maxSize of zero'
        openApi.components.schemas['Widget'].properties.note.maxLength == 0
    }

    void 'resolves schemas with the converter that matches the document version'() {
        given: 'a 3.1 document, which is what springdoc serves by default'
        def openApi = new OpenAPI(SpecVersion.V31)

        when:
        customizer().customise(openApi)

        then: 'the schemas are still described, through the 3.1 converter'
        openApi.specVersion == SpecVersion.V31
        openApi.components.schemas['Widget'].properties.name.maxLength == 40
        'name' in openApi.components.schemas['Widget'].required
    }

    private static UrlMappingsOpenApiCustomizer customizer() {
        MappingContext context = new KeyValueMappingContext('test')
        context.addPersistentEntity(Widget)
        context.addPersistentEntity(Crate)
        // Constraints are only available once a validator registry has evaluated them.
        context.setValidatorRegistry(new DefaultValidatorRegistry(context, new ConnectionSourceSettings()))
        new UrlMappingsOpenApiCustomizer(holder()).tap { mappingContext = context }
    }

    private static DefaultUrlMappingsHolder holder() {
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def evaluator = new DefaultUrlMappingEvaluator(ctx)
        new DefaultUrlMappingsHolder(evaluator.evaluateMappings {
            '/widgets'(resources: 'widget')
        })
    }
}

@SchemaAnnotation(description = 'A widget in the catalogue')
@Entity
class Widget {
    @SchemaAnnotation(description = 'The name shown to a customer', example = 'Sprocket')
    String name
    Double weight
    Boolean active
    String color
    String code
    String note
    String contact
    Crate crate

    static constraints = {
        name blank: false, nullable: false, maxSize: 40
        weight nullable: true, min: 0.0d
        active nullable: true
        color nullable: true, inList: ['red', 'green']
        code nullable: true, matches: '[A-Z]{3}'
        note nullable: true, maxSize: 0
        contact nullable: true, email: true
        crate nullable: true
    }
}

@Entity
class Crate {
    String label
    static hasMany = [widgets: Widget]
}

@Entity
class Orphan {
    String note
}
