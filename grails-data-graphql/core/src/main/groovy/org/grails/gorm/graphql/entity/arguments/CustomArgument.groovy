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

package org.grails.gorm.graphql.entity.arguments

import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLInputType
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.MappingContext
import org.grails.gorm.graphql.entity.dsl.helpers.Defaultable
import org.grails.gorm.graphql.entity.dsl.helpers.Describable
import org.grails.gorm.graphql.entity.dsl.helpers.Named
import org.grails.gorm.graphql.entity.dsl.helpers.Nullable
import org.grails.gorm.graphql.types.GraphQLTypeManager

import static graphql.schema.GraphQLArgument.newArgument

/**
 * Describes an argument to a custom operation
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
abstract class CustomArgument<T> implements Named<T>, Describable<T>, Nullable<T>, Defaultable<T> {

    CustomArgument() {
        nullable = false
    }

    abstract GraphQLInputType getType(GraphQLTypeManager typeManager, MappingContext mappingContext)

    GraphQLArgument.Builder getArgument(GraphQLTypeManager typeManager, MappingContext mappingContext) {
        GraphQLInputType type = getType(typeManager, mappingContext)

        newArgument()
            .name(name)
            .description(description)
            .defaultValue(defaultValue)
            .type(type)
    }

    void validate() {
        if (name == null) {
            throw new IllegalArgumentException('A name is required for creating custom operations')
        }
    }
}
