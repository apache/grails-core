/*
 * Copyright 2004-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.plugins.core;

import grails.config.ConfigProperties;
import grails.config.Settings;
import grails.core.GrailsApplication;
import org.grails.spring.context.support.GrailsPlaceholderConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.Ordered;

/**
 * Core beans.
 *
 * @author graemerocher
 * @since 4.0
 */
@AutoConfiguration(before = { PropertyPlaceholderAutoConfiguration.class })
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
public class CoreAutoConfiguration {

    @Value("${" + Settings.SPRING_PLACEHOLDER_PREFIX + ":#{null}}")
    private String placeholderPrefix;

    @Bean
    @Primary
    public ClassLoader classLoader(GrailsApplication grailsApplication) {
        return grailsApplication.getClassLoader();
    }

    @Bean
    @Primary
    public ConfigProperties grailsConfigProperties(GrailsApplication grailsApplication) {
        return new ConfigProperties(grailsApplication.getConfig());
    }

    @Bean
    @Primary
    PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        GrailsPlaceholderConfigurer grailsPlaceholderConfigurer = new GrailsPlaceholderConfigurer();
        if (placeholderPrefix != null) {
            grailsPlaceholderConfigurer.setPlaceholderPrefix(placeholderPrefix);
        }
        return grailsPlaceholderConfigurer;
    }
}
