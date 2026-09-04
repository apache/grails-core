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
package quartz

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.plugins.GrailsPluginManager
import grails.plugins.quartz.JobArtefactHandler
import grails.plugins.quartz.listeners.ExceptionPrinterJobListener
import grails.plugins.quartz.listeners.SessionBinderJobListener
import grails.spring.BeanBuilder
import grails.util.Metadata
import org.grails.config.PropertySourcesConfig
import org.quartz.Scheduler
import org.quartz.impl.matchers.GroupMatcher
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.RuntimeBeanReference
import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification

/**
 * Tests the configuration and the Spring bean definitions of the Quartz plugin.
 */
class QuartzGrailsPluginSpec extends Specification {

    void 'unconfigured options fall back to their documented defaults'() {
        given:
            QuartzGrailsPlugin plugin = pluginFor([:])

        expect:
            plugin.isPluginEnabled()
            plugin.isAutoStartup()
            !plugin.isJdbcStore()
            plugin.getJdbcStoreDataSource() == 'dataSource'
            !plugin.isPurgeQuartzTablesOnStartup()
            plugin.isWaitForJobsToCompleteOnShutdown()
            !plugin.isExposeSchedulerInRepository()
            !plugin.isFailOnNeverFiringTriggers()
            plugin.getSchedulerInstanceName() == null
    }

    void 'the application name the jobs of the application are stamped with comes from the configuration'() {
        given:
            QuartzGrailsPlugin plugin = pluginFor('info.app.name': 'reporting')

        expect:
            plugin.getApplicationName() == 'reporting'
    }

    void 'an application which does not name itself falls back to the name of its metadata'() {
        given:
            QuartzGrailsPlugin plugin = pluginFor([:])

        expect:
            plugin.getApplicationName() == plugin.grailsApplication.metadata.getApplicationName()
            plugin.getApplicationName()
    }

    void 'an application which names itself registers its jobs under a name of its own'() {
        given:
            QuartzGrailsPlugin plugin = pluginFor('info.app.name': 'reporting')

        expect:
            plugin.isApplicationNamed()
    }

    void 'an application named as every unnamed application is does not count as named'() {
        given:
            QuartzGrailsPlugin plugin = pluginFor('info.app.name': Metadata.DEFAULT_APPLICATION_NAME)

        expect:
            !plugin.isApplicationNamed()
    }

    void 'a quartz block that does not mention an option leaves that option at its default'() {
        given: 'a configuration which configures quartz, but says nothing about the job store'
            QuartzGrailsPlugin plugin = pluginFor('quartz.threadPool.threadCount': 10)

        expect:
            !plugin.isJdbcStore()
            plugin.isPluginEnabled()
    }

    void 'configured options are read from the quartz configuration block'() {
        given:
            QuartzGrailsPlugin plugin = pluginFor(
                    'quartz.pluginEnabled': false,
                    'quartz.autoStartup': false,
                    'quartz.jdbcStore': true,
                    'quartz.jdbcStoreDataSource': 'quartzDataSource',
                    'quartz.purgeQuartzTablesOnStartup': true,
                    'quartz.waitForJobsToCompleteOnShutdown': false,
                    'quartz.exposeSchedulerInRepository': true,
                    'quartz.failOnNeverFiringTriggers': true,
                    'quartz.scheduler.instanceName': 'reportScheduler')

        expect:
            !plugin.isPluginEnabled()
            !plugin.isAutoStartup()
            plugin.isJdbcStore()
            plugin.getJdbcStoreDataSource() == 'quartzDataSource'
            plugin.isPurgeQuartzTablesOnStartup()
            !plugin.isWaitForJobsToCompleteOnShutdown()
            plugin.isExposeSchedulerInRepository()
            plugin.isFailOnNeverFiringTriggers()
            plugin.getSchedulerInstanceName() == 'reportScheduler'
    }

    void 'options configured as strings are coerced to booleans'() {
        given:
            QuartzGrailsPlugin plugin = pluginFor(
                    'quartz.pluginEnabled': 'false',
                    'quartz.jdbcStore': 'true',
                    'quartz.failOnNeverFiringTriggers': 'true')

        expect:
            !plugin.isPluginEnabled()
            plugin.isJdbcStore()
            plugin.isFailOnNeverFiringTriggers()
    }

