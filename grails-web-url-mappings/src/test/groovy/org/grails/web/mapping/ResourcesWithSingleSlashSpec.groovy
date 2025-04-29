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
import grails.web.mapping.LinkGenerator
import org.grails.web.util.WebUtils
import spock.lang.Issue

/**
 * Created by graemerocher on 25/10/16.
 */
class ResourcesWithSingleSlashSpec extends AbstractUrlMappingsSpec {

    @Issue('https://github.com/apache/grails-core/issues/10210')
    void "test that resources expressed with a single slash product the correct URI"() {
        given:"url mappings within groups with resources expressed with a single slash"
        LinkGenerator linkGenerator = getLinkGenerator {
            group "/api", {
                group "/v1", {
                    group "/books", {
                        "/" resources:"book"
                    }
                }
            }
        }

        expect:
        linkGenerator.link(resource:"book", id:1L, method:"GET") == 'http://localhost/api/v1/books/1'
    }
}
