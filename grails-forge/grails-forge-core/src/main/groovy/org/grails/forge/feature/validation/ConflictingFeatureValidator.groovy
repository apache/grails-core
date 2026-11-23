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
import java.util.function.Function
import org.apache.grails.forge.core.pack.PackFeature
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.options.Options
import org.springframework.stereotype.Component

/**
 * Refuses a feature pack together with a feature it says it conflicts with. The features are those
 * the application is made of in the end, with the defaults and with what other features brought in,
 * so the conflict is found when the features are chosen and before any of them is applied.
 *
 * @since 9.0
 */
@Component
@CompileStatic
class ConflictingFeatureValidator implements FeatureValidator {

    @Override
    void validatePreProcessing(Options options, ApplicationType applicationType, Set<Feature> features) {
        // the features are not all there yet: a feature can still bring in another
    }

    @Override
    void validatePostProcessing(Options options, ApplicationType applicationType, Set<Feature> features) {
        String conflict = conflict(features) { String name -> [name] }
        if (conflict != null) {
            throw new FeatureSelectionException(conflict)
        }
    }

    /**
     * @param features the features of an application
     * @param requirementsTo gives, for the name of a feature, the names of the features that led to it
     * through what each requires, ending with the feature itself
     * @return what to say of the first pack, by name, that is there with a feature it conflicts with, or
     * {@code null} when there is none. A name among the conflicts of a pack that is no feature of the
     * application conflicts with nothing: a pack can name a feature another type of application has. A
     * full name, {@code group:artifact#name}, is the feature of the pack of that plugin, see
     * {@link PackFeature#isNamed}.
     */
    static String conflict(Set<Feature> features, Function<String, List<String>> requirementsTo) {
        List<Feature> sorted = features.toSorted(Comparator.comparing(Feature::getName))
        for (Feature feature : sorted) {
            if (feature instanceof PackFeature) {
                for (String conflict : ((PackFeature) feature).getPack().descriptor.conflicts) {
                    Feature other = sorted.find { Feature each -> PackFeature.isNamed(conflict, each) }
                    if (other != null) {
                        // which feature brought in the one that conflicts, or else the pack itself
                        List<String> way = requirementsTo.apply(other.getName())
                        if (way.size() < 2) {
                            way = requirementsTo.apply(feature.getName())
                        }
                        return "The feature ${feature.getName()} cannot be used with the feature ${other.getName()}" +
                                (way.size() < 2 ? '' : " (${FeatureSelectionException.chain(way)})")
                    }
                }
            }
        }
        return null
    }
}
