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

package org.grails.gorm.graphql.types.input

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ManyToOne
import org.grails.gorm.graphql.types.GraphQLPropertyType
import org.grails.gorm.graphql.entity.property.manager.GraphQLDomainPropertyManager
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * The class used to define which properties are available
 * when providing an object as a part of a parent object
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class NestedInputObjectTypeBuilder extends AbstractInputObjectTypeBuilder {

    NestedInputObjectTypeBuilder(GraphQLDomainPropertyManager propertyManager, GraphQLTypeManager typeManager, GraphQLPropertyType type) {
        super(propertyManager, typeManager)
        this.type = type
    }

    GraphQLDomainPropertyManager.Builder builder
    GraphQLPropertyType type

    {
        builder = propertyManager.builder()
                .excludeTimestamps()
                .excludeVersion()
                .alwaysNullable()
                .condition { PersistentProperty prop ->
                    if (prop instanceof Association) {
                        Association association = (Association)prop
                        boolean owningSide
                        if (association instanceof ManyToOne) {
                            owningSide = false
                        } else {
                            owningSide = association.owningSide
                        }
                        (owningSide || !association.bidirectional)
                    } else {
                        true
                    }
                }
    }

}
