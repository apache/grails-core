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

package org.example.grails.layout

/**
 * Controller for testing GSP tag library functionality.
 * Provides actions that render various GSP tags for functional testing.
 */
class TagLibController {

    def index() {
        [items: ['Apple', 'Banana', 'Cherry']]
    }

    def eachTag() {
        [items: ['Item 1', 'Item 2', 'Item 3', 'Item 4', 'Item 5']]
    }

    def ifTag() {
        [showContent: params.show == 'true', value: params.value ?: 'default']
    }

    def elseTag() {
        [condition: params.condition == 'true']
    }

    def linkTag() {
        [bookId: 123]
    }

    def formTag() {
        [username: 'testuser', email: 'test@example.com']
    }

    def formatTags() {
        [
            dateValue: new Date(),
            numberValue: 12345.6789,
            booleanValue: true
        ]
    }

    def setTag() {
        render(view: 'setTag')
    }

    def renderTag() {
        [message: 'Hello from Controller']
    }

    def messageTag() {
        render(view: 'messageTag')
    }

    def createLinkTag() {
        render(view: 'createLinkTag')
    }

    def collectTag() {
        [items: [
            [name: 'First', value: 1],
            [name: 'Second', value: 2],
            [name: 'Third', value: 3]
        ]]
    }

    def joinTag() {
        [items: ['Red', 'Green', 'Blue']]
    }

    def encodeTags() {
        [
            htmlContent: '<script>alert("XSS")</script>',
            urlContent: 'param=value&other=test',
            jsonContent: [key: 'value', nested: [a: 1]]
        ]
    }
}
