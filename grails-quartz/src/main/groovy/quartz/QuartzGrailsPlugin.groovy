/*
 * Copyright 2015-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package quartz

import grails.plugins.Plugin
import grails.plugins.metadata.GrailsPluginDescriptor
import grails.plugins.quartz.CustomTriggerFactoryBean
import grails.plugins.quartz.GrailsJobClass
import grails.plugins.quartz.JobArtefactHandler
import grails.plugins.quartz.JobDetailFactoryBean
import grails.plugins.quartz.QuartzBeanDefinitionsPostProcessor
import grails.plugins.quartz.listeners.SessionBinderJobListener
import grails.util.Metadata
import groovy.util.logging.Slf4j
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.ListenerManager
import org.quartz.Scheduler
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.quartz.impl.matchers.GroupMatcher
import org.quartz.impl.matchers.KeyMatcher
import org.grails.spring.context.support.MapBasedSmartPropertyOverrideConfigurer
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment

@Slf4j
@GrailsPluginDescriptor(
        loadAfter = ['hibernate', 'services'],
        watchedResources = ['file:./grails-app/jobs/**/*Job.groovy'])
class QuartzGrailsPlugin extends Plugin {

    /**
     * Whether the plugin is active. When disabled no scheduler, job beans or listeners are registered
     * and no job is ever scheduled.
     * @return {@code true} unless {@code quartz.pluginEnabled} is configured otherwise
     */
    boolean isPluginEnabled() {
        config.getProperty('quartz.pluginEnabled', Boolean, true)
    }

    /**
     * Whether the scheduler is started once the application has finished bootstrapping.
     * @return {@code true} unless {@code quartz.autoStartup} is configured otherwise
     */
    boolean isAutoStartup() {
        config.getProperty('quartz.autoStartup', Boolean, true)
    }

    /**
     * Whether Quartz persists jobs and triggers in a database instead of the in memory {@code RAMJobStore}.
     * @return {@code false} unless {@code quartz.jdbcStore} is configured otherwise
     */
    boolean isJdbcStore() {
        config.getProperty('quartz.jdbcStore', Boolean, false)
    }

    /**
     * The name of the Spring bean holding the {@code DataSource} Quartz uses when the JDBC job store is enabled.
     * @return the value of {@code quartz.jdbcStoreDataSource}, or {@code dataSource}
     */
    String getJdbcStoreDataSource() {
        config.getProperty('quartz.jdbcStoreDataSource', String, 'dataSource')
    }

    /**
     * Whether every Quartz table is emptied during startup. Only honoured when the JDBC job store is
     * enabled and a Hibernate plugin is present.
     * @return {@code false} unless {@code quartz.purgeQuartzTablesOnStartup} is configured otherwise
     */
    boolean isPurgeQuartzTablesOnStartup() {
        config.getProperty('quartz.purgeQuartzTablesOnStartup', Boolean, false)
    }

    /**
     * Whether shutdown blocks until currently executing jobs have finished.
     * @return {@code true} unless {@code quartz.waitForJobsToCompleteOnShutdown} is configured otherwise
     */
    boolean isWaitForJobsToCompleteOnShutdown() {
        config.getProperty('quartz.waitForJobsToCompleteOnShutdown', Boolean, true)
    }

    /**
     * Whether the scheduler is registered with the Quartz {@code SchedulerRepository}.
     * @return {@code false} unless {@code quartz.exposeSchedulerInRepository} is configured otherwise
     */
    boolean isExposeSchedulerInRepository() {
        config.getProperty('quartz.exposeSchedulerInRepository', Boolean, false)
    }

    /**
     * Whether startup fails when a job declares a trigger which can never fire, for example a cron
     * expression whose last occurrence is in the past. When {@code false} such a trigger is reported
     * and skipped, and the rest of the application starts as usual.
     * @return {@code false} unless {@code quartz.failOnNeverFiringTriggers} is configured otherwise
     */
    boolean isFailOnNeverFiringTriggers() {
        config.getProperty('quartz.failOnNeverFiringTriggers', Boolean, false)
    }

    /**
     * The name of the application, which every job registered by the plugin is stamped with. It is what
     * lets the plugin recognise its own jobs in a job store shared with other applications, so that it
     * never removes a job it did not register itself.
     * @return the value of {@code info.app.name}, or the name the application metadata reports
     */
    String getApplicationName() {
        config.getProperty('info.app.name', String) ?: grailsApplication.metadata.getApplicationName()
    }

    /**
     * Whether the application registers its jobs under a name of its own. An application which does not
     * name itself registers them under the default name, which every application that does not name
     * itself shares.
     * @return {@code false} when the name of the application is the default one
     */
    boolean isApplicationNamed() {
        getApplicationName() != Metadata.DEFAULT_APPLICATION_NAME
    }

