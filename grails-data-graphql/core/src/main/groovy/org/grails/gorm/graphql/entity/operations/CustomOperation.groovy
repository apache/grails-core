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

package org.grails.gorm.graphql.entity.operations

import graphql.schema.*
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.GraphQLServiceManager
import org.grails.gorm.graphql.entity.dsl.helpers.Arguable
import org.grails.gorm.graphql.entity.dsl.helpers.Deprecatable
import org.grails.gorm.graphql.entity.dsl.helpers.Describable
import org.grails.gorm.graphql.entity.dsl.helpers.ExecutesClosures
import org.grails.gorm.graphql.entity.dsl.helpers.Named
import org.grails.gorm.graphql.entity.arguments.CustomArgument
import org.grails.gorm.graphql.fetcher.interceptor.CustomMutationInterceptorInvoker
import org.grails.gorm.graphql.fetcher.interceptor.CustomQueryInterceptorInvoker
import org.grails.gorm.graphql.fetcher.interceptor.InterceptingDataFetcher
import org.grails.gorm.graphql.fetcher.interceptor.InterceptorInvoker
import org.grails.gorm.graphql.types.GraphQLTypeManager

import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition

/**
 * This class stores data about custom query operations
 * that users provide in the mapping of the entity.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
abstract class CustomOperation<T> implements Named<T>, Describable<T>, Deprecatable<T>, Arguable<T>, ExecutesClosures {

    private static InterceptorInvoker queryInvoker = new CustomQueryInterceptorInvoker()
    private static InterceptorInvoker mutationInvoker = new CustomMutationInterceptorInvoker()
    DataFetcher dataFetcher
    boolean defaultListArguments = false

    T dataFetcher(DataFetcher<?> dataFetcher) {
        this.dataFetcher = dataFetcher
        (T)this
    }

    OperationType operationType

    /**
     * If the argument is true, the default list arguments created in the
     * schema through configuration will be prepended to any other
     * arguments defined for the operation.
     * (max, offset, sort, order, etc..)
     *
     * @param useDefaultListArguments Whether to use the default list args
     * @return The operation in order to chain method calls
     */
    CustomOperation defaultListArguments(boolean useDefaultListArguments = true) {
        if (operationType == OperationType.MUTATION && useDefaultListArguments) {
            throw new UnsupportedOperationException('The default list arguments are only supported for query operations')
        }
        this.defaultListArguments = useDefaultListArguments
        this
    }

    protected abstract GraphQLOutputType getType(GraphQLTypeManager typeManager, MappingContext mappingContext)

    void validate() {
        if (name == null) {
            throw new IllegalArgumentException('A name is required for creating custom operations')
        }
        if (dataFetcher == null) {
            throw new IllegalArgumentException('A data fetcher is required for creating custom operations')
        }
    }

    protected DataFetcher buildDataFetcher(PersistentEntity entity,
                                           GraphQLServiceManager serviceManager) {
        InterceptorInvoker interceptorInvoker = null
        if (operationType == OperationType.QUERY) {
            interceptorInvoker = queryInvoker
        }
        else if (operationType == OperationType.MUTATION) {
            interceptorInvoker = mutationInvoker
        }

        new InterceptingDataFetcher(entity, serviceManager, interceptorInvoker, null, dataFetcher)
    }

    /**
     * Creates the field to be added to the query or mutation returnType in the schema.
     *
     * @param entity The persistent entity the operation belongs to
     * @param typeManager The returnType manager
     * @param interceptorManager The interceptor manager to be used for executing
     * interceptors with the custom data fetcher
     * @param mappingContext The mapping context
     * @return The custom field
     */
    GraphQLFieldDefinition.Builder createField(PersistentEntity entity,
                                               GraphQLServiceManager serviceManager,
                                               MappingContext mappingContext,
                                               Map<String, GraphQLInputType> listArguments) {

        validate()

        GraphQLTypeManager typeManager = serviceManager.getService(GraphQLTypeManager)

        GraphQLOutputType outputType = getType(typeManager, mappingContext)

        GraphQLFieldDefinition.Builder customQuery = newFieldDefinition()
                .name(name)
                .type(outputType)
                .description(description)
                .deprecate(deprecationReason)
                .dataFetcher(buildDataFetcher(entity, serviceManager))

        if (defaultListArguments) {
            for (Map.Entry<String, GraphQLInputType> argument: listArguments) {
                customQuery.argument(newArgument()
                           .name(argument.key)
                           .type(argument.value))
            }
        }

        if (!arguments.empty) {
            for (CustomArgument argument: arguments) {
                customQuery.argument(argument.getArgument(typeManager, mappingContext))
            }
        }

        customQuery
    }
}
