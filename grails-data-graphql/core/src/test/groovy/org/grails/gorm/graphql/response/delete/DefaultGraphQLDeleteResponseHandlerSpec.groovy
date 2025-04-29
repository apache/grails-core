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

package org.grails.gorm.graphql.response.delete

import graphql.Scalars
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import org.grails.gorm.graphql.testing.GraphQLSchemaSpec
import org.grails.gorm.graphql.types.GraphQLTypeManager
import spock.lang.Shared
import spock.lang.Specification

class DefaultGraphQLDeleteResponseHandlerSpec extends Specification implements GraphQLSchemaSpec {

    GraphQLDeleteResponseHandler handler

    @Shared GraphQLTypeManager typeManager

    void setupSpec() {
        typeManager = Stub(GraphQLTypeManager) {
            getType(Boolean, false) >> {
                GraphQLNonNull.nonNull(Scalars.GraphQLBoolean)
            }
            getType(String) >> {
                Scalars.GraphQLString
            }
        }
    }

    void setup() {
        handler = new DefaultGraphQLDeleteResponseHandler()
    }

    void "test the result is cached"() {
        expect:
        handler.getObjectType(typeManager) == handler.getObjectType(typeManager)
    }

    void "test the return data"() {
        expect:
        handler.createResponse(null, false, null) == [success: false, error: null]
        handler.createResponse(null, true, null) == [success: true, error: null]
        handler.createResponse(null, true, new RuntimeException('exception')) == [success: true, error: 'exception']
    }

    void "test the object type definition"() {
        GraphQLObjectType type = handler.getObjectType(typeManager)

        expect:
        type.name == 'DeleteResult'
        type.description == 'Whether or not the operation was successful'
        type.interfaces.empty
        type.fieldDefinitions.size() == 2
        type.fieldDefinitions[0].name == 'success'
        type.fieldDefinitions[1].name == 'error'
        unwrap(null, type.fieldDefinitions[0].type) == Scalars.GraphQLBoolean
        type.fieldDefinitions[1].type == Scalars.GraphQLString
    }
}
