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
import grails.validation.Validateable
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.util.WebUtils

import spock.lang.Specification

class CommandObjectSpec extends Specification {

    void setup() {
        WebUtils.clearGrailsWebRequest()
    }

    void 'describes the request body as the command object the action binds'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'not the domain class the controller is named for'
        openApi.paths['/orders/submit'].post.requestBody
                .content['application/json'].schema.$ref == '#/components/schemas/OrderCommand'
    }

    void 'registers the command object schema'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        openApi.components.schemas.containsKey('OrderCommand')

        and: 'described from the properties the command declares'
        openApi.components.schemas['OrderCommand'].properties.keySet().containsAll(['customerEmail', 'quantity'])
    }

    void 'carries the command object constraints into its schema'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        with(openApi.components.schemas['OrderCommand']) {
            properties.customerEmail.format == 'email'
            properties.quantity.minimum == 1G
            'customerEmail' in required
        }
    }

    void 'does not drag the Validateable and Groovy machinery into the document'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'the command schema carries only what it declares'
        openApi.components.schemas['OrderCommand'].properties.keySet() == ['customerEmail', 'quantity'] as Set

        and: 'so nothing those properties would have pulled in is registered'
        !openApi.components.schemas.containsKey('Errors')
        !openApi.components.schemas.containsKey('MetaClass')
        openApi.components.schemas.keySet().every { !it.endsWith('Node') && !it.startsWith('Groovy') }
    }

    void 'still describes the resource where an action takes no command object'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'a RestfulController binds the domain class itself'
        openApi.paths['/order/save'].post.requestBody
                .content['application/json'].schema.$ref == '#/components/schemas/Order'
    }

    void 'describes a nested command object and its constraints'() {
        given:
        def openApi = new OpenAPI()

        when:
        nestedCustomizer().customise(openApi)

        then: 'the nested command is described'
        openApi.components.schemas.containsKey('AddressCommand')

        and: 'with its own constraints, not only the outer command'
        'street' in openApi.components.schemas['AddressCommand'].required
        openApi.components.schemas['AddressCommand'].properties.street.maxLength == 12

        and: 'and only the commands, not what pruning removes from them'
        openApi.components.schemas.keySet() == ['NestedOrderCommand', 'AddressCommand', 'LineCommand'] as Set
    }

    void 'describes the element type of a collection property'() {
        given:
        def openApi = new OpenAPI()

        when:
        nestedCustomizer().customise(openApi)

        then:
        openApi.components.schemas['NestedOrderCommand'].properties.lines.items.$ref ==
                '#/components/schemas/LineCommand'
        openApi.components.schemas.containsKey('LineCommand')
    }

    private static UrlMappingsOpenApiCustomizer nestedCustomizer() {
        def application = new DefaultGrailsApplication(NestedOrdersController).tap { it.initialise() }
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, application)
        def holder = new DefaultUrlMappingsHolder(new DefaultUrlMappingEvaluator(ctx).evaluateMappings {
            post '/nested/submit'(controller: 'nestedOrders', action: 'submit')
        })
        new UrlMappingsOpenApiCustomizer(holder).tap { grailsApplication = application }
    }

    private static UrlMappingsOpenApiCustomizer customizer() {
        def application = new DefaultGrailsApplication(OrdersController, OrderController).tap {
            it.initialise()
        }
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, application)
        def holder = new DefaultUrlMappingsHolder(new DefaultUrlMappingEvaluator(ctx).evaluateMappings {
            post '/orders/submit'(controller: 'orders', action: 'submit')
            "/$controller/$action?/$id?(.$format)?" {}
        })

        MappingContext context = new KeyValueMappingContext('test')
        context.addPersistentEntity(Order)
        context.setValidatorRegistry(new DefaultValidatorRegistry(context, new ConnectionSourceSettings()))

        new UrlMappingsOpenApiCustomizer(holder).tap {
            grailsApplication = application
            mappingContext = context
        }
    }
}

@Entity
class Order {
    String reference
}

class OrderCommand implements Validateable {
    String customerEmail
    Integer quantity

    static constraints = {
        customerEmail email: true, nullable: false
        quantity min: 1, nullable: true
    }
}

class AddressCommand implements Validateable {
    String street

    static constraints = {
        street nullable: false, maxSize: 12
    }
}

class LineCommand implements Validateable {
    String sku
}

class NestedOrderCommand implements Validateable {
    AddressCommand shipping
    List<LineCommand> lines
}

@Artefact('Controller')
class NestedOrdersController {
    def submit(NestedOrderCommand cmd) { }
}

@Artefact('Controller')
class OrdersController {
    def submit(OrderCommand cmd) { }
}

@Artefact('Controller')
class OrderController extends grails.rest.RestfulController<Order> {
    OrderController() { super(Order) }
}