    /**
     * The name given to the scheduler. When unset the bean name is used.
     * @return the value of {@code quartz.scheduler.instanceName}, or {@code null}
     */
    String getSchedulerInstanceName() {
        config.getProperty('quartz.scheduler.instanceName', String)
    }

    /**
     * Registers the beans of the plugin through {@link QuartzBeanDefinitionsPostProcessor}: the job beans
     * are prototypes created by a factory method and autowired by name, which the
     * {@link org.springframework.beans.factory.BeanRegistry} API cannot express.
     */
    @Override
    BeanRegistrar beanRegistrar() {
        { BeanRegistry registry, Environment environment ->
            registry.registerBean('quartzBeanDefinitionsPostProcessor', QuartzBeanDefinitionsPostProcessor) {
                it.infrastructure().supplier {
                    new QuartzBeanDefinitionsPostProcessor(this)
                }
            }
        } as BeanRegistrar
    }

    /**
     * Loads the quartz stanza from the grails configuration and turns it into a
     * flattened Properties object suitable for use by the Quartz SchedulerFactoryBean.
     * @return Quartz properties as defined in the Grails Configuration object
     */
    def loadQuartzProperties() {
        Properties quartzProperties = new Properties()
        if (config.get('quartz')) {
            // Convert to a properties file adding a prefix to each property
            ConfigObject configObject = new ConfigObject()
            configObject.putAll(config.get('quartz') ?: [:])
            quartzProperties << configObject.toProperties('org.quartz')
        }
        quartzProperties
    }

    void onChange(Map<String, Object> event) {
        if (!isPluginEnabled()) {
            return
        }

        if (event.source) {
            def jobClass = (GrailsJobClass) grailsApplication.addArtefact(JobArtefactHandler.TYPE, event.source)
            QuartzBeanDefinitionsPostProcessor.registerJobBeans((BeanDefinitionRegistry) applicationContext, jobClass)
            new MapBasedSmartPropertyOverrideConfigurer(grailsApplication: grailsApplication)
                    .postProcessBeanFactory(applicationContext.beanFactory)
        }
        refreshJobs(true)
    }

    /**
     * Schedules jobs. Creates job details and trigger beans. And schedules them.
     */
    def scheduleJob(GrailsJobClass jobClass, ApplicationContext ctx, boolean hasHibernate) {
        Scheduler scheduler = ctx.quartzScheduler
        if (scheduler) {
            def fullName = jobClass.fullName

            // Creates job details
            JobDetailFactoryBean jdfb = new JobDetailFactoryBean()
            jdfb.jobClass = jobClass
            jdfb.applicationName = getApplicationName()
            jdfb.afterPropertiesSet()
            JobDetail jobDetail = jdfb.object

            // adds the job to the scheduler, and associates triggers with it
            scheduler.addJob(jobDetail, true)

            // The session listener if is needed
            if (hasHibernate && (jobClass.sessionRequired || isJdbcStore())) {
                SessionBinderJobListener listener = ctx.getBean(SessionBinderJobListener.NAME)
                if (listener != null) {
                    ListenerManager listenerManager = scheduler.getListenerManager()
                    KeyMatcher<JobKey> matcher = KeyMatcher.keyEquals(jobDetail.key)
                    if (listenerManager.getJobListener(listener.getName()) == null) {
                        listenerManager.addJobListener(listener, matcher)
                    } else {
                        listenerManager.addJobListenerMatcher(listener.getName(), matcher)
                    }
                } else {
                    log.error('The SessionBinderJobListener has not been initialized.')
                }
            }

            // Creates and schedules triggers
            jobClass.triggers.each { name, Expando descriptor ->
                CustomTriggerFactoryBean factory = new CustomTriggerFactoryBean()
                factory.triggerClass = descriptor.triggerClass
                factory.triggerAttributes = descriptor.triggerAttributes
                factory.jobDetail = jobDetail
                factory.afterPropertiesSet()
                Trigger trigger = factory.object

                TriggerKey key = trigger.key
                if (!isFailOnNeverFiringTriggers() && !willEverFire(trigger)) {
                    log.error("The trigger ${key} of the job ${fullName} will never fire based on its configured " +
                            'schedule, so it has not been scheduled. Check its cron expression, start time and end time.')
                    return
                }
                log.debug("Scheduling $fullName with trigger $key: ${trigger}")
                if (scheduler.getTrigger(key) != null) {
                    scheduler.rescheduleJob(key, trigger)
                } else {
                    scheduler.scheduleJob(trigger)
                }
                log.debug("Job ${fullName} scheduled")
            }
        } else {
            log.error('Failed to schedule job details and job triggers: scheduler not found.')
        }
    }

