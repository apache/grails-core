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
package org.grails.forge.feature.validation

import groovy.transform.CompileStatic
import org.springframework.stereotype.Component
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Feature
import org.grails.forge.feature.OneOfFeature
import org.grails.forge.options.Options

@Component
@CompileStatic
class OneOfFeatureValidator implements FeatureValidator {

    private void validate(Set<Feature> features) {
        Map<String, List<String>> selectedByGroup = new TreeMap<>()
        for (Feature feature : features) {
            if (feature instanceof OneOfFeature) {
                String group = ((OneOfFeature) feature).getGroup()
                if (group == null || group.isBlank()) {
                    throw new IllegalArgumentException("The feature ${feature.getName()} names no exclusive group")
                }
                selectedByGroup.computeIfAbsent(group) { String key -> new ArrayList<String>() }.add(feature.getName())
            }
        }
        for (List<String> selected : selectedByGroup.values()) {
            if (selected.size() > 1) {
                throw new IllegalArgumentException(String.format('There can only be one of the following features selected: %s', selected.toSorted()))
            }
        }
    }

    @Override
    void validatePreProcessing(Options options, ApplicationType applicationType, Set<Feature> features) {
        validate(features)
    }

    @Override
    void validatePostProcessing(Options options, ApplicationType applicationType, Set<Feature> features) {
        validate(features)
    }
}
