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

import java.lang.reflect.Method
import java.lang.reflect.Modifier

import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.context.support.AbstractApplicationContext
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import grails.boot.config.GrailsAutoConfiguration
import grails.boot.config.GrailsEarlyPluginRegistrationPostProcessor
import grails.core.GrailsApplication
import grails.plugins.GrailsPluginManager
import grails.util.Environment
import grails.util.Holders

/**
 * Covers {@link GrailsAppBuilder} as the fluent way to run a {@link GrailsApp}: every builder method
 * keeps the Grails type, and a parent/child hierarchy built with it starts a {@code GrailsApp} in
 * every context, with the plugin lifecycle in the one that has the application class.
 */
@RestoreSystemProperties
class GrailsAppBuilderSpec extends Specification {

    /** Builders whose contexts a feature started, closed youngest first so children close before parents. */
    private final List<GrailsAppBuilder> builders = []

    void setup() {
        System.setProperty(Environment.KEY, Environment.TEST.getName())
    }

    void cleanup() {
        for (GrailsAppBuilder builder in builders.reverse()) {
            ConfigurableApplicationContext context = builder.context()
            if (context?.isActive()) {
                context.close()
            }
        }
        Holders.clear()
        Environment.setInitializing(false)
    }

    private GrailsAppBuilder tracked(GrailsAppBuilder builder) {
        builders << builder
        return builder
    }

    void 'the builder runs a GrailsApp with the Grails startup defaults'() {
        given:
        GrailsAppBuilder builder = tracked(new GrailsAppBuilder(BuilderPlainConfig).web(WebApplicationType.NONE))

        when:
        ConfigurableApplicationContext context = builder.run()

        then: 'the application is a GrailsApp, and is typed as one'
        builder.application() instanceof GrailsApp
        GrailsAppBuilder.getMethod('application').returnType == GrailsApp

        and: 'with the Grails defaults for bean overriding and circular references'
        ((DefaultListableBeanFactory) context.beanFactory).allowBeanDefinitionOverriding
        ((DefaultListableBeanFactory) context.beanFactory).allowCircularReferences

        and: 'the Grails environment as an active profile'
        context.environment.activeProfiles.contains(Environment.TEST.name)

        and: 'standing alone, the plugin lifecycle whatever the sources are'
        context.beanFactory.containsSingleton(GrailsApplication.APPLICATION_ID)
        context.getBean(BuilderPlainConfig.PlainMarker) != null
    }

    void '#method.name(#method.parameterCount) keeps the Grails builder type'() {
        expect: 'so a chain stays in Grails terms, and its child() and parent() are the Grails ones'
        GrailsAppBuilder.getMethod(method.name, method.parameterTypes).returnType == GrailsAppBuilder

        where:
        method << fluentMethodsOfSpringApplicationBuilder()
    }

    private static List<Method> fluentMethodsOfSpringApplicationBuilder() {
        SpringApplicationBuilder.declaredMethods.findAll { Method method ->
            Modifier.isPublic(method.modifiers) && !method.synthetic && method.returnType == SpringApplicationBuilder
        }.sort { Method method -> method.toGenericString() }
    }

    void 'build() hands out the GrailsApp, typed as one'() {
        given:
        GrailsAppBuilder builder = new GrailsAppBuilder(BuilderPlainConfig).web(WebApplicationType.NONE)

        expect:
        builder.build().is(builder.application())
        builder.build('--builder.unused=true').is(builder.application())
        GrailsAppBuilder.getMethod('build').returnType == GrailsApp
        GrailsAppBuilder.getMethod('build', String[]).returnType == GrailsApp
    }

    void 'child() builds a Grails child beneath the parent and carries the parent configuration to it'() {
        given:
        GrailsAppBuilder parentBuilder = tracked(new GrailsAppBuilder(BuilderSharedConfig)
                .properties('builder.shared=from-parent')
                .profiles('shared'))
        GrailsAppBuilder childBuilder = tracked(parentBuilder.child(BuilderTestApplication).web(WebApplicationType.NONE))

        when:
        ConfigurableApplicationContext child = childBuilder.run()
        ConfigurableApplicationContext parent = parentBuilder.context()

        then: 'both contexts were started by a GrailsApp, the parent without a web server'
        parentBuilder.application() instanceof GrailsApp
        childBuilder.application() instanceof GrailsApp
        parentBuilder.application().webApplicationType == WebApplicationType.NONE
        parent.isActive()
        child.parent.is(parent)

        and: 'what the parent was configured with reached the child'
        child.environment.getProperty('builder.shared') == 'from-parent'
        child.environment.activeProfiles.contains('shared')

        and: 'the child sees the beans of the parent'
        child.getBean(BuilderSharedConfig.SharedService).is(parent.getBean(BuilderSharedConfig.SharedService))
    }

