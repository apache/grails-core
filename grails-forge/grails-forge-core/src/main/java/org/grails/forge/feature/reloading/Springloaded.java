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
package org.grails.forge.feature.reloading;

import jakarta.inject.Singleton;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.FeatureContext;
import org.grails.forge.feature.FeaturePredicate;
import org.grails.forge.template.BinaryTemplate;

import java.util.Optional;

@Singleton
public class Springloaded implements ReloadingFeature {

    private static final String JAR_NAME = "springloaded-1.2.8.RELEASE.jar";

    @Override
    public String getName() {
        return "springloaded";
    }

    @Override
    public String getTitle() {
        return "Spring Loaded JVM Agent";
    }

    @Override
    public String getDescription() {
        return "Adds support for class reloading with Spring Loaded";
    }

    @Override
    public boolean isVisible() {
        return false;
    }

    @Override
    public void processSelectedFeatures(FeatureContext featureContext) {
        int jdkVersion = featureContext.getJavaVersion().majorVersion();
        if (jdkVersion >= 9) {
            Springloaded springloaded = this;
            featureContext.exclude(new FeaturePredicate() {
                @Override
                public boolean test(Feature feature) {
                    return feature == springloaded;
                }

                @Override
                public Optional<String> getWarning() {
                    return Optional.of("Springloaded was excluded because it does not support JDK " + jdkVersion);
                }
            });
        }
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        generatorContext.addTemplate("springLoadedJar",
                new BinaryTemplate("agent/" + JAR_NAME, classLoader.getResource("springloaded/" + JAR_NAME)));
    }

    @Override
    public String getDocumentation() {
        return "https://docs.micronaut.io/latest/guide/index.html#springloaded";
    }
}
