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

class UrlMappings {

    static mappings = {
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/micronaut-test"(controller: 'micronautTest', action: 'index')

        "/external-api"(controller: 'externalApi', action: 'index', method: 'GET')
        "/external-api/search"(controller: 'externalApi', action: 'search', method: 'GET')
        "/external-api/secure"(controller: 'externalApi', action: 'secureShow', method: 'GET')
        "/external-api/$id"(controller: 'externalApi', action: 'show', method: 'GET')
        "/external-api"(controller: 'externalApi', action: 'save', method: 'POST')
        "/external-api/$id"(controller: 'externalApi', action: 'update', method: 'PUT')
        "/external-api/$id"(controller: 'externalApi', action: 'patchAction', method: 'PATCH')
        "/external-api/$id"(controller: 'externalApi', action: 'delete', method: 'DELETE')
        "/external-api/safe/$id"(controller: 'externalApi', action: 'showWithErrorHandling')
        "/external-api/async"(controller: 'externalApi', action: 'asyncShow', method: 'GET')
        "/external-api/path/$id"(controller: 'externalApi', action: 'pathShow', method: 'GET')
        "/external-api/filtered"(controller: 'externalApi', action: 'filteredShow', method: 'GET')

        "/"(view:"/index")
        "500"(view:'/error')
        "404"(view:'/notFound')
    }
}
