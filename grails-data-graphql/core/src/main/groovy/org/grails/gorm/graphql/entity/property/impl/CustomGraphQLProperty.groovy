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

package org.grails.gorm.graphql.entity.property.impl

import graphql.schema.DataFetcher
import graphql.schema.GraphQLType
import groovy.transform.AutoClone
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.MappingContext
import org.grails.gorm.graphql.entity.dsl.helpers.Arguable
import org.grails.gorm.graphql.entity.dsl.helpers.Deprecatable
import org.grails.gorm.graphql.entity.dsl.helpers.Describable
import org.grails.gorm.graphql.entity.dsl.helpers.Named
import org.grails.gorm.graphql.entity.dsl.helpers.Nullable
import org.grails.gorm.graphql.entity.property.GraphQLDomainProperty
import org.grails.gorm.graphql.fetcher.impl.ClosureDataFetcher
import org.grails.gorm.graphql.types.GraphQLPropertyType
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * Implementation of {@link GraphQLDomainProperty} to be used to define
 * additional properties beyond the ones defined in GORM entities
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@AutoClone
@CompileStatic
abstract class CustomGraphQLProperty<T> extends OrderedGraphQLProperty implements Named<T>, Describable<T>, Deprecatable<T>, Nullable<T>, Arguable<T> {

    Integer order = null
    boolean input = true
    boolean output = true
    Closure closureDataFetcher = null

    T dataFetcher(Closure dataFetcher) {
        this.closureDataFetcher = dataFetcher
        (T)this
    }

    T input(boolean input) {
        this.input = input
        (T)this
    }

    T output(boolean output) {
        this.output = output
        (T)this
    }
    
    T order(Integer order) {
        this.order = order
        (T)this
    }
    
    //should be set by the property manager
    protected MappingContext mappingContext

    void setMappingContext(MappingContext mappingContext) {
        this.mappingContext = mappingContext
    }

    @Override
    abstract GraphQLType getGraphQLType(GraphQLTypeManager typeManager, GraphQLPropertyType propertyType)

    DataFetcher getDataFetcher() {
        closureDataFetcher ? new ClosureDataFetcher(closureDataFetcher) : null
    }

    void validate() {
        if (name == null) {
            throw new IllegalArgumentException('A name is required for creating custom properties')
        }
    }

}
