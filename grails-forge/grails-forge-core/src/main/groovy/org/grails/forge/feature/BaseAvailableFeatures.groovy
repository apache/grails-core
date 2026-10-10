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
package org.grails.forge.feature

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import org.grails.forge.application.ApplicationType
import java.util.stream.Stream

@CompileStatic
class BaseAvailableFeatures implements AvailableFeatures {

    private final Map<String, Feature> features
    private final ApplicationType applicationType

    BaseAvailableFeatures(List<Feature> features, ApplicationType applicationType) {
        this.applicationType = applicationType
        Map<String, Feature> byName = new LinkedHashMap<>()
        for (Feature feature : features) {
            if (feature.supports(applicationType) && byName.put(feature.name, feature) != null) {
                throw new IllegalArgumentException('Duplicate feature found ' + feature.name)
            }
        }
        this.features = byName
    }

    /**
     * @return the application type these features are available to
     */
    ApplicationType getApplicationType() {
        return applicationType
    }

    /**
     * Iterates the visible feature names in name order. The injected features are ordered only by
     * {@link Feature#getOrder()}, and most share the same order, so without sorting the names picocli
     * lists as completion candidates would vary between runs.
     */
    @Override
    Iterator<String> iterator() {
        return getFeatures()
                .map(Feature::getName)
                .sorted()
                .iterator()
    }

    @Override
    Optional<Feature> findFeature(@Nonnull String name) {
        return findFeature(name, false)
    }

    @Override
    Optional<Feature> findFeature(@Nonnull String name, boolean ignoreVisibility) {
        Feature feature = features.get(name)
        if (feature != null) {
            if (ignoreVisibility || feature.isVisible()) {
                return Optional.of(feature)
            }
        }
        return Optional.empty()
    }

    @Override
    Stream<Feature> getFeatures() {
        return getAllFeatures().filter(Feature::isVisible)
    }

    @Override
    Stream<Feature> getAllFeatures() {
        return features.values().stream()
    }
}
