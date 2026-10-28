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
package org.grails.config

import org.grails.config.yaml.YamlPropertySourceLoader
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.io.ByteArrayResource
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires
import spock.lang.Specification

import java.lang.reflect.Field

/**
 * @author Iván López
 */
class SystemEnvironmentConfigSpec extends Specification {

    private static final String YAML = '''
property.with.period: from-yml
property_with_underscore: from-yml
property-with-hyphen: from-yml
property-with_mixed.symbols: from-yml
'''

    @Issue('#10670')
    void 'configuration properties defined in SystemEnvironment take precedence'() {
        given: 'a value in the system environment'
        installVariable(systemProperty, value)

        and: 'default configuration in application-yml'
        def config = configFor(YAML)

        expect:
        config.getProperty(propertyToFind) == value

        cleanup:
        removeVariable(systemProperty)

        where:
        propertyToFind                | systemProperty
        'property.with.period'        | 'property.with.period'
        'property.with.period'        | 'property_with_period'
        'property.with.period'        | 'PROPERTY_WITH_PERIOD'
        'property_with_underscore'    | 'property_with_underscore'
        'property_with_underscore'    | 'PROPERTY_WITH_UNDERSCORE'
        'property-with-hyphen'        | 'property-with-hyphen'
        'property-with-hyphen'        | 'PROPERTY-WITH-HYPHEN'
        'property-with-hyphen'        | 'PROPERTY_WITH_HYPHEN'
        'property-with_mixed.symbols' | 'property-with_mixed.symbols'
        'property-with_mixed.symbols' | 'property_with_mixed_symbols'
        'property-with_mixed.symbols' | 'PROPERTY-WITH_MIXED.SYMBOLS'
        'property-with_mixed.symbols' | 'PROPERTY_WITH_MIXED_SYMBOLS'
        value = 'from-' + systemProperty
    }

    void 'a key is matched to its upper-cased variable under any default locale'() {
        given: 'the default locale, restored afterwards'
        Locale defaultLocale = Locale.getDefault()

        and: 'a variable that is the upper-cased key'
        installVariable('PROPERTY_WITH_ID', 'from-env')
        def config = configFor('property.with.id: from-yml')

        and: 'a default locale whose upper-case i is not I'
        Locale.setDefault(Locale.forLanguageTag('tr-TR'))

        expect: 'the variable overrides the configured value'
        config.getProperty('property.with.id') == 'from-env'

        cleanup:
        Locale.setDefault(defaultLocale)
        removeVariable('PROPERTY_WITH_ID')
    }

    void 'a key with a character above ASCII is matched through its upper-cased form'() {
        given: 'a variable that is the upper-cased key, in which the sharp s has become two letters'
        installVariable('STRASSE_X', 'from-env')
        def config = configFor('stra\u00dfe.x: from-yml')

        expect: 'the variable overrides the configured value'
        config.getProperty('stra\u00dfe.x') == 'from-env'

        cleanup:
        removeVariable('STRASSE_X')
    }

    void 'a null key resolves to the default value'() {
        given: 'any configuration'
        def config = configFor(YAML)

        expect: 'a null key is absent rather than an error from the environment probe'
        config.getProperty(null) == null
        config.getProperty(null, String, 'dflt') == 'dflt'
        !config.containsProperty(null)
    }

    void 'a key that no environment variable can match resolves from configuration'() {
        given: 'configuration with no matching variable in the environment'
        def config = configFor(YAML)

        expect: 'the configured value is returned'
        config.getProperty('property.with.period') == 'from-yml'
        config.getProperty('property-with-hyphen') == 'from-yml'

        and: 'a key that is configured nowhere falls back to the default'
        config.getProperty('property.nobody.set', String, 'fallback') == 'fallback'
    }

    @IgnoreIf(value = { os.windows }, reason = 'Windows matches variable names regardless of case')
    void 'a variable that matches the key in none of the probed spellings does not override it'() {
        given: 'a lower-case variable for a mixed-case key'
        installVariable('property_with_period', 'from-env')
        def config = configFor('Property.With.Period: from-yml')

        expect: 'only the key as written and its upper-cased form are probed, as Spring does'
        config.getProperty('Property.With.Period') == 'from-yml'

        cleanup:
        removeVariable('property_with_period')
    }

    @Requires({ os.windows })
    void 'on Windows a variable matches the key in any case, as System.getenv does there'() {
        given: 'a mixed-case variable for a lower-case key'
        installVariable('Property_With_Period', 'from-env')
        def config = configFor(YAML)

        expect: 'the variable overrides the configured value'
        config.getProperty('property.with.period') == 'from-env'

        cleanup:
        removeVariable('Property_With_Period')
    }

    @IgnoreIf(value = { os.windows }, reason = 'the spellings that differ only in case are one variable on Windows')
    void 'when several variables match a key the first probed spelling wins'() {
        given: 'two variables that both match the key'
        installVariable(winner, 'from-winner')
        installVariable(loser, 'from-loser')
        def config = configFor(YAML)

        expect: 'the value of the spelling that is probed first is returned'
        config.getProperty('property.with.period') == 'from-winner'

        cleanup:
        removeVariable(winner)
        removeVariable(loser)

        where:
        winner                 | loser
        'property.with.period' | 'PROPERTY_WITH_PERIOD'
        'property_with_period' | 'PROPERTY_WITH_PERIOD'
        'property_with_period' | 'PROPERTY.WITH.PERIOD'
    }

