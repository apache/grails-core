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

package org.sitemesh.grails.plugins.sitemesh3

class DemoController {

    def index() { // Force layout
        render view:'index', layout: 'bootstrap5'
    }

    def renderText() {
        render text: '<p>Hello World</p>', contentType: 'text/html'
    }

    def chaining() {} // Multiple layouts
    def jsp() { render view:'hello' } // JSP page with layout
    def viewException() {} // Exception in a view

    def exception() { // Exception from a controller.
        throw new RuntimeException("Whoops, why would you ever want to see an exception??")
    }

    // Use Controller to handle 500 error.
    def error500() {
        def exception = request.exception?:request.getAttribute('jakarta.servlet.error.exception')
        Map model = [error:exception?.message]
        if (request.forwardURI?.endsWith('.json')) {
            params.format = 'json'
        }
        respond model, view:'/error'
    }
}