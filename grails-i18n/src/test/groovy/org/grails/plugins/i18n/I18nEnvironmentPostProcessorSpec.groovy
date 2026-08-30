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
package org.grails.plugins.i18n

import org.springframework.boot.SpringApplication
import org.springframework.boot.bootstrap.DefaultBootstrapContext
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor
import org.springframework.core.Ordered
import org.springframework.core.env.MapPropertySource
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.mock.env.MockEnvironment

import org.apache.grails.core.plugins.PluginDiscovery
import org.apache.grails.core.plugins.PluginInfo
import org.apache.grails.core.plugins.PluginUtils
import org.grails.plugins.i18n.fixtures.AlphaPluginGrailsPlugin
import org.grails.plugins.i18n.fixtures.BetaPluginGrailsPlugin

import spock.lang.Specification
import spock.lang.TempDir

class I18nEnvironmentPostProcessorSpec extends Specification {

    @TempDir
    File tempDir

    private DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext()

    /**
     * A class loader carrying a plugin descriptor per directory, on top of the test classpath (which
     * already provides the application descriptor). Separate directories matter: two descriptors at
     * the same resource path in one directory would collapse into a single {@code getResources} hit.
     */
    private ClassLoader classLoaderWithPluginDescriptors(List<String> pluginNames) {
        List<URL> urls = pluginNames.collect { String name ->
            File root = new File(tempDir, name)
            File descriptor = new File(root, I18nDescriptors.DESCRIPTOR_PATH)
            descriptor.parentFile.mkdirs()
            descriptor.text = """\
format.version=1
artifact.type=plugin
artifact.name=${name}
artifact.version=1.0.0
basenames=${name}
locales=fr
"""
            root.toURI().toURL()
        }
        new URLClassLoader(urls as URL[], getClass().classLoader)
    }

    private static SpringApplication applicationUsing(ClassLoader classLoader) {
        new SpringApplication().tap {
            resourceLoader = new DefaultResourceLoader(classLoader)
        }
    }

    private static PluginInfo pluginInfo(Class<?> pluginClass) {
        PluginUtils.createPluginInfo(pluginClass, null, true)
    }

    private I18nEnvironmentPostProcessor processor(List<PluginInfo> topological, List<PluginInfo> load) {
        PluginDiscovery discovery = Stub(PluginDiscovery) {
            getPluginsInTopologicalOrder() >> topological
            getPluginsInLoadOrder() >> load
        }
        bootstrapContext.register(PluginDiscovery, { context -> discovery })
        new I18nEnvironmentPostProcessor(bootstrapContext)
    }

    void 'it runs after config data is loaded and after the Grails environment post processor'() {
        expect: 'otherwise it would see only defaults and replace the application\'s own value'
        I18nEnvironmentPostProcessor.ORDER > ConfigDataEnvironmentPostProcessor.ORDER

        and: 'GrailsEnvironmentPostProcessor is HIGHEST_PRECEDENCE + 15'
        I18nEnvironmentPostProcessor.ORDER > Ordered.HIGHEST_PRECEDENCE + 15
    }

    void 'the application descriptor supplies the base name list'() {
        given:
        MockEnvironment environment = new MockEnvironment()

        when:
        new I18nEnvironmentPostProcessor(bootstrapContext)
                .postProcessEnvironment(environment, applicationUsing(getClass().classLoader))

        then:
        environment.getProperty(I18nEnvironmentPostProcessor.BASENAME_PROPERTY) == 'messages'
    }

    void 'a base name the application declared is kept and ranked first'() {
        given: 'an application.yml value that Boot would otherwise replace wholesale'
        MockEnvironment environment = new MockEnvironment()
        environment.propertySources.addLast(new MapPropertySource('application.yml',
                [(I18nEnvironmentPostProcessor.BASENAME_PROPERTY): 'config/i18n/custom']))

        when:
        new I18nEnvironmentPostProcessor(bootstrapContext)
                .postProcessEnvironment(environment, applicationUsing(getClass().classLoader))

        then: 'neither side is discarded, and the declared value outranks the discovered one'
        environment.getProperty(I18nEnvironmentPostProcessor.BASENAME_PROPERTY) == 'config/i18n/custom,messages'
    }

