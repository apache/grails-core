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

package org.grails.config;

import grails.util.Environment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySources;

import java.util.ArrayList;
import java.util.List;

/**
 * A PropertySource aware of the Grails environment and that resolves keys based on the environment from other property sources
 *
 * @author Graeme Rocher
 * @since 3.0
 */

public class EnvironmentAwarePropertySource extends EnumerablePropertySource<PropertySources> {
    EnvironmentAwarePropertySource(PropertySources source) {
        super("grails.environment", source);
    }

    protected List<String> propertyNames;

    @Override
    public String[] getPropertyNames() {
        initialize();
        return propertyNames.toArray(new String[propertyNames.size()]);
    }

    @Override
    public Object getProperty(String name) {
        initialize();
        if(!propertyNames.contains(name)) {
            return null;
        }

        Environment env = Environment.getCurrent();
        String key = "environments." + env.getName() + '.' + name;
        for(PropertySource propertySource : source) {
            if(propertySource != this) {
                Object value = propertySource.getProperty(key);
                if(value != null) return value;
            }
        }
        return null;
    }

    private void initialize() {
        if(propertyNames == null) {
            propertyNames = new ArrayList<>();
            Environment env = Environment.getCurrent();
            String key = "environments." + env.getName();
            for(PropertySource propertySource : source) {

                if((propertySource != this) &&
                        !propertySource.getName().contains("plugin") && // plugin default configuration is not allowed to be environment aware (GRAILS-12123)
                        propertySource instanceof EnumerablePropertySource) {
                    EnumerablePropertySource enumerablePropertySource = (EnumerablePropertySource)propertySource;

                    for(String propertyName : enumerablePropertySource.getPropertyNames()) {
                        if(propertyName.startsWith(key) && propertyName.length() > key.length()) {
                            propertyNames.add(propertyName.substring(key.length() + 1));
                        }
                    }
                }
            }
        }
    }
}
