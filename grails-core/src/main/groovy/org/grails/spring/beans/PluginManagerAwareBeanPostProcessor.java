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
package org.grails.spring.beans;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;

import grails.plugins.GrailsPluginManager;
import grails.plugins.PluginManagerAware;

/**
 * Auto-injects beans that implement PluginManagerAware.
 *
 * @author Graeme Rocher
 * @since 1.2
 */
public class PluginManagerAwareBeanPostProcessor extends BeanPostProcessorAdapter implements BeanFactoryAware {

    private GrailsPluginManager pluginManager;
    private BeanFactory beanFactory;

    public PluginManagerAwareBeanPostProcessor() {

    }

    public PluginManagerAwareBeanPostProcessor(GrailsPluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if(pluginManager == null) {
            if(beanFactory.containsBean(GrailsPluginManager.BEAN_NAME)) {
                pluginManager = beanFactory.getBean(GrailsPluginManager.BEAN_NAME, GrailsPluginManager.class);
            }
        }
        if(pluginManager != null) {

            if (bean instanceof PluginManagerAware) {
                ((PluginManagerAware)bean).setPluginManager(pluginManager);
            }
        }

        return bean;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }


}
