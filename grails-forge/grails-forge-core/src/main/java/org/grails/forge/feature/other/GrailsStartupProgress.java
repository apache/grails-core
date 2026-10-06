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
package org.grails.forge.feature.other;

import jakarta.inject.Singleton;

import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.Category;
import org.grails.forge.feature.Feature;
import org.grails.forge.util.VersionInfo;

/**
 * Adds the startup progress page, which answers on the application's port while the application starts,
 * and the report of how the start went that the application serves once it is running.
 */
@Singleton
public class GrailsStartupProgress implements Feature {

    public static final String FEATURE_NAME = "grails-startup-progress";

    @Override
    public String getName() {
        return FEATURE_NAME;
    }

    @Override
    public String getTitle() {
        return "Startup Progress Page";
    }

    @Override
    public String getDescription() {
        return "Shows a progress page on the application's port while the application starts, instead of the port " +
                "refusing connections, and a report of how long each stage of the start took once it is running.";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-startup-progress")
                .implementation());
    }

    /** Only an application starts its own web server; a plugin's dependency would reach every application using it. */
    @Override
    public boolean supports(ApplicationType applicationType) {
        return applicationType == ApplicationType.WEB || applicationType == ApplicationType.REST_API;
    }

    @Override
    public String getCategory() {
        return Category.DEV_TOOLS;
    }

    @Override
    public String getDocumentation() {
        return "https://grails.apache.org/docs/" + VersionInfo.getDocumentationVersion() + "/guide/gettingStarted.html#startupProgress";
    }
}
