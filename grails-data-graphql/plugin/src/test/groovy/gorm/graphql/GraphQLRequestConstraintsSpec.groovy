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

package gorm.graphql

import org.grails.gorm.graphql.plugin.GraphQLRequest
import spock.lang.Specification
import spock.lang.Subject

class GraphQLRequestConstraintsSpec extends Specification {

    @Subject
    GraphQLRequest graphQLRequest = new GraphQLRequest()

    void "GraphQLExecutionRequest.query cannot be null"() {
        when:
        graphQLRequest.query = null

        then:
        !graphQLRequest.validate(['query'])
    }

    void "GraphQLExecutionRequest.operationName can be null"() {
        when:
        graphQLRequest.operationName = null

        then:
        graphQLRequest.validate(['operationName'])
    }

    void "GraphQLExecutionRequest.variables cannot be null"() {
        when:
        graphQLRequest.variables = null

        then:
        !graphQLRequest.validate(['variables'])
    }

    void "GraphQLExecutionRequest.variables can be an empty Map"() {
        when:
        graphQLRequest.variables = [:]

        then:
        graphQLRequest.validate(['variables'])
    }
}
