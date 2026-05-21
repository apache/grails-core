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
import org.springframework.core.io.Resource
import spock.lang.Issue
import spock.lang.Specification

import java.lang.reflect.Field

/**
 * @author Iván López
 */
class SystemEnvironmentConfigSpec extends Specification {

    @Issue('#10670')
    void 'configuration properties defined in SystemEnvironment take precedence'() {
        given: 'default configuration in application-yml'
        Resource resource1 = new ByteArrayResource('''
property.with.period: from-yml
property_with_underscore: from-yml
property-with-hyphen: from-yml
property-with_mixed.symbols: from-yml
'''.bytes, 'test.yml')

        def propertySourceLoader = new YamlPropertySourceLoader()
        def yamlPropertiesSource = propertySourceLoader.load('application.yml', resource1, null)
        def propertySources = new MutablePropertySources()
        propertySources.addFirst(yamlPropertiesSource.first())
        def config = new PropertySourcesConfig(propertySources)

        and: 'overriding value with system environment variable'
        modifiableSystemEnvironment.put(systemProperty, value)

        expect:
        config.getProperty(propertyToFind) == value

        cleanup:
        modifiableSystemEnvironment.remove(systemProperty)

        where:
        propertyToFind                | systemProperty                | value
        'property.with.period'        | 'property.with.period'        | 'from-env'
        'property.with.period'        | 'property_with_period'        | 'from-env'
        'property.with.period'        | 'PROPERTY_WITH_PERIOD'        | 'from-env'
        'property_with_underscore'    | 'property_with_underscore'    | 'from-env'
        'property_with_underscore'    | 'PROPERTY_WITH_UNDERSCORE'    | 'from-env'
        'property-with-hyphen'        | 'property-with-hyphen'        | 'from-env'
        'property-with-hyphen'        | 'PROPERTY-WITH-HYPHEN'        | 'from-env'
        'property-with-hyphen'        | 'PROPERTY_WITH_HYPHEN'        | 'from-env'
        'property-with_mixed.symbols' | 'property-with_mixed.symbols' | 'from-env'
        'property-with_mixed.symbols' | 'property_with_mixed_symbols' | 'from-env'
        'property-with_mixed.symbols' | 'PROPERTY-WITH_MIXED.SYMBOLS' | 'from-env'
        'property-with_mixed.symbols' | 'PROPERTY_WITH_MIXED_SYMBOLS' | 'from-env'
    }

    // From https://github.com/spring-projects/spring-framework/blob/4.3.x/spring-core/src/test/java/org/springframework/core/env/StandardEnvironmentTests.java#L492
    @SuppressWarnings("unchecked")
    static Map<String, String> getModifiableSystemEnvironment() {
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
                        return (Map<String, String>) obj;
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

        try {
            Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            Object obj = theCaseInsensitiveEnvironmentField.get(null);
            return (Map<String, String>) obj;
        }
        catch (NoSuchFieldException ex) {
            // do nothing
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

        try {
            Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            theEnvironmentField.setAccessible(true);
            Object obj = theEnvironmentField.get(null);
            return (Map<String, String>) obj;
        }
        catch (NoSuchFieldException ex) {
            // do nothing
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

        throw new IllegalStateException();
    }
}
