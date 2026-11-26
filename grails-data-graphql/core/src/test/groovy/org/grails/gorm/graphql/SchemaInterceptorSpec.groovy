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
package org.grails.gorm.graphql

import graphql.Scalars
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLType
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.domain.general.GeneralPackage
import org.grails.gorm.graphql.domain.hibernate.HibernatePackage
import org.grails.gorm.graphql.interceptor.GraphQLSchemaInterceptor
import org.grails.gorm.graphql.interceptor.manager.DefaultGraphQLInterceptorManager
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SchemaInterceptorSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore

    void setupSpec() {
        hibernateDatastore = new HibernateDatastore(
                DatastoreUtils.createPropertyResolver(Collections.singletonMap(Settings.SETTING_DB_CREATE, 'create-drop')),
                GeneralPackage.getPackage(), HibernatePackage.getPackage())
    }

    void 'additional types from a schema interceptor are added to the schema, unwrapped from list and non-null'() {
        given:
        GraphQLObjectType standalone = objectType('Standalone')
        GraphQLObjectType wrapped = objectType('Wrapped')
        def interceptorManager = new DefaultGraphQLInterceptorManager()
        interceptorManager.registerInterceptor(new GraphQLSchemaInterceptor() {
            @Override
            void interceptEntity(PersistentEntity entity,
                                 List<GraphQLFieldDefinition.Builder> queryFields,
                                 List<GraphQLFieldDefinition.Builder> mutationFields) {
            }

            @Override
            void interceptSchema(GraphQLObjectType.Builder queryType,
                                 GraphQLObjectType.Builder mutationType,
                                 Set<GraphQLType> additionalTypes) {
                additionalTypes << standalone
                additionalTypes << GraphQLNonNull.nonNull(GraphQLList.list(wrapped))
            }
        })
        def schema = new Schema(hibernateDatastore.mappingContext)
        schema.interceptorManager = interceptorManager

        when:
        GraphQLSchema graphQLSchema = schema.generate()

        then:
        graphQLSchema.getType('Standalone') == standalone
        graphQLSchema.getType('Wrapped') == wrapped
    }

    private static GraphQLObjectType objectType(String name) {
        GraphQLObjectType.newObject()
                .name(name)
                .field(GraphQLFieldDefinition.newFieldDefinition().name('value').type(Scalars.GraphQLString))
                .build()
    }
}
