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
package org.grails.forge.feature.validation;

import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.OneOfFeature;
import org.grails.forge.options.Options;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class OneOfFeatureValidator implements FeatureValidator {

    private void validate(Set<Feature> features) {
        Set<Class<?>> oneOfFeatures = features.stream()
                .filter(feature -> feature instanceof OneOfFeature)
                .map(OneOfFeature.class::cast)
                .map(OneOfFeature::getFeatureClass)
                .collect(Collectors.toSet());

        for (Class<?> featureClass: oneOfFeatures) {
            List<String> matches = features.stream()
                    .filter(feature -> featureClass.isAssignableFrom(feature.getClass()))
                    .map(Feature::getName)
                    .collect(Collectors.toList());
            if (matches.size() > 1) {
                throw new IllegalArgumentException(String.format("There can only be one of the following features selected: %s", matches));
            }
        }
    }

    @Override
    public void validatePreProcessing(Options options, ApplicationType applicationType, Set<Feature> features) {
        validate(features);
    }

    @Override
    public void validatePostProcessing(Options options, ApplicationType applicationType, Set<Feature> features) {
        validate(features);
    }
}
