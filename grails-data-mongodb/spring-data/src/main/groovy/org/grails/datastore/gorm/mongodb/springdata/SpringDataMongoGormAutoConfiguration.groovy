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
package org.grails.datastore.gorm.mongodb.springdata

import groovy.transform.CompileStatic

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.transaction.PlatformTransactionManager

import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * Auto-configuration that wires Spring Data MongoDB on top of the same {@link MongoDatastore}
 * (and therefore the same {@code MongoClient}, database and codecs) that GORM for MongoDB uses,
 * and registers a primary transaction manager that lets GORM and Spring Data operations share a
 * single MongoDB transaction.
 *
 * <p>Activates only when Spring Data MongoDB is on the classpath and a GORM {@link MongoDatastore}
 * bean is present. All beans are conditional on the application not already defining them, so an
 * application that configures its own {@code MongoTemplate}, factory or transaction manager wins.</p>
 *
 * @since 8.0
 */
@CompileStatic
@AutoConfiguration
@ConditionalOnClass(MongoTemplate)
@ConditionalOnBean(MongoDatastore)
@AutoConfigureAfter(name = 'org.grails.datastore.gorm.mongodb.boot.autoconfigure.MongoDbGormAutoConfiguration')
class SpringDataMongoGormAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MongoDatabaseFactory)
    MongoDatabaseFactory mongoDatabaseFactory(MongoDatastore mongoDatastore) {
        // SimpleMongoClientDatabaseFactory(MongoClient, String) does not own the client - its
        // destroy() will not close GORM's MongoClient, so the client's lifecycle stays with GORM.
        new SimpleMongoClientDatabaseFactory(mongoDatastore.getMongoClient(), mongoDatastore.getDefaultDatabase())
    }

    @Bean
    @ConditionalOnMissingBean(MongoCustomConversions)
    MongoCustomConversions mongoCustomConversions() {
        new MongoCustomConversions([])
    }

    @Bean
    @ConditionalOnMissingBean(MongoMappingContext)
    MongoMappingContext springDataMongoMappingContext(MongoCustomConversions conversions) {
        MongoMappingContext context = new MongoMappingContext()
        context.setSimpleTypeHolder(conversions.simpleTypeHolder)
        context.afterPropertiesSet()
        return context
    }

    @Bean
    @ConditionalOnMissingBean(MappingMongoConverter)
    MappingMongoConverter mappingMongoConverter(MongoDatabaseFactory databaseFactory,
                                                MongoMappingContext mappingContext,
                                                MongoCustomConversions conversions) {
        MappingMongoConverter converter = new MappingMongoConverter(new DefaultDbRefResolver(databaseFactory), mappingContext)
        converter.setCustomConversions(conversions)
        // Share the driver-level CodecRegistry carried by GORM's MongoClient.
        converter.setCodecRegistryProvider(databaseFactory)
        converter.afterPropertiesSet()
        return converter
    }

    @Bean(name = 'mongoTemplate')
    @ConditionalOnMissingBean(MongoOperations)
    MongoTemplate mongoTemplate(MongoDatabaseFactory databaseFactory, MappingMongoConverter converter) {
        new MongoTemplate(databaseFactory, converter)
    }

    @Bean(name = 'transactionManager')
    @Primary
    @ConditionalOnMissingBean(name = 'transactionManager')
    PlatformTransactionManager transactionManager(MongoDatastore mongoDatastore, MongoDatabaseFactory databaseFactory) {
        new GormSharedSessionMongoTransactionManager(mongoDatastore, databaseFactory)
    }
}
