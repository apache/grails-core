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
package org.grails.datastore.mapping.config;

import org.grails.datastore.mapping.reflect.ReflectionUtils;
import org.springframework.core.env.PropertyResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Utility methods for configuration
 * 
 * @author Graeme Rocher
 * @since 6.0
 */
public class ConfigurationUtils {

    /**
     * Finds services of the given type from configuration or via {@link ServiceLoader}
     *
     * @param configuration The configuration
     * @param configurationKey The configuration key
     * @param serviceType The type of service
     * @param <T> The service type
     * @return A list of services
     */
    public static <T> Iterable<T> findServices(PropertyResolver configuration, String configurationKey, Class<T> serviceType) {
        List servicesList = configuration.getProperty(configurationKey, List.class, Collections.emptyList());
        return findServices(servicesList, serviceType);
    }

    /**
     * Finds services of the given type from the given list or via {@link ServiceLoader}
     *
     * @param servicesList The list of services
     * @param serviceType The type of service
     * @param <T> The service type
     * @return A list of services
     */
    public static <T> Iterable<T> findServices(List servicesList, Class<T> serviceType) {
        List<T> services = new ArrayList<>();

        if(servicesList != null) {
            for (Object serviceObject : servicesList) {
                if(serviceType.isInstance(serviceObject)) {
                    services.add((T)serviceObject);
                }
                else {
                    Class serviceTypeClass = null;
                    if(serviceObject instanceof Class) {
                        serviceTypeClass = (Class) serviceObject;
                    }
                    else if(serviceObject instanceof CharSequence) {
                        serviceTypeClass = ReflectionUtils.forName(serviceObject.toString(),ConfigurationUtils.class.getClassLoader());
                    }
                    if(serviceTypeClass != null && serviceType.isAssignableFrom(serviceTypeClass)) {
                        T serviceInstance = (T) ReflectionUtils.instantiate(serviceTypeClass);
                        services.add(serviceInstance);
                    }
                }
            }
        }

        ServiceLoader<T> serviceLoader = ServiceLoader.load(serviceType, ConfigurationUtils.class.getClassLoader());
        for (T service : serviceLoader) {
            services.add(service);
        }
        return services;
    }
}
