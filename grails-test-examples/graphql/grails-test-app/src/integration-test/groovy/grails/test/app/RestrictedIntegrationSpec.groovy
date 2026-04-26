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
import spock.lang.Specification
import spock.lang.Stepwise

@Integration
@Stepwise
class RestrictedIntegrationSpec extends Specification implements GraphQLSpec {

    void "test creating a restricted"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
              restrictedCreate(restricted: {
                name: "John"
              }) {
                id
                name
              }
            }
        """)

        def obj = resp.body().data.restrictedCreate

        then:
        obj.id == 1
        obj.name == "John"
    }

    void "test deleting a restricted"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
              restrictedDelete(id: 1) {
                success
              }
            }
        """)

        def obj = resp.body().data.restrictedDelete

        then: "the registered interceptor prevented the action"
        obj == null
    }

    void "test updating a restricted"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
              restrictedUpdate(id: 1, restricted: {
                name: "Mason"
              }) {
                id
                name
              }
            }
        """)

        def obj = resp.body().data.restrictedUpdate

        then: "the registered interceptor prevented the action"
        obj == null
    }

    void "test retrieving the restricted"() {
        when:
        def resp = graphQL.graphql("""
            query {
              restricted(id: 1) {
                id
                name
              }
            }
        """)

        def obj = resp.body().data.restricted

        then: "the registered interceptor prevented the action"
        obj.id == 1
        obj.name == "John"
    }

    void "test retrieving the restricted count"() {
        when:
        def resp = graphQL.graphql("""
            query {
              restrictedCount
            }
        """)

        def obj = resp.body().data.restrictedCount

        then: "the registered interceptor prevented the action"
        obj == 1
    }

    void "test retrieving a list of restricted"() {
        when:
        def resp = graphQL.graphql("""
            query {
              restrictedList {
                id
                name
              }
            }
        """)

        def obj = resp.body().data.restrictedList

        then: "the registered interceptor prevented the action"
        obj.size() == 1
        obj[0].id == 1
        obj[0].name == "John"
    }

}
