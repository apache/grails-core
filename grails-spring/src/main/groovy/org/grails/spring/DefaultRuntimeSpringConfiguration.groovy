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
package org.grails.spring

import groovy.transform.CompileStatic
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.aot.AotDetector
import org.springframework.beans.PropertyValue
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.util.Assert

/**
 * A programmable runtime Spring configuration that allows a spring ApplicationContext
 * to be constructed at runtime.
 *
 * Credit must go to Solomon Duskis and the
 * article: http://jroller.com/page/Solomon?entry=programmatic_configuration_in_spring
 *
 * @author Graeme
 * @since 0.3
 */
@CompileStatic
class DefaultRuntimeSpringConfiguration implements RuntimeSpringConfiguration {

    private static final Log LOG = LogFactory.getLog(DefaultRuntimeSpringConfiguration)
    protected GenericApplicationContext context
    private Map<String, BeanConfiguration> beanConfigs = [:]
    private Map<String, BeanDefinition> beanDefinitions = [:]
    private Set<String> beanNames = [] as Set
    protected ApplicationContext parent
    protected ClassLoader classLoader
    protected Map<String, List<String>> aliases = [:]
    protected ListableBeanFactory beanFactory

    /**
     * Creates the ApplicationContext instance. Subclasses can override to customise the used ApplicationContext
     *
     * @param parentCtx The parent ApplicationContext instance. Can be null.
     *
     * @return An instance of GenericApplicationContext
     */
    protected GenericApplicationContext createApplicationContext(ApplicationContext parentCtx) {
        if (parentCtx != null && beanFactory != null) {
            Assert.isInstanceOf(DefaultListableBeanFactory, beanFactory,
                    'ListableBeanFactory set must be a subclass of DefaultListableBeanFactory')

            return new GrailsApplicationContext((DefaultListableBeanFactory) beanFactory, parentCtx)
        }

        if (beanFactory != null) {
            Assert.isInstanceOf(DefaultListableBeanFactory, beanFactory,
                    'ListableBeanFactory set must be a subclass of DefaultListableBeanFactory')

            return new GrailsApplicationContext((DefaultListableBeanFactory) beanFactory)
        }

        if (parentCtx != null) {
            return new GrailsApplicationContext(parentCtx)
        }

        return new GrailsApplicationContext()
    }

    DefaultRuntimeSpringConfiguration() {
        super()
    }

    DefaultRuntimeSpringConfiguration(ApplicationContext parent) {
        this(parent, null)
    }

    DefaultRuntimeSpringConfiguration(ApplicationContext parent, ClassLoader cl) {
        this.parent = parent
        classLoader = cl
    }

    private void trySettingClassLoaderOnContextIfFoundInParent(ApplicationContext parentCtx) {
        if (parentCtx.containsBean('classLoader')) {
            Object cl = parentCtx.getBean('classLoader')
            if (cl instanceof ClassLoader) {
                setClassLoaderOnContext(cl)
            }
        }
    }

    private void setClassLoaderOnContext(ClassLoader cl) {
        context.classLoader = cl
        context.beanFactory.beanClassLoader = cl
    }

    /**
     * Initialises the ApplicationContext instance.
     */
    protected void initialiseApplicationContext() {
        if (context != null) {
            return
        }

        context = createApplicationContext(parent)

        if (parent != null && classLoader == null) {
            trySettingClassLoaderOnContextIfFoundInParent(parent)
        }
        else if (classLoader != null) {
            setClassLoaderOnContext(classLoader)
        }

        Assert.notNull(context, 'ApplicationContext cannot be null')
    }

    BeanConfiguration addSingletonBean(String name, @SuppressWarnings('rawtypes') Class clazz) {
        BeanConfiguration bc = new DefaultBeanConfiguration(name, clazz)
        registerBeanConfiguration(name, bc)
        return bc
    }

    BeanConfiguration addPrototypeBean(String name, @SuppressWarnings('rawtypes') Class clazz) {
        BeanConfiguration bc = new DefaultBeanConfiguration(name, clazz, true)
        registerBeanConfiguration(name, bc)
        return bc
    }

