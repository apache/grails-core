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

package org.grails.gorm.graphql.testing

import graphql.execution.MergedField
import graphql.language.Field
import spock.lang.Specification

class MockDataFetchingEnvironmentSpec extends Specification {

    void "test getMergedField builds a MergedField from fields when none is set explicitly"() {
        given:
        Field one = Field.newField('one').build()
        Field two = Field.newField('two').build()
        MockDataFetchingEnvironment environment = new MockDataFetchingEnvironment(fields: [one, two])

        expect:
        environment.getMergedField().fields == [one, two]
    }

    void "test getMergedField returns the explicitly set MergedField instead of building one from fields"() {
        given:
        Field ignored = Field.newField('ignored').build()
        MergedField explicit = MergedField.newMergedField(Field.newField('explicit').build()).build()
        MockDataFetchingEnvironment environment = new MockDataFetchingEnvironment(fields: [ignored], mergedField: explicit)

        expect:
        environment.getMergedField().is(explicit)
    }
}