    void 'plugin base names follow reverse topological order, not load order'() {
        given: 'topological and load order deliberately disagree'
        List<PluginInfo> topological = [pluginInfo(AlphaPluginGrailsPlugin), pluginInfo(BetaPluginGrailsPlugin)]
        List<PluginInfo> load = topological.reverse()
        MockEnvironment environment = new MockEnvironment()
        ClassLoader classLoader = classLoaderWithPluginDescriptors(['alpha-plugin', 'beta-plugin'])

        when:
        processor(topological, load).postProcessEnvironment(environment, applicationUsing(classLoader))

        then: 'reading the load-order list instead would have produced alpha-plugin,beta-plugin'
        environment.getProperty(I18nEnvironmentPostProcessor.BASENAME_PROPERTY) ==
                'messages,beta-plugin,alpha-plugin'
    }

    void 'include-plugin-bundles=false leaves plugin base names out entirely'() {
        given:
        MockEnvironment environment = new MockEnvironment()
        environment.setProperty(I18nEnvironmentPostProcessor.INCLUDE_PLUGIN_BUNDLES_PROPERTY, 'false')
        ClassLoader classLoader = classLoaderWithPluginDescriptors(['alpha-plugin'])

        when:
        new I18nEnvironmentPostProcessor(bootstrapContext)
                .postProcessEnvironment(environment, applicationUsing(classLoader))

        then:
        environment.getProperty(I18nEnvironmentPostProcessor.BASENAME_PROPERTY) == 'messages'
    }

    void 'Grails message-source defaults are contributed but stay overridable'() {
        given:
        MockEnvironment environment = new MockEnvironment()
        environment.setProperty('grails.views.gsp.encoding', 'ISO-8859-1')

        when:
        new I18nEnvironmentPostProcessor(bootstrapContext)
                .postProcessEnvironment(environment, applicationUsing(getClass().classLoader))

        then: 'Grails has always resolved without the system-locale fallback'
        environment.getProperty('spring.messages.fallback-to-system-locale') == 'false'

        and: 'the GSP encoding carries over, which also fixes the old platform-encoding mismatch'
        environment.getProperty('spring.messages.encoding') == 'ISO-8859-1'
    }

    void 'an application that sets spring.messages.encoding itself keeps its own value'() {
        given:
        MockEnvironment environment = new MockEnvironment()
        environment.setProperty('grails.views.gsp.encoding', 'ISO-8859-1')
        environment.setProperty('spring.messages.encoding', 'UTF-16')

        when:
        new I18nEnvironmentPostProcessor(bootstrapContext)
                .postProcessEnvironment(environment, applicationUsing(getClass().classLoader))

        then: 'the defaults land at the lowest precedence'
        environment.getProperty('spring.messages.encoding') == 'UTF-16'
    }

    void 'plugin bundles without plugin discovery fail loudly rather than being dropped'() {
        given: 'a bootstrap context with no PluginDiscovery registered'
        MockEnvironment environment = new MockEnvironment()
        ClassLoader classLoader = classLoaderWithPluginDescriptors(['alpha-plugin'])

        when:
        new I18nEnvironmentPostProcessor(new DefaultBootstrapContext())
                .postProcessEnvironment(environment, applicationUsing(classLoader))

        then: 'silently omitting the plugin would leave its messages unresolvable at runtime'
        IllegalStateException e = thrown()
        e.message.contains(I18nEnvironmentPostProcessor.INCLUDE_PLUGIN_BUNDLES_PROPERTY)
    }
    void 'a short cache duration is contributed when reload is enabled'() {
        given:
        MockEnvironment environment = new MockEnvironment()
        environment.setProperty('grails.gsp.enable.reload', 'true')

        when:
        new I18nEnvironmentPostProcessor(bootstrapContext)
                .postProcessEnvironment(environment, applicationUsing(getClass().classLoader))

        then: "without it ResourceBundleMessageSource caches bundles forever in a map ResourceBundle.clearCache cannot reach"
        environment.getProperty('spring.messages.cache-duration') == '5s'
    }

