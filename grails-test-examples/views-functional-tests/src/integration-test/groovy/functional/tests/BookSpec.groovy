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

package functional.tests

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import spock.lang.Specification

import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration
import grails.web.http.HttpHeaders
import org.apache.grails.testing.httpclient.HttpClientSupport

@Integration
class BookSpec extends Specification implements HttpClientSupport {
    
    @Autowired
    ObjectMapper objectMapper
    
    void 'Test errors view rendering'() {
        when: 'A POST is issued with a missing title'
        httpClient.exchange(
                HttpRequest.POST('/books', [title: '']),
                Argument.of(String),
                Argument.of(String)
        )

        then: 'The proper error is returned'
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.UNPROCESSABLE_ENTITY
        e.response.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        e.response.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/vnd.error;charset=UTF-8'
        objectMapper.readTree(e.response.body().toString()) == objectMapper.readTree("""
            {
              "message": "Property [title] of class [class functional.tests.Book] cannot be null",
              "path": "/book/index",
              "_links": {
                "self": {
                  "href": "$httpClientURL/book/index"
                }
              }
            }""")
    }

    void 'Test REST view rendering'() {
        when: 'A GET is issued to get all books'
        def resp = httpClient.exchange('/books', String)

        then: 'The response is correct'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        resp.body() == '[]'

        when: 'A POST is issued to create a new book'
        def request = HttpRequest.POST('/books', new SaveBookVM(title: 'The Stand'))
        resp = httpClient.exchange(request, Map)

        then: 'The REST resource is created and the correct JSON is returned'
        resp.status == HttpStatus.CREATED
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        resp.body()
        resp.body().id == 1
        resp.body().timeZone == 'America/New_York'
        resp.body().title == 'The Stand'
        resp.body().vendor == 'MyCompany'

        when: 'A GET request is issued'
        resp = httpClient.exchange("/books/${resp.body().id}", Map)

        then: 'The response is correct'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        resp.body()
        resp.body().id == 1
        resp.body().timeZone == 'America/New_York'
        resp.body().title == 'The Stand'
        resp.body().vendor == 'MyCompany'

        when: 'A PUT is issued'
        resp = httpClient.exchange(
                HttpRequest.PUT(
                        "/books/${resp.body().id}",
                        new SaveBookVM(title: 'The Changeling')
                ),
                Map
        )

        then: 'The resource is updated'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        resp.body()
        resp.body().id == 1
        resp.body().timeZone == 'America/New_York'
        resp.body().title == 'The Changeling'
        resp.body().vendor == 'MyCompany'

        when: 'A GET is issued for all books'
        resp = httpClient.exchange('/books', String)

        then: 'The response is correct'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            [
                {
                    "id": 1,
                    "title": "The Changeling",
                    "timeZone": "America/New_York",
                    "vendor": "MyCompany"
                }
            ]
        ''')

        when: 'A GET is issued for all books with excludes'
        resp = httpClient.exchange('/books/listExcludes?testParam=3', String)

        then: 'Access to config and params works'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            [
                {
                    "id": 1,
                    "timeZone": "America/New_York",
                    "title": "The Changeling",
                    "vendor": "ConfigVendor",
                    "fromParams": 3
                }
            ]
        ''')

        when: 'A GET is issued for all books with excludes'
        resp = httpClient.exchange('/books/listExcludesRespond?testParam=4', String)

        then: 'view rendering works with a map with respond'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            [
                {
                    "id": 1,
                    "timeZone": "America/New_York",
                    "vendor": "ConfigVendor",
                    "fromParams": 4
                }
            ]
        ''')

        when: 'A GET is issued for a specific book rendered by a template'
        resp = httpClient.exchange('/books/showWithParams/1?expand=foo', Map)

        then: 'view rendering with template passes parameters'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        resp.body().paramsFromView == resp.body().book['paramsFromTemplate']

    }

    void 'View parameter passed to the render method can be used for non-standard view locations'() {
        when: 'A GET is issued to a request with a template at a non-standard location'
        def resp = httpClient.exchange('/books/non-standard-template', String)

        then: 'The template was rendered successfully. The custom converter was also used'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            {
                "bookTitle": "template found",
                "custom": "Sally"
            }
        ''')
    }

    void 'Object type of list is used for model variable when rendering templates'() {
        when:
        def resp = httpClient.exchange('/books/listCallsTmpl', String)

        then: 'The template was rendered successfully'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        resp.body() == '[{"title":"The Changeling"}]'
    }

    void 'Object type of list is used for model variable in addition to specified model when rendering templates'() {
        when:
        def resp = httpClient.exchange('/books/listCallsTmplExtraData', String)

        then: 'The template was rendered successfully'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            [
                {
                    "title": "The Changeling",
                    "value": true
                }
            ]
        ''')
    }

    void 'Object type of list is used for model variable in addition to specified model and var when rendering templates'() {
        when:
        def resp = httpClient.exchange('/books/listCallsTmplVar', String)

        then: 'The template was rendered successfully'
        resp.status == HttpStatus.OK
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).isPresent()
        resp.headers.getFirst(HttpHeaders.CONTENT_TYPE).get() == 'application/json;charset=UTF-8'
        objectMapper.readTree(resp.body()) == objectMapper.readTree('''
            [
                {
                    "title": "The Changeling",
                    "value": true
                }
            ]
        ''')
    }
}

class SaveBookVM {
    String title
}
