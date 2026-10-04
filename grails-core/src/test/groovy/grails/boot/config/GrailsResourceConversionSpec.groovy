/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package grails.boot.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.PropertiesFactoryBean
import org.springframework.boot.WebApplicationType
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.convert.ConversionFailedException
import org.springframework.core.convert.support.ConfigurableConversionService
import org.springframework.core.env.MapPropertySource
import org.springframework.core.io.Resource
import spock.lang.Shared
import spock.lang.Specification

import grails.boot.GrailsApp
import grails.core.GrailsApplication
import grails.plugins.Plugin
import grails.util.Environment
import grails.util.Holders
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginDiscovery

/**
 * Verifies that {@code Resource[]} bean properties and configuration values expand location patterns
 * once the Grails lifecycle has added its {@code String -> Resource} converter to the conversion
 * service that Spring Boot shares between the environment and the bean factory.
 */
class GrailsResourceConversionSpec extends Specification {

    static final String PACKAGE_PATH = 'grails/boot/config'
    static final String PATTERN = "classpath*:${PACKAGE_PATH}/ResourceConversion*.class"
    static final String MISSING = 'classpath*:META-INF/grails-resource-conversion-missing.properties'
    static final String HOLDER = "classpath:${PACKAGE_PATH}/ResourceConversionHolder.class"
    static final String APPLICATION = "classpath:${PACKAGE_PATH}/ResourceConversionApplication.class"

    @Shared
    ConfigurableApplicationContext application

    @Shared
    String previousEnvironment

    void setupSpec() {
        previousEnvironment = System.getProperty(Environment.KEY)
        System.setProperty(Environment.KEY, Environment.TEST.name)
        GrailsApp app = new GrailsApp(ResourceConversionApplication)
        app.webApplicationType = WebApplicationType.NONE
        app.defaultProperties = [
                'resource.conversion.package'  : PACKAGE_PATH,
                'resource.conversion.locations': PATTERN,
                'resource.conversion.list[0]'  : PATTERN,
                'resource.conversion.list[1]'  : HOLDER
        ] as Map<String, Object>
        application = app.run()
    }

    void cleanupSpec() {
        application?.close()
        Holders.clear()
        Environment.setInitializing(false)
        if (previousEnvironment == null) {
            System.clearProperty(Environment.KEY)
        }
        else {
            System.setProperty(Environment.KEY, previousEnvironment)
        }
    }

    void 'an optional classpath* properties location that matches nothing contributes no properties'() {
        expect: 'the bean definition Spring Integration registers for its optional overrides starts'
        application.getBean('optionalProperties', Properties).isEmpty()

        and: 'the unmatched pattern gives an empty array, not a resource with the pattern as its path'
        application.getBean('missingHolder', ResourceConversionHolder).resources.length == 0
    }

    void 'a Resource[] property given #shape expands each location pattern'() {
        when:
        Resource[] resources = application.getBean(beanName, ResourceConversionHolder).resources

        then:
        urls(resources) == expected.collectMany { urls(application.getResources(it)) } as Set
        resources.every { it.exists() }

        where:
        shape                          | beanName            | expected
        'a classpath* pattern'         | 'patternHolder'     | [PATTERN]
        'a comma-delimited String'     | 'commaHolder'       | [HOLDER, APPLICATION]
        'a list of patterns'           | 'listHolder'        | [PATTERN]
        'an array of patterns'         | 'arrayHolder'       | [PATTERN]
        'a pattern with a placeholder' | 'placeholderHolder' | [PATTERN]
    }

    void 'a @Value Resource[] field expands the pattern its placeholder resolves to'() {
        when:
        Resource[] resources = application.getBean('valueHolder', ResourceConversionValueHolder).resources

        then:
        resources.length > 1
        urls(resources) == urls(application.getResources(PATTERN))
    }

    void 'a list naming the same location twice gives one resource, a comma-delimited String keeps both'() {
        expect: 'a list or array is collected into a set, as Spring does'
        application.getBean('duplicateListHolder', ResourceConversionHolder).resources.length == 1

        and: 'a comma-delimited String is split and each location kept'
        application.getBean('duplicateCommaHolder', ResourceConversionHolder).resources.length == 2
    }

