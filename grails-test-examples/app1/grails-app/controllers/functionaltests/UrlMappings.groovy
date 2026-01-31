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

package functionaltests

class UrlMappings {

    static mappings = {
        "/viewBooks"(redirect: '/book/index')
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/alphaDemo"(controller: 'demo', action: 'doit', namespace: 'alpha')
        "/betaDemo"(controller: 'demo', action: 'doit', namespace: 'beta')

        // See RedirectWithAndWithoutParamsFunctionalSpec.groovy
        "/old-with-uri"(redirect: [uri: '/new-url'])
        "/old-with-uri-2"(redirect: [uri: '/new-url', keepParamsWhenRedirect: false])
        "/old-with-controller-action"(redirect: [controller: 'baz', action: 'newUrl'])
        "/old-with-controller-action-2"(redirect: [controller: 'baz', action: 'newUrl', keepParamsWhenRedirect: false])
        "/old-uri-with-params"(redirect: [uri: '/new-url', keepParamsWhenRedirect: true])
        "/old-controller-action-with-params"(redirect: [controller: 'baz', action: 'newUrl', keepParamsWhenRedirect: true])
        "/new-url"(controller: 'baz', 'action': 'newUrl')

        "/forward/$param1"(controller: 'forwarding', action: 'two')

        // === URL Mappings Test Routes ===
        
        // Static path mapping
        "/api/test"(controller: 'urlMappingsTest', action: 'index')
        
        // Path variable mapping
        "/api/items/$id"(controller: 'urlMappingsTest', action: 'show')
        
        // Multiple path variables (date pattern)
        "/api/archive/$year/$month/$day"(controller: 'urlMappingsTest', action: 'pathVars')
        
        // Named URL mapping
        name testNamed: "/api/named/$name"(controller: 'urlMappingsTest', action: 'named')
        
        // Constrained path variable (only uppercase letters allowed)
        "/api/codes/$code" {
            controller = 'urlMappingsTest'
            action = 'constrained'
            constraints {
                code matches: /[A-Z]+/
            }
        }
        
        // Wildcard double-star captures remaining path
        "/api/files/**"(controller: 'urlMappingsTest', action: 'wildcard') {
            path = { request.forwardURI - '/api/files/' }
        }
        
        // HTTP method constraints
        "/api/resources"(controller: 'urlMappingsTest') {
            action = [GET: 'list', POST: 'save']
        }
        "/api/resources/$id"(controller: 'urlMappingsTest') {
            action = [GET: 'show', PUT: 'update', DELETE: 'delete']
        }
        
        // Optional path variable
        "/api/optional/$required/$optional?"(controller: 'urlMappingsTest', action: 'optional')
        
        // HTTP method only mapping
        "/api/method-test"(controller: 'urlMappingsTest', action: 'httpMethod')
        
        // Redirect mapping with permanent flag
        "/api/old-endpoint"(redirect: '/api/test', permanent: true)

        // === CORS Test Routes (under /api/** which has CORS enabled) ===
        "/api/cors"(controller: 'corsTest', action: 'index')
        "/api/cors/data"(controller: 'corsTest', action: 'getData')
        "/api/cors/items/$id"(controller: 'corsTest') {
            action = [GET: 'getItem', PUT: 'update', DELETE: 'delete']
        }
        "/api/cors/items"(controller: 'corsTest') {
            action = [GET: 'getData', POST: 'create']
        }
        "/api/cors/custom-headers"(controller: 'corsTest', action: 'withCustomHeaders')
        "/api/cors/echo-origin"(controller: 'corsTest', action: 'echoOrigin')
        "/api/cors/authenticated"(controller: 'corsTest', action: 'authenticated')
        "/api/cors/slow"(controller: 'corsTest', action: 'slowRequest')

        // === Advanced Interceptor Matching Test Routes ===
        "/api/advancedMatching/$action?/$id?"(controller: 'advancedMatching', namespace: 'api')

        "/"(view:"/index")
        "500"(view:'/error')
        "404"(controller:"errors", action:"notFound")
        "500"(controller:"errors", action:'customErrorHandler', exception:CustomException)
    }
}
