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
import grails.plugins.quartz.CustomTriggerFactoryBean
import grails.plugins.quartz.GrailsJobClass
import grails.plugins.quartz.GrailsJobFactory
import grails.plugins.quartz.JobArtefactHandler
import grails.plugins.quartz.JobDetailFactoryBean
import grails.plugins.quartz.cleanup.JdbcCleanup
import grails.plugins.quartz.listeners.ExceptionPrinterJobListener
import grails.plugins.quartz.listeners.SessionBinderJobListener
import groovy.util.logging.Slf4j
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.ListenerManager
import org.quartz.Scheduler
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.quartz.impl.matchers.GroupMatcher
import org.quartz.impl.matchers.KeyMatcher
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.quartz.SchedulerFactoryBean

@Slf4j
class QuartzGrailsPlugin extends Plugin {

    def grailsVersion = '7.0.0-SNAPSHOT > *'
    def watchedResources = 'file:./grails-app/jobs/**/*Job.groovy'
    def title = 'Quartz Plugin'
    def author = 'Apache Grails Team'
    def description = 'Adds Quartz job scheduling features'
    def profiles = ['web']
    // 'hibernate' is the GORM for Hibernate plugin. Quartz must be configured after it so that the
    // dataSource, transactionManager and session beans it contributes already exist -- the scheduler
    // refs them when the JDBC job store is enabled, and SessionBinderJobListener autowires by name.
    List loadAfter = ['hibernate', 'services']
    def documentation = 'https://grails.apache.org/documentation.html'
    def license = 'APACHE'
    def issueManagement = [ system: 'Github Issues', url: 'https://github.com/apache/grails-core/issues' ]

    // Online location of the plugin's browsable source code.
    def scm = [url: 'https://github.com/apache/grails-core']

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
     * The name given to the scheduler. When unset the bean name is used.
     * @return the value of {@code quartz.scheduler.instanceName}, or {@code null}
     */
    String getSchedulerInstanceName() {
        config.getProperty('quartz.scheduler.instanceName', String)
    }

    Closure doWithSpring() {
        { ->
            if (!isPluginEnabled()) {
                return
            }

            boolean hasHibernate = hasHibernate(manager)

            if (isJdbcStore() && hasHibernate && isPurgeQuartzTablesOnStartup()) {
                String dataSourceName = getJdbcStoreDataSource()
                purgeTablesBean(JdbcCleanup) { bean ->
                    dataSource = ref(dataSourceName)
                    bean.autowire = 'byName'
                }
            }

            // Configure job beans
            grailsApplication.jobClasses.each { GrailsJobClass jobClass ->
                configureJobBeans.delegate = delegate
                configureJobBeans(jobClass, hasHibernate)
            }

            // Configure the session listener if there is the Hibernate is configured
            if (hasHibernate) {
                log.debug('Registering hibernate SessionBinderJobListener')

                // register SessionBinderJobListener to bind Hibernate Session to each Job's thread
                "${SessionBinderJobListener.NAME}"(SessionBinderJobListener) { bean ->
                    bean.autowire = 'byName'
                }
            }

            // register global ExceptionPrinterJobListener which will log exceptions occured
            // during job's execution
            "${ExceptionPrinterJobListener.NAME}"(ExceptionPrinterJobListener)

            // Configure the job factory to create job instances on executions.
            quartzJobFactory(GrailsJobFactory)

            // Configure Scheduler
            configureScheduler.delegate = delegate
            configureScheduler()
        }
    }

    /**
     * Configure job beans.
     */
    def configureJobBeans = { GrailsJobClass jobClass, boolean hasHibernate = true ->
        def fullName = jobClass.fullName

        try {
            "${fullName}Class"(MethodInvokingFactoryBean) {
                targetObject = ref('grailsApplication', false)
                targetMethod = 'getArtefact'
                arguments = [JobArtefactHandler.TYPE, jobClass.fullName]
            }

            "${fullName}"(ref("${fullName}Class")) { bean ->
                bean.factoryMethod = 'newInstance'
                bean.autowire = 'byName'
                bean.scope = 'prototype'
            }
        } catch (Exception e) {
            log.error("Error declaring ${fullName}Detail bean in context", e)
        }
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

    def configureScheduler = { ->
        // Everything the bean definition closure below needs is resolved here: within that closure
        // property and method names are resolved against the bean builder, not against this plugin.
        Properties properties = loadQuartzProperties()
        String instanceName = getSchedulerInstanceName()
        boolean jdbcStore = isJdbcStore()
        String dataSourceName = getJdbcStoreDataSource()
        boolean waitForJobs = isWaitForJobsToCompleteOnShutdown()
        boolean exposeInRepository = isExposeSchedulerInRepository()

        quartzScheduler(SchedulerFactoryBean) { bean ->
            quartzProperties = properties

            // The bean name is used by the factory bean as the scheduler name so you must explicitly set it if
            // you want a name different from the bean name.
            if (instanceName) {
                schedulerName = instanceName
            }

            // Delay scheduler startup to the after-bootstrap stage: onStartup() honours quartz.autoStartup,
            // whereas this bean's own startup is not grails aware.
            autoStartup = false

            // Store
            if (jdbcStore) {
                dataSource = ref(dataSourceName)
                transactionManager = ref('transactionManager')
            }

            waitForJobsToCompleteOnShutdown = waitForJobs
            exposeSchedulerInRepository = exposeInRepository

            jobFactory = quartzJobFactory

            // Global listeners on each job.
            globalJobListeners = [ref(ExceptionPrinterJobListener.NAME)]
        }
    }

    void onChange(Map<String, Object> event) {
        if (!isPluginEnabled()) {
            return
        }

        if (event.source) {
            boolean hasHibernate = hasHibernate(manager)
            def jobClass = grailsApplication.addArtefact(JobArtefactHandler.TYPE, event.source)
            beans {
                configureJobBeans.delegate = delegate
                configureJobBeans(jobClass, hasHibernate)
            }
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

        //Remove any recently removed / disabled Jobs
        jobKeys.each { JobKey key ->
            def match = grailsApplication.jobClasses.find { GrailsJobClass jobClass -> jobClass.isEnabled() && jobClass.group == key.group && jobClass.clazz.name == key.name }
            if (!match) {
                log.info("Removing No longer Active Job: ${key.name}")
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
