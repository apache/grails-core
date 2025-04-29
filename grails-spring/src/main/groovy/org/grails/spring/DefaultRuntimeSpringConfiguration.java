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
package org.grails.spring;

import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.Assert;

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
public class DefaultRuntimeSpringConfiguration implements RuntimeSpringConfiguration {

    private static final Log LOG = LogFactory.getLog(DefaultRuntimeSpringConfiguration.class);
    protected GenericApplicationContext context;
    private Map<String, BeanConfiguration> beanConfigs = new HashMap<String, BeanConfiguration>();
    private Map<String, BeanDefinition> beanDefinitions = new HashMap<String, BeanDefinition>();
    private Set<String> beanNames = new LinkedHashSet<String>();
    protected ApplicationContext parent;
    protected ClassLoader classLoader;
    protected Map<String, List<String>> aliases = new HashMap<String, List<String>>();
    protected ListableBeanFactory beanFactory;

    /**
     * Creates the ApplicationContext instance. Subclasses can override to customise the used ApplicationContext
     *
     * @param parentCtx The parent ApplicationContext instance. Can be null.
     *
     * @return An instance of GenericApplicationContext
     */
    protected GenericApplicationContext createApplicationContext(ApplicationContext parentCtx) {
        if (parentCtx != null && beanFactory != null) {
            Assert.isInstanceOf(DefaultListableBeanFactory.class, beanFactory,
                    "ListableBeanFactory set must be a subclass of DefaultListableBeanFactory");

            return new GrailsApplicationContext((DefaultListableBeanFactory) beanFactory,parentCtx);
        }

        if (beanFactory != null) {
            Assert.isInstanceOf(DefaultListableBeanFactory.class, beanFactory,
                    "ListableBeanFactory set must be a subclass of DefaultListableBeanFactory");

            return new GrailsApplicationContext((DefaultListableBeanFactory) beanFactory);
        }

        if (parentCtx != null) {
            return new GrailsApplicationContext(parentCtx);
        }

        return new GrailsApplicationContext();
    }

    public DefaultRuntimeSpringConfiguration() {
        super();
    }

    public DefaultRuntimeSpringConfiguration(ApplicationContext parent) {
        this(parent, null);
    }

    public DefaultRuntimeSpringConfiguration(ApplicationContext parent, ClassLoader cl) {
        this.parent = parent;
        classLoader = cl;
    }

    private void trySettingClassLoaderOnContextIfFoundInParent(ApplicationContext parentCtx) {
        if (parentCtx.containsBean("classLoader")) {
            Object cl = parentCtx.getBean("classLoader");
            if (cl instanceof ClassLoader) {
                setClassLoaderOnContext((ClassLoader)cl);
            }
        }
    }

    private void setClassLoaderOnContext(ClassLoader cl) {
        context.setClassLoader(cl);
        context.getBeanFactory().setBeanClassLoader(cl);
    }

    /**
     * Initialises the ApplicationContext instance.
     */
    protected void initialiseApplicationContext() {
        if (context != null) {
            return;
        }

        context = createApplicationContext(parent);

        if (parent != null && classLoader == null) {
            trySettingClassLoaderOnContextIfFoundInParent(parent);
        }
        else if (classLoader != null) {
            setClassLoaderOnContext(classLoader);
        }

        Assert.notNull(context, "ApplicationContext cannot be null");
    }

    public BeanConfiguration addSingletonBean(String name, @SuppressWarnings("rawtypes") Class clazz) {
        BeanConfiguration bc = new DefaultBeanConfiguration(name,clazz);
        registerBeanConfiguration(name, bc);
        return bc;
    }

    public BeanConfiguration addPrototypeBean(String name, @SuppressWarnings("rawtypes") Class clazz) {
        BeanConfiguration bc = new DefaultBeanConfiguration(name,clazz,true);
        registerBeanConfiguration(name, bc);
        return bc;
    }