    void 'properties() set on a child are shared with its parent, as in Spring Boot'() {
        given:
        GrailsAppBuilder parentBuilder = tracked(new GrailsAppBuilder(BuilderSharedConfig))
        GrailsAppBuilder childBuilder = tracked(parentBuilder.child(BuilderTestApplication)
                .web(WebApplicationType.NONE)
                .properties('builder.child=from-child'))

        when:
        ConfigurableApplicationContext child = childBuilder.run()
        ConfigurableApplicationContext parent = parentBuilder.context()

        then:
        child.environment.getProperty('builder.child') == 'from-child'
        parent.environment.getProperty('builder.child') == 'from-child'
    }

    void 'a child registers no JVM shutdown hook unless asked to, because closing its parent closes it'() {
        given:
        GrailsAppBuilder parentBuilder = tracked(new GrailsAppBuilder(BuilderSharedConfig))
        GrailsAppBuilder quietBuilder = tracked(parentBuilder.child(BuilderTestApplication).web(WebApplicationType.NONE))

        when:
        ConfigurableApplicationContext quiet = quietBuilder.run()
        ConfigurableApplicationContext parent = parentBuilder.context()

        then: 'the parent, which nothing else closes, is registered with the shutdown hook; the child is not'
        registeredWithShutdownHook(parent)
        !registeredWithShutdownHook(quiet)

        when: 'a sibling asks for one'
        ConfigurableApplicationContext loud = tracked(quietBuilder.sibling(BuilderPlainConfig)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(true)).run()

        then:
        registeredWithShutdownHook(loud)
    }

    /**
     * Spring Boot registers a context with its shutdown hook by adding the hook's close listener to
     * the context, which is the only trace of the registration a test can see without reaching into
     * Boot's package-private hook.
     */
    private static boolean registeredWithShutdownHook(ConfigurableApplicationContext context) {
        ((AbstractApplicationContext) context).applicationListeners.any {
            it.getClass().name.startsWith('org.springframework.boot.SpringApplicationShutdownHook')
        }
    }

    void 'the plugin lifecycle runs in the context with the application class, which lends its singletons to a plain parent'() {
        given:
        GrailsAppBuilder parentBuilder = tracked(new GrailsAppBuilder(BuilderSharedConfig))
        GrailsAppBuilder childBuilder = tracked(parentBuilder.child(BuilderTestApplication).web(WebApplicationType.NONE))

        when:
        ConfigurableApplicationContext child = childBuilder.run()
        ConfigurableApplicationContext parent = parentBuilder.context()
        GrailsApplication grailsApplication = child.getBean(GrailsApplication.APPLICATION_ID, GrailsApplication)

        then: 'the lifecycle ran in the child'
        child.beanFactory.containsSingleton(GrailsEarlyPluginRegistrationPostProcessor.EARLY_REGISTRATION_COMPLETE_BEAN_NAME)
        child.beanFactory.containsSingleton(GrailsPluginManager.BEAN_NAME)
        grailsApplication.mainContext.is(child)
        Holders.grailsApplication.is(grailsApplication)

        and: 'not in the parent, although it was launched by a GrailsApp too'
        !parent.beanFactory.containsSingleton(GrailsEarlyPluginRegistrationPostProcessor.EARLY_REGISTRATION_COMPLETE_BEAN_NAME)

        and: 'the parent holds the application of the child while it runs'
        parent.getBean(GrailsApplication.APPLICATION_ID).is(grailsApplication)
        parent.getBean(GrailsPluginManager.BEAN_NAME).is(child.getBean(GrailsPluginManager.BEAN_NAME))

        when: 'the child closes'
        child.close()

        then: 'the parent is given back what it had, and nothing more'
        parent.isActive()
        !parent.containsBean(GrailsApplication.APPLICATION_ID)
        !parent.containsBean(GrailsPluginManager.BEAN_NAME)
        parent.getBean(BuilderSharedConfig.SharedService) != null
    }