    /**
     * Whether the job was registered by this application, which is the case when it carries the name of
     * the application as job data. A job of another application, and a job registered through the Quartz
     * API rather than as a job artefact, does not.
     */
    private boolean isJobOfThisApplication(JobDetail jobDetail) {
        jobDetail?.jobDataMap?.get(JobDetailFactoryBean.APPLICATION_NAME_PARAMETER) == getApplicationName()
    }

    /**
     * Whether the trigger can fire at least once, which is what the scheduler demands of a trigger before
     * it accepts it. The first fire time is computed the way the scheduler computes it, from the second
     * before the start time. A trigger bound to a Quartz calendar can still be rejected by the scheduler,
     * because the calendar which excludes its fire times lives in the job store.
     */
    private boolean willEverFire(Trigger trigger) {
        Date startTime = trigger.startTime
        // Without a start time the first fire time cannot be computed here, so leave the decision to the scheduler.
        startTime == null || trigger.getFireTimeAfter(new Date(startTime.time - 1000L)) != null
    }

    /**
     * Whether a Hibernate plugin is loaded, which binds a Hibernate session to the thread of each job.
     * @return {@code true} when the {@code hibernate}, {@code hibernate3}, {@code hibernate4} or
     * {@code hibernate5} plugin is loaded
     */
    boolean isHibernatePresent() {
        hasHibernate(manager)
    }

    private boolean hasHibernate(manager) {
        manager?.hasGrailsPlugin('hibernate') ||
                manager?.hasGrailsPlugin('hibernate3') ||
                manager?.hasGrailsPlugin('hibernate4') ||
                manager?.hasGrailsPlugin('hibernate5')
    }

    void refreshJobs(boolean ignoreErrors = false) {
        if (!isPluginEnabled()) {
            return
        }

        def quartzScheduler = applicationContext.quartzScheduler

        Set<JobKey> jobKeys = quartzScheduler.getJobKeys(GroupMatcher.anyGroup())

        //Remove the jobs of this application which are no longer active. Jobs another application
        //registered, and jobs registered through the Quartz API rather than as job artefacts, are left
        //alone: a job store can be shared, and the plugin only owns what it registered itself.
        jobKeys.each { JobKey key ->
            if (!isJobOfThisApplication(quartzScheduler.getJobDetail(key))) {
                log.debug("Leaving job ${key} alone: it was not registered by this application")
                return
            }
            def match = grailsApplication.jobClasses.find { GrailsJobClass jobClass -> jobClass.isEnabled() && jobClass.group == key.group && jobClass.clazz.name == key.name }
            if (!match) {
                log.info("Removing job ${key}, which is no longer an enabled job artefact of this application")
                def triggersForJob = quartzScheduler.getTriggersOfJob(key)?.collect { it.key }
                if (triggersForJob) {
                    //clean up triggers before we remove the job
                    quartzScheduler.unscheduleJobs(triggersForJob)
                }
                quartzScheduler.deleteJob(key)
            }
        }

        //Add new jobs
        grailsApplication.jobClasses.findAll { GrailsJobClass jobClass -> jobClass.isEnabled() }.each { GrailsJobClass jobClass ->
            try {
                scheduleJob(jobClass, applicationContext, hasHibernate(manager))
                def clz = jobClass.clazz
                clz.scheduler = quartzScheduler
                clz.grailsJobClass = jobClass
            } catch (Exception ex) {
                if (ignoreErrors) {
                    log.error("Error Scheduling Job Class ${jobClass} - ${ex.message}", ex)
                } else {
                    throw ex
                }
            }
        }
    }

    void onStartup(Map<String, Object> event) {
        if (isPluginEnabled()) {
            if (isJdbcStore() && !isApplicationNamed()) {
                log.warn('The application does not name itself, so it registers its jobs under the default ' +
                        "name [${Metadata.DEFAULT_APPLICATION_NAME}]. Another application which shares both " +
                        'the job store and that name removes them when it starts: set info.app.name.')
            }
            refreshJobs()
            if (isAutoStartup()) {
                applicationContext.quartzScheduler.start()
                log.info('Quartz Scheduler - Started')
            }
        }
        log.debug('Scheduled Job Classes count: ' + grailsApplication.jobClasses.size())
    }

    void onShutdown(Map<String, Object> event) {
        if (isPluginEnabled()) {
            applicationContext.quartzScheduler.shutdown(isWaitForJobsToCompleteOnShutdown())
        }
    }
}
