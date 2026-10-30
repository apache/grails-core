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
package org.grails.forge.api

import groovy.transform.CompileStatic
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureRegistry
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import java.util.stream.Collectors

@Service
@CompileStatic
class FeatureService implements FeatureOperations {

    private final FeatureRegistry featureRegistry
    private final MessageSource messageSource
    private final ProjectGenerator projectGenerator

    FeatureService(FeatureRegistry featureRegistry, MessageSource messageSource, ProjectGenerator projectGenerator) {
        this.featureRegistry = featureRegistry
        this.messageSource = messageSource
        this.projectGenerator = projectGenerator
    }

    @Override
    List<FeatureDTO> getAllFeatures(Locale locale) {
        return featureRegistry.visible()
                .map(feature -> new FeatureDTO(feature, messageSource, locale))
                .sorted(Comparator.comparing(FeatureDTO::getName))
                .collect(Collectors.toList())
    }

    @Override
    List<FeatureDTO> getFeatures(Locale locale, ApplicationType type, Options options) {
        Set<Feature> defaults = defaultFeatures(type, options)
        return featureRegistry.availableFeatures(type)
                .getFeatures()
                .filter(f -> !defaults.contains(f))
                .map(feature -> new FeatureDTO(feature, messageSource, locale))
                .sorted(Comparator.comparing(FeatureDTO::getName))
                .collect(Collectors.toList())
    }

    private Set<Feature> defaultFeatures(ApplicationType type, Options options) {
        projectGenerator.createGeneratorContext(type, NameUtils.parse('example'), options,
                options.operatingSystem, [], ConsoleOutput.NOOP).features.features
    }

    @Override
    List<FeatureDTO> getDefaultFeatures(Locale locale, ApplicationType type, Options options) {
        Set<Feature> defaults = defaultFeatures(type, options)
        return featureRegistry.availableFeatures(type)
                .getFeatures()
                .filter(f -> defaults.contains(f))
                .map(feature -> new FeatureDTO(feature, messageSource, locale))
                .sorted(Comparator.comparing(FeatureDTO::getName))
                .collect(Collectors.toList())
    }
}
