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
package org.grails.core

import groovy.transform.CompileStatic
import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanClassLoaderAware
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationEvent
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.SmartApplicationListener
import org.springframework.core.Ordered
import org.springframework.util.ClassUtils

import grails.config.Config
import grails.core.ArtefactHandler
import grails.core.GrailsApplication
import grails.core.support.GrailsConfigurationAware
import grails.util.Environment
import grails.util.Holders
import grails.util.Metadata
import org.grails.config.PropertySourcesConfig

@CompileStatic
abstract class AbstractGrailsApplication extends GroovyObjectSupport implements GrailsApplication, ApplicationContextAware, BeanClassLoaderAware, SmartApplicationListener {

    protected ClassLoader classLoader
    protected Config config

    @SuppressWarnings('rawtypes')
    protected ApplicationContext parentContext
    protected Metadata applicationMeta = Metadata.getCurrent()
    protected boolean contextInitialized

    @Override
    void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.parentContext = applicationContext
        if (applicationContext instanceof ConfigurableApplicationContext) {
            ((ConfigurableApplicationContext) applicationContext).addApplicationListener(this)
        }
    }

    @Override
    Metadata getMetadata() {
        return applicationMeta
    }

    @Override
    boolean isWarDeployed() {
        return Environment.isWarDeployed()
    }

    Config getConfig() {
        return config
    }

    void setConfig(Config config) {
        this.config = config
        Holders.setConfig(config)
    }

    void setConfig(ConfigObject config) {
        this.config = new PropertySourcesConfig().merge(config)
        Holders.setConfig(this.config)
    }

    @Override
    void configChanged() {
        final ArtefactHandler[] handlers = getArtefactHandlers()
        if (handlers != null) {
            for (ArtefactHandler handler in handlers) {
                if (handler instanceof GrailsConfigurationAware) {
                    ((GrailsConfigurationAware) handler).setConfiguration(config)
                }
            }
        }
    }

    @Override
    void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader
    }

    @Override
    ClassLoader getClassLoader() {
        return classLoader
    }

    @SuppressWarnings('rawtypes')
    @Override
    Class getClassForName(String className) {
        return ClassUtils.resolveClassName(className, getClassLoader())
    }

    ApplicationContext getMainContext() {
        return parentContext
    }

    void setMainContext(ApplicationContext context) {
        this.parentContext = context
    }

    ApplicationContext getParentContext() {
        return parentContext
    }

    @Override
    void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            this.contextInitialized = true
        }
    }

    @Override
    boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return ContextRefreshedEvent.isAssignableFrom(eventType)
    }

    @Override
    boolean supportsSourceType(Class<?> sourceType) {
        return true
    }

    @Override
    int getOrder() {
        return Ordered.LOWEST_PRECEDENCE
    }

}
