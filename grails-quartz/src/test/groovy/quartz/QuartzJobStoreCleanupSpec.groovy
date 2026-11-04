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
import grails.plugins.quartz.DefaultGrailsJobClass
import grails.plugins.quartz.JobArtefactHandler
import grails.plugins.quartz.JobDetailFactoryBean
import grails.plugins.quartz.TestQuartzJob
import org.grails.config.PropertySourcesConfig
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.quartz.TriggerKey
import org.quartz.impl.StdSchedulerFactory
import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification

/**
 * Tests which jobs the plugin removes from the job store of a starting application. A job store can be
 * shared — with other applications, and with code which schedules jobs through the Quartz API — so only
 * the jobs the application registered itself are its to remove.
 */
class QuartzJobStoreCleanupSpec extends Specification {

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

    void 'a job the application registered is removed once its artefact is gone'() {
        given: 'an application which registered two jobs'
            pluginFor(KeptJob, RemovedJob).onStartup([:])

        expect:
            scheduler.checkExists(JobKey.jobKey(RemovedJob.name, JOBS_GROUP))

        when: 'it starts again without one of them'
            pluginFor(KeptJob).onStartup([:])

        then: 'the job it no longer declares is removed, along with its triggers'
            !scheduler.checkExists(JobKey.jobKey(RemovedJob.name, JOBS_GROUP))
            !scheduler.checkExists(TriggerKey.triggerKey('removed', TRIGGERS_GROUP))

        and: 'the job it still declares is left scheduled'
            scheduler.checkExists(JobKey.jobKey(KeptJob.name, JOBS_GROUP))
            scheduler.checkExists(TriggerKey.triggerKey('kept', TRIGGERS_GROUP))
    }

    void 'a job the application registered is removed once its artefact is disabled'() {
        given: 'a job which was registered while it was still enabled'
            QuartzGrailsPlugin plugin = pluginFor(DisabledArtefactJob)
            scheduler.addJob(jobDetailFor(DisabledArtefactJob, plugin.applicationName), true)

        expect:
            scheduler.checkExists(JobKey.jobKey(DisabledArtefactJob.name, JOBS_GROUP))

        when:
            plugin.onStartup([:])

        then:
            !scheduler.checkExists(JobKey.jobKey(DisabledArtefactJob.name, JOBS_GROUP))
    }

    void 'the jobs of another application sharing the job store are left alone'() {
        given: 'an application which registered a job of its own in the shared job store'
            pluginFor('reporting', RemovedJob).onStartup([:])

        expect:
            scheduler.checkExists(JobKey.jobKey(RemovedJob.name, JOBS_GROUP))

        when: 'another application, which does not declare that job, starts against the same store'
            pluginFor('scheduling', KeptJob).onStartup([:])

        then: 'the job of the first application is still scheduled'
            scheduler.checkExists(JobKey.jobKey(RemovedJob.name, JOBS_GROUP))
            scheduler.checkExists(TriggerKey.triggerKey('removed', TRIGGERS_GROUP))

        and: 'the second application registered its own job'
            scheduler.checkExists(JobKey.jobKey(KeptJob.name, JOBS_GROUP))
    }

    void 'an application only removes the jobs it registered itself when it starts again'() {
        given: 'two applications which each registered a job in the shared job store'
            pluginFor('reporting', RemovedJob).onStartup([:])
            pluginFor('scheduling', KeptJob, DroppedJob).onStartup([:])

        when: 'the second application starts again without one of its jobs'
            pluginFor('scheduling', KeptJob).onStartup([:])

        then: 'only its own job is gone'
            !scheduler.checkExists(JobKey.jobKey(DroppedJob.name, JOBS_GROUP))
            scheduler.checkExists(JobKey.jobKey(KeptJob.name, JOBS_GROUP))
            scheduler.checkExists(JobKey.jobKey(RemovedJob.name, JOBS_GROUP))
    }

    void 'a job registered through the quartz api is left in the job store'() {
        given: 'a job scheduled without the plugin, in a group of its own'
            JobDetail native_ = JobBuilder.newJob(TestQuartzJob)
                    .withIdentity('nativeJob', 'nativeJobs')
                    .storeDurably()
                    .build()
            scheduler.addJob(native_, true)
            scheduler.scheduleJob(triggerFor(native_, 'nativeTrigger'))

        when:
            pluginFor(KeptJob).onStartup([:])

        then:
            scheduler.checkExists(native_.key)
            scheduler.checkExists(TriggerKey.triggerKey('nativeTrigger', TRIGGERS_GROUP))
    }

    void 'the jobs the application registers carry the name of the application'() {
        given:
            QuartzGrailsPlugin plugin = pluginFor(KeptJob)

        when:
            plugin.onStartup([:])

        then:
            JobDetail jobDetail = scheduler.getJobDetail(JobKey.jobKey(KeptJob.name, JOBS_GROUP))
            jobDetail.jobDataMap.get(JobDetailFactoryBean.APPLICATION_NAME_PARAMETER) == plugin.applicationName
    }

    private JobDetail jobDetailFor(Class jobClass, String applicationName) {
        JobDetailFactoryBean factory = new JobDetailFactoryBean()
        factory.jobClass = new DefaultGrailsJobClass(jobClass)
        factory.applicationName = applicationName
        factory.afterPropertiesSet()
        factory.object
    }

    private Trigger triggerFor(JobDetail jobDetail, String name) {
        TriggerBuilder.newTrigger()
                .withIdentity(name, TRIGGERS_GROUP)
                .forJob(jobDetail)
                .startAt(new Date(System.currentTimeMillis() + 600_000L))
                .build()
    }

    private QuartzGrailsPlugin pluginFor(Class... jobClasses) {
        pluginFor(null, jobClasses)
    }

    private QuartzGrailsPlugin pluginFor(String applicationName, Class... jobClasses) {
        Map<String, Object> config = ['quartz.autoStartup': false]
        if (applicationName) {
            config['info.app.name'] = applicationName
        }

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
class KeptJob {

    static triggers = {
        simple name: 'kept', startDelay: 600_000L, repeatInterval: 600_000L
    }

    void execute() {}
}

@Artefact('Job')
class RemovedJob {

    static triggers = {
        simple name: 'removed', startDelay: 600_000L, repeatInterval: 600_000L
    }

    void execute() {}
}

@Artefact('Job')
class DroppedJob {

    static triggers = {
        simple name: 'dropped', startDelay: 600_000L, repeatInterval: 600_000L
    }

    void execute() {}
}

@Artefact('Job')
class DisabledArtefactJob {

    static jobEnabled = false

    void execute() {}
}
