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

import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

@Integration
class BookSpec extends Specification implements HttpClientSupport {

    void 'Test errors view rendering'() {
        when: 'A POST is issued with a missing title'
        def response = httpPostJson('/books', [title: ''])

        then: 'The proper error is returned'
        response.expectJson(422, 'Content-Type': 'application/vnd.error;charset=UTF-8', """
            {
              "message": "Property [title] of class [class functional.tests.Book] cannot be null",
              "path": "/book/index",
              "_links": {
                "self": {
                  "href": "$httpBaseUrl/book/index"
                }
              }
            }"""
        )
    }

    void 'Test REST view rendering'() {
        when: 'A GET is issued to get all books'
        def response = http('/books')

        then: 'The response is correct'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', '[]')

        when: 'A POST is issued to create a new book'
        response = httpPostJson('/books', [title: 'The Stand'])

        then: 'The REST resource is created and the correct JSON is returned'
        response.expectJson(201, 'Content-Type': 'application/json;charset=UTF-8', [
                id: 1,
                timeZone: 'America/New_York',
                title: 'The Stand',
                vendor: 'MyCompany'
        ])

        when: 'A GET request is issued'
       response = http('/books/1')

        then: 'The response is correct'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', [
                id: 1,
                timeZone: 'America/New_York',
                title: 'The Stand',
                vendor: 'MyCompany'
        ])

        when: 'A PUT is issued'
        response = httpPutJson('/books/1', [title: 'The Changeling'])

        then: 'The resource is updated'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', [
                id: 1,
                timeZone: 'America/New_York',
                title: 'The Changeling',
                vendor: 'MyCompany'
        ])

        when: 'A GET is issued for all books'
       response = http('/books')

        then: 'The response is correct'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', '''
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
       response = http('/books/listExcludes?testParam=3')

        then: 'Access to config and params works'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', '''
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
       response = http('/books/listExcludesRespond?testParam=4')

        then: 'view rendering works with a map with respond'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', '''
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
       response = http('/books/showWithParams/1?expand=foo')

        then: 'view rendering with template passes parameters'
        response.expectStatus(200)
        response.hasHeaderValue('Content-Type', 'application/json;charset=UTF-8')
        def json = response.json()
        json.paramsFromView == json.book['paramsFromTemplate']

    }

    void 'View parameter passed to the render method can be used for non-standard view locations'() {
        when: 'A GET is issued to a request with a template at a non-standard location'
        def response = http('/books/non-standard-template')

        then: 'The template was rendered successfully. The custom converter was also used'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', '''
            {
                "bookTitle": "template found",
                "custom": "Sally"
            }
        ''')
    }

    void 'Object type of list is used for model variable when rendering templates'() {
        when:
        def response = http('/books/listCallsTmpl')

        then: 'The template was rendered successfully'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', '''
            [
                {
                    "title": "The Changeling"
                }
            ]
        ''')
    }

    void 'Object type of list is used for model variable in addition to specified model when rendering templates'() {
        when:
        def response = http('/books/listCallsTmplExtraData')

        then: 'The template was rendered successfully'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', '''
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
        def response = http('/books/listCallsTmplVar')

        then: 'The template was rendered successfully'
        response.expectJson(200, 'Content-Type': 'application/json;charset=UTF-8', '''
            [
                {
                    "title": "The Changeling",
                    "value": true
                }
            ]
        ''')
    }
}