    void 'a property read more than once keeps resolving to the system environment value'() {
        given: 'configuration that is overridden by the environment'
        installVariable('PROPERTY_WITH_PERIOD', 'from-env')
        def config = configFor(YAML)

        expect: 'every read resolves to the environment value, not just the first'
        config.getProperty('property.with.period') == 'from-env'
        config.getProperty('property.with.period') == 'from-env'
        config.getProperty('property.with.period') == 'from-env'

        cleanup:
        removeVariable('PROPERTY_WITH_PERIOD')
    }

    void 'a property that has already been read still reflects later configuration changes'() {
        given: 'a configured property'
        def config = configFor('some.nested.value: original')

        expect: 'it resolves to the configured value'
        config.getProperty('some.nested.value') == 'original'

        when: 'the configuration is changed'
        config.merge(['some.nested.value': 'updated'])

        then: 'the new value is returned rather than the previously resolved one'
        config.getProperty('some.nested.value') == 'updated'
    }

    void 'each config observes the environment as it stands when the config is created'() {
        given: 'a config created before the variable exists'
        def before = configFor('late.bound.property: from-yml')

        expect: 'it resolves to the configured value'
        before.getProperty('late.bound.property') == 'from-yml'

        when: 'the variable is installed and a new config is created'
        installVariable('LATE_BOUND_PROPERTY', 'from-env')
        def after = configFor('late.bound.property: from-yml')

        then: 'the new config resolves to the environment value'
        after.getProperty('late.bound.property') == 'from-env'

        and: 'the earlier config keeps the environment it was created with'
        before.getProperty('late.bound.property') == 'from-yml'

        cleanup:
        removeVariable('LATE_BOUND_PROPERTY')
    }

    void 'a variable installed after the config exists is seen once the config is refreshed'() {
        given: 'a config created before the variable exists'
        def config = configFor('late.bound.property: from-yml')

        when: 'the variable is installed'
        installVariable('LATE_BOUND_PROPERTY', 'from-env')

        then: 'the config keeps the environment it was created with'
        config.getProperty('late.bound.property') == 'from-yml'

        when: 'the config is refreshed'
        config.refresh()

        then: 'the variable overrides the configured value'
        config.getProperty('late.bound.property') == 'from-env'

        cleanup:
        removeVariable('LATE_BOUND_PROPERTY')
    }

    private static PropertySourcesConfig configFor(String yaml) {
        def yamlPropertiesSource = new YamlPropertySourceLoader()
                .load('application.yml', new ByteArrayResource(yaml.bytes, 'test.yml'), null)
        def propertySources = new MutablePropertySources()
        propertySources.addFirst(yamlPropertiesSource.first())
        new PropertySourcesConfig(propertySources)
    }

    private static void installVariable(String name, String value) {
        for (def map : systemEnvironmentMaps()) {
            map.put(name, value)
        }
    }

    private static void removeVariable(String name) {
        for (def map : systemEnvironmentMaps()) {
            map.remove(name)
        }
    }

    /**
     * The maps behind System.getenv() and System.getenv(String), which a variable installed for a test
     * has to reach. On Unix both read one map. On Windows System.getenv(String) reads a case-insensitive
     * copy while System.getenv() exposes the original, so the variable goes into both. A wrapper that
     * overrode put and remove to do that was compiled by Groovy 6 with the @Delegate-generated put
     * instead of the override, so the maps are written one by one here.
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> systemEnvironmentMaps() {
        // From https://github.com/spring-projects/spring-framework/blob/4.3.x/spring-core/src/test/java/org/springframework/core/env/StandardEnvironmentTests.java#L492
        // for os x / linux
        Class<?>[] classes = Collections.class.getDeclaredClasses();
        Map<String, String> env = System.getenv();
        for (Class<?> cl : classes) {
            if ('java.util.Collections$UnmodifiableMap'.equals(cl.getName())) {
                try {
                    Field field = cl.getDeclaredField("m");
                    field.setAccessible(true);
                    Object obj = field.get(env);
                    if (obj != null && obj.getClass().getName().equals('java.lang.ProcessEnvironment$StringEnvironment')) {
                        return [(Map<String, String>) obj]
                    }
                }
                catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        // for windows
        Class<?> processEnvironmentClass;
        try {
            processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

        List<Map<String, String>> maps = [
                staticMapField(processEnvironmentClass, 'theCaseInsensitiveEnvironment'),
                staticMapField(processEnvironmentClass, 'theEnvironment')
        ].findAll()
        if (maps) {
            return maps
        }
        throw new IllegalStateException('The process environment could not be opened')
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> staticMapField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name)
            field.setAccessible(true)
            return (Map<String, String>) field.get(null)
        }
        catch (NoSuchFieldException ignored) {
            return null
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex)
        }
    }
}
