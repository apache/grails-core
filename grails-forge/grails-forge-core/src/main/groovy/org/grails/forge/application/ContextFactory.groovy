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
package org.grails.forge.application

import groovy.transform.CompileStatic
import jakarta.annotation.Nullable
import org.springframework.stereotype.Component
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.DefaultCoordinateResolver
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.feature.DefaultFeature
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureContext
import org.grails.forge.feature.validation.FeatureValidator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.options.BuildTool
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.Language
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl

@Component
@CompileStatic
class ContextFactory {

    private final FeatureValidator featureValidator
    private final DefaultCoordinateResolver coordinateResolver

    ContextFactory(FeatureValidator featureValidator,
                          DefaultCoordinateResolver coordinateResolver) {
        this.featureValidator = featureValidator
        this.coordinateResolver = coordinateResolver
    }

    FeatureContext createFeatureContext(AvailableFeatures availableFeatures,
                                               List<String> selectedFeatures,
                                               ApplicationType applicationType,
                                               Options options,
                                               @Nullable OperatingSystem operatingSystem) {
        return createFeatureContext(availableFeatures, selectedFeatures, applicationType, options, operatingSystem, Collections.<String>emptyList())
    }

    /**
     * Selects the features of an application: the selected ones, then what they require, then the
     * defaults for what nothing has chosen and what those require, until nothing is added.
     *
     * @param excludedFeatures the names of features the application is not to have. A default among them
     * is not applied. A name that is no feature, that cannot be selected by name, that is selected too or
     * that a feature of the application requires is refused
     * @since 9.0
     */
    FeatureContext createFeatureContext(AvailableFeatures availableFeatures,
                                               List<String> selectedFeatures,
                                               ApplicationType applicationType,
                                               Options options,
                                               @Nullable OperatingSystem operatingSystem,
                                               Collection<String> excludedFeatures) {
        final Set<Feature> features = Collections.newSetFromMap(new IdentityHashMap<>(8))
        List<String> requested = []
        for (String name : selectedFeatures) {
            Feature feature = availableFeatures.findFeature(name).orElse(null)
            if (feature != null) {
                features.add(feature)
                requested << feature.getName()
            } else {
                throw new IllegalArgumentException('The requested feature does not exist: ' + name)
            }
        }

        // by name, so that what is refused first does not depend on the order the names were given in
        Set<String> excluded = new TreeSet<>(excludedFeatures)
        for (String name : excluded) {
            Feature feature = availableFeatures.findFeature(name, true).orElseThrow {
                new IllegalArgumentException('The excluded feature does not exist: ' + name)
            }
            if (!feature.isVisible()) {
                throw new IllegalArgumentException("The feature ${name} cannot be excluded: it is not a feature that is selected by name")
            }
            if (features.contains(feature)) {
                throw new IllegalArgumentException("The feature ${name} is both selected and excluded")
            }
        }

        // what the selected features cannot be without comes before the defaults, which are only for
        // what nothing has chosen: a feature that needs one database driver is not given another
        Map<Feature, Feature> requiredBy = new IdentityHashMap<>()
        selectRequired(availableFeatures, features, features, requiredBy, excluded)

        Options newOptions = options
            .withDevelopmentReloading(determineDevelopmentReloading(options.getDevelopmentReloading()))
            .withGormImpl(determineGormImpl(options.getGormImpl()))
            .withServletImpl(determineServletImpl(options.getServletImpl()))

        List<DefaultFeature> defaults = availableFeatures.getAllFeatures()
                .filter { it instanceof DefaultFeature }
                .map { (DefaultFeature) it }
                .sorted(Comparator.comparing(Feature::getName))
                .toList()
        // A default can depend on another default, such as Mockito on Spock. Resolve the
        // complete set before processing it, independently of component registration order.
        // Every round adds a feature or is the last, so there are no more rounds than features.
        boolean added
        do {
            added = false
            List<Feature> applied = []
            for (DefaultFeature feature : defaults) {
                if (!features.contains(feature) && !excluded.contains(feature.getName()) && feature.shouldApply(applicationType, newOptions, features)) {
                    features.add(feature)
                    applied.add(feature)
                    added = true
                }
            }
            selectRequired(availableFeatures, features, applied, requiredBy, excluded)
        } while (added)

        featureValidator.validatePreProcessing(newOptions, applicationType, features)

        FeatureContext featureContext = new FeatureContext(newOptions, applicationType, operatingSystem, features)
        featureContext.setRequestedFeatures(requested)
        for (String name : excluded) {
            featureContext.exclude(name)
        }
        return featureContext
    }

