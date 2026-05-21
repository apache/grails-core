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
package grails.web.mapping

import org.springframework.http.HttpMethod

/**
 * @author Graeme Rocher
 */
class VersionedResourceMappingSpec extends AbstractUrlMappingsSpec {
    void "Test that a newer version takes precedence"() {
        given:"A URL mapping with two that maps to different APIs"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/books"(resources:"bookv1", version:"1.0")
                "/books"(resources:"bookv2", version:"2.0")
            }

        when:"A specific version is requested"
            def matches = urlMappingsHolder.matchAll("/books", HttpMethod.GET, "1.0")

        then:"The results are correct"
            matches[0].controllerName == 'bookv1'

        when:"A URL is matched"
            matches = urlMappingsHolder.matchAll("/books", HttpMethod.GET, UrlMapping.ANY_VERSION)

        then:"The first result is correct"
            matches[0].controllerName == 'bookv2'


        when:"The order is reversed"
            urlMappingsHolder = getUrlMappingsHolder {
                "/books"(resources:"bookv2", version:"2.0")
                "/books"(resources:"bookv1", version:"1.0")
            }

            matches = urlMappingsHolder.matchAll("/books", HttpMethod.GET, UrlMapping.ANY_VERSION)

        then:"The first result is correct"
            matches[0].controllerName == 'bookv2'

    }
}
