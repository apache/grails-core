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
import grails.testing.spock.OnceBefore
import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import spock.lang.Shared
import spock.lang.Specification

@Integration
class ArguedFieldIntegrationSpec extends Specification implements GraphQLSpec {

    @Shared Long grailsId

    @OnceBefore
    void createDomain() {
        ArguedField.withTransaction {
            grailsId = new ArguedField(name: 'test').save(flush: true).id
        }

    }

    void "test a simple argument"() {
        when:
        def resp = graphQL.graphql("""
            {
              arguedField(id: ${grailsId}) {
                  withArgument(ping: "PONG")
              }
            }
        """)
        def obj = resp.body().data.arguedField

        then:
        obj.withArgument == "PONG"
    }

    void "test a simple argument list"() {
        when:
        def resp = graphQL.graphql("""
            {
              arguedField(id: ${grailsId}) {
                  withArgumentList(pings: ["P", "O", "N", "G" ])
              }
            }
        """)
        def obj = resp.body().data.arguedField

        then:
        obj.withArgumentList == "P-O-N-G"
    }

    void "test a custom argument"() {
        when:
        def resp = graphQL.graphql("""
            {
              arguedField(id: ${grailsId}) {
                  withCustomArgument(ping: {payload: "PONG"})
              }
            }
        """)
        def obj = resp.body().data.arguedField

        then:
        obj.withCustomArgument == "PONG"
    }

}
