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

import graphql.schema.DataFetcher
import graphql.schema.GraphQLOutputType
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.GraphQLServiceManager
import org.grails.gorm.graphql.binding.manager.GraphQLDataBinderManager
import org.grails.gorm.graphql.entity.dsl.helpers.Typed
import org.grails.gorm.graphql.fetcher.BindingGormDataFetcher
import org.grails.gorm.graphql.fetcher.DeletingGormDataFetcher
import org.grails.gorm.graphql.fetcher.PaginatingGormDataFetcher
import org.grails.gorm.graphql.response.delete.GraphQLDeleteResponseHandler
import org.grails.gorm.graphql.response.pagination.GraphQLPaginationResponseHandler
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * Used to create custom operations with simple types
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class SimpleOperation extends CustomOperation<SimpleOperation> implements Typed<SimpleOperation> {

    @Override
    protected GraphQLOutputType getType(GraphQLTypeManager typeManager, MappingContext mappingContext) {
        resolveOutputType(typeManager, mappingContext)
    }

    protected DataFetcher buildDataFetcher(PersistentEntity entity,
                                           GraphQLServiceManager serviceManager) {

        if (dataFetcher instanceof BindingGormDataFetcher) {
            BindingGormDataFetcher bindingFetcher = ((BindingGormDataFetcher) dataFetcher)
            if (bindingFetcher.dataBinder == null) {
                bindingFetcher.dataBinder = serviceManager.getService(GraphQLDataBinderManager).getDataBinder(returnType)
            }
        }
        if (dataFetcher instanceof DeletingGormDataFetcher) {
            DeletingGormDataFetcher deletingFetcher = ((DeletingGormDataFetcher) dataFetcher)
            if (deletingFetcher.responseHandler == null) {
                deletingFetcher.responseHandler = serviceManager.getService(GraphQLDeleteResponseHandler)
            }
        }
        if (dataFetcher instanceof PaginatingGormDataFetcher) {
            PaginatingGormDataFetcher paginatingFetcher = (PaginatingGormDataFetcher) dataFetcher
            if (paginatingFetcher.responseHandler == null) {
                paginatingFetcher.responseHandler = serviceManager.getService(GraphQLPaginationResponseHandler)
            }
        }

        super.buildDataFetcher(entity, serviceManager)
    }

    void validate() {
        super.validate()

        if (returnType == null) {
            throw new IllegalArgumentException('A return type is required for creating custom operations')
        }
    }
}
