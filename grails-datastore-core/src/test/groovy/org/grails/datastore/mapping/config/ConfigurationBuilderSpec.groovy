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

package org.grails.datastore.mapping.config

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.springframework.core.convert.ConverterNotFoundException
import org.springframework.core.convert.TypeDescriptor
import org.grails.datastore.mapping.multitenancy.resolvers.FixedTenantResolver
import org.springframework.core.env.PropertyResolver
import org.springframework.util.ReflectionUtils
import spock.lang.Specification

import jakarta.persistence.FlushModeType
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.TimeUnit

/**
 * Created by graemerocher on 29/06/16.
 */
class ConfigurationBuilderSpec extends Specification {

    void "Test configuration builder with getter and setter"() {
        given:"A configuration"
        def map = [
                (Settings.SETTING_MULTI_TENANT_RESOLVER): new FixedTenantResolver()
        ]
        def config = DatastoreUtils.createPropertyResolver(map)

        when:"The configuration is built"
        def builder = new TestConfigurationBuilder(config)
        ConnectionSourceSettings connectionSourceSettings = builder.build()

        then:"The result is correct"
        connectionSourceSettings.multiTenancy.tenantResolver instanceof FixedTenantResolver
    }

    void "Test configuration builder"() {

        given:"A configuration"
        def map = [
                (Settings.SETTING_AUTO_FLUSH): "true",
                (Settings.SETTING_DEFAULT_MAPPING): {
                }
        ]
        def config = DatastoreUtils.createPropertyResolver(map)

        when:"The configuration is built"
        def builder = new TestConfigurationBuilder(config)
        ConnectionSourceSettings connectionSourceSettings = builder.build()

        then:"The result is correct"
        connectionSourceSettings.autoFlush
        connectionSourceSettings.getDefault().mapping != null
        map.size() == 2 // don't mutate the original map
    }


    void "Test configuration builder with fallback config"() {

        given:"A configuration"
        def config = DatastoreUtils.createPropertyResolver(
                (Settings.SETTING_AUTO_FLUSH): "true",
                (Settings.SETTING_DEFAULT_MAPPING): {
                }
        )

        when:"The configuration is built"
        def fallback = new ConnectionSourceSettings().flushMode(FlushModeType.COMMIT).defaults(new ConnectionSourceSettings.DefaultSettings().constraints({->}))
        def builder = new TestConfigurationBuilder(config, fallback)
        ConnectionSourceSettings connectionSourceSettings = builder.build()

        then:"The result is correct"
        connectionSourceSettings.autoFlush
        connectionSourceSettings.flushMode == FlushModeType.COMMIT
        connectionSourceSettings.getDefault().mapping != null
        connectionSourceSettings.getDefault().constraints != null
    }

    void "Test configuration builder with builder methods with 0 and >1 args"() {

        given:"A configuration"
        def configSource = DatastoreUtils.createPropertyResolver(
                ["grails.gorm.leakedSessionsLogging": true,
                 "grails.gorm.connectionLivenessCheckTimeout.arg0": 10,
                 "grails.gorm.connectionLivenessCheckTimeout.arg1": "MINUTES"]
        )

        when:"The configuration is built"
        def builder = new WithBuilderConfigurationBuilder(configSource, null)
        Config config = builder.build()

        then:"The result is correct"
        config.logLeakedSessions
        config.idleTimeBeforeConnectionTest == 600000
    }

    void "Test nested map conversion does not default malformed configuration"() {

        given: "A malformed nested configuration map"
        def config = DatastoreUtils.createPropertyResolver(
                (Settings.PREFIX + ".strictNested"): [value: 'bad']
        )

        when: "The configuration is built"
        new StrictNestedConfigurationBuilder(config).build()

        then: "The malformed nested value is rejected"
        def e = thrown(ConfigurationException)
        e.message.contains('strictNested')
    }

    void "Test nested map conversion does not use fallback for malformed configuration"() {

        given: "A fallback and a malformed nested configuration map"
        def config = DatastoreUtils.createPropertyResolver(
                (Settings.PREFIX + ".strictNested"): [value: 'bad']
        )
        def fallback = new StrictNestedConfig(strictNested: new StrictNestedSettings(value: 'fallback'))

        when: "The configuration is built"
        new StrictNestedConfigurationBuilder(config, fallback).build()

        then: "The malformed nested value is rejected"
        def e = thrown(ConfigurationException)
        e.message.contains('strictNested')
    }

    void "Test nested map conversion populates simple configuration types"() {

        given: "A nested configuration map"
        def config = DatastoreUtils.createPropertyResolver(
                (Settings.PREFIX + ".strictNested"): [value: 'ok']
        )

        when: "The configuration is built"
        StrictNestedConfig configuration = new StrictNestedConfigurationBuilder(config).build()

        then: "The nested object is populated"
        configuration.strictNested.value == 'ok'
    }

