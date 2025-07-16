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
package org.grails.forge.application;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.DefaultCoordinateResolver;
import org.grails.forge.feature.AvailableFeatures;
import org.grails.forge.feature.DefaultFeature;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.FeatureContext;
import org.grails.forge.feature.validation.FeatureValidator;
import org.grails.forge.io.ConsoleOutput;
import org.grails.forge.options.BuildTool;
import org.grails.forge.options.GormImpl;
import org.grails.forge.options.Language;
import org.grails.forge.options.Options;
import org.grails.forge.options.ServletImpl;
import org.grails.forge.options.TestFramework;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

@Singleton
public class ContextFactory {

    private final FeatureValidator featureValidator;
    private final DefaultCoordinateResolver coordinateResolver;

    public ContextFactory(FeatureValidator featureValidator,
                          DefaultCoordinateResolver coordinateResolver) {
        this.featureValidator = featureValidator;
        this.coordinateResolver = coordinateResolver;
    }

    public FeatureContext createFeatureContext(AvailableFeatures availableFeatures,
                                               List<String> selectedFeatures,
                                               ApplicationType applicationType,
                                               Options options,
                                               @Nullable OperatingSystem operatingSystem) {
        final Set<Feature> features = Collections.newSetFromMap(new IdentityHashMap<>(8));
        for (String name: selectedFeatures) {
            Feature feature = availableFeatures.findFeature(name).orElse(null);
            if (feature != null) {
                features.add(feature);
            } else {
                throw new IllegalArgumentException("The requested feature does not exist: " + name);
            }
        }

        Options newOptions = options
                .withTestFramework(determineTestFramework(options.getTestFramework()))
                .withGormImpl(determineGormImpl(options.getGormImpl()))
                .withServletImpl(determineServletImpl(options.getServletImpl()));

        availableFeatures.getAllFeatures()
                .filter(f -> f instanceof DefaultFeature)
                .filter(f -> ((DefaultFeature) f).shouldApply(applicationType, newOptions, features))
                .forEach(features::add);

        featureValidator.validatePreProcessing(newOptions, applicationType, features);

        return new FeatureContext(newOptions, applicationType, operatingSystem, features);
    }

    public GeneratorContext createGeneratorContext(Project project,
                                                   FeatureContext featureContext,
                                                   ConsoleOutput consoleOutput) {
        featureContext.processSelectedFeatures();

        Set<Feature> featureList = featureContext.getFinalFeatures(consoleOutput);

        featureValidator.validatePostProcessing(featureContext.getOptions(), featureContext.getApplicationType(), featureList);

        return new GeneratorContext(project, featureContext.getApplicationType(), featureContext.getOptions(), featureContext.getOperatingSystem(), featureList, coordinateResolver);
    }

    TestFramework determineTestFramework(TestFramework testFramework) {
        if (testFramework == null) {
            testFramework = TestFramework.DEFAULT_OPTION;
        }
        return testFramework;
    }

    Language determineLanguage(Language language, Set<Feature> features) {
        if (language == null) {
            language = Language.infer(features);
        }
        if (language == null) {
            language = Language.DEFAULT_OPTION;
        }
        return language;
    }

    BuildTool determineBuildTool(BuildTool buildTool) {
        if (buildTool == null) {
            buildTool = BuildTool.GRADLE;
        }
        return buildTool;
    }

    GormImpl determineGormImpl(GormImpl gormImpl) {
        if (gormImpl == null) {
            gormImpl = GormImpl.DEFAULT_OPTION;
        }
        return gormImpl;
    }

    ServletImpl determineServletImpl(ServletImpl servletImpl) {
        if (servletImpl == null) {
            servletImpl = ServletImpl.DEFAULT_OPTION;
        }
        return servletImpl;
    }
}