    void 'a null location in a list fails the conversion'() {
        given:
        ConfigurableConversionService conversionService = application.environment.conversionService

        when:
        conversionService.convert([HOLDER, null], Resource[])

        then:
        thrown(ConversionFailedException)
    }

    void 'a classpath* pattern matches more than one resource'() {
        expect: 'the fixture pattern is a real multi-resource match, so expansion is observable'
        application.getBean('patternHolder', ResourceConversionHolder).resources.length > 1
    }

    void 'a Resource property still resolves a single location'() {
        when:
        Resource resource = application.getBean('singleHolder', ResourceConversionHolder).resource

        then:
        resource.exists()
        resource.URL == application.getResource(HOLDER).URL
    }

    void 'configuration values convert to an expanded Resource[]'() {
        given:
        GrailsApplication grailsApplication = application.getBean(GrailsApplication.APPLICATION_ID, GrailsApplication)

        when:
        Resource[] resources = grailsApplication.config.getProperty('resource.conversion.locations', Resource[])

        then:
        urls(resources) == urls(application.getResources(PATTERN))
    }

    void 'a list-valued configuration entry converts to the resources of every location it names'() {
        given:
        GrailsApplication grailsApplication = application.getBean(GrailsApplication.APPLICATION_ID, GrailsApplication)

        when:
        Resource[] resources = grailsApplication.config.getProperty('resource.conversion.list', Resource[])

        then:
        urls(resources) == urls(application.getResources(PATTERN)) + urls(application.getResources(HOLDER))
    }

    void 'the converters are registered once when both lifecycle phases run'() {
        given: 'the early phase and the application post-processor both configured this context'
        String converters = application.environment.conversionService.toString()

        expect:
        application.beanFactory.containsSingleton(GrailsEarlyPluginRegistrationPostProcessor.EARLY_REGISTRATION_COMPLETE_BEAN_NAME)
        converters.count("${String.name} -> ${Resource.name} :") == 1
    }

    void 'the application lifecycle expands patterns when the early registration phase does not run'() {
        given: 'an application context without the early phase, converting as Spring Boot wires it'
        AnnotationConfigApplicationContext context = bootConversionContext()
        context.beanFactory.registerSingleton(PluginDiscovery.BEAN_NAME, discovery(context))
        context.register(ResourceConversionApplication)

        when:
        context.refresh()

        then:
        !context.beanFactory.containsSingleton(GrailsEarlyPluginRegistrationPostProcessor.EARLY_REGISTRATION_COMPLETE_BEAN_NAME)
        context.getBean('optionalProperties', Properties).isEmpty()
        urls(context.getBean('listHolder', ResourceConversionHolder).resources) == urls(context.getResources(PATTERN))

        cleanup:
        context?.close()
        Holders.clear()
        Environment.setInitializing(false)
    }

    void 'the early registration phase expands patterns for plugin beans and plugin configuration reads'() {
        given: 'a plugin context with the early phase only and no Grails application post-processor'
        AnnotationConfigApplicationContext context = bootConversionContext()
        context.beanFactory.registerSingleton(PluginDiscovery.BEAN_NAME, discovery(context, ResourceConversionGrailsPlugin))
        context.beanFactory.registerSingleton(GrailsEarlyPluginRegistrationPostProcessor.APPLICATION_SOURCE_CLASSES_BEAN_NAME,
                [ResourceConversionEarlyApplication] as Class<?>[])
        new GrailsPluginLifecycleInitializer().initialize(context)

        when:
        context.refresh()

        then: 'only the early phase ran'
        context.beanFactory.containsSingleton(GrailsEarlyPluginRegistrationPostProcessor.EARLY_REGISTRATION_COMPLETE_BEAN_NAME)
        context.getBeanNamesForType(GrailsApplicationPostProcessor).length == 0

        and: 'plugin bean properties expand their patterns'
        context.getBean('earlyOptionalProperties', Properties).isEmpty()
        urls(context.getBean('earlyHolder', ResourceConversionHolder).resources) == urls(context.getResources(PATTERN))

        and: 'doWithSpring read an expanded Resource[] from the configuration'
        context.getBean('earlyConfigHolder', ResourceConversionHolder).resources.length == context.getResources(PATTERN).length

        cleanup:
        context?.close()
        Holders.clear()
        Environment.setInitializing(false)
    }

