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
import grails.spring.BeanBuilder
import org.grails.config.PropertySourcesConfig
import org.quartz.Scheduler
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.GenericApplicationContext
import quartz.jobs.ContextSpecJob
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * Builds a real Spring context from the bean definitions the plugin contributes, so that the scheduler
 * under test is an actual {@link Scheduler} produced by a {@code SchedulerFactoryBean} rather than a mock.
 */
class QuartzSchedulerContextSpec extends Specification {

    @AutoCleanup
    ConfigurableApplicationContext applicationContext

    void setup() {
        ContextSpecJob.EXECUTIONS.set(0)
    }

    void 'the scheduler is built but left stopped until the plugin starts it'() {
        given:
            QuartzGrailsPlugin plugin = startedPlugin([:], false)

        expect: 'the factory bean produced a usable scheduler'
            scheduler instanceof Scheduler

        and: 'which the plugin has not started yet'
            !scheduler.isStarted()

        when:
            plugin.onStartup([:])

        then:
            scheduler.isStarted()
            !scheduler.isInStandbyMode()
    }

    void 'the scheduler stays stopped when auto startup is disabled'() {
        given:
            QuartzGrailsPlugin plugin = startedPlugin('quartz.autoStartup': false)

        when:
            plugin.onStartup([:])

        then:
            !scheduler.isStarted()
    }

    void 'a job class is scheduled and executed by the running scheduler'() {
        given:
            QuartzGrailsPlugin plugin = startedPlugin([:], true)
            PollingConditions conditions = new PollingConditions(timeout: 10)

        when:
            plugin.onStartup([:])

        then: 'the job was registered with the scheduler'
            scheduler.checkExists(org.quartz.JobKey.jobKey(ContextSpecJob.name, 'GRAILS_JOBS'))

        and: 'and its trigger really fires it'
            conditions.eventually {
                assert ContextSpecJob.EXECUTIONS.get() > 0
            }
    }

    void 'the scheduler is shut down when the plugin shuts down'() {
        given:
            QuartzGrailsPlugin plugin = startedPlugin([:], false)
            plugin.onStartup([:])

        when:
            plugin.onShutdown([:])

        then:
            scheduler.isShutdown()
    }

    void 'no scheduler is built at all when the plugin is disabled'() {
        when:
            QuartzGrailsPlugin plugin = startedPlugin('quartz.pluginEnabled': false)

        then:
            !applicationContext.containsBean('quartzScheduler')

        and: 'and the lifecycle hooks stay clear of it'
            plugin.onStartup([:])
            plugin.onShutdown([:])
            noExceptionThrown()
    }

    private Scheduler getScheduler() {
        applicationContext.getBean('quartzScheduler', Scheduler)
    }

    private QuartzGrailsPlugin startedPlugin(Map<String, Object> config, boolean withJobClass = false) {
        GrailsApplication grailsApplication = withJobClass
                ? new DefaultGrailsApplication(ContextSpecJob)
                : new DefaultGrailsApplication()
        grailsApplication.config = new PropertySourcesConfig(config)
        grailsApplication.registerArtefactHandler(new JobArtefactHandler())
        grailsApplication.initialise()

        GenericApplicationContext parent = new GenericApplicationContext()
        parent.beanFactory.registerSingleton('grailsApplication', grailsApplication)
        parent.refresh()

        QuartzGrailsPlugin plugin = new QuartzGrailsPlugin()
        plugin.grailsApplication = grailsApplication
        plugin.pluginManager = Stub(GrailsPluginManager)

        BeanBuilder beanBuilder = new BeanBuilder(parent)
        beanBuilder.beans plugin.doWithSpring()
        applicationContext = beanBuilder.createApplicationContext() as ConfigurableApplicationContext
        plugin.applicationContext = applicationContext
        plugin
    }
}
