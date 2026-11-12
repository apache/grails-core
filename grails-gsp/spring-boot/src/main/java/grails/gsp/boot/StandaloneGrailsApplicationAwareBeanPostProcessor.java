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
package grails.gsp.boot;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

import grails.core.GrailsApplication;
import grails.core.support.GrailsApplicationAware;
import grails.core.support.GrailsConfigurationAware;
import org.grails.spring.beans.GrailsApplicationAwareBeanPostProcessor;

/**
 * Hands the {@link GrailsApplication} of the context to the beans that expect it, which for GSP are
 * the page locator, the tag library lookup and the JSP tag library resolver: they read the views to
 * search, the tag libraries to register and the tag library descriptors to scan from it.
 *
 * <p>A Grails application has this from its core plugin. A Spring Boot application rendering views
 * with GSP runs no plugins, so the GSP auto-configuration contributes it, and the beans behave the
 * same either way.
 *
 * <p>The application is resolved per bean rather than taken as a constructor argument: a bean post
 * processor is created ahead of the beans it processes, and depending on the application there
 * would pull it, and everything it depends on, into that early round.
 *
 * @since 8.0
 */
class StandaloneGrailsApplicationAwareBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<GrailsApplication> grailsApplication;

    StandaloneGrailsApplicationAwareBeanPostProcessor(ObjectProvider<GrailsApplication> grailsApplication) {
        this.grailsApplication = grailsApplication;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof GrailsApplicationAware || bean instanceof GrailsConfigurationAware) {
            GrailsApplication application = this.grailsApplication.getIfAvailable();
            if (application != null) {
                GrailsApplicationAwareBeanPostProcessor.processAwareInterfaces(application, bean);
            }
        }
        return bean;
    }
}
