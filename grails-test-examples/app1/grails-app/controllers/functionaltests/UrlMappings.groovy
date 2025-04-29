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

        "/"(view:"/index")
        "500"(view:'/error')
        "404"(controller:"errors", action:"notFound")
        "500"(controller:"errors", action:'customErrorHandler', exception:CustomException)
    }
}
