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

class UrlMappings {

    static mappings = {
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/"(view:"/index")
        "500"(view:'/error')
        "401"(controller: 'error', action: 'unauthorized')
        "404"(view:'/notFound')

        "/books"(resources:"book")
        "/books/listExcludes"(controller: "book", action: "listExcludes")
        "/books/listExcludesRespond"(controller: "book", action: "listExcludesRespond")
        "/books/listCallsTmpl"(controller: "book", action: "listCallsTmpl")
        "/books/listCallsTmplVar"(controller: "book", action: "listCallsTmplVar")
        "/books/listCallsTmplExtraData"(controller: "book", action: "listCallsTmplExtraData")
        "/books/showWithParams/$id"(controller: "book", action: "showWithParams")
        "/books/non-standard-template"(controller:"book", action:"nonStandardTemplate")
        "/teams"(resources:"team")
        "/products"(resources:"product")
        "/teams/deep/$id"(controller: "team", action:"deep")
        "/teams/hal/$id"(controller: "team", action:"hal")
        "/authors"(resources:"author")
        "/api/book/$action?"(controller: 'book', namespace: 'api')
        "/person-inheritance"(controller: 'personInheritance', action: 'index')
        "/person-inheritance/npe"(controller: 'personInheritance', action: 'npe')
    }
}
