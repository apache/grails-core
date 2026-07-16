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
package grails.boot

import grails.plugins.GrailsPluginManager
import grails.plugins.Plugin
import grails.util.Environment
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginDiscovery
import org.springframework.boot.bootstrap.BootstrapRegistry
import org.springframework.boot.bootstrap.BootstrapRegistryInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Configuration
import spock.lang.Specification
import spock.util.concurrent.PollingConditions
import spock.util.environment.RestoreSystemProperties

/**
 * @author Anders Aaberg
 */
@RestoreSystemProperties
class DevelopmentModeWatchSpec extends Specification {

    void "test root watchPattern"() {
        setup:
        System.setProperty(Environment.KEY, Environment.DEVELOPMENT.getName())
        System.setProperty("base.dir", ".")
        GrailsApp app = new GrailsApp(GrailsTestConfigurationClass.class)
        DefaultPluginDiscovery discovery = new DefaultPluginDiscovery([WatchedResourcesGrailsPlugin] as Class<?>[])
        discovery.loadPluginsFromClasspath = false
        app.addBootstrapRegistryInitializer(new BootstrapRegistryInitializer() {
            @Override
            void initialize(BootstrapRegistry registry) {
                registry.register(PluginDiscovery, BootstrapRegistry.InstanceSupplier.of(discovery))
            }
        })
        ConfigurableApplicationContext context = app.run()
        GrailsPluginManager pluginManager = context.getBean(GrailsPluginManager.BEAN_NAME, GrailsPluginManager)
        WatchedResourcesGrailsPlugin plugin = (WatchedResourcesGrailsPlugin) pluginManager.getGrailsPlugin('watchedResources').instance

        when:
        File watchedFile = new File('testWatchedFile.properties')
        watchedFile.createNewFile()
        watchedFile.write 'foo.bar=baz'

        then:
        new PollingConditions(timeout: 10).eventually {
            assert plugin.fileIsChanged.endsWith('testWatchedFile.properties')
        }

        cleanup:
        GrailsApp.setDevelopmentModeActive(false)
        context?.close()
        if(watchedFile != null) {
            watchedFile.delete()
        }
    }
}

@Configuration
class GrailsTestConfigurationClass {
}

class WatchedResourcesGrailsPlugin extends Plugin {
    def version = "1.0"
    def watchedResources = "file:./**/*.properties"

    void onChange(Map<String, Object> event) {
        fileIsChanged = event.source.path.toString()
    }
    String fileIsChanged = ""
}
