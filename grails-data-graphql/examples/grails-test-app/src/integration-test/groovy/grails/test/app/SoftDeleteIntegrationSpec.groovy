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
import grails.testing.spock.OnceBefore
import org.grails.web.json.JSONArray
import spock.lang.Shared
import spock.lang.Specification

@Integration
class SoftDeleteIntegrationSpec extends Specification implements GraphQLSpec {

    @Shared
    Long id

    @OnceBefore
    void createInstance() {
        def resp = graphQL.graphql("""
            mutation {
              softDeleteCreate(softDelete: {
                name: "foo"
              }) {
                id
              }
            }
        """)
        id = resp.body().data.softDeleteCreate.id
        assert id != null
    }

    void "test we can query the instance"() {
        when:
        def resp = graphQL.graphql("""
            {
              softDelete(id: $id) {
                name
              }
            }
        """)
        def json = resp.body().data.softDelete

        then:
        json.name == 'foo'
    }

    void "test we can get the instance in a list query"() {
        when:
        def resp = graphQL.graphql("""
            {
              softDeleteList {
                name
              }
            }
        """)
        List json = resp.body().data.softDeleteList

        then:
        json.size() == 1
        json[0].name == 'foo'
    }

    void "test delete"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
              softDeleteDelete(id: $id) {
                success
              }
            }
        """)
        def json = resp.body().data.softDeleteDelete
        SoftDelete softDelete
        SoftDelete.withNewSession {
            softDelete = SoftDelete.get(id)
        }

        then:
        json.success
        !softDelete.active
        softDelete.name == "foo"
    }

    void "test we cant query the instance"() {
        when:
        def resp = graphQL.graphql("""
            {
              softDelete(id: $id) {
                name
              }
            }
        """)
        def json = resp.body().data.softDelete

        then:
        json == null
    }

    void "test we cant get the instance in a list query"() {
        when:
        def resp = graphQL.graphql("""
            {
              softDeleteList {
                name
              }
            }
        """)
        List json = resp.body().data.softDeleteList

        then:
        json.empty
    }
}
