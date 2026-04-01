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
package micronaut

import groovy.transform.CompileStatic

import grails.converters.JSON

@CompileStatic
class ExternalApiController {

    ExternalApiService externalApiService

    def index() {
        String data = externalApiService.fetchAll()
        render([source: 'micronaut-client', data: data] as JSON)
    }

    def show() {
        String id = params.id as String
        String data = externalApiService.fetchById(id)
        if (data == null) {
            response.status = 404
            render([error: 'not found'] as JSON)
            return
        }
        render([source: 'micronaut-client', data: data] as JSON)
    }

    def save() {
        String body = request.inputStream.text
        String data = externalApiService.createResource(body)
        response.status = 201
        render([source: 'micronaut-client', data: data] as JSON)
    }

    def update() {
        String id = params.id as String
        String body = request.inputStream.text
        String data = externalApiService.updateResource(id, body)
        render([source: 'micronaut-client', data: data] as JSON)
    }

    def delete() {
        String id = params.id as String
        int status = externalApiService.deleteResource(id)
        response.status = status
        render([source: 'micronaut-client', deleted: true] as JSON)
    }

    def showWithErrorHandling() {
        String id = params.id as String
        Map result = externalApiService.fetchWithErrorHandling(id)
        if (result.success) {
            render([source: 'micronaut-client', data: result.data] as JSON)
        } else {
            response.status = result.status as int
            render([source: 'micronaut-client', error: result.error] as JSON)
        }
    }

    def search() {
        String q = params.q as String
        String page = params.page as String ?: '1'
        String data = externalApiService.search(q, page)
        render([source: 'micronaut-client', data: data] as JSON)
    }

    def secureShow() {
        String authHeader = request.getHeader('Authorization')
        String data = externalApiService.fetchSecure(authHeader)
        render([source: 'micronaut-client', data: data] as JSON)
    }

    def patchAction() {
        String id = params.id as String
        String body = request.inputStream.text
        String data = externalApiService.patchResource(id, body)
        render([source: 'micronaut-client', data: data] as JSON)
    }

    def asyncShow() {
        String data = externalApiService.fetchAsync()
        render([source: 'micronaut-client', data: data] as JSON)
    }

    def pathShow() {
        String id = params.id as String
        String data = externalApiService.fetchPathItem(id)
        render([source: 'micronaut-client', data: data] as JSON)
    }

    def filteredShow() {
        String data = externalApiService.fetchFiltered()
        render([source: 'micronaut-client', data: data] as JSON)
    }
}
