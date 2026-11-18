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

import grails.artefact.Artefact
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.plugins.GrailsPluginManager
import grails.plugins.quartz.JobArtefactHandler
import org.grails.config.PropertySourcesConfig
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.SchedulerException
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.quartz.impl.StdSchedulerFactory
import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification

import java.time.Year

/**
 * Tests what the plugin does with the triggers of the job artefacts of an application while it starts,
 * against a scheduler holding its jobs in memory.
 */
class QuartzStartupSchedulingSpec extends Specification {

    private static final String JOBS_GROUP = 'GRAILS_JOBS'
    private static final String TRIGGERS_GROUP = 'GRAILS_TRIGGERS'

    Scheduler scheduler

    void setup() {
        Properties properties = new Properties()
        properties.setProperty('org.quartz.scheduler.instanceName', "scheduler-${System.identityHashCode(this)}" as String)
        properties.setProperty('org.quartz.threadPool.threadCount', '1')
        properties.setProperty('org.quartz.job.store.class', 'org.quartz.simpl.RAMJobStore')
        scheduler = new StdSchedulerFactory(properties).getScheduler()
    }

    void cleanup() {
        scheduler.shutdown()
    }

    void 'the triggers a job declares are scheduled while the application starts'() {
        given:
            QuartzGrailsPlugin plugin = pluginFor([:], RepeatingJob)

        when:
            plugin.onStartup([:])

        then:
            scheduler.checkExists(JobKey.jobKey(RepeatingJob.name, JOBS_GROUP))
            scheduler.checkExists(TriggerKey.triggerKey('repeating', TRIGGERS_GROUP))
            scheduler.isStarted()
    }

    void 'a trigger that can never fire does not stop the application from starting'() {
        given: 'a job whose cron expression has its last occurrence in the past'
            QuartzGrailsPlugin plugin = pluginFor([:], PastCronJob)

        when:
            plugin.onStartup([:])

        then: 'the job is registered, but the trigger the scheduler would refuse is left out'
            noExceptionThrown()
            scheduler.checkExists(JobKey.jobKey(PastCronJob.name, JOBS_GROUP))
            scheduler.getTriggersOfJob(JobKey.jobKey(PastCronJob.name, JOBS_GROUP)).isEmpty()
            !scheduler.checkExists(TriggerKey.triggerKey('pastCron', TRIGGERS_GROUP))
    }

    void 'the other triggers of a job with a trigger that can never fire are still scheduled'() {
        given:
            QuartzGrailsPlugin plugin = pluginFor([:], MixedTriggersJob)

        when:
            plugin.onStartup([:])

        then:
            List<Trigger> triggers = scheduler.getTriggersOfJob(JobKey.jobKey(MixedTriggersJob.name, JOBS_GROUP))
            triggers.collect { it.key.name } == ['mixedFiring']
    }

    void 'the other jobs of an application with a trigger that can never fire are still scheduled'() {
        given:
            QuartzGrailsPlugin plugin = pluginFor([:], PastCronJob, RepeatingJob)

        when:
            plugin.onStartup([:])

        then:
            scheduler.checkExists(TriggerKey.triggerKey('repeating', TRIGGERS_GROUP))
            !scheduler.checkExists(TriggerKey.triggerKey('pastCron', TRIGGERS_GROUP))
    }

    void 'a trigger that can never fire stops the application from starting when the plugin is configured to fail'() {
        given:
            QuartzGrailsPlugin plugin = pluginFor(['quartz.failOnNeverFiringTriggers': true], PastCronJob)

        when:
            plugin.onStartup([:])

        then:
            SchedulerException e = thrown()
            e.message.contains('will never fire')

        and: 'the scheduler was never started'
            !scheduler.isStarted()
    }

    void 'a trigger that fires only once in the future is scheduled'() {
        given:
            QuartzGrailsPlugin plugin = pluginFor([:], FutureCronJob)

        when:
            plugin.onStartup([:])

        then:
            scheduler.checkExists(TriggerKey.triggerKey('futureCron', TRIGGERS_GROUP))
    }

    private QuartzGrailsPlugin pluginFor(Map<String, Object> config, Class... jobClasses) {
        GrailsApplication grailsApplication = new DefaultGrailsApplication()
        grailsApplication.config = new PropertySourcesConfig(config)
        grailsApplication.registerArtefactHandler(new JobArtefactHandler())
        grailsApplication.initialise()
        jobClasses.each { grailsApplication.addArtefact(JobArtefactHandler.TYPE, it) }

        QuartzGrailsPlugin plugin = new QuartzGrailsPlugin()
        plugin.grailsApplication = grailsApplication
        plugin.pluginManager = Stub(GrailsPluginManager) {
            hasGrailsPlugin(_ as String) >> false
        }
        GenericApplicationContext applicationContext = new GenericApplicationContext()
        applicationContext.beanFactory.registerSingleton('quartzScheduler', scheduler)
        applicationContext.refresh()
        plugin.applicationContext = applicationContext
        plugin
    }
}

@Artefact('Job')
class RepeatingJob {

    static triggers = {
        simple name: 'repeating', startDelay: 0L, repeatInterval: 60_000L
    }

    void execute() {}
}

@Artefact('Job')
class PastCronJob {

    static triggers = {
        cron name: 'pastCron', cronExpression: '0 0 12 1 1 ? 2020'
    }

    void execute() {}
}

@Artefact('Job')
class FutureCronJob {

    static triggers = {
        cron name: 'futureCron', cronExpression: "0 0 12 1 1 ? ${Year.now().value + 5}"
    }

    void execute() {}
}

@Artefact('Job')
class MixedTriggersJob {

    static triggers = {
        cron name: 'mixedPast', cronExpression: '0 0 12 1 1 ? 2020'
        simple name: 'mixedFiring', startDelay: 0L, repeatInterval: 60_000L
    }

    void execute() {}
}
