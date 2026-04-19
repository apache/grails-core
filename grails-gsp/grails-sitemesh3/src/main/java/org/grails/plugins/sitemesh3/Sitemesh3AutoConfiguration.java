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

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers a {@link GrailsSiteMeshViewResolverBeanPostProcessor} ahead of
 * the upstream auto-configuration. Upstream's
 * {@code SiteMeshViewResolverAutoConfiguration} declares its post-processor
 * bean with {@code @ConditionalOnMissingBean(SiteMeshViewResolverBeanPostProcessor.class)};
 * by scheduling this config first (via {@link AutoConfigureBefore}), our
 * Grails-specific subclass is picked up and the default is suppressed.
 */
@AutoConfiguration
@AutoConfigureBefore(name = "org.sitemesh.autoconfigure.SiteMeshViewResolverAutoConfiguration")
@ConditionalOnClass(SiteMeshViewResolverBeanPostProcessor.class)
@ConditionalOnProperty(name = "sitemesh.integration", havingValue = "view-resolver")
public class Sitemesh3AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SiteMeshViewResolverBeanPostProcessor.class)
    public GrailsSiteMeshViewResolverBeanPostProcessor siteMeshViewResolverBeanPostProcessor() {
        return new GrailsSiteMeshViewResolverBeanPostProcessor();
    }
}
