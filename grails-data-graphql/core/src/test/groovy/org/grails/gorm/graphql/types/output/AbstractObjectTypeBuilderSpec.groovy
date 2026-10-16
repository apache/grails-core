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
package org.grails.gorm.graphql.types.output

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.gorm.graphql.Schema
import org.grails.gorm.graphql.types.output.inheritance.Dog
import org.grails.gorm.graphql.types.output.inheritance.InheritancePackage
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AbstractObjectTypeBuilderSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore
    @Shared GraphQLSchema schema

    void setupSpec() {
        hibernateDatastore = new HibernateDatastore(
                DatastoreUtils.createPropertyResolver(Collections.singletonMap(Settings.SETTING_DB_CREATE, 'create-drop')),
                InheritancePackage.getPackage())
        schema = new Schema(hibernateDatastore.mappingContext).generate()
    }

    void "test a root entity with child entities builds an interface type"() {
        expect:
        schema.getType('Animal') instanceof GraphQLInterfaceType
    }

    void "test a child entity builds an object type that implements the root interface"() {
        given:
        GraphQLInterfaceType animalType = (GraphQLInterfaceType) schema.getType('Animal')
        GraphQLObjectType dogType = (GraphQLObjectType) schema.getType('Dog')

        expect:
        dogType.interfaces.contains(animalType)
    }

    void "test querying the interface type resolves each result to its concrete object type"() {
        given:
        Dog.withNewTransaction {
            new Dog(name: 'Rex', breed: 'Labrador').save(flush: true, failOnError: true)
        }
        GraphQL graphQL = GraphQL.newGraphQL(schema).build()

        when:
        ExecutionResult result = graphQL.execute('{ animalList { __typename name ... on Dog { breed } } }')

        then:
        result.errors.empty
        result.getData() == [animalList: [[__typename: 'Dog', name: 'Rex', breed: 'Labrador']]]
    }
}
