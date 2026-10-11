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
package org.grails.config

import groovy.transform.CompileStatic
import groovy.transform.stc.POJO
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.springframework.core.convert.support.ConfigurableConversionService
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertySource
import org.springframework.core.env.PropertySources
import org.springframework.core.env.PropertySourcesPropertyResolver

import grails.util.GrailsStringUtils

/**
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@POJO
class PropertySourcesConfig extends NavigableMapConfig {

    protected PropertySources propertySources
    protected PropertySourcesPropertyResolver propertySourcesPropertyResolver

    PropertySourcesConfig(PropertySources propertySources) {
        this.propertySources = propertySources
        this.propertySourcesPropertyResolver = new PropertySourcesPropertyResolver(propertySources)
        initializeFromPropertySources(propertySources)
    }

    PropertySourcesConfig() {
        this.propertySources = new MutablePropertySources()
        this.propertySourcesPropertyResolver = new PropertySourcesPropertyResolver(propertySources)
    }

    PropertySourcesConfig(Map<String, Object> mapPropertySource) {
        MutablePropertySources mutablePropertySources = new MutablePropertySources()
        NavigableMap map = new NavigableMap()
        map.merge(mapPropertySource, true)

        mutablePropertySources.addFirst(new MapPropertySource('config', map))
        this.propertySources = mutablePropertySources
        this.propertySourcesPropertyResolver = new PropertySourcesPropertyResolver(propertySources)
        initializeFromPropertySources(propertySources)
    }

    PropertySourcesConfig(PropertySource propertySource) {
        MutablePropertySources mutablePropertySources = new MutablePropertySources()
        mutablePropertySources.addFirst(propertySource)
        this.propertySources = mutablePropertySources
        this.propertySourcesPropertyResolver = new PropertySourcesPropertyResolver(propertySources)
        initializeFromPropertySources(propertySources)
    }

    PropertySources getPropertySources() {
        return propertySources
    }

    void refresh() {
        initializeFromPropertySources(propertySources)
    }

    protected void initializeFromPropertySources(PropertySources propertySources) {

        EnvironmentAwarePropertySource environmentAwarePropertySource = new EnvironmentAwarePropertySource(propertySources)
        if (propertySources instanceof MutablePropertySources) {
            final String applicationConfig = 'applicationConfigurationProperties'
            if (propertySources.contains(applicationConfig)) {
                ((MutablePropertySources) propertySources).addBefore(applicationConfig, environmentAwarePropertySource)
            } else {
                ((MutablePropertySources) propertySources).addLast(environmentAwarePropertySource)
            }
        }

        List<PropertySource<?>> propertySourceList = DefaultGroovyMethods.toList(propertySources)
        Collections.reverse(propertySourceList)
        for (PropertySource propertySource in propertySourceList) {
            if (propertySource instanceof EnumerablePropertySource) {
                EnumerablePropertySource enumerablePropertySource = (EnumerablePropertySource) propertySource
                mergeEnumerablePropertySource(enumerablePropertySource)
            }
        }
    }

    private void mergeEnumerablePropertySource(EnumerablePropertySource enumerablePropertySource) {
        if (enumerablePropertySource instanceof NavigableMapPropertySource) {
            this.@configMap.merge(((NavigableMapPropertySource) enumerablePropertySource).getSource(), false)
        } else {
            Map<String, Object> map = new LinkedHashMap<>()

            final String[] propertyNames = enumerablePropertySource.getPropertyNames()
            for (String propertyName in propertyNames) {
                Object value = enumerablePropertySource.getProperty(propertyName)
                if (value instanceof ConfigObject) {
                    if (((ConfigObject) value).isEmpty()) continue
                } else {
                    value = processAndEvaluate(value)
                }
                map.put(propertyName, value)
            }

            this.@configMap.merge(map, true)
        }
    }

    private Object processAndEvaluate(Object value) {
        if (value instanceof CharSequence) {
            value = resolvePlaceholders(value.toString())
        } else if (value instanceof List) {
            List<Object> result = new ArrayList<>()
            for (Object element in (List) value) {
                result.add(processAndEvaluate(element))
            }
            return result
        } else if (value instanceof Map) {
            Map<Object, Object> result = new LinkedHashMap<>()
            for (Object key in ((Map) value).keySet()) {
                result.put(key, processAndEvaluate(((Map) value).get(key)))
            }
            return result
        }

        return value
    }

    void setClassLoader(ClassLoader classLoader) {
        this.@classLoader = classLoader
    }

    void setConversionService(ConfigurableConversionService conversionService) {
        this.@conversionService = conversionService
    }

    @Override
    String resolvePlaceholders(String text) {
        if (!GrailsStringUtils.isBlank(text)) {
            return propertySourcesPropertyResolver.resolvePlaceholders(text)
        }
        return text
    }

    @Override
    String resolveRequiredPlaceholders(String text) throws IllegalArgumentException {
        return propertySourcesPropertyResolver.resolveRequiredPlaceholders(text)
    }

}
