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
package grails.mongodb.bootstrap

import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import grails.mongodb.MongoEntity
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.PropertyResolver
import org.springframework.core.env.StandardEnvironment
import spock.lang.Specification

/**
 * Pure unit coverage for {@link MongoDbDataStoreSpringInitializer} that does not require a
 * running MongoDB instance, covering the {@code isMappedClass} override and the deprecated
 * bean-style setters that {@link MongoDbDataStoreSpringInitializerSpec} does not reach.
 */
class MongoDbDataStoreSpringInitializerUnitSpec extends Specification {

    void 'isMappedClass and collectMappedClasses discriminate MongoEntity classes from unrelated ones for a secondary datastore'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer([MappedThing, UnmappedThing])
        initializer.setSecondaryDatastore(true)

        expect:
        initializer.isMappedClass('mongo', MappedThing)
        !initializer.isMappedClass('mongo', UnmappedThing)
        initializer.collectMappedClasses('mongo') == [MappedThing]
    }

    void 'setMongoBeanName updates the mongo bean name'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()

        when:
        initializer.setMongoBeanName('customMongo')

        then:
        initializer.mongoBeanName == 'customMongo'
    }

    void 'setMongoOptionsBeanName updates the mongo options bean name'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()

        when:
        initializer.setMongoOptionsBeanName('customMongoOptions')

        then:
        initializer.mongoOptionsBeanName == 'customMongoOptions'
    }

    void 'setDatabaseName updates the database name'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()

        when:
        initializer.setDatabaseName('customDb')

        then:
        initializer.databaseName == 'customDb'
    }

    void 'setDefaultMapping updates the default mapping closure'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()
        def mapping = { -> }

        when:
        initializer.setDefaultMapping(mapping)

        then:
        initializer.defaultMapping.is(mapping)
    }

    void 'setMongoOptions updates the mongo client settings'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()
        def settings = MongoClientSettings.builder().build()

        when:
        initializer.setMongoOptions(settings)

        then:
        initializer.mongoOptions.is(settings)
    }

    void 'setMongoClient records the pre-existing client to reuse'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()
        def client = Mock(MongoClient)

        when:
        initializer.setMongoClient(client)

        then:
        initializer.mongo.is(client)
    }

    void 'applyDatabaseNameFallback does nothing when the database name was never customized'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()
        removeSystemPropertySources(initializer)

        expect:
        !initializer.configuration.containsProperty(MongoSettings.SETTING_DATABASE_NAME)

        when:
        initializer.applyDatabaseNameFallback()

        then:
        !initializer.configuration.containsProperty(MongoSettings.SETTING_DATABASE_NAME)
    }

    void 'applyDatabaseNameFallback injects the customized database name into a ConfigurableEnvironment when not already set'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()
        removeSystemPropertySources(initializer)
        initializer.setDatabaseName('customDb')

        when:
        initializer.applyDatabaseNameFallback()

        then:
        initializer.configuration.getProperty(MongoSettings.SETTING_DATABASE_NAME) == 'customDb'
    }

    void 'applyDatabaseNameFallback does not override an already-configured database name on a ConfigurableEnvironment'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()
        initializer.configuration.propertySources.addFirst(
                new org.springframework.core.env.MapPropertySource('test', [(MongoSettings.SETTING_DATABASE_NAME): 'explicit']))
        initializer.setDatabaseName('customDb')

        when:
        initializer.applyDatabaseNameFallback()

        then:
        initializer.configuration.getProperty(MongoSettings.SETTING_DATABASE_NAME) == 'explicit'
    }

    void 'applyDatabaseNameFallback injects the customized database name into a Map-based PropertyResolver when not already set'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()
        def config = new MapPropertyResolver()
        initializer.configuration = config
        initializer.setDatabaseName('customDb')

        when:
        initializer.applyDatabaseNameFallback()

        then:
        config.get(MongoSettings.SETTING_DATABASE_NAME) == 'customDb'
    }

    void 'applyDatabaseNameFallback does not override an already-configured database name on a Map-based PropertyResolver'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()
        def config = new MapPropertyResolver((MongoSettings.SETTING_DATABASE_NAME): 'explicit')
        initializer.configuration = config
        initializer.setDatabaseName('customDb')

        when:
        initializer.applyDatabaseNameFallback()

        then:
        config.get(MongoSettings.SETTING_DATABASE_NAME) == 'explicit'
    }

    void 'applyDatabaseNameFallback does nothing when configuration is neither a ConfigurableEnvironment nor a Map'() {
        given:
        def initializer = new MongoDbDataStoreSpringInitializer()
        def config = new PlainPropertyResolver()
        initializer.configuration = config
        initializer.setDatabaseName('customDb')

        when:
        initializer.applyDatabaseNameFallback()

        then:
        noExceptionThrown()
        !config.containsProperty(MongoSettings.SETTING_DATABASE_NAME)
    }

    /**
     * The default {@code configuration} is a {@code StandardEnvironment}, which reads system
     * properties and environment variables. Strips those sources so tests asserting on its
     * contents aren't at the mercy of the environment they happen to run in.
     */
    private static void removeSystemPropertySources(MongoDbDataStoreSpringInitializer initializer) {
        def propertySources = ((ConfigurableEnvironment) initializer.configuration).propertySources
        propertySources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)
        propertySources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)
    }
}

