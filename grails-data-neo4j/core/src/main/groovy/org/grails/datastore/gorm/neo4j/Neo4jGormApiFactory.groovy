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
package org.grails.datastore.gorm.neo4j

import groovy.transform.CompileStatic

import org.grails.datastore.gorm.DatastoreResolver
import org.grails.datastore.gorm.DefaultGormApiFactory
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.gorm.neo4j.api.Neo4jGormStaticApi
import org.grails.datastore.mapping.model.MappingContext

/**
 * Neo4j-specific {@link org.grails.datastore.gorm.GormApiFactory} that creates a
 * {@link Neo4jGormStaticApi} instead of the generic {@code GormStaticApi}. Without it the
 * {@link GormRegistry} falls back to the {@link DefaultGormApiFactory}, whose generic
 * {@code GormStaticApi} breaks casts like {@code (Neo4jGormStaticApi) GormEnhancer.findStaticApi(...)}
 * used by {@code Neo4jEntity}/{@code Node} for {@code cypherStatic}, {@code findRelationship},
 * {@code findPath}, etc. The instance and validation APIs reuse the generic GORM implementations
 * inherited from {@link DefaultGormApiFactory}, matching {@code Neo4jDatastore}'s existing
 * {@code GormEnhancer} overrides.
 */
@CompileStatic
class Neo4jGormApiFactory extends DefaultGormApiFactory {

    @Override
    <D> Neo4jGormStaticApi<D> createStaticApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver resolver, String qualifier, GormRegistry registry) {
        Neo4jDatastore neo4jDatastore = (Neo4jDatastore) resolver.resolve()
        List<FinderMethod> finders = createDynamicFinders(resolver, mappingContext)
        return new Neo4jGormStaticApi<D>(persistentClass, neo4jDatastore, finders, neo4jDatastore.getTransactionManager())
    }
}
