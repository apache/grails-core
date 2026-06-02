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
package org.grails.forge.feature;

import io.micronaut.core.annotation.NonNull;
import org.grails.forge.application.ApplicationType;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BaseAvailableFeatures implements AvailableFeatures {
    private final Map<String, Feature> features;

    public BaseAvailableFeatures(List<Feature> features, ApplicationType applicationType) {
        this.features = features.stream()
                .filter(f -> f.supports(applicationType))
                .collect(Collectors.toMap(
                        Feature::getName,
                        Function.identity(),
                        (u, v) -> {
                            throw new IllegalArgumentException("Duplicate feature found " + u.getName());
                        },
                        LinkedHashMap::new));
    }

    @Override
    public Iterator<String> iterator() {
        return getFeatures()
                .map(Feature::getName)
                .iterator();
    }

    @Override
    public Optional<Feature> findFeature(@NonNull String name) {
        return findFeature(name, false);
    }

    @Override
    public Optional<Feature> findFeature(@NonNull String name, boolean ignoreVisibility) {
        Feature feature = features.get(name);
        if (feature != null) {
            if (ignoreVisibility || feature.isVisible()) {
                return Optional.of(feature);
            }
        }
        return Optional.empty();
    }

    @Override
    public Stream<Feature> getFeatures() {
        return getAllFeatures().filter(Feature::isVisible);
    }

    @Override
    public Stream<Feature> getAllFeatures() {
        return features.values().stream();
    }
}
