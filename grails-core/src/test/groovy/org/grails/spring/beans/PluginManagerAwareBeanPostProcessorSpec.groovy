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
package org.grails.spring.beans

import grails.plugins.GrailsPluginManager
import grails.plugins.PluginManagerAware
import org.springframework.beans.factory.BeanFactory
import spock.lang.Specification

class PluginManagerAwareBeanPostProcessorSpec extends Specification {

    void 'a PluginManagerAware bean is injected with the plugin manager supplied at construction'() {
        given:
        GrailsPluginManager pluginManager = Stub(GrailsPluginManager)
        PluginManagerAwareBeanPostProcessor processor = new PluginManagerAwareBeanPostProcessor(pluginManager)
        PluginAwareBean bean = new PluginAwareBean()

        when:
        Object result = processor.postProcessBeforeInitialization(bean, 'awareBean')

        then:
        result.is(bean)
        bean.pluginManager.is(pluginManager)
    }

    void 'with no plugin manager supplied, it is looked up lazily from the bean factory'() {
        given:
        GrailsPluginManager pluginManager = Stub(GrailsPluginManager)
        BeanFactory beanFactory = Stub(BeanFactory) {
            containsBean(GrailsPluginManager.BEAN_NAME) >> true
            getBean(GrailsPluginManager.BEAN_NAME, GrailsPluginManager) >> pluginManager
        }
        PluginManagerAwareBeanPostProcessor processor = new PluginManagerAwareBeanPostProcessor()
        processor.setBeanFactory(beanFactory)
        PluginAwareBean bean = new PluginAwareBean()

        when:
        processor.postProcessBeforeInitialization(bean, 'awareBean')

        then:
        bean.pluginManager.is(pluginManager)
    }

    void 'a bean is returned unchanged when no plugin manager is available anywhere'() {
        given:
        BeanFactory beanFactory = Stub(BeanFactory) {
            containsBean(GrailsPluginManager.BEAN_NAME) >> false
        }
        PluginManagerAwareBeanPostProcessor processor = new PluginManagerAwareBeanPostProcessor()
        processor.setBeanFactory(beanFactory)
        PluginAwareBean bean = new PluginAwareBean()

        when:
        Object result = processor.postProcessBeforeInitialization(bean, 'awareBean')

        then:
        result.is(bean)
        bean.pluginManager == null
    }

}

class PluginAwareBean implements PluginManagerAware {

    GrailsPluginManager pluginManager

    @Override
    void setPluginManager(GrailsPluginManager pluginManager) {
        this.pluginManager = pluginManager
    }

}
