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

package org.grails.gorm.graphql.response.pagination

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLOutputType
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * Defines how a pagination response is defined and built
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLPaginationResponseHandler {

    /**
     * Creates the fields to be used in the schema object for a pagination response
     *
     * @param resultsType The graphql type of the results
     * @param typeManager The type manager
     * @return The GraphQL type
     */
    List<GraphQLFieldDefinition> getFields(GraphQLOutputType resultsType, GraphQLTypeManager typeManager)

    /**
     * @return The description to use in the schema, or null
     */
    String getDescription(PersistentEntity entity)

    /**
     * Create the response data to be sent to the client
     *
     * @param environment The data fetching environment
     * @param results The data retrieved from the query
     * @return Response data
     */
    Object createResponse(DataFetchingEnvironment environment, PaginationResult result)

    /**
     * @return The default maximum value if none provided
     */
    int getDefaultMax()

    /**
     * @return The default offset value if none provided
     */
    int getDefaultOffset()
}
