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
package org.grails.web.mime

import java.util.function.Supplier

import groovy.transform.CompileStatic

import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.web.accept.ContentNegotiationManager

import grails.config.Config
import grails.config.Settings

/**
 * With {@code grails.mime.negotiation.spring}, replaces Spring MVC's content negotiation manager with one that applies
 * the {@code grails.mime} rules Grails keeps to the requests a URL mapping handles (see
 * {@link GrailsContentNegotiationManager}), so that what Spring MVC writes for them, such as a {@code ResponseEntity} or
 * a {@code ProblemDetail} an action returns or {@code respond} writes, is negotiated as Grails resolves their formats.
 * Without it, Spring MVC's manager is left as it is. The setting is read from the application's configuration, which
 * the strategy that resolves Grails formats reads it from too, so that the two never disagree.
 *
 * @since 9.0
 */
@CompileStatic
class GrailsContentNegotiationManagerPostProcessor implements BeanPostProcessor {

    private final Supplier<Config> config

    private final Supplier<GrailsContentNegotiationStrategy> grailsStrategy

    /**
     * @param config supplies the application's configuration, which the setting is read from as Spring MVC's manager is
     *        created, since a post-processor is created before the other beans
     * @param grailsStrategy supplies the strategy that resolves Grails formats, which Spring MVC's manager is created
     *        before
     */
    GrailsContentNegotiationManagerPostProcessor(Supplier<Config> config,
                                                Supplier<GrailsContentNegotiationStrategy> grailsStrategy) {
        this.config = config
        this.grailsStrategy = grailsStrategy
    }

    @Override
    Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (beanName == GrailsContentNegotiationStrategy.MVC_CONTENT_NEGOTIATION_MANAGER &&
                bean instanceof ContentNegotiationManager && !(bean instanceof GrailsContentNegotiationManager) &&
                config.get().getProperty(Settings.MIME_NEGOTIATION_SPRING, Boolean,
                        Boolean.valueOf(Settings.MIME_NEGOTIATION_SPRING_DEFAULT))) {
            return new GrailsContentNegotiationManager((ContentNegotiationManager) bean, grailsStrategy)
        }
        return bean
    }
}
