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

import jakarta.annotation.Nonnull;
import org.grails.forge.application.ApplicationType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Single catalog of Forge features. Adding a feature is one {@link Component}
 * implementing {@link Feature}; this registry discovers it without further wiring.
 */
@Component
public class FeatureRegistry {

    private final List<Feature> features;

    public FeatureRegistry(List<Feature> features) {
        Map<String, Feature> unique = new LinkedHashMap<>();
        List<Feature> sorted = new ArrayList<>(features);
        sorted.sort(Comparator.comparingInt(Feature::getOrder).thenComparing(Feature::getName));
        for (Feature feature : sorted) {
            Feature previous = unique.put(feature.getName(), feature);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate feature found " + previous.getName());
            }
        }
        this.features = List.copyOf(unique.values());
    }

    public List<Feature> all() {
        return features;
    }

    public List<Feature> availableFor(ApplicationType type) {
        return availableFeatures(type).getFeatures().collect(Collectors.toList());
    }

    public AvailableFeatures availableFeatures(ApplicationType type) {
        return new BaseAvailableFeatures(features, type);
    }

    public Optional<Feature> find(@Nonnull ApplicationType type, @Nonnull String name, boolean includeHidden) {
        return availableFeatures(type).findFeature(name, includeHidden);
    }

    public Feature require(ApplicationType type, String name) {
        return find(type, name, false).orElseThrow(() ->
                new IllegalArgumentException("The requested feature does not exist: " + name));
    }

    public Stream<Feature> visible() {
        return features.stream().filter(Feature::isVisible);
    }
}
