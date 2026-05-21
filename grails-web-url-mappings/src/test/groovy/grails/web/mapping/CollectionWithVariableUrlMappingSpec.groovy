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

class CollectionWithVariableUrlMappingSpec extends AbstractUrlMappingsSpec {

    void 'test url with collection and variable'() {
        given:
        def urlMappingsHolder = getUrlMappingsHolder {
            "/tickets"(resources: 'ticket') {
                collection {
                    "/history/${id}"(controller: 'ticket', action:'history', method: 'GET')
                }
            }
        }

        expect:
        urlMappingsHolder.matchAll('/tickets/history/1', 'GET')
    }

    void 'test backwards-compatibility with group mappings'() {
        given:
        def urlMappingsHolder = getUrlMappingsHolder {
            group('/api') {
                '/photo'(resources: 'photo', includes: ['show'])
                "/foo/${id}"(controller: 'foo', action: 'show')
            }
        }

        expect:
        urlMappingsHolder.matchAll('/api/foo/123', 'GET')
    }
}