    void 'an application that sets its own cache duration keeps it'() {
        given:
        MockEnvironment environment = new MockEnvironment()
        environment.setProperty('grails.gsp.enable.reload', 'true')
        environment.setProperty('spring.messages.cache-duration', '30s')

        when:
        new I18nEnvironmentPostProcessor(bootstrapContext)
                .postProcessEnvironment(environment, applicationUsing(getClass().classLoader))

        then: 'the development default lands at the lowest precedence'
        environment.getProperty('spring.messages.cache-duration') == '30s'
    }

    void 'no cache duration is forced when reload is not enabled'() {
        given:
        MockEnvironment environment = new MockEnvironment()

        when:
        new I18nEnvironmentPostProcessor(bootstrapContext)
                .postProcessEnvironment(environment, applicationUsing(getClass().classLoader))

        then: 'production keeps Boot\'s cache-forever behaviour'
        environment.getProperty('spring.messages.cache-duration') == null
    }

    void 'Grails defaults apply even to an application with no descriptor at all'() {
        given: 'spring.messages.basename may point outside grails-app/i18n, so there is no descriptor'
        MockEnvironment environment = new MockEnvironment()
        environment.setProperty('grails.views.gsp.encoding', 'ISO-8859-1')
        ClassLoader empty = new URLClassLoader([] as URL[], null)

        when:
        new I18nEnvironmentPostProcessor(bootstrapContext)
                .postProcessEnvironment(environment, applicationUsing(empty))

        then: 'Boot would otherwise fall back to its own defaults, contradicting documented behaviour'
        environment.getProperty('spring.messages.fallback-to-system-locale') == 'false'
        environment.getProperty('spring.messages.encoding') == 'ISO-8859-1'

        and: 'and nothing is composed, because nothing was discovered'
        environment.getProperty(I18nEnvironmentPostProcessor.BASENAME_PROPERTY) == null
    }

    void 'the deprecated grails.i18n.cache.seconds is translated into a cache duration'() {
        given:
        MockEnvironment environment = new MockEnvironment()
        environment.setProperty('grails.gsp.enable.reload', 'true')
        environment.setProperty('grails.i18n.cache.seconds', '30')

        when:
        new I18nEnvironmentPostProcessor(bootstrapContext)
                .postProcessEnvironment(environment, applicationUsing(getClass().classLoader))

        then: 'an application that tuned reload timing keeps working after upgrading'
        environment.getProperty('spring.messages.cache-duration') == '30s'
    }

    void 'the deprecated property is ignored outside reload mode, as it always was'() {
        given: 'the previous message source applied cacheSeconds only when reload was enabled'
        MockEnvironment environment = new MockEnvironment()
        environment.setProperty('grails.i18n.cache.seconds', '30')

        when:
        new I18nEnvironmentPostProcessor(bootstrapContext)
                .postProcessEnvironment(environment, applicationUsing(getClass().classLoader))

        then: 'honouring it in production would start re-reading bundles that were cached forever'
        environment.getProperty('spring.messages.cache-duration') == null
    }

    void 'spring.messages.cache-duration wins over the deprecated property'() {
        given:
        MockEnvironment environment = new MockEnvironment()
        environment.setProperty('grails.gsp.enable.reload', 'true')
        environment.setProperty('grails.i18n.cache.seconds', '30')
        environment.setProperty('spring.messages.cache-duration', '45s')

        when:
        new I18nEnvironmentPostProcessor(bootstrapContext)
                .postProcessEnvironment(environment, applicationUsing(getClass().classLoader))

        then: 'the replacement takes precedence during a partial migration'
        environment.getProperty('spring.messages.cache-duration') == '45s'
    }
}
