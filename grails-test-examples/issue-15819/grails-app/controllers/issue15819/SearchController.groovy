/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package issue15819

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
class SearchController {

    static allowedMethods = [results: 'GET']

    def results() {
        render(template: 'results', model: [results: ['Grails', 'Groovy', 'Spring Boot']])
    }

    Map resultsWithModel() {
        render(template: 'results', model: [results: ['Grails', 'Groovy', 'Spring Boot']])
        [results: ['This model must not be rendered']]
    }

    def text() {
        render('Grails')
    }

    Map textWithModel() {
        render(text: 'Grails', contentType: 'text/plain')
        [message: 'This model must not be rendered']
    }

    def explicitView() {
        render(view: 'summary', model: [message: 'Explicit view'])
    }

    Map summary() {
        [message: 'Conventional view']
    }

    Map errorWithModel() {
        response.status = 422
        [message: 'Invalid search']
    }
}
