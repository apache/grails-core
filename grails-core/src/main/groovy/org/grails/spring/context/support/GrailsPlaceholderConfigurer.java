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
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.util.StringValueResolver;

import grails.config.Config;
import grails.core.support.GrailsConfigurationAware;

/**
 * Uses Grails' ConfigObject for place holder values.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class GrailsPlaceholderConfigurer extends PropertySourcesPlaceholderConfigurer implements GrailsConfigurationAware {


    private Properties properties;
    private String beanName;
    private BeanFactory beanFactory;
    private Config config;

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
        if (config != null) {
            props.putAll(config.toProperties());
        }
        else if(this.properties != null) {
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
    protected void doProcessProperties(ConfigurableListableBeanFactory beanFactoryToProcess, StringValueResolver valueResolver) {
        BeanDefinitionVisitor visitor = new BeanDefinitionVisitor(valueResolver) {
            @Override
            protected void visitMap(Map<?, ?> mapVal) {
                if(mapVal instanceof Config) return;
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

    @Override
    public void setConfiguration(Config co) {
        this.config = co;
    }
}
