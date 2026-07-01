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
package grails.plugins.mongodb

import groovy.transform.CompileStatic

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Lazy

import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoMappingContext

/**
 * Registers {@code mongoMappingContext} (aliased to {@code grailsDomainClassMappingContext}) <em>before</em>
 * {@code GrailsDomainClassAutoConfiguration}, so its framework fallback — now
 * {@code @ConditionalOnMissingBean(name = "grailsDomainClassMappingContext")} — backs off instead of leaving an
 * orphaned definition that the datastore's alias later shadows.
 *
 * <p>The bean is <strong>lazy</strong> and delegates to the datastore's own context. Its definition exists early
 * (satisfying the back-off), but it is not resolved until first use — by which point
 * {@code MongoDbDataStoreSpringInitializer} has registered {@code mongoDatastore}. So the shared context is the
 * datastore's own, built from the autowired connection sources; Spring-registered codecs and custom type
 * marshallers are honoured, exactly as when the initializer registers the context itself. There is no
 * independently-built context.
 *
 * <p>Ordering is declared {@code beforeName} rather than as a class reference so this module need not put
 * {@code grails-domain-class} on its compile classpath.
 *
 * <p>Engages only when MongoDB is the primary datastore, i.e. no GORM-Hibernate is present
 * ({@code @ConditionalOnMissingClass}). This mirrors {@code MongodbGrailsPlugin}'s
 * {@code setSecondaryDatastore(hasHibernatePlugin())}: when Hibernate is present MongoDB is secondary, must not own
 * {@code grailsDomainClassMappingContext}, and the initializer keeps its own path (registering the context without
 * the alias).
 */
@CompileStatic
@AutoConfiguration(beforeName = 'org.grails.plugins.domain.GrailsDomainClassAutoConfiguration')
@ConditionalOnClass(MongoDatastore)
@ConditionalOnMissingClass('org.grails.orm.hibernate.HibernateDatastore')
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class MongoMappingContextAutoConfiguration {

    @Lazy
    @Bean(name = ['mongoMappingContext', 'grailsDomainClassMappingContext'])
    MongoMappingContext mongoMappingContext(MongoDatastore mongoDatastore) {
        (MongoMappingContext) mongoDatastore.mappingContext
    }
}
