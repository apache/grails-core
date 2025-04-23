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

package org.grails.gorm.graphql.interceptor

import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLType
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Interface to describe a class that can modify the fields and types used
 * to build the GraphQL schema.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLSchemaInterceptor {

    /**
     * Executed for each entity mapped with GraphQL. The fields are mutable
     * and their changes will be applied to the schema.
     *
     * @param entity The entity being processed
     * @param queryFields The query fields associated with the entity
     * @param mutationFields The query fields associated with the entity
     */
    void interceptEntity(PersistentEntity entity,
                   List<GraphQLFieldDefinition.Builder> queryFields,
                   List<GraphQLFieldDefinition.Builder> mutationFields)

    /**
     * Executed a single time before the schema is created. The types are
     * mutable and their changes will be applied in the schema.
     *
     * @param queryType The root query returnType
     * @param mutationType The root mutation returnType
     */
    void interceptSchema(GraphQLObjectType.Builder queryType,
                         GraphQLObjectType.Builder mutationType,
                         Set<GraphQLType> additionalTypes)
}
