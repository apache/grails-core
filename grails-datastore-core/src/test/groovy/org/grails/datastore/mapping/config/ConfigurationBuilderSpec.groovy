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
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode
import org.springframework.core.convert.ConversionFailedException
import org.springframework.core.convert.ConverterNotFoundException
import org.springframework.core.convert.TypeDescriptor
import org.grails.datastore.mapping.multitenancy.resolvers.FixedTenantResolver
import org.springframework.core.env.PropertyResolver
import org.springframework.util.ReflectionUtils
import spock.lang.Specification

import jakarta.persistence.FlushModeType
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
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

    void "Test nested map conversion does not use fallback for scalar malformed configuration"() {

        given: "A fallback and a scalar nested configuration value"
        PropertyResolver config = Mock()
        config.getProperty(Settings.PREFIX + ".strictNested", StrictNestedSettings, _) >> {
            throw new ConverterNotFoundException(TypeDescriptor.valueOf(String), TypeDescriptor.valueOf(StrictNestedSettings))
        }
        config.getProperty(Settings.PREFIX + ".strictNested", Object) >> 'bad'
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

    void "Test nested map conversion rejects unknown properties"() {

        given: "A nested configuration map with an unknown property"
        def config = DatastoreUtils.createPropertyResolver(
                (Settings.PREFIX + ".strictNested"): [valu: 'configured']
        )

        when: "The configuration is built"
        new StrictNestedConfigurationBuilder(config).build()

        then: "The unknown property is rejected"
        def e = thrown(ConfigurationException)
        e.message.contains('strictNested')
        e.message.contains('valu')
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

    void "Test nested map conversion handles ConversionFailedException wrapping ConverterNotFoundException"() {

        given: "A wrapped converter-not-found failure and nested map"
        PropertyResolver config = Mock()
        String propertyPath = Settings.PREFIX + '.strictNested'
        config.getProperty(propertyPath, StrictNestedSettings, _) >> {
            throw conversionFailed(new ConverterNotFoundException(TypeDescriptor.valueOf(Map), TypeDescriptor.valueOf(StrictNestedSettings)))
        }
        config.getProperty(propertyPath, Object) >> [value: 'ok']
        config.getProperty(propertyPath + '.value', String) >> 'ok'

        when: "The configuration is built"
        StrictNestedConfig configuration = new StrictNestedConfigurationBuilder(config).build()

        then: "The map fallback is used"
        configuration.strictNested.value == 'ok'
    }

    void "Test nested map conversion does not handle unrelated ConversionFailedException"() {

        given: "A conversion failure not caused by a missing converter"
        PropertyResolver config = Mock()
        String propertyPath = Settings.PREFIX + '.strictNested'
        config.getProperty(propertyPath, StrictNestedSettings, _) >> {
            throw conversionFailed(new IllegalArgumentException('converter rejected value'))
        }

        when: "The configuration is built"
        new StrictNestedConfigurationBuilder(config).build()

        then: "The original conversion failure is not bypassed"
        def e = thrown(ConfigurationException)
        e.message.contains('strictNested')
        0 * config.getProperty(propertyPath, Object)
    }

    void "Test nested map conversion propagates strict failure through wrapped converter exception"() {

        given: "A wrapped converter-not-found failure and unknown nested property"
        PropertyResolver config = Mock()
        String propertyPath = Settings.PREFIX + '.strictNested'
        config.getProperty(propertyPath, StrictNestedSettings, _) >> {
            throw conversionFailed(new ConverterNotFoundException(TypeDescriptor.valueOf(Map), TypeDescriptor.valueOf(StrictNestedSettings)))
        }
        config.getProperty(propertyPath, Object) >> [valu: 'configured']

        when: "The configuration is built"
        new StrictNestedConfigurationBuilder(config).build()

        then: "The strict population failure is preserved"
        def e = thrown(ConfigurationException)
        e.message.contains('valu')
        e.cause == null
    }

    void "Test nested map conversion rejects raw lookup failure instead of returning fallback"() {

        given: "A fallback and a raw lookup failure"
        PropertyResolver config = Mock()
        String propertyPath = Settings.PREFIX + '.strictNested'
        config.getProperty(propertyPath, StrictNestedSettings, _) >> {
            throw new ConverterNotFoundException(TypeDescriptor.valueOf(Map), TypeDescriptor.valueOf(StrictNestedSettings))
        }
        config.getProperty(propertyPath, Object) >> {
            throw new IllegalStateException('raw lookup failed')
        }
        def fallback = new StrictNestedConfig(strictNested: new StrictNestedSettings(value: 'fallback'))

        when: "The configuration is built"
        new StrictNestedConfigurationBuilder(config, fallback).build()

        then: "The raw lookup failure is reported"
        def e = thrown(ConfigurationException)
        e.message.contains('raw lookup failed')
    }

    void "Test nested map conversion retains unspecified fallback fields"() {

        given: "A fallback nested value and an override for one field"
        def config = DatastoreUtils.createPropertyResolver(
                (Settings.PREFIX + '.strictNested'): [value: 'configured']
        )
        def fallback = new StrictNestedConfig(strictNested: new StrictNestedSettings(value: 'fallback', inherited: 'retained'))

        when: "The configuration is built"
        StrictNestedConfig configuration = new StrictNestedConfigurationBuilder(config, fallback).build()

        then: "Only the configured field changes"
        configuration.strictNested.value == 'configured'
        configuration.strictNested.inherited == 'retained'
        fallback.strictNested.value == 'fallback'
        fallback.strictNested.inherited == 'retained'
    }

    void "Test nested map conversion retains unknown keys on map-backed types"() {

        given: "A map-backed nested type with a declared property and an arbitrary key"
        PropertyResolver config = Mock()
        String propertyPath = Settings.PREFIX + '.mapBacked'
        config.getProperty(propertyPath, MapBackedSettings, _) >> {
            throw new ConverterNotFoundException(TypeDescriptor.valueOf(Map), TypeDescriptor.valueOf(MapBackedSettings))
        }
        config.getProperty(propertyPath, Object) >> [configClass: 'com.example.MyConfig', 'hibernate.hbm2ddl.auto': 'update']
        config.getProperty(propertyPath + '.configClass', String) >> 'com.example.MyConfig'

        when: "The configuration is built"
        MapBackedConfig configuration = new MapBackedConfigurationBuilder(config).build()

        then: "The declared property is bound via its setter and the arbitrary key is kept as a map entry"
        configuration.mapBacked.configClass == 'com.example.MyConfig'
        !configuration.mapBacked.containsKey('configClass')
        configuration.mapBacked['hibernate.hbm2ddl.auto'] == 'update'
    }

    void "Test nested map conversion still rejects unknown keys on non-map types"() {

        given: "A plain nested configuration map with a valid property and an unknown key"
        def config = DatastoreUtils.createPropertyResolver(
                (Settings.PREFIX + '.strictNested'): [value: 'ok', unknownKey: 'configured']
        )

        when: "The configuration is built"
        new StrictNestedConfigurationBuilder(config).build()

        then: "The unknown key is still rejected"
        def e = thrown(ConfigurationException)
        e.message.contains('Unknown setting')
        e.message.contains('unknownKey')
    }

    void "Test nested map conversion applies explicit null over fallback value"() {

        given: "A fallback nested value and an explicit null override"
        PropertyResolver config = Mock()
        String propertyPath = Settings.PREFIX + '.strictNested'
        config.getProperty(propertyPath, StrictNestedSettings, _) >> {
            throw new ConverterNotFoundException(TypeDescriptor.valueOf(Map), TypeDescriptor.valueOf(StrictNestedSettings))
        }
        config.getProperty(propertyPath, Object) >> [inherited: null]
        config.getProperty(propertyPath + '.inherited', String) >> null
        def fallback = new StrictNestedConfig(strictNested: new StrictNestedSettings(value: 'fallback', inherited: 'retained'))

        when: "The configuration is built"
        StrictNestedConfig configuration = new StrictNestedConfigurationBuilder(config, fallback).build()

        then: "The explicit null replaces the inherited value"
        configuration.strictNested.inherited == null
        configuration.strictNested.value == 'fallback'
        fallback.strictNested.inherited == 'retained'
    }

    void "Test nested map conversion supports lowercase enum values"() {

        given: "A nested map with a lowercase enum value"
        def config = DatastoreUtils.createPropertyResolver(
                (Settings.PREFIX + '.strictNested'): [mode: 'database']
        )

        when: "The configuration is built"
        StrictNestedConfig configuration = new StrictNestedConfigurationBuilder(config).build()

        then: "The enum is converted case-insensitively"
        configuration.strictNested.mode == MultiTenancyMode.DATABASE
    }

    void "Test nested map conversion accepts flattened descendants of known properties"() {

        given: "A two-level nested configuration map flattened by the property resolver"
        String propertyPath = Settings.PREFIX + '.strictNested'
        def config = converterNotFoundFor(
                DatastoreUtils.createPropertyResolver((propertyPath): [nested: [value: 'configured']]),
                propertyPath,
                StrictNestedSettings
        )

        when: "The configuration is built"
        StrictNestedConfig configuration = new StrictNestedConfigurationBuilder(config).build()

        then: "The nested value is bound without treating its flattened key as unknown"
        configuration.strictNested.nested.value == 'configured'
    }

    void "Test nested map conversion rejects flattened descendants of unknown properties"() {

        given: "A two-level nested configuration map whose first segment is unknown"
        String propertyPath = Settings.PREFIX + '.strictNested'
        def config = converterNotFoundFor(
                DatastoreUtils.createPropertyResolver((propertyPath): [unknown: [value: 'configured']]),
                propertyPath,
                StrictNestedSettings
        )

        when: "The configuration is built"
        new StrictNestedConfigurationBuilder(config).build()

        then: "The unknown first segment is rejected"
        def e = thrown(ConfigurationException)
        e.message.contains('Unknown setting')
        e.message.contains('unknown')
    }

    void "Test nested map conversion retains fallback fields at every level"() {

        given: "A nested fallback value and an override for one child field"
        String propertyPath = Settings.PREFIX + '.strictNested'
        def config = converterNotFoundFor(
                DatastoreUtils.createPropertyResolver((propertyPath): [nested: [value: 'configured']]),
                propertyPath,
                StrictNestedSettings
        )
        def fallback = new StrictNestedConfig(
                strictNested: new StrictNestedSettings(nested: new NestedStrictSettings(value: 'fallback', inherited: 'retained'))
        )

        when: "The configuration is built"
        StrictNestedConfig configuration = new StrictNestedConfigurationBuilder(config, fallback).build()

        then: "The configured child field changes while its unspecified fallback field remains"
        configuration.strictNested.nested.value == 'configured'
        configuration.strictNested.nested.inherited == 'retained'
        fallback.strictNested.nested.value == 'fallback'
        fallback.strictNested.nested.inherited == 'retained'
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

        String inherited

        MultiTenancyMode mode

        NestedStrictSettings nested

        void setValue(String value) {
            if (value == 'bad') {
                throw new IllegalArgumentException('bad value')
            }
            this.value = value
        }
    }

    static class NestedStrictSettings {

        String value

        String inherited
    }

    static class MapBackedConfigurationBuilder extends ConfigurationBuilder<MapBackedConfig, MapBackedConfig> {

        MapBackedConfigurationBuilder(PropertyResolver propertyResolver) {
            super(propertyResolver, Settings.PREFIX)
        }

        @Override
        protected MapBackedConfig createBuilder() {
            return new MapBackedConfig()
        }

        @Override
        protected MapBackedConfig toConfiguration(MapBackedConfig builder) {
            return builder
        }
    }

    static class MapBackedConfig {

        MapBackedSettings mapBacked

        MapBackedConfig mapBacked(MapBackedSettings mapBacked) {
            this.mapBacked = mapBacked
            return this
        }
    }

    static class MapBackedSettings extends LinkedHashMap<String, String> {

        String configClass
    }

    private static ConversionFailedException conversionFailed(Throwable cause) {
        return new ConversionFailedException(
                TypeDescriptor.valueOf(Map),
                TypeDescriptor.valueOf(StrictNestedSettings),
                [:],
                cause
        )
    }

    private static PropertyResolver converterNotFoundFor(PropertyResolver delegate, String propertyPath, Class propertyType) {
        return Proxy.newProxyInstance(
                PropertyResolver.classLoader,
                [PropertyResolver] as Class[],
                { Object proxy, Method method, Object[] arguments ->
                    if (method.name == 'getProperty' && arguments.length == 3 && arguments[0] == propertyPath && arguments[1] == propertyType) {
                        throw new ConverterNotFoundException(TypeDescriptor.valueOf(Map), TypeDescriptor.valueOf(propertyType))
                    }
                    try {
                        return method.invoke(delegate, arguments)
                    } catch (InvocationTargetException e) {
                        throw e.targetException
                    }
                } as InvocationHandler
        ) as PropertyResolver
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
