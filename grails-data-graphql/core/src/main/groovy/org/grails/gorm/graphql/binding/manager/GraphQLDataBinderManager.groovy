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

package org.grails.gorm.graphql.binding.manager

import org.grails.gorm.graphql.binding.GraphQLDataBinder

/**
 * An interface to describe a manager that will store
 * and return instances of data binders to be used
 * with GraphQL operations on GORM entities
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLDataBinderManager {

    /**
     * Register a data binder for use with the provided class
     *
     * @param clazz The class to be bound
     * @param dataBinder The data binding instance to be used
     */
    void registerDataBinder(Class clazz, GraphQLDataBinder dataBinder)

    /**
     * Returns a data binder to be used for the provided class
     *
     * @param clazz The class to be bound
     * @return The data binding instance to be used
     */
    GraphQLDataBinder getDataBinder(Class clazz)
}
