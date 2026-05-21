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
package grails.boot.config.tools

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext

import grails.boot.config.GrailsApplicationPostProcessor
import grails.core.GrailsApplicationLifeCycle
import grails.plugins.DefaultGrailsPluginManager
import org.apache.grails.core.plugins.PluginDiscovery

/**
 * Profiles bean creation outputting data to the console
 *
 * @author Graeme Rocher
 * @since 3.0
 */
class ProfilingGrailsApplicationPostProcessor extends GrailsApplicationPostProcessor implements BeanPostProcessor {

    private static final Log LOG = LogFactory.getLog(DefaultGrailsPluginManager)

    long startTime

    ProfilingGrailsApplicationPostProcessor(GrailsApplicationLifeCycle lifeCycle, ApplicationContext applicationContext, Class... classes) {
        super(lifeCycle, applicationContext, resolveAndResetDiscovery(applicationContext), classes)
        ((ConfigurableApplicationContext) applicationContext).beanFactory.addBeanPostProcessor(this)
    }

    /**
     * Resolves the {@link PluginDiscovery} bean from the application context,
     * resets and reinitializes it. The GrailsApplication always has an application context
     * set when these deprecated constructors are called.
     */
    private static PluginDiscovery resolveAndResetDiscovery(ApplicationContext ctx) {
        PluginDiscovery discovery = (PluginDiscovery) ctx.getBean(PluginDiscovery.BEAN_NAME)
        LOG.warn('Using deprecated DefaultGrailsPluginManager constructor. ' +
                'Plugin discovery should be configured through the GrailsPluginDiscovery bean. ' +
                'Reinitializing plugin discovery.')
        discovery.reset()
        discovery.init(ctx.getEnvironment())
        discovery
    }

    @Override
    Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        startTime = System.currentTimeMillis()
        return bean
    }

    @Override
    Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        def totalTime = System.currentTimeMillis() - startTime
        if (totalTime > 10) {
            println("Creating bean $beanName of type ${bean.getClass()} took ${totalTime}ms")
        }
        return bean
    }
}
