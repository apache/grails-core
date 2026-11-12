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
package org.grails.spring.aop.autoproxy;

import java.lang.reflect.Field;
import java.util.List;

import org.slf4j.LoggerFactory;

import org.springframework.aop.config.AopConfigUtils;

/**
 * Makes the Grails auto-proxy creators known to Spring's {@link AopConfigUtils}.
 *
 * <p>Grails registers {@link GroovyAwareAspectJAwareAdvisorAutoProxyCreator} or
 * {@link GroovyAwareInfrastructureAdvisorAutoProxyCreator} under Spring's reserved
 * {@code org.springframework.aop.config.internalAutoProxyCreator} bean name. Anything that later asks
 * Spring to register or escalate the auto-proxy creator - Spring Boot's {@code AopAutoConfiguration},
 * {@code @EnableAspectJAutoProxy}, the {@code <aop:config>} namespace - looks the already-registered bean
 * class up in Spring's priority list and rejects a class it does not know with
 * {@code "Class name [...] is not a known auto-proxy creator class"}.
 *
 * <p>Appending the Grails creators to that list makes them known, and ranks them above every creator
 * Spring ships, so an escalation attempt leaves the Grails creator in place instead of replacing it
 * with one that does not proxy Groovy classes.
 *
 * @since 8.0
 */
public final class GroovyAwareAutoProxyCreators {

    private static final String APC_PRIORITY_LIST_FIELD = "APC_PRIORITY_LIST";

    private static final List<Class<?>> CREATORS = List.of(
            GroovyAwareInfrastructureAdvisorAutoProxyCreator.class,
            GroovyAwareAspectJAwareAdvisorAutoProxyCreator.class);

    private GroovyAwareAutoProxyCreators() {
    }

    /**
     * Adds the Grails auto-proxy creators to Spring's auto-proxy creator priority list. Must be called
     * before the Grails creator is registered under Spring's auto-proxy creator bean name, and is
     * idempotent, so every registration site can call it.
     */
    public static void registerWithAopConfigUtils() {
        try {
            Field field = AopConfigUtils.class.getDeclaredField(APC_PRIORITY_LIST_FIELD);
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Class<?>> priorityList = (List<Class<?>>) field.get(null);
            for (Class<?> creator : CREATORS) {
                if (!priorityList.contains(creator)) {
                    priorityList.add(creator);
                }
            }
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // The logger is resolved here rather than held in a static field because this runs from the
            // static initializer of GrailsAutoConfiguration, which must not initialize logging.
            LoggerFactory.getLogger(GroovyAwareAutoProxyCreators.class).warn(
                    "Unable to make the Grails auto-proxy creators known to Spring's [{}]. Spring AOP " +
                            "configuration that registers or escalates the auto-proxy creator will fail " +
                            "with 'is not a known auto-proxy creator class'", AopConfigUtils.class.getName(), e);
        }
    }

}