    void 'a Grails child that fails to start takes its loan back, so another can start beneath the same parent'() {
        given:
        GrailsAppBuilder parentBuilder = tracked(new GrailsAppBuilder(BuilderSharedConfig))

        when: 'a Grails child fails after its bean factory post-processors have lent its singletons to the parent'
        tracked(parentBuilder.child(BuilderTestApplication, BuilderFailingConfig).web(WebApplicationType.NONE)).run()

        then:
        BeanCreationException e = thrown()
        e.beanName == 'failingBean'

        and: 'the parent is running and holds nothing of the application that never started'
        ConfigurableApplicationContext parent = parentBuilder.context()
        parent.isActive()
        !parent.containsBean(GrailsApplication.APPLICATION_ID)
        !parent.containsBean(GrailsPluginManager.BEAN_NAME)

        when: 'another Grails child starts beneath it'
        ConfigurableApplicationContext second = tracked(parentBuilder.child(BuilderTestApplication).web(WebApplicationType.NONE)).run()

        then:
        second.isActive()
        second.parent.is(parent)
        parent.getBean(GrailsApplication.APPLICATION_ID).is(second.getBean(GrailsApplication.APPLICATION_ID))
    }

    void 'a parent keeps a pluginManager of its own when the Grails child closes'() {
        given:
        GrailsAppBuilder parentBuilder = tracked(new GrailsAppBuilder(BuilderOwnPluginManagerConfig))
        GrailsAppBuilder childBuilder = tracked(parentBuilder.child(BuilderTestApplication).web(WebApplicationType.NONE))
        ConfigurableApplicationContext child = childBuilder.run()
        ConfigurableApplicationContext parent = parentBuilder.context()
        Object own = parent.getBean(GrailsPluginManager.BEAN_NAME)

        expect: 'the child lent the parent only what it did not already hold'
        own instanceof BuilderOwnPluginManagerConfig.OwnPluginManager
        !own.is(child.getBean(GrailsPluginManager.BEAN_NAME))
        parent.getBean(GrailsApplication.APPLICATION_ID).is(child.getBean(GrailsApplication.APPLICATION_ID))

        when:
        child.close()

        then: 'the loan is withdrawn and the parent\'s own bean is left alone'
        !parent.containsBean(GrailsApplication.APPLICATION_ID)
        parent.getBean(GrailsPluginManager.BEAN_NAME).is(own)
    }

    void 'a parent bean that injected the lent application goes with the loan and is recreated against the next one'() {
        given:
        GrailsAppBuilder parentBuilder = tracked(new GrailsAppBuilder(BuilderObservingConfig))
        ConfigurableApplicationContext first = tracked(parentBuilder.child(BuilderTestApplication).web(WebApplicationType.NONE)).run()
        ConfigurableApplicationContext parent = parentBuilder.context()

        when: 'the parent bean is first asked for while the application runs'
        BuilderObservingConfig.GrailsObserver observer = parent.getBean(BuilderObservingConfig.GrailsObserver)

        then:
        observer.grailsApplication.is(first.getBean(GrailsApplication.APPLICATION_ID))
        parent.beanFactory.containsSingleton('grailsObserver')

        when: 'the application closes'
        first.close()

        then: 'the bean that depended on it is destroyed with the loan, as it holds a closed application'
        parent.isActive()
        !parent.beanFactory.containsSingleton('grailsObserver')

        when: 'another Grails application starts beneath the parent'
        ConfigurableApplicationContext second = tracked(parentBuilder.child(BuilderTestApplication).web(WebApplicationType.NONE)).run()
        BuilderObservingConfig.GrailsObserver recreated = parent.getBean(BuilderObservingConfig.GrailsObserver)

        then: 'the bean is created again, against the new application'
        !recreated.is(observer)
        recreated.grailsApplication.is(second.getBean(GrailsApplication.APPLICATION_ID))
    }

