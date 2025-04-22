/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.web.mapping.cors

import grails.util.TypeConvertingMap
import groovy.transform.CompileStatic
import java.util.function.Consumer
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.web.cors.CorsConfiguration

/**
 * A bean that stores config and converts it to the format expected by Spring
 *
 * @author James Kleeh
 * @since 3.2.1
 */
@CompileStatic
@ConfigurationProperties(prefix = 'grails.cors')
class GrailsCorsConfiguration {

    Boolean enabled = false

    @Delegate
    GrailsDefaultCorsConfiguration grailsCorsMapping = new GrailsDefaultCorsConfiguration()

    Map<String, Object> mappings = [:]

    Map<String, CorsConfiguration> getCorsConfigurations() {
        grailsCorsMapping.applyPermitDefaultValues()
        Map<String, CorsConfiguration> corsConfigurationMap = [:]

        if (enabled) {
            if (mappings.size() > 0) {
                mappings.each { String key, Object value ->
                    GrailsDefaultCorsConfiguration corsConfiguration = new GrailsDefaultCorsConfiguration(grailsCorsMapping)
                    if (value instanceof Map) {
                        TypeConvertingMap config = new TypeConvertingMap((Map)value)
                        parseConfigList(config, 'allowedOrigins', corsConfiguration::setAllowedOrigins)
                        parseConfigList(config, 'allowedMethods', corsConfiguration::setAllowedMethods)
                        parseConfigList(config, 'allowedHeaders', corsConfiguration::setAllowedHeaders)
                        parseConfigList(config, 'exposedHeaders', corsConfiguration::setExposedHeaders)
                        if (config.containsKey('maxAge')) {
                            corsConfiguration.maxAge = config.long('maxAge')
                        }
                        if (config.containsKey('allowCredentials')) {
                            corsConfiguration.allowCredentials = config.boolean('allowCredentials')
                        }
                    }
                    corsConfigurationMap[key] = corsConfiguration
                }
            } else {
                corsConfigurationMap["/**"] = grailsCorsMapping
            }
        }

        corsConfigurationMap
    }

    private List<String> parseConfigList(TypeConvertingMap config, String key, Consumer<List<String>> setter) {
        // Most of the times the config is defined as a single entry: "key"
        if (config.containsKey(key)) {
            setter.accept(config.list(key))
        } else {
            // Some times the config is defined as multiples entries: "key[0]", "key[1]"...
            List<String> list = []
            for (int index = 0; config.containsKey(key + "[$index]"); index++) {
                list << config.get(key + "[$index]").toString()
            }
            if (!list.empty) setter.accept(list)
        }
    }
}