    ApplicationContext getApplicationContext() {
        long now = LOG.isDebugEnabled() ? System.currentTimeMillis() : 0
        initialiseApplicationContext()
        registerBeansWithContext(context)
        context.refresh()
        if (LOG.isDebugEnabled()) {
            LOG.debug('Created ApplicationContext in ' + (System.currentTimeMillis() - now) + 'ms')
        }
        return context
    }

    ApplicationContext getUnrefreshedApplicationContext() {
        initialiseApplicationContext()
        return context
    }

    BeanConfiguration addSingletonBean(String name) {
        BeanConfiguration bc = new DefaultBeanConfiguration(name)
        registerBeanConfiguration(name, bc)
        return bc
    }

    BeanConfiguration createSingletonBean(@SuppressWarnings('rawtypes') Class clazz) {
        return new DefaultBeanConfiguration(clazz)
    }

    @SuppressWarnings('rawtypes')
    BeanConfiguration addSingletonBean(String name, Class clazz, Collection args) {
        BeanConfiguration bc = new DefaultBeanConfiguration(name, clazz, args)
        registerBeanConfiguration(name, bc)
        return bc
    }

    BeanConfiguration addPrototypeBean(String name) {
        BeanConfiguration bc = new DefaultBeanConfiguration(name, true)
        registerBeanConfiguration(name, bc)
        return bc
    }

    private void registerBeanConfiguration(String name, BeanConfiguration bc) {
        beanConfigs.put(name, bc)
        beanNames << name
    }

    @SuppressWarnings('rawtypes')
    BeanConfiguration createSingletonBean(Class clazz, Collection constructorArguments) {
        return new DefaultBeanConfiguration(clazz, constructorArguments)
    }

    BeanConfiguration createPrototypeBean(String name) {
        return new DefaultBeanConfiguration(name, true)
    }

    BeanConfiguration createSingletonBean(String name) {
        return new DefaultBeanConfiguration(name)
    }

    void addBeanConfiguration(String beanName, BeanConfiguration beanConfiguration) {
        beanConfiguration.name = beanName
        registerBeanConfiguration(beanName, beanConfiguration)
    }

    void addBeanDefinition(String name, BeanDefinition bd) {
        beanDefinitions.put(name, bd)
        beanConfigs.remove(name)
        beanNames << name
    }

    boolean containsBean(String name) {
        return name in beanNames
    }

    BeanConfiguration getBeanConfig(String name) {
        return beanConfigs[name]
    }

    AbstractBeanDefinition createBeanDefinition(String name) {
        if (containsBean(name)) {
            if (name in beanDefinitions) {
                return (AbstractBeanDefinition) beanDefinitions[name]
            }
            if (name in beanConfigs) {
                return beanConfigs[name].beanDefinition
            }
        }
        return null
    }

    void registerPostProcessor(BeanFactoryPostProcessor processor) {
        initialiseApplicationContext()
        context.addBeanFactoryPostProcessor(processor)
    }

    List<String> getBeanNames() {
        return Collections.unmodifiableList(new ArrayList<>(beanNames))
    }

    void registerBeansWithContext(GenericApplicationContext applicationContext) {
        registerBeansWithRegistry(applicationContext)
    }

    void registerBeansWithRegistry(BeanDefinitionRegistry registry) {
        registerUnrefreshedBeansWithRegistry(registry)
        registerBeanConfigsWithRegistry(registry)
        registerBeanDefinitionsWithRegistry(registry)
        registerBeanAliasesWithRegistry(registry)
    }

    private void registerUnrefreshedBeansWithRegistry(BeanDefinitionRegistry registry) {
        if (context != null) {
            for (String beanName in context.beanDefinitionNames) {
                register(registry, beanName, context.getBeanDefinition(beanName))
            }
        }
    }

