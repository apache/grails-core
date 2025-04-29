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

package org.grails.gorm.graphql.entity.fields

import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLOutputType
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.MappingContext
import org.grails.gorm.graphql.entity.dsl.helpers.ComplexTyped
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * A class used to represent a field that has a custom (complex) type
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class ComplexField extends Field<ComplexField> implements ComplexTyped<ComplexField> {

    String typeName

    ComplexField typeName(String typeName) {
        this.typeName = typeName
        this
    }

    @Override
    GraphQLOutputType getType(GraphQLTypeManager typeManager, MappingContext mappingContext) {
        buildCustomType(typeName, typeManager, mappingContext)
    }

    @Override
    GraphQLInputType getInputType(GraphQLTypeManager typeManager, MappingContext mappingContext) {
        buildCustomInputType(typeName + 'Input', typeManager, mappingContext, nullable)
    }

    @Override
    void validate() {
    	super.validate()
    	if (typeName == null) {
            throw new IllegalArgumentException('The type name must be specified for fields with a complex type')
        }
        if (fields.empty) {
            throw new IllegalArgumentException('At least 1 field is required for fields with a complex type')
        }
    }
}
