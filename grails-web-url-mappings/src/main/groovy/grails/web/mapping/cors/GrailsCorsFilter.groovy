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
package grails.web.mapping.cors

import groovy.transform.CompileStatic

import org.springframework.core.Ordered
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

/**
 * A class to be registered as a filter for Cors based on Grails configuration
 *
 * @author James Kleeh
 * @since 3.2.2
 */
@CompileStatic
class GrailsCorsFilter extends CorsFilter implements Ordered {

    public GrailsCorsFilter() {
        super(new UrlBasedCorsConfigurationSource())
    }

    public GrailsCorsFilter(GrailsCorsConfiguration corsConfiguration) {
        super(configurationSource(corsConfiguration.corsConfigurations))
    }

    private static UrlBasedCorsConfigurationSource configurationSource(Map<String, CorsConfiguration> corsConfigurations) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource()
        corsConfigurations.each { String key, CorsConfiguration config ->
            source.registerCorsConfiguration(key, config)
        }
        source
    }

    @Override
    int getOrder() {
        HIGHEST_PRECEDENCE
    }
}
