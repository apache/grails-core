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

import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.feature.lang.LanguageFeature;
import org.grails.forge.feature.test.TestFeature;
import org.grails.forge.options.BuildTool;
import org.grails.forge.options.GormImpl;
import org.grails.forge.options.JdkVersion;
import org.grails.forge.options.Options;
import org.grails.forge.util.VersionInfo;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Features extends ArrayList<String> {

    private final Set<Feature> featureList;
    private final BuildTool buildTool;

    private final GormImpl gormImpl;
    private final GeneratorContext context;
    private ApplicationFeature applicationFeature;
    private LanguageFeature languageFeature;
    private TestFeature testFeature;
    private final JdkVersion javaVersion;

    public Features(GeneratorContext context, Set<Feature> featureList, Options options) {
        super(featureList.stream().map(Feature::getName).collect(Collectors.toList()));
        this.featureList = featureList;
        this.context = context;
        for (Feature feature: featureList) {
            if (applicationFeature == null && feature instanceof ApplicationFeature) {
                applicationFeature = (ApplicationFeature) feature;
            }
            if (languageFeature == null && feature instanceof LanguageFeature) {
                languageFeature = (LanguageFeature) feature;
            }
            if (testFeature == null && feature instanceof TestFeature) {
                testFeature = (TestFeature) feature;
            }
        }
        this.javaVersion = options.getJavaVersion();
        this.buildTool = options.getBuildTool();
        this.gormImpl = options.getGormImpl();
    }

    public BuildTool build() {
        return buildTool;
    }

    public ApplicationFeature application() {
        return applicationFeature;
    }

    public LanguageFeature language() {
        return languageFeature;
    }

    public TestFeature testFramework() {
        return testFeature;
    }

    public Set<Feature> getFeatures() {
        return featureList;
    }

    public JdkVersion javaVersion() {
        return javaVersion;
    }

    public GormImpl gormImpl() {
        return gormImpl;
    }

    /**
     * @return The main class
     */
    public Optional<String> mainClass() {
        ApplicationFeature application = application();
        if (application != null && context != null) {
            return Optional.ofNullable(application.mainClassName(context));
        }
        return Optional.empty();
    }

    public String getTargetJdk() {
        return VersionInfo.toJdkVersion(javaVersion.majorVersion());
    }

    public boolean isFeaturePresent(Class<? extends Feature> feature) {
        Objects.requireNonNull(feature, "The feature class cannot be null");
        return getFeatures().stream()
                .map(Feature::getClass)
                .anyMatch(feature::isAssignableFrom);
    }

    public <T extends Feature> Optional<T> getFeature(Class<T> feature) {
        Objects.requireNonNull(feature, "The feature class cannot be null");
        for (Feature f : featureList) {
            if (feature.isInstance(f)) {
                return Optional.of((T) f);
            }
        }
        return Optional.empty();
    }

    public <T extends Feature> T getRequiredFeature(Class<T> feature) {
        Objects.requireNonNull(feature, "The feature class cannot be null");
        for (Feature f : featureList) {
            if (feature.isInstance(f)) {
                return (T) f;
            }
        }
        throw new IllegalStateException(String.format("The required feature type %s does not exist", feature.getName()));
    }
}
