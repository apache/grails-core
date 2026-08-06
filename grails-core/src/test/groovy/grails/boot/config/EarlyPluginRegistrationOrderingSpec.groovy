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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.beans.factory.BeanInitializationException
import org.springframework.beans.factory.support.BeanDefinitionOverrideException
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

import grails.config.Settings
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.GrailsPluginManager
import grails.plugins.Plugin
import grails.util.Environment
import grails.util.Holders
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginDiscovery
import org.grails.core.support.GrailsApplicationDiscoveryStrategy
import spock.lang.Specification

/**
 * Proves the architectural linchpin of {@link GrailsEarlyPluginRegistrationPostProcessor}: a plugin
 * bean registered via {@code doWithSpring} lands in the registry <em>before</em>
 * {@code ConfigurationClassPostProcessor} evaluates {@code @ConditionalOnMissingBean}, so the
 * matching Boot auto-config bean backs off — instead of the plugin having to override or remove
 * it afterwards.
 */
class EarlyPluginRegistrationOrderingSpec extends Specification {

    void 'a plugin doWithSpring bean registered early makes a @ConditionalOnMissingBean auto-config bean defer'() {
        given: 'a context whose configuration would, by itself, register a conditional myResolver'
            def ctx = new AnnotationConfigApplicationContext()
            ctx.register(EarlyOrderingAutoConfigLikeConfig)

        and: 'a plugin discovery promoted to the context, exactly as the bootstrap registry does'
            def discovery = new DefaultPluginDiscovery([earlyOrderingPluginClass] as Class<?>[])
            discovery.loadPluginsFromClasspath = false
            discovery.init(ctx.environment)
            ctx.beanFactory.registerSingleton(PluginDiscovery.BEAN_NAME, discovery)

        and: 'the early registration phase installed through its real entry point'
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the context refreshes'
            ctx.refresh()

        then: 'the early (plugin) bean is the one named myResolver...'
            ctx.getBean('myResolver') instanceof EarlyOrderingPluginResolver

        and: '...and the conditional default was never created'
            ctx.getBeansOfType(EarlyOrderingBootDefaultResolver).isEmpty()

        and: 'the one true grailsApplication and pluginManager singletons were promoted'
            ctx.getBean(GrailsApplication.APPLICATION_ID) instanceof GrailsApplication
            ctx.getBean(GrailsPluginManager.BEAN_NAME) instanceof GrailsPluginManager
            ctx.getBean(GrailsPluginManager.BEAN_NAME, GrailsPluginManager).getGrailsPlugin('earlyOrdering') != null

        and: 'the environment initializing flag was reset on refresh'
            !Environment.isInitializing()

        and: 'the application promoted to the completed context is published to Holders'
            Holders.findApplication().is(ctx.getBean(GrailsApplication.APPLICATION_ID, GrailsApplication))

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'without a promoted plugin discovery the early phase is a no-op and the conditional bean is created (control)'() {
        given:
            def ctx = new AnnotationConfigApplicationContext()
            ctx.register(EarlyOrderingAutoConfigLikeConfig)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when:
            ctx.refresh()

        then: 'the conditional default wins when nothing registered the bean first'
            ctx.getBean('myResolver') instanceof EarlyOrderingBootDefaultResolver

        and: 'no Grails singletons were promoted'
            !ctx.beanFactory.containsSingleton(GrailsApplication.APPLICATION_ID)
            !ctx.beanFactory.containsSingleton(GrailsPluginManager.BEAN_NAME)

        cleanup:
            ctx.close()
    }

    void 'a plugin beanRegistrar bean registered early makes a @ConditionalOnMissingBean auto-config bean defer'() {
        given: 'a context whose configuration would, by itself, register a conditional myResolver'
            def ctx = new AnnotationConfigApplicationContext()
            ctx.register(EarlyOrderingAutoConfigLikeConfig)

        and: 'a plugin exposing a BeanRegistrar promoted through plugin discovery'
            registerDiscovery(ctx, EarlyOrderingRegistrarGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the context refreshes'
            ctx.refresh()

        then: 'the registrar (plugin) bean is the one named myResolver and the conditional default backed off'
            ctx.getBean('myResolver') instanceof EarlyOrderingPluginResolver
            ctx.getBeansOfType(EarlyOrderingBootDefaultResolver).isEmpty()

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'plugin beanRegistrar and doWithSpring can coexist on one plugin'() {
        given:
            def ctx = new AnnotationConfigApplicationContext()
            registerDiscovery(ctx, EarlyOrderingDualGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when:
            ctx.refresh()

        then: 'both the DSL bean and the registrar bean are present'
            ctx.getBean('dualDslBean') instanceof EarlyOrderingPluginResolver
            ctx.getBean('dualRegistrarBean') instanceof EarlyOrderingPluginResolver

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'a plugin beanRegistrar defined as a coerced closure registers its bean'() {
        given: 'a plugin whose beanRegistrar() is a closure coerced to BeanRegistrar (no separate class)'
            def ctx = new AnnotationConfigApplicationContext()
            registerDiscovery(ctx, EarlyOrderingClosureRegistrarGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when:
            ctx.refresh()

        then: 'the bean registered by the closure-coerced registrar is present'
            ctx.getBean('closureRegistrarBean') instanceof EarlyOrderingPluginResolver

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'an application doWithSpring bean still overrides a plugin bean of the same name under the default settings'() {
        given: 'a plugin registering overrideProbe and an application registering a different bean under the same name'
            def ctx = new AnnotationConfigApplicationContext()
            registerDiscovery(ctx, EarlyOrderingOverrideGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)
            ctx.register(EarlyOrderingOverrideApplication)

        when:
            ctx.refresh()

        then: 'the application bean wins, replacing the plugin bean registered in the early phase'
            ctx.getBean('overrideProbe') instanceof EarlyOrderingAppResolver

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'with bean-definition overriding disabled an application bean shadowing a plugin bean fails'() {
        given: 'overriding disabled, a plugin registering overrideProbe and an app registering the same name'
            def ctx = new AnnotationConfigApplicationContext()
            ctx.allowBeanDefinitionOverriding = false
            registerDiscovery(ctx, EarlyOrderingOverrideGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)
            ctx.register(EarlyOrderingOverrideApplication)

        when: 'the context refreshes'
            ctx.refresh()

        then: 'the colliding application bean is rejected rather than silently merging (see the upgrade notes)'
            thrown(BeanDefinitionOverrideException)

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'the early phase loads plugins with a single manager pass, not a throwaway extra one'() {
        given: 'the plugin construction count from one bare plugin-manager load, as a baseline'
            // Grails wraps each plugin in a GrailsClass (a reference instance) and then creates the
            // real instance, so a single load constructs the plugin more than once; what the retimed
            // lifecycle guarantees is a single manager pass rather than a throwaway pass plus the real
            // one (the rejected earlier approach), which would multiply this count.
            EarlyOrderingCountingGrailsPlugin.INSTANCE_COUNT.set(0)
            def baselineDiscovery = new DefaultPluginDiscovery([EarlyOrderingCountingGrailsPlugin] as Class<?>[])
            baselineDiscovery.loadPluginsFromClasspath = false
            baselineDiscovery.init(new StandardEnvironment())
            new DefaultGrailsPluginManager(new DefaultGrailsApplication(), baselineDiscovery).loadPlugins()
            int singleLoadCount = EarlyOrderingCountingGrailsPlugin.INSTANCE_COUNT.get()

        and: 'the early phase booted through the real initializer'
            EarlyOrderingCountingGrailsPlugin.INSTANCE_COUNT.set(0)
            def ctx = new AnnotationConfigApplicationContext()
            registerDiscovery(ctx, EarlyOrderingCountingGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the context refreshes'
            ctx.refresh()

        then: 'the early phase instantiates the plugin no more than a single manager load — no throwaway second pass'
            singleLoadCount > 0
            EarlyOrderingCountingGrailsPlugin.INSTANCE_COUNT.get() == singleLoadCount

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'a Grails 7-style plugin cannot access grailsApplication from Holders during doWithSpring when the legacy Holder shim property is absent'() {
        given: 'a legacy plugin that reads Holders.grailsApplication from its doWithSpring closure'
            def ctx = new AnnotationConfigApplicationContext()
            def warningAppender = attachEarlyRegistrationWarningAppender()
            registerDiscovery(ctx, EarlyOrderingHoldersLegacyGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the context refreshes through the real early registration path'
            ctx.refresh()

        then: 'the modern lifecycle remains the default'
            def failure = thrown(IllegalArgumentException)
            failure.message == 'GrailsApplication not found'
            EarlyOrderingHoldersLegacyGrailsPlugin.grailsApplicationSeen == null

        and: 'the compatibility warning is not emitted when the shim property is absent'
            legacyHoldersWarnings(warningAppender).isEmpty()

        cleanup:
            detachEarlyRegistrationWarningAppender(warningAppender)
            ctx.close()
    }

    void 'a Grails 7-style plugin cannot access grailsApplication from Holders during doWithSpring when the legacy Holder shim property is false'() {
        given: 'a legacy plugin with the legacy Holder shim explicitly disabled'
            def ctx = new AnnotationConfigApplicationContext()
            def warningAppender = attachEarlyRegistrationWarningAppender()
            setLegacyHoldersDuringDoWithSpring(ctx, false)
            registerDiscovery(ctx, EarlyOrderingHoldersLegacyGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the context refreshes through the real early registration path'
            ctx.refresh()

        then: 'the modern lifecycle remains the default'
            def failure = thrown(IllegalArgumentException)
            failure.message == 'GrailsApplication not found'
            EarlyOrderingHoldersLegacyGrailsPlugin.grailsApplicationSeen == null

        and: 'the compatibility warning is not emitted when the shim is explicitly disabled'
            legacyHoldersWarnings(warningAppender).isEmpty()

        cleanup:
            detachEarlyRegistrationWarningAppender(warningAppender)
            ctx.close()
    }

    void 'the default-off lifecycle does not replace a preexisting Holder during doWithSpring'() {
        given: 'a previous application remains in the process-global Holder'
            def previousApplication = new DefaultGrailsApplication()
            Holders.setGrailsApplication(previousApplication)
            def ctx = new AnnotationConfigApplicationContext()
            registerDiscovery(ctx, EarlyOrderingHoldersLegacyGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the new context refreshes without the compatibility shim'
            ctx.refresh()

        then: 'the plugin sees the untouched previous Holder rather than the application being initialized'
            EarlyOrderingHoldersLegacyGrailsPlugin.grailsApplicationSeen.is(previousApplication)

        and: 'the new application is published only after successful early registration'
            Holders.findApplication().is(ctx.getBean(GrailsApplication.APPLICATION_ID))

        cleanup:
            ctx.close()
    }

    void 'a default-off context does not overwrite a newer Holder publisher during late promotion'() {
        given: 'a default-off context whose plugin pauses during doWithSpring'
            EarlyOrderingLatePromotionOwnershipGrailsPlugin.reset()
            Holders.clear()
            def ctx = new AnnotationConfigApplicationContext()
            registerDiscovery(ctx, EarlyOrderingLatePromotionOwnershipGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)
            def refreshFailure = new AtomicReference<Throwable>()
            Thread refreshThread = Thread.start {
                try {
                    ctx.refresh()
                }
                catch (Throwable failure) {
                    refreshFailure.set(failure)
                }
            }

        when: 'a newer application is published while early registration is blocked'
            assert EarlyOrderingLatePromotionOwnershipGrailsPlugin.entered.await(5, TimeUnit.SECONDS)
            def newerApplication = new DefaultGrailsApplication()
            Holders.setGrailsApplication(newerApplication)
            EarlyOrderingLatePromotionOwnershipGrailsPlugin.release.countDown()
            refreshThread.join(5000)

        then: 'the refresh succeeds without replacing the newer publisher during its late promotion'
            !refreshThread.alive
            refreshFailure.get() == null
            def contextApplication = ctx.getBean(GrailsApplication.APPLICATION_ID, GrailsApplication)
            !newerApplication.is(contextApplication)
            Holders.findApplication().is(newerApplication)
            !Holders.findApplication().is(contextApplication)

        and: 'the default-off plugin never observed the context application through Holders'
            EarlyOrderingLatePromotionOwnershipGrailsPlugin.holderApplicationSeen.get() == null

        when: 'the downstream application postprocessor handles the already-promoted context'
            def postProcessor = new GrailsApplicationPostProcessor(
                null,
                ctx,
                ctx.getBean(PluginDiscovery.BEAN_NAME, PluginDiscovery)
            )
            postProcessor.loadExternalBeans = false
            postProcessor.postProcessBeanDefinitionRegistry(ctx.beanFactory)

        then: 'it preserves the ownership decision made during early registration'
            Holders.findGrailsApplicationFallback().is(newerApplication)

        cleanup:
            EarlyOrderingLatePromotionOwnershipGrailsPlugin.release.countDown()
            refreshThread?.join(5000)
            assert !refreshThread?.alive
            ctx?.close()
            Holders.clear()
            Environment.setInitializing(false)
            EarlyOrderingLatePromotionOwnershipGrailsPlugin.reset()
    }

    void 'a Grails 7-style plugin can access the promoted grailsApplication from Holders during doWithSpring when the legacy Holder shim property is true'() {
        given: 'a legacy plugin with the legacy Holder shim enabled'
            def ctx = new AnnotationConfigApplicationContext()
            def warningAppender = attachEarlyRegistrationWarningAppender()
            setLegacyHoldersDuringDoWithSpring(ctx, true)
            registerDiscovery(ctx, EarlyOrderingHoldersLegacyGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the context refreshes through the real early registration path'
            ctx.refresh()

        then: 'the plugin observed the exact Grails application instance promoted to the context'
            EarlyOrderingHoldersLegacyGrailsPlugin.grailsApplicationSeen.is(ctx.getBean(GrailsApplication.APPLICATION_ID))

        and: 'the compatibility warning is emitted exactly once'
            legacyHoldersWarnings(warningAppender).size() == 1

        cleanup:
            detachEarlyRegistrationWarningAppender(warningAppender)
            ctx.close()
    }

    void 'the initializing flag is reset when the early phase throws'() {
        given: 'a plugin whose doWithSpring throws while the early phase drains it'
            def previousApplication = new DefaultGrailsApplication()
            Holders.setGrailsApplication(previousApplication)
            def ctx = new AnnotationConfigApplicationContext()
            setLegacyHoldersDuringDoWithSpring(ctx, true)
            registerDiscovery(ctx, EarlyOrderingThrowingGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the context refreshes'
            ctx.refresh()

        then: 'the refresh fails...'
            thrown(Exception)

        and: '...but the initializing flag (a system property) was reset, not leaked to later contexts'
            !Environment.isInitializing()

        and: 'the Grails application Holder was restored, not replaced by the failed context'
            Holders.findApplication().is(previousApplication)

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'a default-off late singleton registration failure restores the prior Holder application'() {
        given: 'a prior Holder application and a plugin whose runtime configuration completes before promotion fails'
            def previousApplication = new DefaultGrailsApplication()
            Holders.setGrailsApplication(previousApplication)
            def ctx = new AnnotationConfigApplicationContext()
            ctx.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, new Object())
            registerDiscovery(ctx, EarlyOrderingRuntimeConfiguredGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the context fails to promote its application because grailsApplication is already registered'
            ctx.refresh()

        then: 'plugin runtime configuration completed before the late failure'
            thrown(Exception)
            EarlyOrderingRuntimeConfiguredGrailsPlugin.runtimeConfigured

        and: 'the failed application was not leaked through global state'
            Holders.replaceGrailsApplication(null).is(previousApplication)
            !Environment.isInitializing()

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'an Error from a shim-enabled plugin propagates unchanged after global state is restored'() {
        given: 'a prior Holder application and a plugin that throws a specific Error'
            def previousApplication = new DefaultGrailsApplication()
            def expectedFailure = new AssertionError('error from doWithSpring')
            Holders.setGrailsApplication(previousApplication)
            EarlyOrderingErrorThrowingGrailsPlugin.failure = expectedFailure
            def ctx = new AnnotationConfigApplicationContext()
            setLegacyHoldersDuringDoWithSpring(ctx, true)
            registerDiscovery(ctx, EarlyOrderingErrorThrowingGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the context refreshes'
            ctx.refresh()

        then: 'the original Error instance propagates without wrapping'
            def failure = thrown(Error)
            failure.is(expectedFailure)

        and: 'the prior Holder application and initializing state are restored'
            Holders.findApplication().is(previousApplication)
            !Environment.isInitializing()

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'failure restoration does not invoke Grails application discovery strategies'() {
        given: 'a prior fallback application, a broken discovery strategy, and a throwing plugin'
            def previousApplication = new DefaultGrailsApplication()
            Holders.setGrailsApplication(previousApplication)
            def throwingStrategy = new EarlyOrderingThrowingApplicationDiscoveryStrategy()
            Holders.addApplicationDiscoveryStrategy(throwingStrategy)
            def ctx = new AnnotationConfigApplicationContext()
            setLegacyHoldersDuringDoWithSpring(ctx, true)
            registerDiscovery(ctx, EarlyOrderingThrowingGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the plugin fails after the new application is published'
            ctx.refresh()

        then: 'the plugin failure propagates without consulting discovery or leaking global state'
            thrown(Exception)
            throwingStrategy.invocationCount.get() == 0
            !Environment.isInitializing()
            Holders.replaceGrailsApplication(null).is(previousApplication)

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'a checked doWithSpring failure is wrapped after restoring global state'() {
        given: 'a prior application and a plugin whose doWithSpring sneaky-throws a checked exception'
            def previousApplication = new DefaultGrailsApplication()
            Holders.setGrailsApplication(previousApplication)
            def ctx = new AnnotationConfigApplicationContext()
            setLegacyHoldersDuringDoWithSpring(ctx, true)
            registerDiscovery(ctx, EarlyOrderingCheckedThrowingGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the context refreshes'
            ctx.refresh()

        then: 'the checked failure is wrapped and both process-wide values are restored'
            def failure = thrown(BeanInitializationException)
            failure.cause.message == 'checked failure from doWithSpring'
            !Environment.isInitializing()
            Holders.findApplication().is(previousApplication)

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'a failed context does not overwrite a newer Grails application publisher'() {
        given: 'a plugin that replaces the fallback application before its startup fails'
            def ctx = new AnnotationConfigApplicationContext()
            setLegacyHoldersDuringDoWithSpring(ctx, true)
            registerDiscovery(ctx, EarlyOrderingCompetingPublisherGrailsPlugin)
            new GrailsPluginLifecycleInitializer().initialize(ctx)

        when: 'the context refreshes and fails after the competing publication'
            ctx.refresh()

        then: 'rollback leaves the newer publisher in place'
            thrown(Exception)
            Holders.replaceGrailsApplication(null).is(EarlyOrderingCompetingPublisherGrailsPlugin.competitor)
            !Environment.isInitializing()

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }

    void 'conditional fallback restoration cannot overwrite a concurrent publisher'() {
        given: 'an owned fallback, a value to restore, and a newer competing application'
            def ownedApplication = new DefaultGrailsApplication()
            def previousApplication = new DefaultGrailsApplication()
            def competingApplication = new DefaultGrailsApplication()
            Holders.setGrailsApplication(ownedApplication)
            def ready = new CountDownLatch(2)
            def start = new CountDownLatch(1)
            def finished = new CountDownLatch(2)

        and: 'two publishers ready to race'
            def restoreThread = Thread.start {
                ready.countDown()
                start.await()
                Holders.restoreGrailsApplication(ownedApplication, previousApplication)
                finished.countDown()
            }
            def publishThread = Thread.start {
                ready.countDown()
                start.await()
                Holders.setGrailsApplication(competingApplication)
                finished.countDown()
            }

        when: 'rollback and the competing publication start together'
            assert ready.await(5, TimeUnit.SECONDS)
            start.countDown()

        then: 'the newer publication wins regardless of operation order'
            finished.await(5, TimeUnit.SECONDS)
            Holders.replaceGrailsApplication(null).is(competingApplication)

        cleanup:
            start.countDown()
            restoreThread.join(5000)
            publishThread.join(5000)
            Holders.clear()
    }

    void 'a temporary plain competitor preserves the stacked failed-context rollback sequence P to A to B to C to B to P'() {
        given: 'a prior application and two shim-enabled contexts paused after publishing A then B'
            def previousApplication = new DefaultGrailsApplication()
            Holders.setGrailsApplication(previousApplication)
            def refreshes = startStackedRefreshes(true, true)
            def firstApplication = EarlyOrderingStackedFailureGrailsPlugin.firstPromotedApplication.get()
            def secondApplication = EarlyOrderingStackedFailureGrailsPlugin.secondPromotedApplication.get()
            def temporaryApplication = new DefaultGrailsApplication()

        expect: 'each context captured the application it published through Holders'
            firstApplication != null
            secondApplication != null
            !firstApplication.is(secondApplication)
            Holders.findApplication().is(secondApplication)

        when: 'C replaces B, A fails while C is visible, C restores B, and then B fails'
            def replacedApplication = Holders.replaceGrailsApplication(temporaryApplication)
            refreshes.releaseFirstAndJoin()
            def applicationWhileTemporaryPublisherIsVisible = Holders.findApplication()
            Holders.restoreGrailsApplication(temporaryApplication, replacedApplication)
            def applicationAfterTemporaryPublisherRestores = Holders.findApplication()
            refreshes.releaseSecondAndJoin()

        then: 'the public Holder operations follow the exact sequence and both contexts throw'
            replacedApplication.is(secondApplication)
            applicationWhileTemporaryPublisherIsVisible.is(temporaryApplication)
            applicationAfterTemporaryPublisherRestores.is(secondApplication)
            refreshes.firstFailure.get() != null
            refreshes.secondFailure.get() != null
            Holders.replaceGrailsApplication(null).is(previousApplication)
            !Environment.isInitializing()

        cleanup:
            refreshes?.releaseAllAndJoin()
            refreshes?.closeContexts()
            Holders.clear()
            Environment.setInitializing(false)
            EarlyOrderingStackedFailureGrailsPlugin.reset()
    }

    void 'when B fails before A, both failed shim-enabled contexts restore the original Holder application'() {
        given: 'a prior application and two contexts paused after publishing A then B'
            def previousApplication = new DefaultGrailsApplication()
            Holders.setGrailsApplication(previousApplication)
            def refreshes = startStackedRefreshes(true, true)
            def firstApplication = EarlyOrderingStackedFailureGrailsPlugin.firstPromotedApplication.get()

        when: 'B fails and restores A before A fails'
            refreshes.releaseSecondAndJoin()
            def applicationAfterSecondFailure = Holders.findApplication()
            refreshes.releaseFirstAndJoin()

        then: 'both refreshes throw and the final Holder application is P'
            refreshes.firstFailure.get() != null
            refreshes.secondFailure.get() != null
            applicationAfterSecondFailure.is(firstApplication)
            Holders.replaceGrailsApplication(null).is(previousApplication)
            !Environment.isInitializing()

        cleanup:
            refreshes?.releaseAllAndJoin()
            refreshes?.closeContexts()
            Holders.clear()
            Environment.setInitializing(false)
            EarlyOrderingStackedFailureGrailsPlugin.reset()
    }

    void 'when A succeeds while B owns Holders and B then fails, A is the final Holder application'() {
        given: 'two contexts paused after publishing A then B, with A succeeding and B failing'
            def refreshes = startStackedRefreshes(false, true)
            def firstApplication = EarlyOrderingStackedFailureGrailsPlugin.firstPromotedApplication.get()
            def secondApplication = EarlyOrderingStackedFailureGrailsPlugin.secondPromotedApplication.get()

        when: 'A completes while B remains the visible Holder application, then B fails'
            refreshes.releaseFirstAndJoin()
            def applicationAfterFirstSuccess = Holders.findApplication()
            refreshes.releaseSecondAndJoin()

        then: 'only B throws and its rollback restores A'
            refreshes.firstFailure.get() == null
            refreshes.secondFailure.get() != null
            applicationAfterFirstSuccess.is(secondApplication)
            Holders.replaceGrailsApplication(null).is(firstApplication)
            !Environment.isInitializing()

        cleanup:
            refreshes?.releaseAllAndJoin()
            refreshes?.closeContexts()
            Holders.clear()
            Environment.setInitializing(false)
            EarlyOrderingStackedFailureGrailsPlugin.reset()
    }

    void 'when A fails while B owns Holders and B then succeeds, B is the final Holder application'() {
        given: 'two contexts paused after publishing A then B, with A failing and B succeeding'
            def refreshes = startStackedRefreshes(true, false)
            def secondApplication = EarlyOrderingStackedFailureGrailsPlugin.secondPromotedApplication.get()

        when: 'A fails while B is visible, then B completes successfully'
            refreshes.releaseFirstAndJoin()
            def applicationAfterFirstFailure = Holders.findApplication()
            refreshes.releaseSecondAndJoin()

        then: 'only A throws and B remains published'
            refreshes.firstFailure.get() != null
            refreshes.secondFailure.get() == null
            applicationAfterFirstFailure.is(secondApplication)
            Holders.replaceGrailsApplication(null).is(secondApplication)
            !Environment.isInitializing()

        cleanup:
            refreshes?.releaseAllAndJoin()
            refreshes?.closeContexts()
            Holders.clear()
            Environment.setInitializing(false)
            EarlyOrderingStackedFailureGrailsPlugin.reset()
    }

    private static void registerDiscovery(AnnotationConfigApplicationContext ctx, Class<?> pluginClass) {
        def discovery = new DefaultPluginDiscovery([pluginClass] as Class<?>[])
        discovery.loadPluginsFromClasspath = false
        discovery.init(ctx.environment)
        ctx.beanFactory.registerSingleton(PluginDiscovery.BEAN_NAME, discovery)
    }

    private static void setLegacyHoldersDuringDoWithSpring(AnnotationConfigApplicationContext ctx, boolean enabled) {
        ctx.environment.propertySources.addFirst(new MapPropertySource('legacyHoldersDuringDoWithSpring', [
            (Settings.LEGACY_HOLDERS_DURING_DO_WITH_SPRING): enabled.toString()
        ]))
    }

    private static EarlyOrderingStackedRefreshes startStackedRefreshes(boolean firstFails, boolean secondFails) {
        EarlyOrderingStackedFailureGrailsPlugin.configure(firstFails, secondFails)
        def firstContext = stackedFailureContext()
        def secondContext = stackedFailureContext()
        def firstFailure = new AtomicReference<Throwable>()
        def secondFailure = new AtomicReference<Throwable>()
        def firstRefresh = Thread.start {
            try {
                firstContext.refresh()
            }
            catch (Throwable failure) {
                firstFailure.set(failure)
            }
        }
        assert EarlyOrderingStackedFailureGrailsPlugin.firstConfigured.await(5, TimeUnit.SECONDS)
        def secondRefresh = Thread.start {
            try {
                secondContext.refresh()
            }
            catch (Throwable failure) {
                secondFailure.set(failure)
            }
        }
        assert EarlyOrderingStackedFailureGrailsPlugin.secondConfigured.await(5, TimeUnit.SECONDS)
        new EarlyOrderingStackedRefreshes(firstContext, secondContext, firstRefresh, secondRefresh, firstFailure, secondFailure)
    }

    private static AnnotationConfigApplicationContext stackedFailureContext() {
        def ctx = new AnnotationConfigApplicationContext()
        setLegacyHoldersDuringDoWithSpring(ctx, true)
        registerDiscovery(ctx, EarlyOrderingStackedFailureGrailsPlugin)
        new GrailsPluginLifecycleInitializer().initialize(ctx)
        ctx
    }

    private static ListAppender<ILoggingEvent> attachEarlyRegistrationWarningAppender() {
        def appender = new ListAppender<ILoggingEvent>()
        appender.start()
        earlyRegistrationLogger().addAppender(appender)
        appender
    }

    private static void detachEarlyRegistrationWarningAppender(ListAppender<ILoggingEvent> appender) {
        earlyRegistrationLogger().detachAppender(appender)
        appender.stop()
    }

    private static List<ILoggingEvent> legacyHoldersWarnings(ListAppender<ILoggingEvent> appender) {
        appender.list.findAll { ILoggingEvent event ->
            event.formattedMessage.contains(Settings.LEGACY_HOLDERS_DURING_DO_WITH_SPRING) &&
                event.formattedMessage.contains('legacy doWithSpring compatibility shim')
        }
    }

    private static Logger earlyRegistrationLogger() {
        LoggerFactory.getLogger(GrailsEarlyPluginRegistrationPostProcessor) as Logger
    }

    void cleanup() {
        Holders.clear()
        Environment.setInitializing(false)
        EarlyOrderingCountingGrailsPlugin.INSTANCE_COUNT.set(0)
        EarlyOrderingHoldersLegacyGrailsPlugin.grailsApplicationSeen = null
        EarlyOrderingRuntimeConfiguredGrailsPlugin.runtimeConfigured = false
        EarlyOrderingErrorThrowingGrailsPlugin.failure = null
        EarlyOrderingStackedFailureGrailsPlugin.reset()
        EarlyOrderingLatePromotionOwnershipGrailsPlugin.reset()
    }

    private static Class<?> getEarlyOrderingPluginClass() {
        new GroovyClassLoader(EarlyPluginRegistrationOrderingSpec.classLoader).parseClass('''
class EarlyOrderingGrailsPlugin {
    def version = '1.0'
    def doWithSpring = {
        myResolver(grails.boot.config.EarlyOrderingPluginResolver)
    }
}
''')
    }

    /** Stands in for a Spring Boot auto-configuration: a name-guarded conditional bean. */
    @Configuration
    static class EarlyOrderingAutoConfigLikeConfig {

        @Bean
        @ConditionalOnMissingBean(name = 'myResolver')
        EarlyOrderingBootDefaultResolver myResolver() {
            new EarlyOrderingBootDefaultResolver()
        }
    }
}

class EarlyOrderingBootDefaultResolver {
}

class EarlyOrderingPluginResolver {
}

class EarlyOrderingRegistrarGrailsPlugin extends Plugin {

    def version = '1.0'

    @Override
    BeanRegistrar beanRegistrar() {
        new EarlyOrderingResolverRegistrar()
    }
}

class EarlyOrderingResolverRegistrar implements BeanRegistrar {

    @Override
    void register(BeanRegistry registry, org.springframework.core.env.Environment environment) {
        registry.registerBean('myResolver', EarlyOrderingPluginResolver)
    }
}

class EarlyOrderingDualGrailsPlugin extends Plugin {

    def version = '1.0'

    @Override
    Closure doWithSpring() {
        { ->
            dualDslBean(EarlyOrderingPluginResolver)
        }
    }

    @Override
    BeanRegistrar beanRegistrar() {
        new EarlyOrderingDualRegistrar()
    }
}

class EarlyOrderingDualRegistrar implements BeanRegistrar {

    @Override
    void register(BeanRegistry registry, org.springframework.core.env.Environment environment) {
        registry.registerBean('dualRegistrarBean', EarlyOrderingPluginResolver)
    }
}

class EarlyOrderingClosureRegistrarGrailsPlugin extends Plugin {

    def version = '1.0'

    @Override
    BeanRegistrar beanRegistrar() {
        { BeanRegistry registry, org.springframework.core.env.Environment environment ->
            registry.registerBean('closureRegistrarBean', EarlyOrderingPluginResolver)
        } as BeanRegistrar
    }
}

class EarlyOrderingCountingGrailsPlugin extends Plugin {

    static final AtomicInteger INSTANCE_COUNT = new AtomicInteger()

    def version = '1.0'

    EarlyOrderingCountingGrailsPlugin() {
        INSTANCE_COUNT.incrementAndGet()
    }
}

class EarlyOrderingThrowingGrailsPlugin extends Plugin {

    def version = '1.0'

    @Override
    Closure doWithSpring() {
        { -> throw new IllegalStateException('boom from doWithSpring') }
    }
}

class EarlyOrderingRuntimeConfiguredGrailsPlugin extends Plugin {

    static boolean runtimeConfigured

    def version = '1.0'

    @Override
    Closure doWithSpring() {
        { ->
            runtimeConfigured = true
            runtimeConfigurationBean(EarlyOrderingPluginResolver)
        }
    }
}

class EarlyOrderingLatePromotionOwnershipGrailsPlugin extends Plugin {

    static CountDownLatch entered
    static CountDownLatch release
    static final AtomicReference<GrailsApplication> holderApplicationSeen = new AtomicReference<>()

    static void reset() {
        entered = new CountDownLatch(1)
        release = new CountDownLatch(1)
        holderApplicationSeen.set(null)
    }

    def version = '1.0'

    @Override
    Closure doWithSpring() {
        { ->
            holderApplicationSeen.set(Holders.findApplication())
            entered.countDown()
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException('timed out waiting to release late promotion ownership test')
            }
        }
    }
}

class EarlyOrderingErrorThrowingGrailsPlugin extends Plugin {

    static Error failure

    def version = '1.0'

    @Override
    Closure doWithSpring() {
        { -> throw failure }
    }
}

class EarlyOrderingThrowingApplicationDiscoveryStrategy implements GrailsApplicationDiscoveryStrategy {

    final AtomicInteger invocationCount = new AtomicInteger()

    @Override
    GrailsApplication findGrailsApplication() {
        invocationCount.incrementAndGet()
        throw new IllegalStateException('broken Grails application discovery')
    }

    @Override
    ApplicationContext findApplicationContext() {
        null
    }
}

class EarlyOrderingCheckedThrowingGrailsPlugin extends Plugin {

    def version = '1.0'

    @Override
    Closure doWithSpring() {
        { -> throw new Exception('checked failure from doWithSpring') }
    }
}

class EarlyOrderingCompetingPublisherGrailsPlugin extends Plugin {

    static final GrailsApplication competitor = new DefaultGrailsApplication()

    def version = '1.0'

    @Override
    Closure doWithSpring() {
        { ->
            Holders.setGrailsApplication(competitor)
            throw new IllegalStateException('failure after competing publication')
        }
    }
}

class EarlyOrderingStackedFailureGrailsPlugin extends Plugin {

    static CountDownLatch firstConfigured
    static CountDownLatch secondConfigured
    static CountDownLatch releaseFirst
    static CountDownLatch releaseSecond
    static final AtomicInteger invocationCount = new AtomicInteger()
    static final AtomicReference<GrailsApplication> firstPromotedApplication = new AtomicReference<>()
    static final AtomicReference<GrailsApplication> secondPromotedApplication = new AtomicReference<>()
    static boolean firstFails
    static boolean secondFails

    static void configure(boolean firstFailure, boolean secondFailure) {
        reset()
        firstFails = firstFailure
        secondFails = secondFailure
    }

    static void reset() {
        firstConfigured = new CountDownLatch(1)
        secondConfigured = new CountDownLatch(1)
        releaseFirst = new CountDownLatch(1)
        releaseSecond = new CountDownLatch(1)
        invocationCount.set(0)
        firstPromotedApplication.set(null)
        secondPromotedApplication.set(null)
        firstFails = false
        secondFails = false
    }

    def version = '1.0'

    @Override
    Closure doWithSpring() {
        { ->
            if (invocationCount.incrementAndGet() == 1) {
                firstPromotedApplication.set(Holders.findApplication())
                firstConfigured.countDown()
                if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('timed out waiting to release the first context')
                }
                if (firstFails) {
                    throw new IllegalStateException('first context failed')
                }
            }
            else {
                secondPromotedApplication.set(Holders.findApplication())
                secondConfigured.countDown()
                if (!releaseSecond.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('timed out waiting to release the second context')
                }
                if (secondFails) {
                    throw new IllegalStateException('second context failed')
                }
            }
        }
    }
}

class EarlyOrderingStackedRefreshes {

    final AnnotationConfigApplicationContext firstContext
    final AnnotationConfigApplicationContext secondContext
    final Thread firstRefresh
    final Thread secondRefresh
    final AtomicReference<Throwable> firstFailure
    final AtomicReference<Throwable> secondFailure

    EarlyOrderingStackedRefreshes(
        AnnotationConfigApplicationContext firstContext,
        AnnotationConfigApplicationContext secondContext,
        Thread firstRefresh,
        Thread secondRefresh,
        AtomicReference<Throwable> firstFailure,
        AtomicReference<Throwable> secondFailure) {
        this.firstContext = firstContext
        this.secondContext = secondContext
        this.firstRefresh = firstRefresh
        this.secondRefresh = secondRefresh
        this.firstFailure = firstFailure
        this.secondFailure = secondFailure
    }

    void releaseFirstAndJoin() {
        EarlyOrderingStackedFailureGrailsPlugin.releaseFirst.countDown()
        firstRefresh.join(5000)
        assert !firstRefresh.alive
    }

    void releaseSecondAndJoin() {
        EarlyOrderingStackedFailureGrailsPlugin.releaseSecond.countDown()
        secondRefresh.join(5000)
        assert !secondRefresh.alive
    }

    void releaseAllAndJoin() {
        EarlyOrderingStackedFailureGrailsPlugin.releaseFirst.countDown()
        EarlyOrderingStackedFailureGrailsPlugin.releaseSecond.countDown()
        firstRefresh.join(5000)
        secondRefresh.join(5000)
        assert !firstRefresh.alive
        assert !secondRefresh.alive
    }

    void closeContexts() {
        firstContext.close()
        secondContext.close()
    }
}

class EarlyOrderingHoldersLegacyGrailsPlugin {

    static GrailsApplication grailsApplicationSeen

    def version = '1.0'

    def doWithSpring = { grailsApplicationSeen = Holders.grailsApplication }
}

class EarlyOrderingAppResolver {
}

class EarlyOrderingOverrideGrailsPlugin extends Plugin {

    def version = '1.0'

    @Override
    Closure doWithSpring() {
        { ->
            overrideProbe(EarlyOrderingPluginResolver)
        }
    }
}

class EarlyOrderingOverrideApplication extends GrailsAutoConfiguration {

    @Override
    Closure doWithSpring() {
        { ->
            overrideProbe(EarlyOrderingAppResolver)
        }
    }
}
