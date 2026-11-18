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

import spock.lang.Issue
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import org.springframework.core.env.MapPropertySource

import grails.config.Config
import grails.util.Holders

@Issue('https://github.com/apache/grails-core/issues/15840')
@RestoreSystemProperties
class PropertySourcesConfigReloadSpec extends Specification {

    private Config previousConfig

    void setup() {
        previousConfig = Holders.getConfig()
        System.clearProperty('spring.profiles.active')
    }

    void cleanup() {
        Holders.setConfig(previousConfig)
    }

    void 'backup clear and merge restores configuration without profile metadata with JVM profile #activeProfile'() {
        given:
        Holders.setConfig(new PropertySourcesConfig(new MapPropertySource('application', [
                'sample.message': 'original',
                'sample.enabled': true,
                'sample.items': ['first', 'second']
        ])))
        PropertySourcesConfig initialConfig = backupInitialConfiguration()

        when:
        if (activeProfile != null) {
            System.setProperty('spring.profiles.active', activeProfile)
        }
        Holders.config.clear()
        Holders.config.merge(initialConfig)

        then:
        Holders.config.getProperty('sample.message') == 'original'
        Holders.config.getProperty('sample.enabled', Boolean) == true
        Holders.config.getProperty('sample.items', List) == ['first', 'second']
        initialConfig.getProperty('sample.message') == 'original'

        where:
        activeProfile << [null, 'beta']
    }

    void 'backup preserves configuration containing spring.profiles.active with JVM profile #activeProfile'() {
        given:
        if (activeProfile != null) {
            System.setProperty('spring.profiles.active', activeProfile)
        }
        Holders.setConfig(new PropertySourcesConfig(new MapPropertySource('application', [
                'spring.profiles.active': 'alpha',
                'sample.message': 'original'
        ])))

        when:
        PropertySourcesConfig initialConfig = backupInitialConfiguration()
        Holders.config.clear()
        Holders.config.merge(initialConfig)

        then:
        initialConfig.getProperty('sample.message') == 'original'
        initialConfig.getProperty('spring.profiles.active') == 'alpha'
        Holders.config.getProperty('sample.message') == 'original'
        Holders.config.getProperty('spring.profiles.active') == 'alpha'

        where:
        activeProfile << [null, 'alpha', 'beta']
    }

    void 'merge restores an existing snapshot containing spring.profiles.active'() {
        given:
        def initialConfig = new PropertySourcesConfig(new MapPropertySource('application', [
                'spring.profiles.active': 'alpha',
                'sample.message': 'original'
        ]))
        Holders.setConfig(new PropertySourcesConfig([sample: [message: 'changed']]))
        assert initialConfig.getProperty('sample.message') == 'original'

        when:
        Holders.config.clear()
        Holders.config.merge(initialConfig)

        then:
        Holders.config.getProperty('sample.message') == 'original'
        Holders.config.getProperty('spring.profiles.active') == 'alpha'
    }

    void 'merge restores an existing profile-specific snapshot with JVM profile #activeProfile'() {
        given:
        System.setProperty('spring.profiles.active', 'alpha')
        Holders.setConfig(new PropertySourcesConfig(new MapPropertySource('application', [
                'spring.config.activate.on-profile': 'alpha',
                'sample.message': 'original'
        ])))
        PropertySourcesConfig initialConfig = backupInitialConfiguration()
        assert initialConfig.getProperty('sample.message') == 'original'

        when:
        System.setProperty('spring.profiles.active', activeProfile)
        Holders.config.clear()
        Holders.config.merge(initialConfig)

        then:
        Holders.config.getProperty('sample.message') == 'original'
        Holders.config.getProperty('spring.config.activate.on-profile') == 'alpha'

        where:
        activeProfile << ['alpha', 'beta']
    }

    void 'isolated application classloaders restore snapshots with profile metadata #profileMetadata and JVM profile #activeProfile'() {
        given:
        URL[] runtimeUrls = runtimeClasspath()
        def firstLoader = new URLClassLoader(runtimeUrls, ClassLoader.getPlatformClassLoader())
        def secondLoader = new URLClassLoader(runtimeUrls, ClassLoader.getPlatformClassLoader())
        def firstShell = firstLoader.loadClass('groovy.lang.GroovyShell').getConstructor(ClassLoader).newInstance(firstLoader)
        def secondShell = secondLoader.loadClass('groovy.lang.GroovyShell').getConstructor(ClassLoader).newInstance(secondLoader)
        System.setProperty('spring.profiles.active', 'alpha')

        firstShell.evaluate("""
            import grails.util.Holders
            import org.grails.config.PropertySourcesConfig
            import org.springframework.core.env.MapPropertySource

            def values = ['sample.message': 'first']
            if (${profileMetadata}) {
                values['spring.config.activate.on-profile'] = 'alpha'
            }
            Holders.config = new PropertySourcesConfig(new MapPropertySource('application', values))
            def configMap = [:]
            for (def entry : Holders.config) {
                configMap.put(entry.key, Holders.config.get(entry.key))
            }
            initialConfig = new PropertySourcesConfig(configMap)
            assert initialConfig.getProperty('sample.message') == 'first'
        """)

        secondShell.evaluate("""
            import grails.util.Holders
            import org.grails.config.PropertySourcesConfig

            Holders.config = new PropertySourcesConfig(['sample.message': 'second'])
            System.setProperty('spring.profiles.active', '${activeProfile}')
        """)

        assert !firstLoader.loadClass('grails.util.Holders').is(secondLoader.loadClass('grails.util.Holders'))
        assert firstShell.evaluate("grails.util.Holders.config.getProperty('sample.message')") == 'first'
        assert secondShell.evaluate("grails.util.Holders.config.getProperty('sample.message')") == 'second'

        when:
        def restoredValue = firstShell.evaluate('''
            grails.util.Holders.config.clear()
            grails.util.Holders.config.merge(initialConfig)
            grails.util.Holders.config.getProperty('sample.message')
        ''')

        then:
        restoredValue == 'first'
        secondShell.evaluate("grails.util.Holders.config.getProperty('sample.message')") == 'second'

        cleanup:
        firstLoader?.close()
        secondLoader?.close()

        where:
        profileMetadata | activeProfile
        false           | 'beta'
        true            | 'alpha'
        true            | 'beta'
    }

    private URL[] runtimeClasspath() {
        Set<URL> urls = System.getProperty('java.class.path').split(File.pathSeparator).collect {
            new File(it).toURI().toURL()
        }
        ClassLoader loader = getClass().getClassLoader()
        while (loader != null) {
            if (loader instanceof URLClassLoader) {
                urls.addAll(Arrays.asList(loader.getURLs()))
            }
            loader = loader.getParent()
        }
        urls.toArray(new URL[0])
    }

    private static PropertySourcesConfig backupInitialConfiguration() {
        Config config = Holders.config
        Map<String, Object> configMap = [:]
        for (def entry : config) {
            configMap.put(entry.key, config.get(entry.key))
        }
        new PropertySourcesConfig(configMap)
    }
}
