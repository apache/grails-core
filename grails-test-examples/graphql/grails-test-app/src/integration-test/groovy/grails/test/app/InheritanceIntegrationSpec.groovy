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

package grails.test.app

import grails.testing.mixin.integration.Integration
import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import org.grails.web.json.JSONArray
import spock.lang.Specification

@Integration
class InheritanceIntegrationSpec extends Specification implements GraphQLSpec {

    void "test the ... on directive works"() {
        when:
        def resp = graphQL.graphql("""
            {
                mammalList {
                    id
                    name
                    ... on LandMammal {
                        limbCount
                        moveSpeed
                    }
                    ... on Human {
                        language
                    }
                    ... on Dog {
                        barks
                    }
                    ... on Labradoodle {
                        cutenessLevel
                    }
                }
            }
        """, String.class)
        String data = resp.getBody().get()

        then:
        data == '{"data":{"mammalList":[{"id":1,"name":"Spot","barks":true},{"id":2,"name":"Chloe","cutenessLevel":100},{"id":3,"name":"Kotlin Ken","language":true}]}}'
    }
}
