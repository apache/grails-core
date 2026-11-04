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
package org.grails.web.config.http

import org.springframework.core.Ordered
import spock.lang.Specification

class GrailsFiltersSpec extends Specification {

    void 'the character encoding filter is ordered ahead of every Grails filter after it'() {
        expect:
        GrailsFilters.CHARACTER_ENCODING_FILTER.order == Ordered.HIGHEST_PRECEDENCE
        GrailsFilters.values().findAll { it.ordinal() > GrailsFilters.CHARACTER_ENCODING_FILTER.ordinal() }
                .every { it.order > GrailsFilters.CHARACTER_ENCODING_FILTER.order }
    }

    void 'lists the filters in the order they run'() {
        expect:
        GrailsFilters.values()*.order == GrailsFilters.values()*.order.sort(false)
    }

    void '#filter keeps the order #order'() {
        expect:
        filter.order == order

        where:
        filter                                     | order
        GrailsFilters.STARTUP_PROGRESS_FILTER      | Ordered.HIGHEST_PRECEDENCE
        GrailsFilters.LATENCY_FILTER               | Ordered.HIGHEST_PRECEDENCE
        GrailsFilters.CORS_FILTER                  | Ordered.HIGHEST_PRECEDENCE
        GrailsFilters.CHARACTER_ENCODING_FILTER    | Ordered.HIGHEST_PRECEDENCE
        GrailsFilters.CAS_SINGLE_SIGN_OUT_FILTER   | Ordered.HIGHEST_PRECEDENCE + 1
        GrailsFilters.FORM_CONTENT_FILTER          | -9900
        GrailsFilters.FIRST                        | -200
        GrailsFilters.ASSET_PIPELINE_FILTER        | -190
        GrailsFilters.HIDDEN_HTTP_METHOD_FILTER    | -170
        GrailsFilters.SITEMESH_FILTER              | -160
        GrailsFilters.GRAILS_WEB_REQUEST_FILTER    | -150
        GrailsFilters.LAST                         | -110
        GrailsFilters.SPRING_SECURITY_FILTER_CHAIN | -100
    }
}
