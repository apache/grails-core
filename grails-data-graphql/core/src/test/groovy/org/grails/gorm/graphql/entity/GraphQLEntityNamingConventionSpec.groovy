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

package org.grails.gorm.graphql.entity

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.types.GraphQLPropertyType
import spock.lang.Shared
import spock.lang.Specification

class GraphQLEntityNamingConventionSpec extends Specification {

    @Shared GraphQLEntityNamingConvention namingConvention
    @Shared PersistentEntity entity

    @SuppressWarnings('UnnecessaryGetter')
    void setupSpec() {
        namingConvention = new GraphQLEntityNamingConvention()
        entity = Stub(PersistentEntity) {
            getDecapitalizedName() >> 'foo'
            getJavaClass() >> Foo
        }
    }

    void "test naming conventions"() {
        expect:
        namingConvention.getGet(entity) == 'foo'
        namingConvention.getList(entity) == 'fooList'
        namingConvention.getCreate(entity) == 'fooCreate'
        namingConvention.getUpdate(entity) == 'fooUpdate'
        namingConvention.getDelete(entity) == 'fooDelete'

        when:
        String name = namingConvention.getType(entity, type)

        then:
        name == expected

        where:
        type                                | expected
        GraphQLPropertyType.CREATE          | 'FooCreate'
        GraphQLPropertyType.CREATE_EMBEDDED | 'FooCreateEmbedded'
        GraphQLPropertyType.CREATE_NESTED   | 'FooCreateNested'
        GraphQLPropertyType.UPDATE          | 'FooUpdate'
        GraphQLPropertyType.UPDATE_EMBEDDED | 'FooUpdateEmbedded'
        GraphQLPropertyType.UPDATE_NESTED   | 'FooUpdateNested'
        GraphQLPropertyType.OUTPUT          | 'Foo'
        GraphQLPropertyType.OUTPUT_EMBEDDED | 'FooEmbedded'
    }

    class Foo {

    }
}
