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
package loadfirst;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the loadfirst plugin that is conditional on a property
 * defined in the plugin's {@code plugin.yml}. This verifies that
 * {@code grails.boot.config.GrailsEnvironmentPostProcessor} loads plugin configuration early
 * enough for {@code @ConditionalOnProperty} to evaluate correctly.
 *
 * @since 7.0
 */
@AutoConfiguration
@ConditionalOnProperty(name = "loadfirst.feature.enabled", havingValue = "true")
public class LoadfirstFeatureAutoConfiguration {

    /**
     * A simple marker bean whose presence proves that {@code @ConditionalOnProperty}
     * successfully read {@code loadfirst.feature.enabled=true} from the plugin's
     * {@code plugin.yml} during auto-configuration evaluation.
     */
    @Bean
    public LoadfirstFeatureInfo loadfirstFeatureInfo() {
        return new LoadfirstFeatureInfo("loadfirst", "Feature enabled via plugin.yml @ConditionalOnProperty");
    }

    /**
     * Simple record acting as a marker bean for the loadfirst plugin feature.
     */
    public record LoadfirstFeatureInfo(String pluginName, String message) {}
}