    void 'the plugin registers a scheduler, a job factory and an exception listener'() {
        when:
            Map<String, BeanDefinition> beans = beanDefinitionsFor([:])

        then:
            beans.containsKey('quartzScheduler')
            beans.containsKey('quartzJobFactory')
            beans.containsKey(ExceptionPrinterJobListener.NAME)

        and: 'no hibernate plugin means no session binder'
            !beans.containsKey(SessionBinderJobListener.NAME)
    }

    void 'no beans are registered when the plugin is disabled'() {
        when:
            Map<String, BeanDefinition> beans = beanDefinitionsFor('quartz.pluginEnabled': false)

        then:
            beans.isEmpty()
    }

    void 'the scheduler is not wired to a data source unless the jdbc job store is enabled'() {
        when:
            BeanDefinition scheduler = beanDefinitionsFor(config).get('quartzScheduler')

        then:
            !scheduler.propertyValues.contains('dataSource')
            !scheduler.propertyValues.contains('transactionManager')

        where:
            config << [[:], ['quartz.jdbcStore': false], ['quartz.threadPool.threadCount': 10]]
    }

    void 'the scheduler is wired to the jdbc job store when it is enabled'() {
        when:
            BeanDefinition scheduler = beanDefinitionsFor('quartz.jdbcStore': true).get('quartzScheduler')

        then:
            beanReferenceName(scheduler, 'dataSource') == 'dataSource'
            beanReferenceName(scheduler, 'transactionManager') == 'transactionManager'
    }

    void 'the jdbc job store honours a custom data source bean name'() {
        when:
            BeanDefinition scheduler = beanDefinitionsFor(
                    'quartz.jdbcStore': true, 'quartz.jdbcStoreDataSource': 'quartzDataSource').get('quartzScheduler')

        then:
            beanReferenceName(scheduler, 'dataSource') == 'quartzDataSource'
    }

    void 'the scheduler bean never starts itself so that startup stays under the control of the plugin'() {
        when:
            BeanDefinition scheduler = beanDefinitionsFor(config).get('quartzScheduler')

        then:
            scheduler.propertyValues.getPropertyValue('autoStartup').value == false

        where:
            config << [[:], ['quartz.autoStartup': true], ['quartz.autoStartup': false]]
    }

    void 'the scheduler bean is configured from the shutdown and repository options'() {
        when:
            BeanDefinition scheduler = beanDefinitionsFor(config).get('quartzScheduler')

        then:
            scheduler.propertyValues.getPropertyValue('waitForJobsToCompleteOnShutdown').value == waitForJobs
            scheduler.propertyValues.getPropertyValue('exposeSchedulerInRepository').value == exposeScheduler

        where:
            config                                                                              || waitForJobs | exposeScheduler
            [:]                                                                                 || true        | false
            ['quartz.waitForJobsToCompleteOnShutdown': false]                                   || false       | false
            ['quartz.exposeSchedulerInRepository': true]                                        || true        | true
    }

    void 'the scheduler name is only set when an instance name is configured'() {
        when:
            Map<String, BeanDefinition> beans = beanDefinitionsFor([:])

        then:
            !beans.get('quartzScheduler').propertyValues.contains('schedulerName')

        when:
            beans = beanDefinitionsFor('quartz.scheduler.instanceName': 'reportScheduler')

        then:
            beans.get('quartzScheduler').propertyValues.getPropertyValue('schedulerName').value == 'reportScheduler'
    }

    void 'the quartz configuration is passed through to the scheduler'() {
        when:
            BeanDefinition scheduler = beanDefinitionsFor('quartz.threadPool.threadCount': 10).get('quartzScheduler')
            Properties quartzProperties = scheduler.propertyValues.getPropertyValue('quartzProperties').value as Properties

        then:
            quartzProperties.getProperty('org.quartz.threadPool.threadCount') == '10'
    }

    void 'the quartz tables are only purged when the jdbc job store, hibernate and the purge option are all in place'() {
        when:
            Map<String, BeanDefinition> beans = beanDefinitionsFor(config, hibernate)

        then:
            beans.containsKey('purgeTablesBean') == purges

        where:
            config                                                                  | hibernate || purges
            ['quartz.jdbcStore': true, 'quartz.purgeQuartzTablesOnStartup': true]    | true      || true
            ['quartz.jdbcStore': true, 'quartz.purgeQuartzTablesOnStartup': true]    | false     || false
            ['quartz.jdbcStore': false, 'quartz.purgeQuartzTablesOnStartup': true]   | true      || false
            ['quartz.jdbcStore': true]                                              | true      || false
            [:]                                                                     | true      || false
    }

