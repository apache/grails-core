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
package org.grails.forge.feature.grails

import groovy.transform.CompileStatic
import org.springframework.stereotype.Component
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.Category
import org.grails.forge.feature.DefaultFeature
import org.grails.forge.feature.Feature
import org.grails.forge.options.Options

/**
 * The settings of the configuration of every application that say what the application is: the
 * package its classes are created in when none is given, which the framework reads when it runs,
 * and the name of its type.
 *
 * <p>The name of the type is still written as {@code grails.profile}, although no profile is part of
 * the build of the application any more: the framework makes it an active Spring profile, {@code web}
 * when there is none, so an application without it would run with another Spring profile than it
 * does now. The setting goes when the framework stops reading it.</p>
 */
@Component
@CompileStatic
class GrailsProjectSettings implements DefaultFeature {

    @Override
    boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return true
    }

    // the name is one that is applied where grails-profiles was, between the features whose names are
    // around it: the settings of an application are written in the order its features are applied
    @Override
    String getName() {
        return 'grails-project-settings'
    }

    @Override
    boolean isVisible() {
        return false
    }

    @Override
    boolean supports(ApplicationType applicationType) {
        return true
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        final Map<String, Object> config = generatorContext.getConfiguration()
        config.put('grails.codegen.defaultPackage', generatorContext.getProject().getPackageName())
        config.put('grails.profile', generatorContext.getApplicationType().getName().replace('_', '-'))
    }

    @Override
    String getCategory() {
        return Category.CONFIGURATION
    }
}
