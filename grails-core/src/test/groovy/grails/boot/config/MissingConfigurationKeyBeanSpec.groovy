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
package grails.boot.config

import java.nio.file.Files
import java.nio.file.Path

import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.context.annotation.AnnotationConfigApplicationContext

import grails.util.Environment
import grails.util.Holders
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginDiscovery
import spock.lang.Specification
import spock.lang.TempDir

/**
 * A bean of the application's {@code doWithSpring} or {@code grails-app/conf/spring/resources.groovy}
 * that assigns a configuration key nobody set, {@code grailsApplication.config.some.missing.key}, gets
 * {@code null}, not the navigator that stands for the missing key, for a property of type {@code String}
 * or {@code Object}, whether or not the context has a conversion service of its own.
 */
class MissingConfigurationKeyBeanSpec extends Specification {

    @TempDir
    Path resourcesRoot

    void cleanup() {
        Holders.clear()
        Environment.setInitializing(false)
    }

    void 'a doWithSpring bean assigning a missing configuration key gets null: #description'() {
        given:
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()
        if (bootConversionService) {
            ApplicationConversionService conversionService = new ApplicationConversionService()
            context.environment.conversionService = conversionService
            context.beanFactory.conversionService = conversionService
        }
        startAsGrailsApplication(context, MissingConfigurationKeyApplication)

        when:
        context.refresh()

        then:
        MissingConfigurationKeyHolder holder = context.getBean('missingKeyHolder', MissingConfigurationKeyHolder)
        holder.text == null
        holder.value == null

        cleanup:
        context.close()

        where:
        description                                       | bootConversionService
        'a context without a conversion service'          | false
        'a context with the conversion service of Boot'   | true
    }

    void 'a resources.groovy bean assigning a missing configuration key gets null'() {
        given: 'a spring/resources.groovy on the class path of the context'
        Path spring = Files.createDirectories(resourcesRoot.resolve('spring'))
        spring.resolve('resources.groovy').toFile().text = '''
            beans = {
                resourcesMissingKeyHolder(grails.boot.config.MissingConfigurationKeyHolder) {
                    text = grailsApplication.config.some.missing.key
                    value = application.config.another.missing.key
                }
            }
        '''
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()
        context.classLoader = new URLClassLoader([resourcesRoot.toUri().toURL()] as URL[], getClass().classLoader)
        startAsGrailsApplication(context, MissingConfigurationKeyResourcesApplication)

        when:
        context.refresh()

        then:
        MissingConfigurationKeyHolder holder = context.getBean('resourcesMissingKeyHolder', MissingConfigurationKeyHolder)
        holder.text == null
        holder.value == null

        cleanup:
        context.close()
    }

    /**
     * Promotes plugin discovery and stashes the application class, as the bootstrap registry and
     * {@code GrailsApp} do, so the context starts as a Grails application.
     */
    private static void startAsGrailsApplication(AnnotationConfigApplicationContext context, Class<?> applicationClass) {
        DefaultPluginDiscovery discovery = new DefaultPluginDiscovery(new Class<?>[0])
        discovery.loadPluginsFromClasspath = false
        discovery.init(context.environment)
        context.beanFactory.registerSingleton(PluginDiscovery.BEAN_NAME, discovery)
        context.beanFactory.registerSingleton(GrailsEarlyPluginRegistrationPostProcessor.APPLICATION_SOURCE_CLASSES_BEAN_NAME,
                [applicationClass] as Class<?>[])
        new GrailsPluginLifecycleInitializer().initialize(context)
        context.register(applicationClass)
    }
}

class MissingConfigurationKeyHolder {

    String text
    Object value
}

class MissingConfigurationKeyApplication extends GrailsAutoConfiguration {

    @Override
    Closure doWithSpring() {
        { ->
            missingKeyHolder(MissingConfigurationKeyHolder) {
                text = grailsApplication.config.some.missing.key
                value = grailsApplication.config.another.missing.key
            }
        }
    }
}

class MissingConfigurationKeyResourcesApplication extends GrailsAutoConfiguration {
}
