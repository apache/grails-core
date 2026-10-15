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
import org.springframework.stereotype.Component
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * Single catalog of Forge features. Adding a feature is one {@link Component}
 * implementing {@link Feature}; this registry discovers it without further wiring.
 */
@Component
@CompileStatic
class FeatureRegistry {

    private final List<Feature> features
    private final Map<ApplicationType, AvailableFeatures> availableByType

    /**
     * @param features every feature
     * @param availableFeatures the features available to each application type: one bean per type
     */
    FeatureRegistry(List<Feature> features, List<BaseAvailableFeatures> availableFeatures) {
        Map<ApplicationType, AvailableFeatures> byType = new EnumMap<>(ApplicationType)
        for (BaseAvailableFeatures available : availableFeatures) {
            if (byType.put(available.applicationType, available) != null) {
                throw new IllegalArgumentException("Several AvailableFeatures beans for ${available.applicationType}")
            }
        }
        this.availableByType = byType
        Map<String, Feature> unique = new LinkedHashMap<>()
        List<Feature> sorted = new ArrayList<>(features)
        sorted.sort { Feature a, Feature b ->
            int byOrder = a.order <=> b.order
            byOrder != 0 ? byOrder : a.name <=> b.name
        }
        for (Feature feature : sorted) {
            Feature previous = unique.put(feature.getName(), feature)
            if (previous != null) {
                throw new IllegalArgumentException('Duplicate feature found ' + previous.getName())
            }
        }
        this.features = List.copyOf(unique.values())
    }

    List<Feature> all() {
        return features
    }

    List<Feature> availableFor(ApplicationType type) {
        return availableFeatures(type).getFeatures().collect(Collectors.toList())
    }

    AvailableFeatures availableFeatures(ApplicationType type) {
        AvailableFeatures available = availableByType.get(type)
        if (available == null) {
            throw new IllegalArgumentException("No features are registered for application type ${type}")
        }
        return available
    }

    Optional<Feature> find(@Nonnull ApplicationType type, @Nonnull String name, boolean includeHidden) {
        return availableFeatures(type).findFeature(name, includeHidden)
    }

    Feature require(ApplicationType type, String name) {
        return find(type, name, false).orElseThrow(() ->
                new IllegalArgumentException('The requested feature does not exist: ' + name))
    }

    Stream<Feature> visible() {
        return features.stream().filter(Feature::isVisible)
    }
}