    public ApplicationContext getApplicationContext() {
        long now = LOG.isDebugEnabled() ? System.currentTimeMillis() : 0;
        initialiseApplicationContext();
        registerBeansWithContext(context);
        context.refresh();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Created ApplicationContext in " + (System.currentTimeMillis() - now) + "ms");
        }
        return context;
    }

    public ApplicationContext getUnrefreshedApplicationContext() {
        initialiseApplicationContext();
        return context;
    }

    public BeanConfiguration addSingletonBean(String name) {
        BeanConfiguration bc = new DefaultBeanConfiguration(name);
        registerBeanConfiguration(name, bc);
        return bc;
    }

    public BeanConfiguration createSingletonBean(@SuppressWarnings("rawtypes") Class clazz) {
        return new DefaultBeanConfiguration(clazz);
    }

    @SuppressWarnings("rawtypes")
    public BeanConfiguration addSingletonBean(String name, Class clazz, Collection args) {
        BeanConfiguration bc = new DefaultBeanConfiguration(name,clazz,args);
        registerBeanConfiguration(name, bc);
        return bc;
    }

    public BeanConfiguration addPrototypeBean(String name) {
        BeanConfiguration bc = new DefaultBeanConfiguration(name,true);
        registerBeanConfiguration(name, bc);
        return bc;
    }

    private void registerBeanConfiguration(String name, BeanConfiguration bc) {
        beanConfigs.put(name,bc);
        beanNames.add(name);
    }

    @SuppressWarnings("rawtypes")
    public BeanConfiguration createSingletonBean(Class clazz, Collection constructorArguments) {
        return new DefaultBeanConfiguration(clazz, constructorArguments);
    }

    public BeanConfiguration createPrototypeBean(String name) {
        return new DefaultBeanConfiguration(name,true);
    }

    public BeanConfiguration createSingletonBean(String name) {
        return new DefaultBeanConfiguration(name);
    }

    public void addBeanConfiguration(String beanName, BeanConfiguration beanConfiguration) {
        beanConfiguration.setName(beanName);
        registerBeanConfiguration(beanName, beanConfiguration);
    }

    public void addBeanDefinition(String name, BeanDefinition bd) {
        beanDefinitions.put(name,bd);
        beanConfigs.remove(name);
        beanNames.add(name);
    }

    public boolean containsBean(String name) {
        return beanNames.contains(name);
    }

    public BeanConfiguration getBeanConfig(String name) {
        return beanConfigs.get(name);
    }

    public AbstractBeanDefinition createBeanDefinition(String name) {
        if (containsBean(name)) {
            if (beanDefinitions.containsKey(name)) {
                return (AbstractBeanDefinition)beanDefinitions.get(name);
            }
            if (beanConfigs.containsKey(name)) {
                return beanConfigs.get(name).getBeanDefinition();
            }
        }
        return null;
    }

    public void registerPostProcessor(BeanFactoryPostProcessor processor) {
        initialiseApplicationContext();
        context.addBeanFactoryPostProcessor(processor);
    }

    public List<String> getBeanNames() {
        return Collections.unmodifiableList(new ArrayList<String>(beanNames));
    }

    public void registerBeansWithContext(GenericApplicationContext applicationContext) {
        registerBeansWithRegistry(applicationContext);
    }

    public void registerBeansWithRegistry(BeanDefinitionRegistry registry) {
        registerUnrefreshedBeansWithRegistry(registry);
        registerBeanConfigsWithRegistry(registry);
        registerBeanDefinitionsWithRegistry(registry);
        registerBeanAliasesWithRegistry(registry);
    }

    private void registerUnrefreshedBeansWithRegistry(BeanDefinitionRegistry registry) {
        if (context != null) {
            for (String beanName : context.getBeanDefinitionNames()) {
                registry.registerBeanDefinition(beanName, context.getBeanDefinition(beanName));
            }
        }
    }

    private void registerBeanConfigsWithRegistry(BeanDefinitionRegistry registry) {
        for (BeanConfiguration bc : beanConfigs.values()) {
            String beanName = bc.getName();
            if (LOG.isDebugEnabled()) {
                LOG.debug("[RuntimeConfiguration] Registering bean [" + beanName + "]");
                if (LOG.isTraceEnabled()) {
                    PropertyValue[] pvs = bc.getBeanDefinition()
                                            .getPropertyValues()
                                            .getPropertyValues();
                    for (PropertyValue pv : pvs) {
                        LOG.trace("[RuntimeConfiguration] With property [" + pv.getName() + "] set to [" + pv.getValue() + "]");
                    }
                }
            }

            registry.registerBeanDefinition(beanName, bc.getBeanDefinition());
        }
    }

    private void registerBeanDefinitionsWithRegistry(BeanDefinitionRegistry registry) {
        for (Object key : beanDefinitions.keySet()) {
            BeanDefinition bd = beanDefinitions.get(key);
            if (LOG.isDebugEnabled()) {
                LOG.debug("[RuntimeConfiguration] Registering bean [" + key + "]");
                if (LOG.isTraceEnabled()) {
                    PropertyValue[] pvs = bd.getPropertyValues().getPropertyValues();
                    for (PropertyValue pv : pvs) {
                        LOG.trace("[RuntimeConfiguration] With property [" + pv.getName() + "] set to [" + pv.getValue() + "]");
                    }
                }
            }
            final String beanName = key.toString();
            registry.registerBeanDefinition(beanName, bd);
        }
    }

    public void registerBeansWithConfig(RuntimeSpringConfiguration targetSpringConfig) {
        if (targetSpringConfig == null) {
            return;
        }

        ApplicationContext ctx = targetSpringConfig.getUnrefreshedApplicationContext();
        if (ctx instanceof BeanDefinitionRegistry) {
            final BeanDefinitionRegistry registry = (BeanDefinitionRegistry) ctx;
            registerUnrefreshedBeansWithRegistry(registry);
            registerBeansWithRegistry(registry);
        }
        for (Map.Entry<String, BeanConfiguration> beanEntry : beanConfigs.entrySet()) {
            targetSpringConfig.addBeanConfiguration(beanEntry.getKey(), beanEntry.getValue());
        }
    }

    private void registerBeanAliasesWithRegistry(BeanDefinitionRegistry beanDefinitionRegistry) {
        for (Map.Entry<String, List<String>> entry : aliases.entrySet()) {
            String beanName = entry.getKey();
            List<String> beanAliases = entry.getValue();
            if (beanAliases != null && !beanAliases.isEmpty()) {
                for (String alias : beanAliases) {
                    beanDefinitionRegistry.registerAlias(beanName, alias);
                }
            }
        }
    }

    private void removeBeanDefinition(BeanDefinitionRegistry registry, String beanName) {
        MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(registry.getClass());
        if (!mc.respondsTo(registry, "removeBeanDefinition").isEmpty()) {
            mc.invokeMethod(registry, "removeBeanDefinition", new Object[] { beanName });
        }
    }

    public BeanConfiguration addAbstractBean(String name) {
        BeanConfiguration bc = new DefaultBeanConfiguration(name);
        bc.setAbstract(true);
        registerBeanConfiguration(name, bc);
        return bc;
    }

    public void addAlias(String alias, String beanName) {
        List<String> beanAliases = aliases.get(beanName);
        if (beanAliases == null) {
            beanAliases = new ArrayList<String>();
            aliases.put(beanName, beanAliases);
        }
        beanAliases.add(alias);
    }

    public BeanDefinition getBeanDefinition(String beanName) {
        return beanDefinitions.get(beanName);
    }

    public void setBeanFactory(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }
}
