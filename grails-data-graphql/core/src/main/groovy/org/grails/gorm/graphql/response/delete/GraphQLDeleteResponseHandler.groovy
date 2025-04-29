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

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLObjectType
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * Responsible for determining the data available in a GraphQL delete mutation response
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLDeleteResponseHandler {

    /**
     * Creates the schema object for a delete response
     *
     * @param typeManager The type manager
     * @return The GraphQL type
     */
    GraphQLObjectType getObjectType(GraphQLTypeManager typeManager)

    /**
     * Create the response data to be sent to the client
     *
     * @param environment The data fetching environment
     * @param success Whether or not the operation was successful
     * @param exception If not successful, the exception that occurred
     * @return Response data
     */
    Object createResponse(DataFetchingEnvironment environment, boolean success, Exception exception)
}
