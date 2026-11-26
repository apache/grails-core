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
package grails.plugins.quartz

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.aot.AotDetector
import org.springframework.beans.BeansException
import org.springframework.beans.MutablePropertyValues
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import org.springframework.beans.factory.config.RuntimeBeanReference
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.beans.factory.support.ManagedList
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered
import org.springframework.scheduling.quartz.SchedulerFactoryBean

import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.plugins.quartz.cleanup.JdbcCleanup
import grails.plugins.quartz.listeners.ExceptionPrinterJobListener
import grails.plugins.quartz.listeners.SessionBinderJobListener
import quartz.QuartzGrailsPlugin

/**
 * Registers the beans of the Quartz plugin: the scheduler, the job factory, the job listeners, the
 * cleanup of the job store's tables, and two beans for every job artefact - a factory bean that looks
 * the job class up in the application, and the prototype job bean it creates. Prototype job beans
 * created by a factory method and autowired by name are what the
 * {@link org.springframework.beans.factory.BeanRegistry} API cannot express, so they are registered
 * here, from the plugin's registrar.
 *
 * <p>It runs as a {@link PriorityOrdered} post-processor with highest precedence, so the definitions
 * are registered before Spring Boot reads the auto-configurations and a {@code @ConditionalOnMissingBean}
 * default backs off from them. A definition registered earlier under one of these names, by a plugin
 * loaded before this one, is replaced. Running on artifacts generated ahead of time, a definition the
 * generator wrote is kept.</p>
 *
 * @since 9.0
 */
@Slf4j
@CompileStatic
class QuartzBeanDefinitionsPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    private final QuartzGrailsPlugin plugin

    /**
     * @param plugin the plugin, whose configuration decides which beans are registered
     */
    QuartzBeanDefinitionsPostProcessor(QuartzGrailsPlugin plugin) {
        this.plugin = plugin
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!plugin.isPluginEnabled()) {
            return
        }

        boolean hasHibernate = plugin.isHibernatePresent()

        if (plugin.isJdbcStore() && hasHibernate && plugin.isPurgeQuartzTablesOnStartup()) {
            GenericBeanDefinition purgeTables = definition(JdbcCleanup)
            purgeTables.propertyValues.add('dataSource', new RuntimeBeanReference(plugin.getJdbcStoreDataSource()))
            purgeTables.autowireMode = AbstractBeanDefinition.AUTOWIRE_BY_NAME
            register(registry, 'purgeTablesBean', purgeTables)
        }

        for (GrailsClass jobClass : plugin.grailsApplication.getArtefacts(JobArtefactHandler.TYPE)) {
            registerJobBeans(registry, (GrailsJobClass) jobClass)
        }

        // binds a Hibernate session to the thread of each job when Hibernate is configured
        if (hasHibernate) {
            log.debug('Registering hibernate SessionBinderJobListener')
            GenericBeanDefinition sessionBinder = definition(SessionBinderJobListener)
            sessionBinder.autowireMode = AbstractBeanDefinition.AUTOWIRE_BY_NAME
            register(registry, SessionBinderJobListener.NAME, sessionBinder)
        }

        // logs the exceptions jobs throw, for every job
        register(registry, ExceptionPrinterJobListener.NAME, definition(ExceptionPrinterJobListener))

        // creates the job instances on execution
        register(registry, 'quartzJobFactory', definition(GrailsJobFactory))

        register(registry, 'quartzScheduler', schedulerDefinition())
    }

    /**
     * Registers the beans of a job artefact: a factory bean named {@code <job class name>Class} that
     * looks the job class up in the application, and the prototype job bean, named after the job class
     * and autowired by name, that it creates.
     *
     * @param registry the registry to register the definitions with
     * @param jobClass the job artefact
     */
    static void registerJobBeans(BeanDefinitionRegistry registry, GrailsJobClass jobClass) {
        String fullName = jobClass.fullName
        try {
            GenericBeanDefinition lookup = definition(MethodInvokingFactoryBean)
            MutablePropertyValues lookupProperties = lookup.propertyValues
            lookupProperties.add('targetObject', new RuntimeBeanReference(GrailsApplication.APPLICATION_ID, false))
            lookupProperties.add('targetMethod', 'getArtefact')
            lookupProperties.add('arguments', [JobArtefactHandler.TYPE, fullName])
            register(registry, "${fullName}Class".toString(), lookup)

            GenericBeanDefinition job = new GenericBeanDefinition()
            job.factoryBeanName = "${fullName}Class".toString()
            job.factoryMethodName = 'newInstance'
            job.autowireMode = AbstractBeanDefinition.AUTOWIRE_BY_NAME
            job.scope = BeanDefinition.SCOPE_PROTOTYPE
            register(registry, fullName, job)
        } catch (Exception e) {
            log.error("Error declaring ${fullName}Detail bean in context", e)
        }
    }

    private GenericBeanDefinition schedulerDefinition() {
        GenericBeanDefinition scheduler = definition(SchedulerFactoryBean)
        MutablePropertyValues properties = scheduler.propertyValues
        properties.add('quartzProperties', plugin.loadQuartzProperties())
        // The bean name is used by the factory bean as the scheduler name, so it must be set explicitly
        // for a name other than the bean name.
        String instanceName = plugin.getSchedulerInstanceName()
        if (instanceName) {
            properties.add('schedulerName', instanceName)
        }
        // Delay scheduler startup to the after-bootstrap stage: onStartup() honours quartz.autoStartup,
        // whereas this bean's own startup is not grails aware.
        properties.add('autoStartup', false)
        if (plugin.isJdbcStore()) {
            properties.add('dataSource', new RuntimeBeanReference(plugin.getJdbcStoreDataSource()))
            properties.add('transactionManager', new RuntimeBeanReference('transactionManager'))
        }
        properties.add('waitForJobsToCompleteOnShutdown', plugin.isWaitForJobsToCompleteOnShutdown())
        properties.add('exposeSchedulerInRepository', plugin.isExposeSchedulerInRepository())
        properties.add('jobFactory', new RuntimeBeanReference('quartzJobFactory'))
        // global listeners on each job
        ManagedList<RuntimeBeanReference> listeners = new ManagedList<>()
        listeners.add(new RuntimeBeanReference(ExceptionPrinterJobListener.NAME))
        properties.add('globalJobListeners', listeners)
        scheduler
    }

    @Override
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    @Override
    int getOrder() {
        Ordered.HIGHEST_PRECEDENCE
    }

    private static GenericBeanDefinition definition(Class<?> beanClass) {
        GenericBeanDefinition definition = new GenericBeanDefinition()
        definition.beanClass = beanClass
        definition.scope = BeanDefinition.SCOPE_SINGLETON
        definition
    }

    /**
     * Registers a definition, keeping one generated ahead of time: running on generated artifacts,
     * what this registered was written out as code, and registering over it would discard the
     * instance supplier the generator wrote.
     */
    private static void register(BeanDefinitionRegistry registry, String beanName, BeanDefinition definition) {
        if (AotDetector.useGeneratedArtifacts() && registry.containsBeanDefinition(beanName)) {
            return
        }
        registry.registerBeanDefinition(beanName, definition)
    }
}
