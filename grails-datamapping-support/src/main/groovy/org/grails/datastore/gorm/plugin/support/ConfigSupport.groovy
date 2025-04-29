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

package org.grails.datastore.gorm.plugin.support

import org.grails.config.PropertySourcesConfig
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.convert.converter.Converter
import org.springframework.core.convert.support.ConfigurableConversionService
import org.springframework.core.env.PropertyResolver

/**
 * Support for configuration when developing Grails plugins
 *
 * @author Graeme Rocher
 * @since 6.0
 */
class ConfigSupport {

    /**
     * Workaround method because Grails' config doesn't convert strings to classes correctly
     *
     * @param config The config
     * @param applicationContext The application context
     */
    static void prepareConfig(PropertyResolver config, ConfigurableApplicationContext applicationContext) {
        if(config instanceof PropertySourcesConfig) {
            ConfigurableConversionService conversionService = applicationContext.getEnvironment().getConversionService()
            conversionService.addConverter(new Converter<String, Class>() {
                @Override
                Class convert(String source) {
                    Class.forName(source)
                }
            })
            ((PropertySourcesConfig)config).setConversionService(conversionService)
        }
    }
}
