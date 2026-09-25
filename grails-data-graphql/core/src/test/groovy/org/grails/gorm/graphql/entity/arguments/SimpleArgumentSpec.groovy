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

package org.grails.gorm.graphql.entity.arguments

import graphql.Scalars
import graphql.schema.GraphQLArgument
import org.grails.datastore.mapping.model.MappingContext
import org.grails.gorm.graphql.types.GraphQLTypeManager
import spock.lang.Specification

class SimpleArgumentSpec extends Specification {

    GraphQLTypeManager typeManager = Stub(GraphQLTypeManager) {
        hasType(String) >> true
        getType(String, _ as boolean) >> Scalars.GraphQLString
    }
    MappingContext mappingContext = Stub(MappingContext)

    void "test getArgument sets an externally provided default value"() {
        given:
        SimpleArgument argument = new SimpleArgument()
                .name('foo')
                .description('a foo argument')
                .returns(String)
                .defaultValue('bar')

        when:
        GraphQLArgument built = argument.getArgument(typeManager, mappingContext).build()

        then:
        built.name == 'foo'
        built.description == 'a foo argument'
        built.argumentDefaultValue.set
        built.argumentDefaultValue.external
        built.argumentDefaultValue.value == 'bar'
    }

    void "test getArgument leaves the default value not set when none is configured"() {
        given:
        SimpleArgument argument = new SimpleArgument()
                .name('foo')
                .returns(String)

        when:
        GraphQLArgument built = argument.getArgument(typeManager, mappingContext).build()

        then:
        !built.argumentDefaultValue.set
    }

    void "test validate requires a return type"() {
        given:
        SimpleArgument argument = new SimpleArgument().name('foo')

        when:
        argument.validate()

        then:
        thrown(IllegalArgumentException)
    }

    void "test validate passes when a name and return type are set"() {
        given:
        SimpleArgument argument = new SimpleArgument().name('foo').returns(String)

        when:
        argument.validate()

        then:
        noExceptionThrown()
    }
}