    void 'a Grails parent shares its application with a plain child that runs no lifecycle of its own'() {
        given:
        GrailsAppBuilder parentBuilder = tracked(new GrailsAppBuilder(BuilderTestApplication))
        GrailsAppBuilder childBuilder = tracked(parentBuilder.child(BuilderPlainConfig).web(WebApplicationType.NONE))

        when:
        ConfigurableApplicationContext child = childBuilder.run()
        ConfigurableApplicationContext parent = parentBuilder.context()

        then: 'the parent is the Grails application'
        parent.beanFactory.containsSingleton(GrailsEarlyPluginRegistrationPostProcessor.EARLY_REGISTRATION_COMPLETE_BEAN_NAME)
        parent.beanFactory.containsSingleton(GrailsApplication.APPLICATION_ID)

        and: 'the child, launched by a GrailsApp too, left the lifecycle to it'
        !child.beanFactory.containsSingleton(GrailsEarlyPluginRegistrationPostProcessor.EARLY_REGISTRATION_COMPLETE_BEAN_NAME)
        !child.beanFactory.containsSingleton(GrailsApplication.APPLICATION_ID)
        !child.beanFactory.containsSingleton(GrailsPluginManager.BEAN_NAME)

        and: 'yet reaches the Grails beans through it'
        child.getBean(GrailsApplication.APPLICATION_ID).is(parent.getBean(GrailsApplication.APPLICATION_ID))
        child.getBean(BuilderPlainConfig.PlainMarker) != null
    }

    void 'a second Grails application in the hierarchy is refused'() {
        given:
        GrailsAppBuilder parentBuilder = tracked(new GrailsAppBuilder(BuilderTestApplication))
        GrailsAppBuilder childBuilder = tracked(parentBuilder.child(BuilderOtherApplication).web(WebApplicationType.NONE))

        when:
        childBuilder.run()

        then:
        IllegalStateException e = thrown()
        e.message.contains('Only one context in a hierarchy can be the Grails application')

        and: 'the parent is unaffected'
        parentBuilder.context().isActive()
        parentBuilder.context().getBean(GrailsApplication.APPLICATION_ID) != null
    }

    void 'parent(Class) attaches a Grails parent that starts before the child'() {
        given:
        GrailsAppBuilder childBuilder = new GrailsAppBuilder(BuilderTestApplication).web(WebApplicationType.NONE)
        GrailsAppBuilder parentBuilder = tracked(childBuilder.parent(BuilderSharedConfig))
        tracked(childBuilder)

        expect: 'asking again adds sources to the same parent'
        childBuilder.parent(BuilderPlainConfig).is(parentBuilder)

        when:
        ConfigurableApplicationContext child = childBuilder.run()
        ConfigurableApplicationContext parent = parentBuilder.context()

        then:
        parentBuilder.application() instanceof GrailsApp
        parentBuilder.application().webApplicationType == WebApplicationType.NONE
        child.parent.is(parent)
        parent.getBean(BuilderSharedConfig.SharedService) != null
        parent.getBean(BuilderPlainConfig.PlainMarker) != null

        and: 'the plugin lifecycle ran in the child alone'
        child.beanFactory.containsSingleton(GrailsEarlyPluginRegistrationPostProcessor.EARLY_REGISTRATION_COMPLETE_BEAN_NAME)
        !parent.beanFactory.containsSingleton(GrailsEarlyPluginRegistrationPostProcessor.EARLY_REGISTRATION_COMPLETE_BEAN_NAME)
    }

    void 'parent(ConfigurableApplicationContext) places the child beneath a context that is already running'() {
        given:
        ConfigurableApplicationContext existing = new AnnotationConfigApplicationContext(BuilderSharedConfig)
        GrailsAppBuilder childBuilder = tracked(new GrailsAppBuilder(BuilderTestApplication).web(WebApplicationType.NONE))

        when:
        GrailsAppBuilder returned = childBuilder.parent(existing)
        ConfigurableApplicationContext child = childBuilder.run()

        then:
        returned.is(childBuilder)
        child.parent.is(existing)
        child.getBean(BuilderSharedConfig.SharedService).is(existing.getBean(BuilderSharedConfig.SharedService))
        child.beanFactory.containsSingleton(GrailsApplication.APPLICATION_ID)

        cleanup:
        existing.close()
    }

