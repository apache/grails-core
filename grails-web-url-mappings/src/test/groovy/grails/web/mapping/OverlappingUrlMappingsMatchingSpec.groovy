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
/**
 */
class OverlappingUrlMappingsMatchingSpec extends AbstractUrlMappingsSpec{

    void "Test that when there are 2 overlapping mappings the most specific is matched"() {
        given:"A url mappings holder with overlapping mappings"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/books/$bookId/authors/create"(controller:"author", action:"create")
                "/books/$bookId/authors/$id(.$format)?"(controller: "author", action: 'show')
            }

        when:"A URL is matched that overlaps"
            def mappings = urlMappingsHolder.matchAll("/books/1/authors/create")

        then:"The correct match is first"
            mappings
            mappings[0].actionName == "create"
    }
}
