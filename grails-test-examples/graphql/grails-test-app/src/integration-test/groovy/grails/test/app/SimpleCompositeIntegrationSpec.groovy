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

import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import grails.testing.mixin.integration.Integration
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import spock.lang.Specification
import spock.lang.Stepwise

@Integration
@Stepwise
class SimpleCompositeIntegrationSpec extends Specification implements GraphQLSpec {

    void "test creating an entity with a simple composite id"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                simpleCompositeCreate(simpleComposite: {
                    title: "x",
                    description: "y",
                    someUUID: "20666c44-f42a-4db2-935d-a97af6646c77"
                }) {
                    title
                    description
                    someUUID
                }
            }
        """)
        Map obj = resp.body().data.simpleCompositeCreate

        then:
        obj.title == 'x'
        obj.description == 'y'
        obj.someUUID == '20666c44-f42a-4db2-935d-a97af6646c77'
    }

    void "test updating an entity with a simple composite id"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                simpleCompositeUpdate(title: "x", description: "y", simpleComposite: {
                    someUUID: "8e22054f-a419-44dd-8726-1e53023cb7be"
                }) {
                    title
                    description
                    someUUID
                }
            }
        """)
        Map obj = resp.body().data.simpleCompositeUpdate

        then:
        obj.title == 'x'
        obj.description == 'y'
        obj.someUUID == '8e22054f-a419-44dd-8726-1e53023cb7be'
    }

    void "test retrieving an entity with a simple composite id"() {
        when:
        def resp = graphQL.graphql("""
            {
                simpleComposite(title: "x", description: "y") {
                    title
                    description
                    someUUID
                }
            }
        """)
        Map obj = resp.body().data.simpleComposite

        then:
        obj.title == 'x'
        obj.description == 'y'
        obj.someUUID == '8e22054f-a419-44dd-8726-1e53023cb7be'
    }

    void "test listing entities with a simple composite id"() {
        when:
        def resp = graphQL.graphql("""
            {
                simpleCompositeList {
                    title
                    description
                    someUUID
                }
            }
        """)
        List obj = resp.body().data.simpleCompositeList

        then:
        obj.size() == 1
        obj[0].title == 'x'
        obj[0].description == 'y'
        obj[0].someUUID == '8e22054f-a419-44dd-8726-1e53023cb7be'
    }

    void "test deleting an entity with a simple composite id"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                simpleCompositeDelete(title: "x", description: "y") {
                    success
                }
            }
        """)
        Map obj = resp.body().data.simpleCompositeDelete

        then:
        obj.success
    }
}
