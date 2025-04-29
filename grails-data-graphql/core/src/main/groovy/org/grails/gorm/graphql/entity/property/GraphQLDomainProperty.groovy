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

package org.grails.gorm.graphql.entity.property

import graphql.schema.DataFetcher
import graphql.schema.GraphQLType
import org.grails.gorm.graphql.types.GraphQLPropertyType
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * An interface to describe a property to be used in the
 * creation of a GraphQL schema
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLDomainProperty {

    /**
     * @return The name of the property
     */
    String getName()

    /**
     * @param typeManager The returnType manager used to retrieve GraphQL types
     * @param propertyType The returnType of property being created
     * @return The GraphQLType representing the property
     */
    GraphQLType getGraphQLType(GraphQLTypeManager typeManager, GraphQLPropertyType propertyType)

    /**
     * @return The description of the property
     */
    String getDescription()

    /**
     * @return True if the property is deprecated
     */
    boolean isDeprecated()

    /**
     * @return The reason why the property is deprecated, or null if it isn't
     */
    String getDeprecationReason()

    /**
     * @return True if the property is to be used for input operations (CREATE/UPDATE)
     */
    boolean isInput()

    /**
     * @return True if the property is to be used for output operations (GET/LIST)
     */
    boolean isOutput()

    /**
     * @return True if the property allows nulls
     */
    boolean isNullable()

    /**
     * @return The closure to retrieve the data for the property. If not null, it
     * will be used to create a {@link org.grails.gorm.graphql.fetcher.impl.ClosureDataFetcher},
     * otherwise the default fetcher will be used.
     */
    DataFetcher getDataFetcher()

}
