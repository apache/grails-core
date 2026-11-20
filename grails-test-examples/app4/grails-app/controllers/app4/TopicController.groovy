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
package app4

import grails.compiler.GrailsCompileStatic
import grails.converters.JSON

@GrailsCompileStatic
class TopicController {

    static responseFormats = ['json']

    def create() {
        render([controller: 'topic', action: 'create'] as JSON)
    }

    def gallery() {
        render([controller: 'topic', action: 'gallery'] as JSON)
    }

    def home() {
        render([controller: 'topic', action: 'home'] as JSON)
    }

    def save() {
        render([controller: 'topic', action: 'save'] as JSON)
    }

    def show() {
        def payload = [
            controller: 'topic',
            action: 'show'
        ]
        if (params.id) {
            payload.id = params.id
        }
        render(payload as JSON)
    }
}



