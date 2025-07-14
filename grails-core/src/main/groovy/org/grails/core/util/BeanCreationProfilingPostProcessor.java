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
package org.grails.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Adds timings of bean creation times logged to the "org.grails.startup" group
 *
 * @author Graeme Rocher
 * @since 3.0.12
 */
public class BeanCreationProfilingPostProcessor implements InstantiationAwareBeanPostProcessor, ApplicationListener<ContextRefreshedEvent> {

    private static final Logger LOG = LoggerFactory.getLogger("org.grails.startup");
    private final StopWatch stopWatch = new StopWatch("Bean Creation StopWatch");

    @Override
    public Object postProcessBeforeInstantiation(@Nullable Class<?> beanClass, @Nullable String beanName) throws BeansException {
        stopWatch.start("Create Bean: " + beanName);
        return null;
    }

    @Override
    public Object postProcessAfterInitialization(@Nullable Object bean, @Nullable String beanName) throws BeansException {
        stopWatch.stop();
        return bean;
    }

    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        stopWatch.complete();
        if (LOG.isDebugEnabled()) {
            LOG.debug(stopWatch.prettyPrint());
        }
    }
}
