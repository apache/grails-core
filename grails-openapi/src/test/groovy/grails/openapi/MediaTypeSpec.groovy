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
import org.springframework.web.multipart.MultipartFile

import grails.artefact.Artefact
import grails.gorm.annotation.Entity
import grails.rest.RestfulController
import grails.validation.Validateable
import grails.web.mime.MimeType
import org.grails.support.MockApplicationContext

import spock.lang.Specification

class MediaTypeSpec extends Specification {

    void 'describes the media types of the formats a controller responds in'() {
        when:
        def openApi = OpenApiFixture.document([TicketController], [Ticket]) {
            '/tickets'(resources: 'ticket')
        }

        then: 'the resource, the validation errors and the body it binds'
        openApi.paths['/tickets/{id}'].get.responses['200'].content.keySet() as List == ['application/json', 'text/xml']
        openApi.paths['/tickets'].post.responses['422'].content.keySet() as List == ['application/json', 'text/xml']
        openApi.paths['/tickets'].post.requestBody.content.keySet() as List == ['application/json', 'text/xml']

        and: 'each describes the same shape'
        openApi.paths['/tickets/{id}'].get.responses['200'].content.values()*.schema*.$ref.unique() ==
                ['#/components/schemas/Ticket']
    }

    void 'describes the formats a controller declares for each action'() {
        when:
        def openApi = OpenApiFixture.document([VoucherController], [Voucher]) {
            '/vouchers'(resources: 'voucher')
        }

        then: 'the formats declared for the action'
        openApi.paths['/vouchers'].get.responses['200'].content.keySet() as List == ['text/xml']

        and: 'a format with no media type configured is left out'
        openApi.paths['/vouchers/{id}'].get.responses['200'].content.keySet() as List == ['application/json']

        and: 'JSON for an action it declares none for'
        openApi.paths['/vouchers'].post.requestBody.content.keySet() as List == ['application/json']
    }

    void 'describes JSON for a controller that declares no formats'() {
        when:
        def openApi = OpenApiFixture.document([TicketStubController], [Ticket]) {
            '/stubs'(resources: 'ticketStub')
        }

        then:
        openApi.paths['/stubs/{id}'].get.responses['200'].content.keySet() as List == ['application/json']
    }

    void 'describes a format that renders a view or a HAL document without the shape of the resource'() {
        when:
        def openApi = OpenApiFixture.document([PosterController], [Ticket]) {
            '/posters'(resources: 'poster')
        }
        def content = openApi.paths['/posters/{id}'].get.responses['200'].content

        then: 'every format it responds in'
        content.keySet() as List == ['application/json', 'text/html', 'application/hal+json']

        and: 'the shape only in the data format'
        content['application/json'].schema.$ref == '#/components/schemas/Ticket'
        content['text/html'].schema == null
        content['application/hal+json'].schema == null

        and: 'a body bound only from the data format'
        openApi.paths['/posters'].post.requestBody.content.keySet() as List == ['application/json']
    }

    void 'describes a body with a file as multipart form data'() {
        when:
        def openApi = OpenApiFixture.document([UploadDeskController], []) {
            post '/uploads'(controller: 'uploadDesk', action: 'upload')
        }
        def content = openApi.paths['/uploads'].post.requestBody.content

        then:
        content.keySet() as List == ['multipart/form-data']
        with(openApi.components.schemas['TicketScan'].properties.scan) {
            OpenApiFixture.typeOf(it) == 'string'
            format == 'binary'
        }
    }

    void 'describes where a RestfulController says the resource it created is'() {
        when:
        def openApi = OpenApiFixture.document([TicketController], [Ticket]) {
            '/tickets'(resources: 'ticket')
        }

        then:
        with(openApi.paths['/tickets'].post.responses['201'].headers['Location']) {
            description == 'The URL of the created resource'
            schema.format == 'uri'
        }
    }

    void 'maps a format to the first media type the application configures for it'() {
        given:
        def application = OpenApiFixture.application([TicketController])
        def context = new MockApplicationContext()
        context.registerMockBean(MimeType.BEAN_NAME, [
                new MimeType('application/json', 'json'),
                new MimeType('application/xml', 'xml'),
                new MimeType('text/xml', 'xml')] as MimeType[])
        application.mainContext = context

        when:
        OpenAPI openApi = OpenApiFixture.generator(OpenApiFixture.holder { '/tickets'(resources: 'ticket') },
                application, OpenApiFixture.context([Ticket])).generate()

        then:
        openApi.paths['/tickets/{id}'].get.responses['200'].content.keySet() as List == ['application/json', 'application/xml']
    }
}

@Entity
class Ticket {
    String seat

    static constraints = {
        seat nullable: false
    }
}

@Entity
class Voucher {
    String code
}

@Artefact('Controller')
class TicketController extends RestfulController<Ticket> {
    static responseFormats = ['json', 'xml']

    TicketController() { super(Ticket) }
}

@Artefact('Controller')
class TicketStubController extends RestfulController<Ticket> {
    TicketStubController() { super(Ticket) }
}

@Artefact('Controller')
class VoucherController extends RestfulController<Voucher> {
    static responseFormats = [index: ['xml'], show: ['json', 'mystery']]

    VoucherController() { super(Voucher) }
}

@Artefact('Controller')
class PosterController extends RestfulController<Ticket> {
    static responseFormats = ['json', 'html', 'hal']

    PosterController() { super(Ticket) }
}

class TicketScan implements Validateable {
    String caption
    MultipartFile scan
}

@Artefact('Controller')
class UploadDeskController {
    def upload(TicketScan ticketScan) { }
}
