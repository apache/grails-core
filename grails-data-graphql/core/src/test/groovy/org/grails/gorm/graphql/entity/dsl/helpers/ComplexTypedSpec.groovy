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

package org.grails.gorm.graphql.entity.dsl.helpers

import graphql.Scalars
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import org.grails.datastore.mapping.model.MappingContext
import org.grails.gorm.graphql.entity.fields.SimpleField
import org.grails.gorm.graphql.types.GraphQLTypeManager
import spock.lang.Specification

class ComplexTypedSpec extends Specification {

    GraphQLTypeManager typeManager = Stub(GraphQLTypeManager) {
        hasType(String) >> true
        getType(String, _ as boolean) >> Scalars.GraphQLString
    }
    MappingContext mappingContext = Stub(MappingContext)

    ComplexTyped buildComplexTyped() {
        (ComplexTyped) new Object().withTraits(ComplexTyped)
    }

    void "test buildCustomInputType sets an externally provided default value on its fields"() {
        given:
        ComplexTyped complexTyped = buildComplexTyped()
        complexTyped.fields.add(new SimpleField().name('foo').returns(String).defaultValue('bar'))

        when:
        GraphQLInputObjectType type = (GraphQLInputObjectType) complexTyped.buildCustomInputType('Foo', typeManager, mappingContext, true)
        GraphQLInputObjectField field = type.getFieldDefinition('foo')

        then:
        field.inputFieldDefaultValue.set
        field.inputFieldDefaultValue.external
        field.inputFieldDefaultValue.value == 'bar'
    }

    void "test buildCustomInputType leaves the default value not set when none is configured"() {
        given:
        ComplexTyped complexTyped = buildComplexTyped()
        complexTyped.fields.add(new SimpleField().name('foo').returns(String))

        when:
        GraphQLInputObjectType type = (GraphQLInputObjectType) complexTyped.buildCustomInputType('Foo', typeManager, mappingContext, true)
        GraphQLInputObjectField field = type.getFieldDefinition('foo')

        then:
        !field.inputFieldDefaultValue.set
    }
}