    void "Test nested map conversion preserves empty map defaults"() {

        given: "An empty nested configuration map that Spring cannot convert directly"
        PropertyResolver config = Mock()
        config.getProperty(Settings.PREFIX + ".strictNested", StrictNestedSettings, null) >> {
            throw new ConverterNotFoundException(TypeDescriptor.valueOf(Map), TypeDescriptor.valueOf(StrictNestedSettings))
        }
        config.getProperty(Settings.PREFIX + ".strictNested", Object) >> [:]

        when: "The configuration is built"
        StrictNestedConfig configuration = new StrictNestedConfigurationBuilder(config).build()

        then: "The nested object is created with defaults"
        configuration.strictNested != null
        configuration.strictNested.value == null
    }

    static class TestConfigurationBuilder extends ConfigurationBuilder<ConnectionSourceSettings, ConnectionSourceSettings> {

        TestConfigurationBuilder(PropertyResolver propertyResolver) {
            super(propertyResolver, Settings.PREFIX)
        }

        TestConfigurationBuilder(PropertyResolver propertyResolver, ConnectionSourceSettings fallback) {
            super(propertyResolver, Settings.PREFIX, fallback)
        }

        @Override
        protected ConnectionSourceSettings createBuilder() {
            return new ConnectionSourceSettings()
        }

        @Override
        protected ConnectionSourceSettings toConfiguration(ConnectionSourceSettings builder) {
            return builder
        }
    }

    static class StrictNestedConfigurationBuilder extends ConfigurationBuilder<StrictNestedConfig, StrictNestedConfig> {

        StrictNestedConfigurationBuilder(PropertyResolver propertyResolver) {
            super(propertyResolver, Settings.PREFIX)
        }

        StrictNestedConfigurationBuilder(PropertyResolver propertyResolver, StrictNestedConfig fallback) {
            super(propertyResolver, Settings.PREFIX, fallback)
        }

        @Override
        protected StrictNestedConfig createBuilder() {
            return new StrictNestedConfig()
        }

        @Override
        protected StrictNestedConfig toConfiguration(StrictNestedConfig builder) {
            return builder
        }
    }

    static class StrictNestedConfig {

        StrictNestedSettings strictNested

        StrictNestedConfig strictNested(StrictNestedSettings strictNested) {
            this.strictNested = strictNested
            return this
        }
    }

    static class StrictNestedSettings {

        String value

        void setValue(String value) {
            if (value == 'bad') {
                throw new IllegalArgumentException('bad value')
            }
            this.value = value
        }
    }

    static class WithBuilderConfigurationBuilder extends ConfigurationBuilder<Config.ConfigBuilder, Config> {

        WithBuilderConfigurationBuilder(PropertyResolver propertyResolver) {
            super(propertyResolver, Settings.PREFIX, "longPrefix")
        }

        WithBuilderConfigurationBuilder(PropertyResolver propertyResolver, ConnectionSourceSettings fallback) {
            super(propertyResolver, Settings.PREFIX, fallback, "longPrefix")
        }

        @Override
        protected Config.ConfigBuilder createBuilder() {
            return Config.build()
        }

        @Override
        protected Config toConfiguration(Config.ConfigBuilder builder) {
            return builder.toConfig()
        }

        @Override
        protected Object getFallBackValue(Object fallBackConfig, String methodName) {
            if(fallBackConfig != null) {
                Method fallBackMethod = ReflectionUtils.findMethod(fallBackConfig.getClass(), methodName)
                if(fallBackMethod != null && Modifier.isPublic(fallBackMethod.getModifiers())) {
                    return fallBackMethod.invoke(fallBackConfig)

                }
                else {
                    return super.getFallBackValue(fallBackConfig, methodName)
                }
            }
            return null
        }
    }

    static class Config {

        final boolean logLeakedSessions
        final long idleTimeBeforeConnectionTest

        private Config( ConfigBuilder builder) {
            this.logLeakedSessions = builder.logLeakedSessions
            this.idleTimeBeforeConnectionTest = builder.idleTimeBeforeConnectionTest
        }

        static ConfigBuilder build() {
            new ConfigBuilder()
        }

        static class ConfigBuilder {
            private boolean logLeakedSessions
            private long idleTimeBeforeConnectionTest

            private ConfigBuilder() {}

            ConfigBuilder longPrefixLeakedSessionsLogging() {
                this.logLeakedSessions = true
                this
            }

            ConfigBuilder longPrefixConnectionLivenessCheckTimeout(long value, TimeUnit unit) {
                this.idleTimeBeforeConnectionTest = unit.toMillis(value)
                this
            }

            Config toConfig() {
                new Config(this)
            }
        }
    }
}
