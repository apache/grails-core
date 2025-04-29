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
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.mapping.reflect.FieldEntityAccess

/**
 * A default data fetcher for persistent properties that
 * uses GORM instead of the standard reflection used by the
 * default {@link graphql.schema.PropertyDataFetcher}
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class PersistentPropertyDataFetcher implements DataFetcher {

    private String name
    private EntityReflector entityReflector

    PersistentPropertyDataFetcher(PersistentProperty property) {
        this.name = property.name
        this.entityReflector = FieldEntityAccess.getOrIntializeReflector(property.owner)
    }

    @Override
    Object get(DataFetchingEnvironment environment) {
        entityReflector.getPropertyReader(name).getter().invoke(environment.source)
    }
}