    /**
     * Registers a definition, leaving in place one that was generated ahead of time.
     *
     * <p>Running on generated artifacts, the plugins that produced these definitions already ran:
     * they ran while the artifacts were being generated, and what they registered was written out as
     * code and registered again here, ahead of this phase. Registering over that discards the
     * instance supplier the generator wrote -- which resolves the constructor, the fields and the
     * injection methods up front -- and replaces it with a definition that has to find all of that
     * by reflection, which is what a generated image does not carry. So the generated one stands and
     * this one is dropped.</p>
     *
     * <p>Anything the generator did not produce a definition for is registered as usual, so a bean a
     * plugin contributes conditionally is unaffected. On a normal start nothing is skipped, and a
     * plugin overrides what came before it exactly as it did.</p>
     */
    private void register(BeanDefinitionRegistry registry, String beanName, BeanDefinition definition) {
        if (AotDetector.useGeneratedArtifacts() && registry.containsBeanDefinition(beanName)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug('[RuntimeConfiguration] Keeping the generated definition of bean [' + beanName + ']')
            }
            return
        }
        registry.registerBeanDefinition(beanName, definition)
    }

    private void registerBeanConfigsWithRegistry(BeanDefinitionRegistry registry) {
        for (BeanConfiguration bc in beanConfigs.values()) {
            String beanName = bc.name
            if (LOG.isDebugEnabled()) {
                LOG.debug('[RuntimeConfiguration] Registering bean [' + beanName + ']')
                if (LOG.isTraceEnabled()) {
                    PropertyValue[] pvs = bc.beanDefinition
                                            .propertyValues
                                            .getPropertyValues()
                    for (PropertyValue pv in pvs) {
                        LOG.trace('[RuntimeConfiguration] With property [' + pv.name + '] set to [' + pv.value + ']')
                    }
                }
            }

            register(registry, beanName, bc.beanDefinition)
        }
    }

    private void registerBeanDefinitionsWithRegistry(BeanDefinitionRegistry registry) {
        beanDefinitions.each { String beanName, BeanDefinition bd ->
            if (LOG.isDebugEnabled()) {
                LOG.debug('[RuntimeConfiguration] Registering bean [' + beanName + ']')
                if (LOG.isTraceEnabled()) {
                    PropertyValue[] pvs = bd.propertyValues.getPropertyValues()
                    for (PropertyValue pv in pvs) {
                        LOG.trace('[RuntimeConfiguration] With property [' + pv.name + '] set to [' + pv.value + ']')
                    }
                }
            }
            register(registry, beanName, bd)
        }
    }

    void registerBeansWithConfig(RuntimeSpringConfiguration targetSpringConfig) {
        if (targetSpringConfig == null) {
            return
        }

        ApplicationContext ctx = targetSpringConfig.unrefreshedApplicationContext
        if (ctx instanceof BeanDefinitionRegistry) {
            registerUnrefreshedBeansWithRegistry(ctx)
            registerBeansWithRegistry(ctx)
        }
        beanConfigs.each { String beanName, BeanConfiguration beanConfiguration ->
            targetSpringConfig.addBeanConfiguration(beanName, beanConfiguration)
        }
    }

    private void registerBeanAliasesWithRegistry(BeanDefinitionRegistry beanDefinitionRegistry) {
        aliases.each { String beanName, List<String> beanAliases ->
            if (beanAliases) {
                for (String alias in beanAliases) {
                    beanDefinitionRegistry.registerAlias(beanName, alias)
                }
            }
        }
    }

    private void removeBeanDefinition(BeanDefinitionRegistry registry, String beanName) {
        MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(registry.getClass())
        if (!mc.respondsTo(registry, 'removeBeanDefinition').isEmpty()) {
            mc.invokeMethod(registry, 'removeBeanDefinition', [beanName] as Object[])
        }
    }

    BeanConfiguration addAbstractBean(String name) {
        BeanConfiguration bc = new DefaultBeanConfiguration(name)
        bc.setAbstract(true)
        registerBeanConfiguration(name, bc)
        return bc
    }

    void addAlias(String alias, String beanName) {
        List<String> beanAliases = aliases[beanName]
        if (beanAliases == null) {
            beanAliases = []
            aliases.put(beanName, beanAliases)
        }
        beanAliases << alias
    }

    BeanDefinition getBeanDefinition(String beanName) {
        return beanDefinitions[beanName]
    }

    void setBeanFactory(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory
    }

}
