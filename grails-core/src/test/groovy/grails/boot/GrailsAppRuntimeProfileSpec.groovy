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

import spock.lang.Specification
import spock.lang.TempDir
import spock.util.environment.RestoreSystemProperties

import org.springframework.boot.WebApplicationType
import org.springframework.boot.bootstrap.BootstrapRegistry
import org.springframework.boot.bootstrap.BootstrapRegistryInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Profiles

import grails.boot.config.GrailsAutoConfiguration
import grails.plugins.GrailsPluginManager
import grails.plugins.Plugin
import grails.util.Environment
import grails.util.Holders
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginDiscovery

/**
 * An application has no profile at run time: {@code grails.profile} is a key like any other that
 * nothing reads, and the only active Spring profiles are the ones that were asked for and the name
 * of the Grails environment. Each fixture is run without the key and with it.
 *
 * <p>Until Grails 9 the value of the key, {@code web} without one, was added to the active profiles
 * when the application context was handed to the Grails post processor: after the configuration
 * files were read, the conditions of the configuration classes were evaluated and the plugins had
 * registered their beans. A bean of that profile, a file of it and a plugin asking for it were
 * therefore as they are here; what differed was the list of active profiles that code saw once the
 * application ran.</p>
 */
@RestoreSystemProperties
class GrailsAppRuntimeProfileSpec extends Specification {

    @TempDir
    File configDirectory

    void cleanup() {
        Environment.reset()
        Holders.clear()
    }

    void 'with grails.profile #profileKey and #asked #springProfiles the active profiles are #activeProfiles'() {
        given:
        ConfigurableApplicationContext context = run(profileKey, springProfiles, asked == 'the system property')

        expect: 'no profile is active but the ones asked for and the Grails environment'
        context.environment.activeProfiles.toList() == activeProfiles
        context.environment.acceptsProfiles(Profiles.of('web')) == web
        !context.environment.acceptsProfiles(Profiles.of('rest-api'))

        and: 'a bean of a Spring profile is there when that profile was asked for'
        context.containsBean('webProfileBean') == web
        !context.containsBean('restApiProfileBean')
        context.containsBean('alphaProfileBean') == alpha

        and: 'so is the configuration file of that profile'
        context.environment.getProperty('sample.file') == (web ? 'web' : alpha ? 'alpha' : null)

        and: 'a document of application.yml follows the system property alone, as it did'
        context.environment.getProperty('sample.document') == (web && asked == 'the system property' ? 'web' : 'default')

        and: 'the environments block follows the Grails environment'
        context.environment.getProperty('sample.environment') == 'test'

        and: 'the key stays readable as the configuration it is'
        context.environment.getProperty('grails.profile') == profileKey

        cleanup:
        context?.close()

        where:
        profileKey | asked                 | springProfiles || activeProfiles           | web   | alpha
        null       | 'no'                  | null           || ['test']                 | false | false
        'web'      | 'no'                  | null           || ['test']                 | false | false
        'rest-api' | 'no'                  | null           || ['test']                 | false | false
        null       | 'the argument'        | 'web'          || ['web', 'test']          | true  | false
        'web'      | 'the argument'        | 'web'          || ['web', 'test']          | true  | false
        'rest-api' | 'the argument'        | 'web'          || ['web', 'test']          | true  | false
        null       | 'the system property' | 'web'          || ['web', 'test']          | true  | false
        'rest-api' | 'the system property' | 'web'          || ['web', 'test']          | true  | false
        null       | 'the argument'        | 'alpha'        || ['alpha', 'test']        | false | true
        'web'      | 'the argument'        | 'alpha'        || ['alpha', 'test']        | false | true
        'web'      | 'the system property' | 'alpha'        || ['alpha', 'test']        | false | true
        null       | 'the argument'        | 'alpha,web'    || ['alpha', 'web', 'test'] | true  | true
        'plugin'   | 'the argument'        | 'alpha,web'    || ['alpha', 'web', 'test'] | true  | true
    }

    void 'a plugin that declares profiles is enabled with grails.profile #profileKey and spring.profiles.active #springProfiles'() {
        given:
        ConfigurableApplicationContext context = run(profileKey, springProfiles, false)
        GrailsPluginManager pluginManager = context.getBean(GrailsPluginManager.BEAN_NAME, GrailsPluginManager)

        expect: 'the declaration is not read: the plugin takes part whatever is active'
        pluginManager.getGrailsPlugin('declaredProfiles').isEnabled(context.environment.activeProfiles)
        context.getBean('declaredProfilesPluginBean') == 'registered'

        and: 'a plugin that asks the environment gets the Spring profiles that were asked for'
        context.containsBean('alphaPluginBean') == alpha
        context.containsBean('webPluginBean') == web

        cleanup:
        context?.close()

        where:
        profileKey | springProfiles || web   | alpha
        null       | null           || false | false
        'web'      | null           || false | false
        'rest-api' | null           || false | false
        null       | 'web'          || true  | false
        'rest-api' | 'web'          || true  | false
        null       | 'alpha'        || false | true
        'web'      | 'alpha'        || false | true
    }