    void 'sibling() starts the first child and creates another beneath the same parent'() {
        given:
        GrailsAppBuilder parentBuilder = tracked(new GrailsAppBuilder(BuilderSharedConfig))
        GrailsAppBuilder firstBuilder = tracked(parentBuilder.child(BuilderTestApplication).web(WebApplicationType.NONE))

        when:
        GrailsAppBuilder secondBuilder = tracked(firstBuilder.sibling(BuilderPlainConfig).web(WebApplicationType.NONE))
        ConfigurableApplicationContext second = secondBuilder.run()
        ConfigurableApplicationContext first = firstBuilder.context()
        ConfigurableApplicationContext parent = parentBuilder.context()

        then:
        first.isActive()
        first.parent.is(parent)
        second.parent.is(parent)
        !second.is(first)

        and: 'the sibling reaches the Grails application through the parent instead of starting its own'
        !second.beanFactory.containsSingleton(GrailsEarlyPluginRegistrationPostProcessor.EARLY_REGISTRATION_COMPLETE_BEAN_NAME)
        second.getBean(GrailsApplication.APPLICATION_ID).is(first.getBean(GrailsApplication.APPLICATION_ID))
    }

    void 'sibling(Class[], String...) starts the first child with the given arguments'() {
        given:
        GrailsAppBuilder parentBuilder = tracked(new GrailsAppBuilder(BuilderSharedConfig))
        GrailsAppBuilder firstBuilder = tracked(parentBuilder.child(BuilderTestApplication).web(WebApplicationType.NONE))

        when:
        GrailsAppBuilder secondBuilder = tracked(firstBuilder.sibling([BuilderPlainConfig] as Class<?>[], '--builder.arg=from-sibling')
                .web(WebApplicationType.NONE))
        ConfigurableApplicationContext first = firstBuilder.context()
        ConfigurableApplicationContext second = secondBuilder.run()

        then: 'the call started the first child, with the arguments'
        first.isActive()
        first.environment.getProperty('builder.arg') == 'from-sibling'

        and: 'created the sibling beneath the same parent'
        second.parent.is(parentBuilder.context())
        second.parent.is(first.parent)
    }

    void 'sibling() without a parent is refused'() {
        given:
        GrailsAppBuilder builder = tracked(new GrailsAppBuilder(BuilderPlainConfig).web(WebApplicationType.NONE))

        when:
        builder.sibling(BuilderSharedConfig)

        then:
        thrown(IllegalStateException)
    }

    void 'closing the parent closes its children'() {
        given:
        GrailsAppBuilder parentBuilder = tracked(new GrailsAppBuilder(BuilderSharedConfig))
        GrailsAppBuilder childBuilder = tracked(parentBuilder.child(BuilderTestApplication).web(WebApplicationType.NONE))
        ConfigurableApplicationContext child = childBuilder.run()

        when:
        parentBuilder.context().close()

        then:
        !child.isActive()
    }
}

@Configuration
class BuilderPlainConfig {

    @Bean
    PlainMarker plainMarker() {
        new PlainMarker()
    }

    static class PlainMarker {
    }
}

@Configuration
class BuilderSharedConfig {

    @Bean
    SharedService sharedService() {
        new SharedService()
    }

    static class SharedService {
    }
}

@Configuration
class BuilderFailingConfig {

    @Bean
    FailingBean failingBean() {
        throw new IllegalStateException('this bean cannot be created')
    }

    static class FailingBean {
    }
}

@Configuration
class BuilderOwnPluginManagerConfig {

    @Bean(name = 'pluginManager')
    OwnPluginManager ownPluginManager() {
        new OwnPluginManager()
    }

    static class OwnPluginManager {
    }
}

@Configuration
class BuilderObservingConfig {

    @Bean
    @Lazy
    GrailsObserver grailsObserver(GrailsApplication grailsApplication) {
        new GrailsObserver(grailsApplication)
    }

    static class GrailsObserver {

        final GrailsApplication grailsApplication

        GrailsObserver(GrailsApplication grailsApplication) {
            this.grailsApplication = grailsApplication
        }
    }
}

class BuilderTestApplication extends GrailsAutoConfiguration {
}

class BuilderOtherApplication extends GrailsAutoConfiguration {
}
