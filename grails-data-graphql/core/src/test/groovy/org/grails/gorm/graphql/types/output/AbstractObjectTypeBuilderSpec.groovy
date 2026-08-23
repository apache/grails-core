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

import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.gorm.graphql.Schema
import org.grails.gorm.graphql.domain.inheritance.InheritancePackage
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Exercises AbstractObjectTypeBuilder#build(PersistentEntity) through a real
 * Schema, covering the interface-type branch (a root entity with child
 * entities) which none of the other object type builder specs reach, since
 * they construct their builders with a null GraphQLTypeManager.
 */
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

    void "test a type resolver is registered for the interface type"() {
        given:
        GraphQLInterfaceType animalType = (GraphQLInterfaceType) schema.getType('Animal')

        expect:
        schema.codeRegistry.getTypeResolver(animalType) != null
    }
}
