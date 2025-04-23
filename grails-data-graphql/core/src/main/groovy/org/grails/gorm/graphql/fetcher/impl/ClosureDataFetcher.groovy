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

package org.grails.gorm.graphql.fetcher.impl

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEntity
import org.grails.gorm.graphql.entity.EntityFetchOptions

/**
 * A class to retrieve data from the environment source
 * with a closure.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class ClosureDataFetcher implements DataFetcher<Object> {

    private Closure closure
    private Class domainType
    private boolean initialized
    private EntityFetchOptions fetchOptions

    ClosureDataFetcher(Closure closure, Class domainType = null) {
        this.closure = closure
        this.domainType = domainType
    }

    @Override
    Object get(DataFetchingEnvironment environment) {
        Object source = environment.source
        if (closure.maximumNumberOfParameters == 2) {
            closure.call(source, new ClosureDataFetchingEnvironment(environment, domainType))
        }
        else {
            closure.call(source)
        }
    }

    EntityFetchOptions buildFetchOptions() {
        if (initialized) {
            return fetchOptions
        }
        if (domainType != null && GormEntity.isAssignableFrom(domainType)) {
            fetchOptions = new EntityFetchOptions(domainType)
        }
        initialized = true
        fetchOptions
    }
}
