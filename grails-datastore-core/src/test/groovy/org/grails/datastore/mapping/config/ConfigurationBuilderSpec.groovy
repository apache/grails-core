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

import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode
import org.springframework.core.convert.ConversionFailedException
import org.springframework.core.convert.ConverterNotFoundException
import org.springframework.core.convert.TypeDescriptor
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.resolvers.FixedTenantResolver
import org.springframework.core.env.PropertyResolver
import org.springframework.core.env.PropertySource
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
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

    void "Test discovered dotted map properties use higher relaxed environment values"() {
        given:
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('low', [
                'dataSource.properties.maximumPoolSize': 10
        ]))
        environment.propertySources.addFirst(new SystemEnvironmentPropertySource('high', [
                DATASOURCE_PROPERTIES_MAXIMUMPOOLSIZE: '50'
        ]))

        expect:
        new MapConfigurationBuilder(environment).build().properties == [maximumPoolSize: '50']
    }

    void "Test discovered dotted map properties use higher non-enumerable values without conversion"() {
        given:
        def environment = new StandardEnvironment()
        def values = [maximumPoolSize: '${pool.size}', options: [enabled: true], connectionInitSqls: ['SET A=1', 'SET B=2']]
        environment.propertySources.addFirst(new MapPropertySource('low', [
                'dataSource.properties.maximumPoolSize': 10,
                'dataSource.properties.options': [enabled: false],
                'dataSource.properties.connectionInitSqls': ['LOW'],
                'pool.size': '50'
        ]))
        environment.propertySources.addFirst(new PropertySource<Map>('high', values) {
            @Override
            Object getProperty(String name) {
                return name.startsWith('dataSource.properties.') ? getSource().get(name.substring('dataSource.properties.'.length())) : null
            }
        })

        when:
        def configuration = new MapConfigurationBuilder(environment).build()

        then:
        configuration.properties == [maximumPoolSize: '50', options: [enabled: true], connectionInitSqls: ['SET A=1', 'SET B=2']]
        configuration.properties.connectionInitSqls.is(values.connectionInitSqls)
        configuration.properties.options.is(values.options)
        values.maximumPoolSize == '${pool.size}'
    }

    void "Test existing maps preserve literal dotted keys at #propertyPath"() {
        given:
        def options = [
                'dbms.memory.pagecache.size': '512M',
                nested: ['dbms.memory.heap.max_size': '1G']
        ]
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('configuration', [(propertyPath): options]))

        when:
        def properties = new MapConfigurationBuilder(environment).build().properties
        def stored = nested ? properties.options : properties

        then:
        stored == options
        stored['dbms.memory.pagecache.size'] == '512M'
        stored.nested == ['dbms.memory.heap.max_size': '1G']
        options == ['dbms.memory.pagecache.size': '512M', nested: ['dbms.memory.heap.max_size': '1G']]

        where:
        propertyPath                    | nested
        'dataSource.properties'         | false
        'dataSource.properties.options' | true
    }

    void "Test string placeholders follow the environment policy"() {
        given:
        def resolving = new StandardEnvironment()
        resolving.propertySources.addFirst(new MapPropertySource('defined', [
                literal: 'ok',
                'dataSource.properties.connectionInitSql': 'SELECT ${literal}'
        ]))
        def lenient = new StandardEnvironment()
        lenient.setIgnoreUnresolvableNestedPlaceholders(true)
        lenient.propertySources.addFirst(new MapPropertySource('open', [
                'dataSource.properties.connectionInitSql': 'SELECT ${literal}'
        ]))

        expect:
        new MapConfigurationBuilder(resolving).build().properties.connectionInitSql == 'SELECT ok'
        new MapConfigurationBuilder(lenient).build().properties.connectionInitSql == 'SELECT ${literal}'
    }

    void "Test Spring Environment rebuilds map-backed builder settings with source precedence"() {
        given:
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('low', [
                'grails.gorm.hibernate.flush.mode.nested': 'low',
                'grails.gorm.hibernate.options': [size: 20, inherited: true],
                'grails.gorm.hibernate.local': 'replaced by nested map',
                'grails.gorm.hibernate.local.enabled': true
        ]))
        environment.propertySources.addFirst(new MapPropertySource('high', [
                'grails.gorm.hibernate.configClass': 'configured',
                'grails.gorm.hibernate.flush.mode': 'HIGH',
                'grails.gorm.hibernate.options.size': 50,
                'grails.gorm.hibernate.connectionInitSqls[1]': 'SET B=2',
                'grails.gorm.hibernate.connectionInitSqls[0]': 'SET A=1'
        ]))

        when:
        def configuration = new SpringMapConfigurationBuilder(environment).build()

        then:
        configuration.hibernate.getConfigClass() == 'configured'
        configuration.hibernate['flush'] == [mode: 'HIGH']
        configuration.hibernate['options'] == [size: 50]
        configuration.hibernate['local'] == 'replaced by nested map'
        configuration.hibernate['connectionInitSqls'] == ['SET A=1', 'SET B=2']
    }

    void "Test a higher map value keeps a missing lower flattened leaf without mutating the source"() {
        given:
        def options = [enabled: true]
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('low', [
                'dataSource.properties.options.timeout': 1000
        ]))
        environment.propertySources.addFirst(new MapPropertySource('high', [
                'dataSource.properties.options': options
        ]))

        when:
        def properties = new MapConfigurationBuilder(environment).build().properties

        then:
        properties.options == [enabled: true, timeout: 1000]
        !properties.options.is(options)
        options == [enabled: true]
    }

    void "Test a higher source list blocks a lower index and an unresolved placeholder"() {
        given:
        def sqls = ['HIGH']
        def emptySqls = []
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('low', [
                'dataSource.properties.sqls[1]': 'LOW',
                'dataSource.properties.emptySqls[1]': '${missing}'
        ]))
        environment.propertySources.addFirst(new MapPropertySource('high', [
                'dataSource.properties.sqls': sqls,
                'dataSource.properties.emptySqls': emptySqls,
                'dataSource.properties.built[1]': 'SET B=2',
                'dataSource.properties.built[0]': 'SET A=1'
        ]))

        when:
        def properties = new MapConfigurationBuilder(environment).build().properties

        then:
        properties.sqls.is(sqls)
        properties.emptySqls.is(emptySqls)
        properties.built == ['SET A=1', 'SET B=2']
        sqls == ['HIGH']
        emptySqls.isEmpty()
    }

    void "Test a higher map leaf is not replaced by a lower map or unresolved placeholder"() {
        given:
        def options = [enabled: true]
        def pool = [size: 4]
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('low', [
                'dataSource.properties.options': [enabled: false],
                'dataSource.properties.options.enabled': '${missing}',
                'dataSource.properties.pool': '${missing}'
        ]))
        environment.propertySources.addFirst(new MapPropertySource('high', [
                'dataSource.properties.options': options,
                'dataSource.properties.pool': pool
        ]))

        when:
        def properties = new MapConfigurationBuilder(environment).build().properties

        then:
        properties.options.is(options)
        properties.pool.is(pool)
        options == [enabled: true]
        pool == [size: 4]
    }

    void "Test a lower circular placeholder does not replace a higher map"() {
        given:
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('low', [
                'dataSource.properties': '${loop}',
                loop: '${loop}'
        ]))
        environment.propertySources.addFirst(new MapPropertySource('high', [
                'dataSource.properties': [x: 50]
        ]))

        expect:
        new MapConfigurationBuilder(environment).build().properties == [x: 50]
    }

    void "Test DatastoreUtils resolver retains existing map-backed builder binding"() {
        given:
        String systemKey = 'grails.gorm.hibernate.minimumIdle'
        String previousIdle = System.setProperty(systemKey, '3')
        def source = [
                'grails.gorm.hibernate.configClass': 'configured',
                'grails.gorm.hibernate.flush.mode': 'AUTO',
                'grails.gorm.hibernate.connectionInitSqls': ['SET A=1', 'SET B=2']
        ]
        def resolver = DatastoreUtils.createPropertyResolver(source)
        def original = new LinkedHashMap(resolver.getProperty('grails.gorm.hibernate', Map))

        when:
        def configuration = new SpringMapConfigurationBuilder(resolver).build()

        then:
        configuration.hibernate.getConfigClass() == 'configured'
        configuration.hibernate['flush.mode'] == 'AUTO'
        configuration.hibernate['connectionInitSqls'] == ['SET A=1', 'SET B=2']
        configuration.hibernate['minimumIdle'] == null
        resolver.getProperty('grails.gorm.hibernate', Map) == original
        source.size() == 3

        cleanup:
        if (previousIdle == null) {
            System.clearProperty(systemKey)
        }
        else {
            System.setProperty(systemKey, previousIdle)
        }
    }

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

    void "Test nested map conversion binds values the resolver does not expose as dotted properties"() {

        given: "a resolver that exposes the nested map but not the entries below it"
        PropertyResolver config = Mock()
        String propertyPath = Settings.PREFIX + '.strictNested'
        config.getProperty(propertyPath, StrictNestedSettings, _) >> {
            throw new ConverterNotFoundException(TypeDescriptor.valueOf(Map), TypeDescriptor.valueOf(StrictNestedSettings))
        }
        config.getProperty(propertyPath, Object) >> [value: 'configured', enabled: true]

        when: "The configuration is built"
        StrictNestedConfig configuration = new StrictNestedConfigurationBuilder(config).build()

        then: "The entries are bound from the map instead of being silently dropped"
        configuration.strictNested.value == 'configured'
        configuration.strictNested.enabled
    }

    void "Test nested map conversion reports a primitive property that resolves to null"() {

        given: "a nested map whose primitive entry resolves to null"
        PropertyResolver config = Mock()
        String propertyPath = Settings.PREFIX + '.strictNested'
        config.getProperty(propertyPath, StrictNestedSettings, _) >> {
            throw new ConverterNotFoundException(TypeDescriptor.valueOf(Map), TypeDescriptor.valueOf(StrictNestedSettings))
        }
        config.getProperty(propertyPath, Object) >> [enabled: null]

        when: "The configuration is built"
        new StrictNestedConfigurationBuilder(config).build()

        then: "The failure names the setting rather than surfacing a reflection error"
        def e = thrown(ConfigurationException)
        e.message.contains('strictNested.enabled')
        e.message.contains('boolean')
    }

    void "Test nested map conversion resolves a Class property from a class name"() {

        given: "A nested map naming a class"
        def config = DatastoreUtils.createPropertyResolver(
                (Settings.PREFIX + '.strictNested'): [resolverClass: FixedTenantResolver.name]
        )

        when: "The configuration is built"
        StrictNestedConfig configuration = new StrictNestedConfigurationBuilder(config).build()

        then: "The class is resolved through the thread context class loader"
        configuration.strictNested.resolverClass == FixedTenantResolver
    }

    void "Test nested map conversion resolves a Class property from a class literal"() {

        given: "A nested map holding a class literal"
        PropertyResolver config = Mock()
        String propertyPath = Settings.PREFIX + '.strictNested'
        config.getProperty(propertyPath, StrictNestedSettings, _) >> {
            throw new ConverterNotFoundException(TypeDescriptor.valueOf(Map), TypeDescriptor.valueOf(StrictNestedSettings))
        }
        config.getProperty(propertyPath, Object) >> [resolverClass: FixedTenantResolver]

        when: "The configuration is built"
        StrictNestedConfig configuration = new StrictNestedConfigurationBuilder(config).build()

        then: "The literal is used as is"
        configuration.strictNested.resolverClass == FixedTenantResolver
    }

    void "Test nested map conversion rejects an unknown class name for a Class property"() {

        given: "A nested map naming a class that does not exist"
        def config = DatastoreUtils.createPropertyResolver(
                (Settings.PREFIX + '.strictNested'): [resolverClass: 'com.example.NoSuchResolver']
        )

        when: "The configuration is built"
        new StrictNestedConfigurationBuilder(config).build()

        then: "The invalid class name is reported"
        def e = thrown(ConfigurationException)
        e.message.contains('com.example.NoSuchResolver')
        e.message.contains('resolverClass')
    }

    void "Test nested map conversion retains an inherited Class when no class is configured"() {

        given: "A fallback class and a nested map whose Class entry names nothing"
        PropertyResolver config = Mock()
        String propertyPath = Settings.PREFIX + '.strictNested'
        config.getProperty(propertyPath, StrictNestedSettings, _) >> {
            throw new ConverterNotFoundException(TypeDescriptor.valueOf(Map), TypeDescriptor.valueOf(StrictNestedSettings))
        }
        config.getProperty(propertyPath, Object) >> [resolverClass: ' ']
        def fallback = new StrictNestedConfig(strictNested: new StrictNestedSettings(resolverClass: FixedTenantResolver))

        when: "The configuration is built"
        StrictNestedConfig configuration = new StrictNestedConfigurationBuilder(config, fallback).build()

        then: "The inherited class is not cleared"
        configuration.strictNested.resolverClass == FixedTenantResolver
    }

    void "Test nested map conversion keeps map-backed keys addressable by String"() {

        given: "A map-backed nested type configured with a non-String key"
        PropertyResolver config = Mock()
        String propertyPath = Settings.PREFIX + '.mapBacked'
        config.getProperty(propertyPath, MapBackedSettings, _) >> {
            throw new ConverterNotFoundException(TypeDescriptor.valueOf(Map), TypeDescriptor.valueOf(MapBackedSettings))
        }
        config.getProperty(propertyPath, Object) >> [("hibernate.hbm2ddl.${'auto'}"): 'update']

        when: "The configuration is built"
        MapBackedConfig configuration = new MapBackedConfigurationBuilder(config).build()

        then: "The entry is stored under a String key"
        configuration.mapBacked['hibernate.hbm2ddl.auto'] == 'update'
        configuration.mapBacked.keySet().every { it instanceof String }
    }

    static class MapConfigurationBuilder extends ConfigurationBuilder<MapConfiguration, MapConfiguration> {

        MapConfigurationBuilder(PropertyResolver propertyResolver) {
            super(propertyResolver, 'dataSource')
        }

        @Override
        protected MapConfiguration createBuilder() {
            return new MapConfiguration()
        }

        @Override
        protected MapConfiguration toConfiguration(MapConfiguration builder) {
            return builder
        }
    }

    @Builder(builderStrategy = SimpleStrategy, prefix = '')
    static class MapConfiguration {

        Map properties
    }

    static class SpringMapConfigurationBuilder extends ConfigurationBuilder<SpringMapConfig, SpringMapConfig> {

        SpringMapConfigurationBuilder(PropertyResolver propertyResolver) {
            super(propertyResolver, Settings.PREFIX)
        }

        @Override
        protected SpringMapConfig createBuilder() {
            return new SpringMapConfig()
        }

        @Override
        protected SpringMapConfig toConfiguration(SpringMapConfig builder) {
            return builder
        }
    }

    @Builder(builderStrategy = SimpleStrategy, prefix = '')
    static class SpringMapConfig {

        SpringMapSettings hibernate
    }

    @Builder(builderStrategy = SimpleStrategy, prefix = '')
    static class SpringMapSettings extends LinkedHashMap<String, Object> {

        String configClass
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

        boolean enabled

        MultiTenancyMode mode

        Class<? extends TenantResolver> resolverClass

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