    void 'the session binder is registered when a hibernate plugin is present'() {
        when:
            Map<String, BeanDefinition> beans = beanDefinitionsFor([:], true)

        then:
            beans.containsKey(SessionBinderJobListener.NAME)
    }

    void 'the scheduler is started on startup'() {
        given:
            Scheduler scheduler = Mock(Scheduler)
            QuartzGrailsPlugin plugin = pluginFor([:], false, scheduler)

        when:
            plugin.onStartup([:])

        then:
            1 * scheduler.getJobKeys(_ as GroupMatcher) >> ([] as Set)
            1 * scheduler.start()
    }

    void 'the scheduler is not started when auto startup is disabled'() {
        given:
            Scheduler scheduler = Mock(Scheduler)
            QuartzGrailsPlugin plugin = pluginFor(['quartz.autoStartup': false], false, scheduler)

        when:
            plugin.onStartup([:])

        then:
            1 * scheduler.getJobKeys(_ as GroupMatcher) >> ([] as Set)
            0 * scheduler.start()
    }

    void 'the scheduler is left alone on startup when the plugin is disabled'() {
        given:
            Scheduler scheduler = Mock(Scheduler)
            QuartzGrailsPlugin plugin = pluginFor(['quartz.pluginEnabled': false], false, scheduler)

        when:
            plugin.onStartup([:])

        then:
            0 * scheduler._
    }

    void 'shutdown waits for running jobs unless configured otherwise'() {
        given:
            Scheduler scheduler = Mock(Scheduler)
            QuartzGrailsPlugin plugin = pluginFor(config, false, scheduler)

        when:
            plugin.onShutdown([:])

        then:
            1 * scheduler.shutdown(waitForJobs)

        where:
            config                                              || waitForJobs
            [:]                                                 || true
            ['quartz.waitForJobsToCompleteOnShutdown': false]    || false
            ['quartz.waitForJobsToCompleteOnShutdown': true]     || true
    }

    void 'the scheduler is left alone on shutdown when the plugin is disabled'() {
        given:
            Scheduler scheduler = Mock(Scheduler)
            QuartzGrailsPlugin plugin = pluginFor(['quartz.pluginEnabled': false], false, scheduler)

        when:
            plugin.onShutdown([:])

        then:
            0 * scheduler._
    }

    void 'a change is ignored when the plugin is disabled'() {
        given:
            Scheduler scheduler = Mock(Scheduler)
            QuartzGrailsPlugin plugin = pluginFor(['quartz.pluginEnabled': false], false, scheduler)

        when:
            plugin.onChange([:])

        then:
            0 * scheduler._
    }

    private String beanReferenceName(BeanDefinition beanDefinition, String propertyName) {
        (beanDefinition.propertyValues.getPropertyValue(propertyName).value as RuntimeBeanReference).beanName
    }

    private Map<String, BeanDefinition> beanDefinitionsFor(Map<String, Object> config, boolean hibernate = false) {
        BeanBuilder beanBuilder = new BeanBuilder()
        beanBuilder.beans pluginFor(config, hibernate).doWithSpring()
        beanBuilder.beanDefinitions
    }

    private QuartzGrailsPlugin pluginFor(Map<String, Object> config, boolean hibernate = false, Scheduler scheduler = null) {
        GrailsApplication grailsApplication = new DefaultGrailsApplication()
        grailsApplication.config = new PropertySourcesConfig(config)
        grailsApplication.registerArtefactHandler(new JobArtefactHandler())
        grailsApplication.initialise()

        QuartzGrailsPlugin plugin = new QuartzGrailsPlugin()
        plugin.grailsApplication = grailsApplication
        plugin.pluginManager = Stub(GrailsPluginManager) {
            hasGrailsPlugin(_ as String) >> { String name -> hibernate && name.startsWith('hibernate') }
        }
        if (scheduler != null) {
            GenericApplicationContext applicationContext = new GenericApplicationContext()
            applicationContext.beanFactory.registerSingleton('quartzScheduler', scheduler)
            applicationContext.refresh()
            plugin.applicationContext = applicationContext
        }
        plugin
    }
}
