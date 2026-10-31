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
package org.grails.spring.context.support;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionVisitor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.ConfigurablePropertyResolver;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

import grails.config.Config;

/**
 * Uses Grails' ConfigObject for place holder values.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class GrailsPlaceholderConfigurer extends PropertySourcesPlaceholderConfigurer {

    private static final String CONFIGURATION_LISTS_PROPERTY_SOURCE_NAME = "grailsConfigurationLists";

    private Properties properties;
    private String beanName;
    private BeanFactory beanFactory;
    private Environment environment;

    public GrailsPlaceholderConfigurer(String placeHolderPrefix, Properties properties) {
        this.properties = properties;
        setPlaceholderPrefix(placeHolderPrefix);
        setIgnoreUnresolvablePlaceholders(true);
    }

    public GrailsPlaceholderConfigurer() {
        setIgnoreUnresolvablePlaceholders(true);
    }

    @Override
    protected void loadProperties(Properties props) throws IOException {
        if (this.properties != null) {
            props.putAll(properties);
        }
        this.properties = props;
    }

    public Properties getProperties() {
        return properties;
    }

    @Override
    public void setBeanName(String beanName) {
        super.setBeanName(beanName);
        this.beanName = beanName;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        super.setBeanFactory(beanFactory);
        this.beanFactory = beanFactory;
    }

    @Override
    public void setEnvironment(Environment environment) {
        super.setEnvironment(environment);
        this.environment = environment;
    }

    /**
     * Resolves a placeholder that names a list of plain values, such as {@code ${app.names}}, to the
     * elements of the list joined by commas, when neither the environment nor the local properties have
     * a value under that name.
     *
     * <p>The environment presents a configuration list only under its indexed names, such as
     * {@code app.names[0]}, so that Spring Boot binds it element by element. The list is read as Spring
     * Boot binds it, from the property source of the highest priority that sets it, and the placeholders
     * in its elements are resolved with the rest of the value. The name is matched as
     * {@code @ConfigurationProperties} binding matches it, so {@code ${app.allowedOrigins}} and
     * {@code ${app.allowed-origins}} read the same list. A list holding objects has no value a
     * placeholder could be replaced with, so a placeholder naming it is not resolved.</p>
     *
     * <p>The lists are read only when the environment is one of the configurer's property sources, as
     * it is unless {@link #setPropertySources} selected others. A configurer given its own sources
     * resolves placeholders from those sources alone, as Spring's does.</p>
     */
    @Override
    protected ConfigurablePropertyResolver createPropertyResolver(MutablePropertySources propertySources) {
        if (this.environment instanceof ConfigurableEnvironment configurableEnvironment &&
                propertySources.contains(ENVIRONMENT_PROPERTIES_PROPERTY_SOURCE_NAME) &&
                !propertySources.contains(CONFIGURATION_LISTS_PROPERTY_SOURCE_NAME)) {
            propertySources.addLast(new ConfigurationListPropertySource(configurableEnvironment));
        }
        return super.createPropertyResolver(propertySources);
    }

    @Override
    protected void doProcessProperties(ConfigurableListableBeanFactory beanFactoryToProcess, StringValueResolver valueResolver) {
        BeanDefinitionVisitor visitor = new BeanDefinitionVisitor(valueResolver) {
            @Override
            protected void visitMap(Map<?, ?> mapVal) {
                if (mapVal instanceof Config) return;
                super.visitMap(mapVal);
            }
        };

        String[] beanNames = beanFactoryToProcess.getBeanDefinitionNames();
        for (String curName : beanNames) {
            // Check that we're not parsing our own bean definition,
            // to avoid failing on unresolvable placeholders in properties file locations.
            if (!(curName.equals(this.beanName) && beanFactoryToProcess.equals(this.beanFactory))) {
                BeanDefinition bd = beanFactoryToProcess.getBeanDefinition(curName);
                try {
                    visitor.visitBeanDefinition(bd);
                }
                catch (Exception ex) {
                    throw new BeanDefinitionStoreException(bd.getResourceDescription(), curName, ex.getMessage(), ex);
                }
            }
        }

        // New in Spring 2.5: resolve placeholders in alias target names and aliases as well.
        beanFactoryToProcess.resolveAliases(valueResolver);

        // New in Spring 3.0: resolve placeholders in embedded values such as annotation attributes.
        beanFactoryToProcess.addEmbeddedValueResolver(valueResolver);
    }

    /**
     * Presents each list of plain values in the environment under the name of the list, its elements
     * joined by commas.
     */
    private static final class ConfigurationListPropertySource extends PropertySource<ConfigurableEnvironment> {

        private final Binder binder;

        ConfigurationListPropertySource(ConfigurableEnvironment environment) {
            super(CONFIGURATION_LISTS_PROPERTY_SOURCE_NAME, environment);
            // without a placeholder resolver: the placeholders in the elements are resolved with the joined value
            this.binder = new Binder(ConfigurationPropertySources.get(environment));
        }

        @Override
        public Object getProperty(String name) {
            // adapted rather than validated: a placeholder names the list in any form the binder matches,
            // such as grails.cors.allowedOrigins, which is not in Spring Boot's canonical form
            ConfigurationPropertyName propertyName = ConfigurationPropertyName.adapt(name, '.');
            if (propertyName.isEmpty()) {
                return null;
            }
            try {
                return this.binder.bind(propertyName, Bindable.listOf(String.class))
                        .map(elements -> elements.isEmpty() ? null : StringUtils.collectionToCommaDelimitedString(elements))
                        .orElse(null);
            }
            catch (BindException ex) {
                // a list holding objects, whose elements are not plain values
                return null;
            }
        }
    }
}
