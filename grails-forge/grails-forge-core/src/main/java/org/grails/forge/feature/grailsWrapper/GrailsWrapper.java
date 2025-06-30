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
package org.grails.forge.feature.grailsWrapper;

import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.OperatingSystem;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.feature.Category;
import org.grails.forge.feature.DefaultFeature;
import org.grails.forge.feature.Feature;
import org.grails.forge.options.Options;
import org.grails.forge.template.BinaryTemplate;

import java.util.Set;

@Singleton
public class GrailsWrapper implements DefaultFeature {

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return true;
    }

    @Override
    public String getName() {
        return "grails-wrapper";
    }

    @Override
    public String getTitle() {
        return "Grails Wrapper for Grails";
    }

    @Override
    public String getDescription() {
        return "Gives you the ability to run grails shell from terminal.";
    }

    @Override
    public boolean isVisible() {
        return false;
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        OperatingSystem operatingSystem = generatorContext.getOperatingSystem();

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        generatorContext.addTemplate("grailsWrapperJar", new BinaryTemplate("grails-wrapper.jar", classLoader.getResource("grails-wrapper/grails-wrapper.jar")));
        generatorContext.addTemplate("grailsWrapper", new BinaryTemplate("grailsw", classLoader.getResource("grails-wrapper/grailsw"), true));
        generatorContext.addTemplate("grailsWrapperBat", new BinaryTemplate("grailsw.bat", classLoader.getResource("grails-wrapper/grailsw.bat"), false));
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }

    @Override
    public String getCategory() {
        return Category.DEV_TOOLS;
    }
}
