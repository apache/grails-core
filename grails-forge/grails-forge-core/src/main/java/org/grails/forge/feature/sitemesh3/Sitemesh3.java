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
package org.grails.forge.feature.sitemesh3;

import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.view.GspLayout;
import org.grails.forge.options.GspLayoutImpl;
import org.grails.forge.options.Options;

import java.util.Set;

/**
 * Default GSP layout decorator, backed by SiteMesh 3 ({@code grails-sitemesh3}).
 * Applied automatically to web applications unless the {@link GspLayoutImpl}
 * option selects the legacy SiteMesh 2 based {@code grails-layout} decorator.
 */
@Singleton
public class Sitemesh3 extends GspLayout {

    @Override
    public String getName() {
        return "sitemesh3";
    }

    @Override
    public String getTitle() {
        return "GSP SiteMesh 3 Layouts";
    }

    @Override
    public String getDescription() {
        return "Adds support for SiteMesh 3 based GSP layouts";
    }

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return supports(applicationType) &&
                options.getGspLayoutImpl() == GspLayoutImpl.SITEMESH3;
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-sitemesh3")
                .implementation());
    }
}
