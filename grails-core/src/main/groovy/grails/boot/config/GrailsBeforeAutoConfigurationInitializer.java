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
package grails.boot.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Registers the {@link GrailsBeforeAutoConfigurationPostProcessor} on the context via
 * {@code addBeanFactoryPostProcessor}, which guarantees it runs ahead of Spring Boot's
 * registry-discovered post-processors (including {@code ConfigurationClassPostProcessor},
 * which expands auto-configuration). That ordering is what lets plugin beans contributed in the
 * {@code doWithSpringBeforeAutoConfiguration} phase be present before Boot's
 * {@code @ConditionalOnMissingBean} guards are evaluated.
 *
 * @since 8.0
 */
public class GrailsBeforeAutoConfigurationInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        applicationContext.addBeanFactoryPostProcessor(new GrailsBeforeAutoConfigurationPostProcessor(applicationContext));
    }
}
