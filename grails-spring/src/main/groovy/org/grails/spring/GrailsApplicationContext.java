/*
 * Copyright 2004-2005 Graeme Rocher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.spring;

import groovy.lang.GroovyObject;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.ui.context.Theme;
import org.springframework.ui.context.ThemeSource;
import org.springframework.ui.context.support.UiApplicationContextUtils;

/**
 * An ApplicationContext that extends StaticApplicationContext and implements GroovyObject such that
 * beans can be retrieved with the dot de-reference syntax instead of using getBean('name').
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class GrailsApplicationContext extends GenericApplicationContext implements GroovyObject {

    protected MetaClass metaClass;
    private BeanWrapper ctxBean = new BeanWrapperImpl(this);
    private ThemeSource themeSource;
    private static final String GRAILS_ENVIRONMENT_BEAN_NAME = "springEnvironment";

    public GrailsApplicationContext(DefaultListableBeanFactory defaultListableBeanFactory) {
        super(defaultListableBeanFactory);
        metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(getClass());
    }

    public GrailsApplicationContext(DefaultListableBeanFactory defaultListableBeanFactory, ApplicationContext applicationContext) {
        super(defaultListableBeanFactory, applicationContext);
        metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(getClass());
    }

    public GrailsApplicationContext(org.springframework.context.ApplicationContext parent) throws org.springframework.beans.BeansException {
        super(parent);
        metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(getClass());
    }

    public GrailsApplicationContext() throws org.springframework.beans.BeansException {
        metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(getClass());
    }

    @Override
    public boolean containsBeanDefinition(String beanName) {
        return super.containsBeanDefinition(beanName);
    }

    public MetaClass getMetaClass() {
        return metaClass;
    }

    public Object getProperty(String property) {
        if (containsBean(property)) {
            return getBean(property);
        }
        if (ctxBean.isReadableProperty(property)) {
            return ctxBean.getPropertyValue(property);
        }
        return null;
    }

    public Object invokeMethod(String name, Object args) {
        return metaClass.invokeMethod(this, name, args);
    }

    public void setMetaClass(MetaClass metaClass) {
        this.metaClass = metaClass;
    }

    /**
     * Initialize the theme capability.
     */
    @Override
    protected void onRefresh() {
        themeSource = UiApplicationContextUtils.initThemeSource(this);
    }

    public Theme getTheme(String themeName) {
        return themeSource.getTheme(themeName);
    }

    public void setProperty(String property, Object newValue) {
        if (newValue instanceof BeanDefinition) {
            if (containsBean(property)) {
                removeBeanDefinition(property);
            }

            registerBeanDefinition(property, (BeanDefinition)newValue);
        }
        else {
            metaClass.setProperty(this, property, newValue);
        }
    }

    /**
     * Register a singleton bean with the underlying bean factory.
     * <p>For more advanced needs, register with the underlying BeanFactory directly.
     * @see #getDefaultListableBeanFactory
     */
    public void registerSingleton(String name, Class<?> clazz) throws BeansException {
        GenericBeanDefinition bd = new GenericBeanDefinition();
        bd.setBeanClass(clazz);
        getDefaultListableBeanFactory().registerBeanDefinition(name, bd);
    }

    /**
     * Register a singleton bean with the underlying bean factory.
     * <p>For more advanced needs, register with the underlying BeanFactory directly.
     * @see #getDefaultListableBeanFactory
     */
    public void registerSingleton(String name, Class<?> clazz, MutablePropertyValues pvs) throws BeansException {
        GenericBeanDefinition bd = new GenericBeanDefinition();
        bd.setBeanClass(clazz);
        bd.setPropertyValues(pvs);
        getDefaultListableBeanFactory().registerBeanDefinition(name, bd);
    }

    /**
     * Register a prototype bean with the underlying bean factory.
     * <p>For more advanced needs, register with the underlying BeanFactory directly.
     * @see #getDefaultListableBeanFactory
     */
    public void registerPrototype(String name, Class<?> clazz) throws BeansException {
        GenericBeanDefinition bd = new GenericBeanDefinition();
        bd.setScope(GenericBeanDefinition.SCOPE_PROTOTYPE);
        bd.setBeanClass(clazz);
        getDefaultListableBeanFactory().registerBeanDefinition(name, bd);
    }

    /**
     * Register a prototype bean with the underlying bean factory.
     * <p>For more advanced needs, register with the underlying BeanFactory directly.
     * @see #getDefaultListableBeanFactory
     */
    public void registerPrototype(String name, Class<?> clazz, MutablePropertyValues pvs) throws BeansException {
        GenericBeanDefinition bd = new GenericBeanDefinition();
        bd.setScope(GenericBeanDefinition.SCOPE_PROTOTYPE);
        bd.setBeanClass(clazz);
        bd.setPropertyValues(pvs);
        getDefaultListableBeanFactory().registerBeanDefinition(name, bd);
    }

    @Override
    protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        super.prepareBeanFactory(beanFactory);

        // workaround for GRAILS-7851, until Spring allows the environment bean name to be configurable
        ((DefaultListableBeanFactory)beanFactory).destroySingleton(ENVIRONMENT_BEAN_NAME);
        beanFactory.registerSingleton(GRAILS_ENVIRONMENT_BEAN_NAME,getEnvironment());
    }

    @Override
    protected void assertBeanFactoryActive() {
        // no-op to prevent excessive synchronization caused by SPR-10307 change
    }
}
