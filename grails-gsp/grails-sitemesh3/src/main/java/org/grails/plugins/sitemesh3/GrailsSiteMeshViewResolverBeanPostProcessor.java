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
package org.grails.plugins.sitemesh3;

import org.sitemesh.webmvc.SiteMeshViewResolverBeanPostProcessor;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationListener;

/**
 * {@link SiteMeshViewResolverBeanPostProcessor} preconfigured to wrap
 * Grails' {@code gspViewResolver} bean with a
 * {@link GrailsSiteMeshViewResolver}.
 */
public class GrailsSiteMeshViewResolverBeanPostProcessor extends SiteMeshViewResolverBeanPostProcessor {

    /**
     * The primary GSP view resolver bean name in Grails. The historical
     * name {@code jspViewResolver} is kept for compatibility with the
     * plugin's {@code GroovyPagesPostProcessor}, which registers the GSP
     * resolver under that name when it isn't already present (see
     * {@code org.grails.plugins.web.GroovyPagesPostProcessor}). The
     * modern {@code GspAutoConfiguration.gspViewResolver()} bean is
     * aliased to this name too when it fires.
     */
    public static final String TARGET_VIEW_RESOLVER_BEAN_NAME = "jspViewResolver";

    public GrailsSiteMeshViewResolverBeanPostProcessor() {
        setTargetViewResolverBeanName(TARGET_VIEW_RESOLVER_BEAN_NAME);
        setSiteMeshViewResolverClass(GrailsSiteMeshViewResolver.class);
    }

    /**
     * Contexts can include this post-processor (via {@code Sitemesh3AutoConfiguration})
     * without the SiteMesh plugin beans being registered — the unit-test context built
     * by grails-testing-support registers all Grails auto-configurations but never runs
     * the plugin's {@code doWithSpring}. Decoration is impossible without those beans,
     * so leave the view resolver unwrapped instead of failing the context.
     *
     * <p>Resolvers implementing {@link ApplicationListener} are also left unwrapped.
     * When the legacy grails-layout module is on the classpath its post-processor
     * installs the SiteMesh 2 {@code GrailsLayoutViewResolver} (an
     * {@code ApplicationListener}) as {@code jspViewResolver}; that resolver already
     * decorates, and wrapping it would additionally break Spring's event listener
     * retrieval, which expects the instance to match the definition's listener type.
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        BeanFactory beanFactory = getBeanFactory();
        if (beanFactory == null ||
                !beanFactory.containsBean(getContentProcessorBeanName()) ||
                !beanFactory.containsBean(getDecoratorSelectorBeanName()) ||
                bean instanceof ApplicationListener) {
            return bean;
        }
        return super.postProcessAfterInitialization(bean, beanName);
    }
}
