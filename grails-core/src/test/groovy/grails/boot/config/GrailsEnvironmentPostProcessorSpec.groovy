/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.boot.config

import grails.util.Environment
import org.apache.grails.core.plugins.PluginDescriptor
import org.apache.grails.core.plugins.PluginDiscovery
import org.apache.grails.core.plugins.PluginInfo
import org.apache.grails.core.plugins.PluginMetadata
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext
import org.springframework.boot.SpringApplication
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.Resource
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import org.grails.core.exceptions.GrailsConfigurationException

@RestoreSystemProperties
class GrailsEnvironmentPostProcessorSpec extends Specification {

    def setup() {
        System.setProperty(Environment.KEY, Environment.DEVELOPMENT.name)
        Environment.reset()
    }

    def cleanup() {
        Environment.reset()
    }

    def 'postProcessEnvironment loads plugin configurations when discovery bean is available'() {
        given:
        def bootstrapContext = Mock(ConfigurableBootstrapContext)
        def discovery = Mock(PluginDiscovery)
        bootstrapContext.get(PluginDiscovery.class) >> discovery
        discovery.getPluginsInLoadOrder() >> []
        
        def processor = new GrailsEnvironmentPostProcessor(bootstrapContext)
        def environment = new StandardEnvironment()
        def application = Mock(SpringApplication)

        when:
        processor.postProcessEnvironment(environment, application)

        then:
        noExceptionThrown()
    }

    def 'postProcessEnvironment errors on missing discovery bean'() {
        given:
        def bootstrapContext = Mock(ConfigurableBootstrapContext)
        bootstrapContext.get(PluginDiscovery.class) >> null
        
        def processor = new GrailsEnvironmentPostProcessor(bootstrapContext)
        def environment = new StandardEnvironment()
        def application = Mock(SpringApplication)

        when:
        processor.postProcessEnvironment(environment, application)

        then:
        thrown(GrailsConfigurationException)
    }

    def 'order value is set appropriately for early loading'() {
        given:
        def bootstrapContext = Mock(ConfigurableBootstrapContext)
        bootstrapContext.get(PluginDiscovery.class) >> null
        
        def processor = new GrailsEnvironmentPostProcessor(bootstrapContext)

        expect:
        processor.order < 0  // Should run early (HIGHEST_PRECEDENCE + 15 is still negative)
    }

    def 'postProcessEnvironment handles IOException from yml configuration gracefully'() {
        given:
        def ymlResource = Mock(Resource)
        ymlResource.exists() >> true
        ymlResource.getFilename() >> 'plugin.yml'
        ymlResource.getInputStream() >> { throw new IOException('Simulated yml read failure') }

        def ymlPlugin = createPluginInfo('ymlPlugin', ymlResource)

        def bootstrapContext = Mock(ConfigurableBootstrapContext)
        def discovery = Mock(PluginDiscovery)
        bootstrapContext.get(PluginDiscovery.class) >> discovery
        discovery.getPluginsInLoadOrder() >> [ymlPlugin]

        def processor = new GrailsEnvironmentPostProcessor(bootstrapContext)
        def environment = new StandardEnvironment()
        def application = Mock(SpringApplication)

        when:
        processor.postProcessEnvironment(environment, application)

        then:
        noExceptionThrown()
    }

    def 'postProcessEnvironment handles groovy configuration failure gracefully'() {
        given: 'a plugin with a groovy config resource that fails to load'
        // GroovyConfigPropertySourceLoader wraps all failures in GrailsConfigurationException
        // (a RuntimeException), which escapes the per-plugin catch(IOException) and is caught
        // by the outer catch(Exception) in postProcessEnvironment
        def groovyResource = Mock(Resource)
        groovyResource.exists() >> true
        groovyResource.getFilename() >> 'plugin.groovy'
        groovyResource.getURL() >> { throw new IOException('Simulated groovy read failure') }

        def groovyPlugin = createPluginInfo('groovyPlugin', groovyResource)

        def bootstrapContext = Mock(ConfigurableBootstrapContext)
        def discovery = Mock(PluginDiscovery)
        bootstrapContext.get(PluginDiscovery.class) >> discovery
        discovery.getPluginsInLoadOrder() >> [groovyPlugin]

        def processor = new GrailsEnvironmentPostProcessor(bootstrapContext)
        def environment = new StandardEnvironment()
        def application = Mock(SpringApplication)

        when:
        processor.postProcessEnvironment(environment, application)

        then:
        noExceptionThrown()
    }