    private static AnnotationConfigApplicationContext bootConversionContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()
        ApplicationConversionService conversionService = new ApplicationConversionService()
        context.environment.conversionService = conversionService
        context.beanFactory.conversionService = conversionService
        context.environment.propertySources.addFirst(new MapPropertySource('resourceConversion', [
                'resource.conversion.package'  : PACKAGE_PATH,
                'resource.conversion.locations': PATTERN
        ] as Map<String, Object>))
        context
    }

    private static PluginDiscovery discovery(ConfigurableApplicationContext context, Class<?>... pluginClasses) {
        DefaultPluginDiscovery discovery = new DefaultPluginDiscovery(pluginClasses)
        discovery.loadPluginsFromClasspath = false
        discovery.init(context.environment)
        discovery
    }

    private static Set<URL> urls(Resource[] resources) {
        resources*.URL as Set<URL>
    }
}

class ResourceConversionHolder {

    Resource[] resources
    Resource resource
}

class ResourceConversionValueHolder {

    @Value('${resource.conversion.locations}')
    Resource[] resources
}

class ResourceConversionApplication extends GrailsAutoConfiguration {

    @Override
    Closure doWithSpring() {
        { ->
            optionalProperties(PropertiesFactoryBean) {
                locations = GrailsResourceConversionSpec.MISSING
            }
            missingHolder(ResourceConversionHolder) {
                resources = GrailsResourceConversionSpec.MISSING
            }
            patternHolder(ResourceConversionHolder) {
                resources = GrailsResourceConversionSpec.PATTERN
            }
            commaHolder(ResourceConversionHolder) {
                resources = "${GrailsResourceConversionSpec.HOLDER}, ${GrailsResourceConversionSpec.APPLICATION}".toString()
            }
            listHolder(ResourceConversionHolder) {
                resources = [GrailsResourceConversionSpec.PATTERN, GrailsResourceConversionSpec.MISSING]
            }
            arrayHolder(ResourceConversionHolder) {
                resources = [GrailsResourceConversionSpec.PATTERN] as String[]
            }
            placeholderHolder(ResourceConversionHolder) {
                resources = 'classpath*:${resource.conversion.package}/ResourceConversion*.class'
            }
            singleHolder(ResourceConversionHolder) {
                resource = GrailsResourceConversionSpec.HOLDER
            }
            valueHolder(ResourceConversionValueHolder)
            duplicateListHolder(ResourceConversionHolder) {
                resources = [GrailsResourceConversionSpec.HOLDER, GrailsResourceConversionSpec.HOLDER]
            }
            duplicateCommaHolder(ResourceConversionHolder) {
                resources = "${GrailsResourceConversionSpec.HOLDER},${GrailsResourceConversionSpec.HOLDER}".toString()
            }
        }
    }
}

/** Stands in for the application class GrailsApp stashes; it contributes no beans of its own. */
class ResourceConversionEarlyApplication extends GrailsAutoConfiguration {
}

class ResourceConversionGrailsPlugin extends Plugin {

    def version = '1.0'

    @Override
    Closure doWithSpring() {
        { ->
            earlyOptionalProperties(PropertiesFactoryBean) {
                locations = GrailsResourceConversionSpec.MISSING
            }
            earlyHolder(ResourceConversionHolder) {
                resources = GrailsResourceConversionSpec.PATTERN
            }
            earlyConfigHolder(ResourceConversionHolder) {
                resources = config.getProperty('resource.conversion.locations', Resource[])
            }
        }
    }
}
