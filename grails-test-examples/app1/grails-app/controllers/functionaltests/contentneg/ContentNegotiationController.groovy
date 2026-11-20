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

package functionaltests.contentneg

import grails.converters.JSON
import grails.converters.XML

/**
 * Controller demonstrating content negotiation features.
 */
class ContentNegotiationController {

    static responseFormats = ['json', 'xml', 'html']

    /**
     * Returns data in format based on Accept header or extension.
     */
    def index() {
        def data = [
            message: 'Hello World',
            timestamp: new Date().format('yyyy-MM-dd'),
            items: ['one', 'two', 'three']
        ]
        
        withFormat {
            json { render data as JSON }
            xml { render data as XML }
            html { render view: 'index', model: [data: data] }
        }
    }

    /**
     * Demonstrates respond method for automatic content negotiation.
     */
    def respond() {
        def data = [
            status: 'success',
            code: 200,
            data: [
                id: 1,
                name: 'Test Item',
                active: true
            ]
        ]
        respond data
    }

    /**
     * Returns different status codes based on format.
     */
    def statusByFormat() {
        withFormat {
            json { render([status: 'ok'] as JSON) }
            xml { render([status: 'ok'] as XML) }
            html { 
                response.status = 200
                render '<html><body><p>OK</p></body></html>'
            }
        }
    }

    /**
     * Demonstrates format parameter override.
     */
    def formatParam() {
        def data = [format: params.format ?: 'unknown', value: 42]
        
        withFormat {
            json { render data as JSON }
            xml { render data as XML }
            '*' { render data as JSON } // Default fallback
        }
    }

    /**
     * Returns a list for collection content negotiation.
     */
    def list() {
        def items = [
            [id: 1, name: 'Item 1'],
            [id: 2, name: 'Item 2'],
            [id: 3, name: 'Item 3']
        ]
        respond items
    }

    /**
     * Demonstrates explicit content type setting.
     */
    def explicitContentType() {
        response.contentType = 'application/json'
        render '{"explicit": true}'
    }

    /**
     * Returns data with custom JSON rendering.
     */
    def customJson() {
        def data = [
            nested: [
                deep: [
                    value: 'found'
                ]
            ]
        ]
        render(contentType: 'application/json') {
            result(data.nested.deep.value)
        }
    }

    /**
     * Demonstrates error response formatting.
     */
    def error() {
        def error = [
            error: true,
            message: 'Something went wrong',
            code: 'ERR_001'
        ]
        
        withFormat {
            json { 
                response.status = 400
                render error as JSON 
            }
            xml { 
                response.status = 400
                render error as XML 
            }
            html {
                response.status = 400
                render view: 'error', model: [error: error]
            }
        }
    }

    /**
     * Demonstrates a custom MIME type contributed via grails.mime.mergeDefaults.
     * Only the 'vnd' type is declared in application.yml; the json and xml handlers
     * below resolve through the framework defaults that mergeDefaults merges in.
     */
    def mergedFormats() {
        def data = [message: 'Hello World', merged: true]

        withFormat {
            vnd { render(contentType: 'application/vnd.app1.v1+json', text: '{"format":"vnd","merged":true}') }
            json { render data as JSON }
            xml { render data as XML }
        }
    }

    /**
     * Demonstrates multiple accept types in request.
     */
    def multiAccept() {
        // Request can have multiple Accept types with quality values
        def acceptHeader = request.getHeader('Accept') ?: 'none'
        def data = [acceptHeader: acceptHeader, negotiated: response.format ?: 'unknown']
        
        withFormat {
            json { render data as JSON }
            xml { render data as XML }
            '*' { render data as JSON }
        }
    }
}