    def 'postProcessEnvironment handles yml IOException then groovy RuntimeException gracefully'() {
        given: 'two plugins where yml fails with IOException and groovy fails with GrailsConfigurationException'
        // loadPluginConfigurations iterates in reverse order, so [groovyPlugin, ymlPlugin]
        // becomes [ymlPlugin, groovyPlugin] after reversal. The yml IOException is caught
        // per-plugin and processing continues. The groovy GrailsConfigurationException (RuntimeException)
        // escapes the inner catch(IOException) and is caught by the outer catch(Exception).
        def ymlResource = Mock(Resource)
        ymlResource.exists() >> true
        ymlResource.getFilename() >> 'plugin.yml'
        ymlResource.getInputStream() >> { throw new IOException('Simulated yml read failure') }

        def groovyResource = Mock(Resource)
        groovyResource.exists() >> true
        groovyResource.getFilename() >> 'plugin.groovy'
        groovyResource.getURL() >> { throw new IOException('Simulated groovy read failure') }

        // Order matters: reversed iteration processes groovyPlugin first, then ymlPlugin.
        // Passing [groovyPlugin, ymlPlugin] means the reversed list is [ymlPlugin, groovyPlugin].
        // yml is processed first (IOException caught per-plugin), then groovy fails (caught by outer handler).
        def ymlPlugin = createPluginInfo('ymlPlugin', ymlResource)
        def groovyPlugin = createPluginInfo('groovyPlugin', groovyResource)

        def bootstrapContext = Mock(ConfigurableBootstrapContext)
        def discovery = Mock(PluginDiscovery)
        bootstrapContext.get(PluginDiscovery.class) >> discovery
        discovery.getPluginsInLoadOrder() >> [groovyPlugin, ymlPlugin]

        def processor = new GrailsEnvironmentPostProcessor(bootstrapContext)
        def environment = new StandardEnvironment()
        def application = Mock(SpringApplication)

        when:
        processor.postProcessEnvironment(environment, application)

        then:
        noExceptionThrown()
    }

    def 'postProcessEnvironment does not add property sources when both yml and groovy configurations fail'() {
        given: 'two plugins where both configurations fail to load'
        // The reversed iteration processes ymlPlugin first (IOException caught per-plugin),
        // then groovyPlugin (GrailsConfigurationException caught by outer handler).
        // Neither should add property sources.
        def ymlResource = Mock(Resource)
        ymlResource.exists() >> true
        ymlResource.getFilename() >> 'plugin.yml'
        ymlResource.getInputStream() >> { throw new IOException('Simulated yml read failure') }

        def groovyResource = Mock(Resource)
        groovyResource.exists() >> true
        groovyResource.getFilename() >> 'plugin.groovy'
        groovyResource.getURL() >> { throw new IOException('Simulated groovy read failure') }

        def ymlPlugin = createPluginInfo('ymlPlugin', ymlResource)
        def groovyPlugin = createPluginInfo('groovyPlugin', groovyResource)

        def bootstrapContext = Mock(ConfigurableBootstrapContext)
        def discovery = Mock(PluginDiscovery)
        bootstrapContext.get(PluginDiscovery.class) >> discovery
        // [groovyPlugin, ymlPlugin] reversed = [ymlPlugin, groovyPlugin]
        discovery.getPluginsInLoadOrder() >> [groovyPlugin, ymlPlugin]

        def processor = new GrailsEnvironmentPostProcessor(bootstrapContext)
        def environment = new StandardEnvironment()
        def application = Mock(SpringApplication)
        def initialSourceCount = environment.propertySources.size()

        when:
        processor.postProcessEnvironment(environment, application)

        then:
        noExceptionThrown()
        environment.propertySources.size() == initialSourceCount
    }

    def 'postProcessEnvironment groovy failure aborts remaining plugin config loading'() {
        given: 'groovy plugin is processed first and its RuntimeException aborts the loop'
        // When [ymlPlugin, groovyPlugin] is passed, the reversed list is [groovyPlugin, ymlPlugin].
        // The groovy GrailsConfigurationException (RuntimeException) escapes the inner
        // catch(IOException), so the yml plugin is never reached. The outer catch(Exception)
        // handles it gracefully.
        def ymlResource = Mock(Resource)
        ymlResource.exists() >> true
        ymlResource.getFilename() >> 'plugin.yml'
        ymlResource.getInputStream() >> { throw new IOException('Simulated yml read failure') }

        def groovyResource = Mock(Resource)
        groovyResource.exists() >> true
        groovyResource.getFilename() >> 'plugin.groovy'
        groovyResource.getURL() >> { throw new IOException('Simulated groovy read failure') }

        def ymlPlugin = createPluginInfo('ymlPlugin', ymlResource)
        def groovyPlugin = createPluginInfo('groovyPlugin', groovyResource)

        def bootstrapContext = Mock(ConfigurableBootstrapContext)
        def discovery = Mock(PluginDiscovery)
        bootstrapContext.get(PluginDiscovery.class) >> discovery
        // [ymlPlugin, groovyPlugin] reversed = [groovyPlugin, ymlPlugin]
        // groovy is processed first, fails with RuntimeException, yml is never reached
        discovery.getPluginsInLoadOrder() >> [ymlPlugin, groovyPlugin]

        def processor = new GrailsEnvironmentPostProcessor(bootstrapContext)
        def environment = new StandardEnvironment()
        def application = Mock(SpringApplication)

        when:
        processor.postProcessEnvironment(environment, application)

        then:
        noExceptionThrown()
    }

    private static PluginInfo createPluginInfo(String name, Resource configResource) {
        def metadata = new PluginMetadata(
                name,
                '1.0',
                '*',
                CoreTestGrailsPlugin,
                new String[0],
                new String[0],
                Collections.emptyMap(),
                new String[0],
                new String[0],
                new String[0],
                Collections.emptyMap(),
                true
        )
        def descriptor = new PluginDescriptor(null, [], [])
        return new PluginInfo(descriptor, metadata, configResource, false)
    }
}

// Test fixture plugin classes (minimal, no GrailsApplication dependency)
class CoreTestGrailsPlugin {

    def version = '1.0'
    def loadAfter = []
    def loadBefore = []
}

class SimpleGrailsPlugin {

    def version = '1.0'
}

class ABCGrailsPlugin {

    def version = '1.0'
}
