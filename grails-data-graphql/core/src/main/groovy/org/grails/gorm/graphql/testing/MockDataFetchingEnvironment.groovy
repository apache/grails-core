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

package org.grails.gorm.graphql.testing

import graphql.GraphQLContext
import graphql.cachecontrol.CacheControl
import graphql.execution.ExecutionId
import graphql.execution.ExecutionStepInfo
import graphql.execution.MergedField
import graphql.execution.directives.QueryDirectives
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.OperationDefinition
import graphql.schema.*
import groovy.transform.CompileStatic
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry

/**
 * A class to use to provide a mock DataFetchingEnvironment to
 * test custom data fetchers.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class MockDataFetchingEnvironment implements DataFetchingEnvironment {

    Object source
    Object context
    Object localContext
    Map<String, Object> arguments = [:]
    List<Field> fields = []
    GraphQLOutputType fieldType
    GraphQLType parentType
    GraphQLSchema graphQLSchema
    Map<String, FragmentDefinition> fragmentsByName
    ExecutionId executionId
    DataLoaderRegistry dataLoaderRegistry
    CacheControl cacheControl
    OperationDefinition operationDefinition
    Locale locale
    DataFetchingFieldSelectionSet selectionSet
    GraphQLFieldDefinition fieldDefinition
    Object root
    MergedField mergedField
    Field field
    ExecutionStepInfo executionStepInfo
    Document document
    Map<String, Object> variables
    QueryDirectives queryDirectives

    @Override
    boolean containsArgument(String name) {
        arguments.containsKey(name)
    }

    @Override
    GraphQLContext getGraphQlContext() {
        GraphQLContext.newContext().build()
    }

    @Override
    Object getArgumentOrDefault(String name, Object defaultValue) {
        arguments.getOrDefault(name, defaultValue)
    }

    @Override
    Object getLocalContext() {
        localContext
    }

    @Override
    MergedField getMergedField() {
        MergedField.newMergedField(fields).build()
    }

    @Override
    QueryDirectives getQueryDirectives() {
        queryDirectives
    }

    @Override
    def <K, V> DataLoader<K,V> getDataLoader(String dataLoaderName) {
        dataLoaderRegistry ? dataLoaderRegistry.getDataLoader(dataLoaderName) : null
    }

    @Override
    CacheControl getCacheControl() {
        cacheControl
    }

    @Override
    Locale getLocale() {
        locale
    }

    @Override
    OperationDefinition getOperationDefinition() {
         operationDefinition
    }

    @Override
    Document getDocument() {
        document
    }

    @Override
    Map<String, Object> getVariables() {
        variables
    }

    @Override
    Object getArgument(String name) {
        arguments.get(name)
    }
}
