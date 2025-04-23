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

import graphql.schema.GraphQLType
import groovy.transform.AutoClone
import groovy.transform.CompileStatic
import org.grails.gorm.graphql.entity.dsl.helpers.ComplexTyped
import org.grails.gorm.graphql.entity.dsl.helpers.ExecutesClosures
import org.grails.gorm.graphql.types.GraphQLOperationType
import org.grails.gorm.graphql.types.GraphQLPropertyType
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * Used to represent a custom property that has a custom (complex) type
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@AutoClone
@CompileStatic
class ComplexGraphQLProperty extends CustomGraphQLProperty<ComplexGraphQLProperty> implements ExecutesClosures {

    String typeName

    ComplexGraphQLProperty typeName(String typeName) {
        this.typeName = typeName
        this
    }

    @Override
    GraphQLType getGraphQLType(GraphQLTypeManager typeManager, GraphQLPropertyType propertyType) {
        String name = typeManager.namingConvention.getType(typeName, propertyType)

        if (propertyType.operationType == GraphQLOperationType.OUTPUT) {
            returns.buildCustomType(name, typeManager, mappingContext)
        }
        else {
            returns.buildCustomInputType(name, typeManager, mappingContext, nullable)
        }
    }

    private ComplexTyped returns = new Object().withTraits(ComplexTyped)

    void type(@DelegatesTo(value = ComplexTyped, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        withDelegate(closure, returns)
    }

    void validate() {
        super.validate()

        if (typeName == null) {
            throw new IllegalArgumentException('The type name must be specified for custom properties with a complex type')
        }
        if (returns.fields.empty) {
            throw new IllegalArgumentException("$name: At least 1 field is required for creating a custom property")
        }
    }
}