/**
 * Minimal {@link PropertyResolver} backed directly by a {@link Map}, standing in for
 * Grails' own {@code Config} type (which is both a {@code Map} and a {@code PropertyResolver})
 * without requiring a dependency on grails-core, which this module deliberately excludes.
 */
class MapPropertyResolver extends LinkedHashMap<String, Object> implements PropertyResolver {

    boolean containsProperty(String key) {
        containsKey(key)
    }

    @Override
    String getProperty(String key) {
        get(key) as String
    }

    @Override
    String getProperty(String key, String defaultValue) {
        containsKey(key) ? get(key) as String : defaultValue
    }

    @Override
    def <T> T getProperty(String key, Class<T> targetType) {
        get(key) as T
    }

    @Override
    def <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        containsKey(key) ? get(key) as T : defaultValue
    }

    @Override
    String getRequiredProperty(String key) {
        get(key) as String
    }

    @Override
    def <T> T getRequiredProperty(String key, Class<T> targetType) {
        get(key) as T
    }

    @Override
    String resolvePlaceholders(String text) {
        text
    }

    @Override
    String resolveRequiredPlaceholders(String text) {
        text
    }
}

/**
 * A {@link PropertyResolver} that is neither a {@link ConfigurableEnvironment} nor a {@link Map},
 * exercising the fallthrough branch of {@code applyDatabaseNameFallback()}.
 */
class PlainPropertyResolver implements PropertyResolver {

    private final Map<String, Object> values = [:]

    boolean containsProperty(String key) {
        values.containsKey(key)
    }

    @Override
    String getProperty(String key) {
        values.get(key) as String
    }

    @Override
    String getProperty(String key, String defaultValue) {
        values.containsKey(key) ? values.get(key) as String : defaultValue
    }

    @Override
    def <T> T getProperty(String key, Class<T> targetType) {
        values.get(key) as T
    }

    @Override
    def <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        values.containsKey(key) ? values.get(key) as T : defaultValue
    }

    @Override
    String getRequiredProperty(String key) {
        values.get(key) as String
    }

    @Override
    def <T> T getRequiredProperty(String key, Class<T> targetType) {
        values.get(key) as T
    }

    @Override
    String resolvePlaceholders(String text) {
        text
    }

    @Override
    String resolveRequiredPlaceholders(String text) {
        text
    }
}

class MappedThing implements MongoEntity<MappedThing> {
    Long id
}

class UnmappedThing {
    static mapWith = 'sql'
}
