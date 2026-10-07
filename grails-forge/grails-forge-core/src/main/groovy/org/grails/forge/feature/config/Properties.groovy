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
package org.grails.forge.feature.config

import groovy.transform.CompileStatic
import org.springframework.stereotype.Component
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.FeaturePhase
import org.grails.forge.template.PropertiesTemplate
import org.grails.forge.template.Template
import java.util.function.Function

@Component
@CompileStatic
class Properties implements ConfigurationFeature {

    private static final String EXTENSION = 'properties'

    @Override
    String getName() {
        return 'properties'
    }

    @Override
    String getTitle() {
        return 'Java Properties Configuration'
    }

    @Override
    String getDescription() {
        return 'Use Java properties for configuration instead of YAML.'
    }

    @Override
    int getOrder() {
        return FeaturePhase.HIGHEST.getOrder()
    }

    @Override
    Function<Configuration, Template> createTemplate() {
        return (config) -> new PropertiesTemplate(config.getFullPath(EXTENSION), config)
    }

    @Override
    boolean supports(ApplicationType applicationType) {
        return true
    }
}
