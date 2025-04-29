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
package org.grails.web.mapping

import grails.web.mapping.AbstractUrlMappingsSpec
import spock.lang.Issue

/**
 * Created by graemerocher on 25/10/16.
 */
class NestedMappingWithinGroupSpec extends AbstractUrlMappingsSpec {

    @Issue('https://github.com/apache/grails-core/issues/10246')
    void "test nested mapping within group"() {
        given:"A link generator with nested mappings withihn a group"
        def linkGenerator = getLinkGenerator {
            group("/api") {
                "/customer-stores"(resources: 'store') {
                    "/catalogs"(controller: 'catalog', action: 'index', method: 'GET')
                    "/catalogs/$catalogId/items"(controller: 'catalog', action: 'getItems', method: 'GET')
                }
            }
        }

        expect:"The generated links to be correct"
        linkGenerator.link(controller:"catalog", action:"index", method:"GET", params:[storeId:'1']) == 'http://localhost/api/customer-stores/1/catalogs'
        linkGenerator.link(controller:"catalog", action:"getItems", method:"GET", params:[storeId:'1', catalogId:'2']) == 'http://localhost/api/customer-stores/1/catalogs/2/items'
    }
}