    void 'an application asks for a profile with #setting in application.yml and grails.profile #profileKey'() {
        given:
        ConfigurableApplicationContext context = run(profileKey, null, false, setting)

        expect:
        context.environment.activeProfiles.toList().sort() == ['test', 'web']
        context.containsBean('webProfileBean')
        context.environment.getProperty('sample.file') == 'web'
        context.containsBean('webPluginBean')

        cleanup:
        context?.close()

        where:
        setting                        | profileKey
        'spring.profiles.active: web'  | null
        'spring.profiles.active: web'  | 'rest-api'
        'spring.profiles.include: web' | null
        'spring.profiles.include: web' | 'rest-api'
    }

    void 'a context that is not started by GrailsApp gets no profile from grails.profile #profileKey either'() {
        given:
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()
        if (profileKey) {
            System.setProperty('grails.profile', profileKey)
        }
        DefaultPluginDiscovery discovery = new DefaultPluginDiscovery([DeclaredProfilesGrailsPlugin] as Class<?>[])
        discovery.loadPluginsFromClasspath = false
        discovery.init(context.environment)
        context.beanFactory.registerSingleton(PluginDiscovery.BEAN_NAME, discovery)
        context.register(RuntimeProfileApplication, RuntimeProfileBeans)

        when:
        context.refresh()

        then:
        context.environment.activeProfiles.toList() == []
        !context.containsBean('webProfileBean')
        context.getBean('declaredProfilesPluginBean') == 'registered'

        cleanup:
        context?.close()
        Environment.setInitializing(false)

        where:
        profileKey << [null, 'web']
    }

    private ConfigurableApplicationContext run(String profileKey, String springProfiles, boolean systemProperty, String setting = '') {
        System.setProperty(Environment.KEY, 'test')
        System.clearProperty('spring.profiles.active')
        if (springProfiles && systemProperty) {
            System.setProperty('spring.profiles.active', springProfiles)
        }
        new File(configDirectory, 'application.yml').text = """
sample.document: default
${profileKey ? "grails.profile: ${profileKey}" : ''}
${setting}
environments:
    test:
        sample.environment: test
    production:
        sample.environment: production
---
spring.config.activate.on-profile: web
sample.document: web
"""
        new File(configDirectory, 'application-web.yml').text = 'sample.file: web\n'
        new File(configDirectory, 'application-alpha.yml').text = 'sample.file: alpha\n'
        new File(configDirectory, 'application-rest-api.yml').text = 'sample.file: rest-api\n'

        DefaultPluginDiscovery discovery = new DefaultPluginDiscovery([DeclaredProfilesGrailsPlugin, EnvironmentProfilesGrailsPlugin] as Class<?>[])
        discovery.loadPluginsFromClasspath = false
        GrailsApp app = new GrailsApp(RuntimeProfileApplication, RuntimeProfileBeans)
        app.webApplicationType = WebApplicationType.NONE
        app.addBootstrapRegistryInitializer(new BootstrapRegistryInitializer() {

            @Override
            void initialize(BootstrapRegistry registry) {
                registry.register(PluginDiscovery, BootstrapRegistry.InstanceSupplier.of(discovery))
            }
        })
        List<String> args = ["--spring.config.location=file:${configDirectory.absolutePath}/".toString()]
        if (springProfiles && !systemProperty) {
            args << "--spring.profiles.active=${springProfiles}".toString()
        }
        app.run(args as String[])
    }
}

class RuntimeProfileApplication extends GrailsAutoConfiguration {
}

@Configuration
class RuntimeProfileBeans {

    @Bean
    @Profile('web')
    String webProfileBean() {
        'web'
    }

    @Bean
    @Profile('rest-api')
    String restApiProfileBean() {
        'rest-api'
    }

    @Bean
    @Profile('alpha')
    String alphaProfileBean() {
        'alpha'
    }
}

/** What the web plugin template generated until Grails 9. */
class DeclaredProfilesGrailsPlugin extends Plugin {

    def version = '1.0'
    def profiles = ['web']
    @Override
    Closure doWithSpring() {
        { ->
            declaredProfilesPluginBean(String, 'registered')
        }
    }
}

/** How a plugin ties beans to a Spring profile: it asks the environment. */
class EnvironmentProfilesGrailsPlugin extends Plugin {

    def version = '1.0'

    @Override
    Closure doWithSpring() {
        boolean alpha = applicationContext.environment.acceptsProfiles(Profiles.of('alpha'))
        boolean web = applicationContext.environment.acceptsProfiles(Profiles.of('web'))
        return { ->
            if (alpha) {
                alphaPluginBean(String, 'alpha')
            }
            if (web) {
                webPluginBean(String, 'web')
            }
        }
    }
}
