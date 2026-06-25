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
package org.grails.forge.feature.view;

import jakarta.inject.Singleton;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;

/**
 * Opt-in GSP layout decorator backed by the legacy SiteMesh 2 based
 * {@code grails-layout} plugin. Mutually exclusive with {@code sitemesh3};
 * selecting this feature replaces the default SiteMesh 3 decorator.
 */
@Singleton
public class GrailsLayout extends GspLayout {

    @Override
    public String getName() {
        return "grails-layout";
    }

    @Override
    public String getTitle() {
        return "GSP SiteMesh 2 Layouts";
    }

    @Override
    public String getDescription() {
        return "Adds support for legacy SiteMesh 2 based GSP layouts (grails-layout) instead of SiteMesh 3";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-layout")
                .implementation());
    }
}