    /**
     * Adds what the given features require, and what that requires, to the features. A feature is looked
     * at once, so features that require each other, directly or through others, are selected together.
     */
    private static void selectRequired(AvailableFeatures availableFeatures,
                                       Set<Feature> features,
                                       Collection<Feature> from,
                                       Map<Feature, Feature> requiredBy,
                                       Set<String> excluded) {
        // by name: the features come from a set that has no order of its own
        Deque<Feature> pending = new ArrayDeque<>(from.toSorted(Comparator.comparing(Feature::getName)))
        while (!pending.isEmpty()) {
            Feature feature = pending.pop()
            for (String name : feature.getDependentFeatures()) {
                Feature required = availableFeatures.findFeature(name, true).orElseThrow {
                    new IllegalArgumentException("The feature ${feature.getName()} requires the feature ${name}, " +
                            "which there is not for this type of application${through(feature, requiredBy)}")
                }
                if (excluded.contains(required.getName())) {
                    throw new IllegalArgumentException("The feature ${name} cannot be excluded: the feature ${feature.getName()} " +
                            "requires it${through(feature, requiredBy)}")
                }
                if (features.add(required)) {
                    requiredBy.put(required, feature)
                    pending.push(required)
                }
            }
        }
    }

    // how a feature nobody selected came to be required, from the feature that was selected or is a default
    private static String through(Feature feature, Map<Feature, Feature> requiredBy) {
        if (!requiredBy.containsKey(feature)) {
            return ''
        }
        List<String> names = []
        for (Feature step = feature; step != null; step = requiredBy.get(step)) {
            names.add(0, step.getName())
        }
        return " (${names.join(' requires ')})"
    }

    GeneratorContext createGeneratorContext(Project project,
                                                   FeatureContext featureContext,
                                                   ConsoleOutput consoleOutput) {
        featureContext.processSelectedFeatures()

        Set<Feature> featureList = featureContext.getFinalFeatures(consoleOutput)

        featureValidator.validatePostProcessing(featureContext.getOptions(), featureContext.getApplicationType(), featureList)

        GeneratorContext generatorContext = new GeneratorContext(project, featureContext.getApplicationType(), featureContext.getOptions(), featureContext.getOperatingSystem(), featureList, coordinateResolver,
                featureContext.getExcludedFeatures())
        generatorContext.setRequestedFeatures(featureContext.getRequestedFeatures())
        return generatorContext
    }

    DevelopmentReloading determineDevelopmentReloading(DevelopmentReloading reloading) {
        if (reloading == null) {
            reloading = DevelopmentReloading.DEFAULT_OPTION
        }
        return reloading
    }

    Language determineLanguage(Language language, Set<Feature> features) {
        if (language == null) {
            language = Language.infer(features)
        }
        if (language == null) {
            language = Language.DEFAULT_OPTION
        }
        return language
    }

    BuildTool determineBuildTool(BuildTool buildTool) {
        if (buildTool == null) {
            buildTool = BuildTool.GRADLE
        }
        return buildTool
    }

    GormImpl determineGormImpl(GormImpl gormImpl) {
        if (gormImpl == null) {
            gormImpl = GormImpl.DEFAULT_OPTION
        }
        return gormImpl
    }

    ServletImpl determineServletImpl(ServletImpl servletImpl) {
        if (servletImpl == null) {
            servletImpl = ServletImpl.DEFAULT_OPTION
        }
        return servletImpl
    }
}
