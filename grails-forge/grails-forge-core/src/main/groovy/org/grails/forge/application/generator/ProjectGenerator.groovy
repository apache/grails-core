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
package org.grails.forge.application.generator

import groovy.transform.CompileStatic
import jakarta.annotation.Nullable
import org.apache.grails.forge.core.pack.Pack
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.OutputHandler
import org.grails.forge.options.Options

@CompileStatic
interface ProjectGenerator {

    void generate(ApplicationType applicationType,
                  Project project,
                  Options options,
                  @Nullable OperatingSystem operatingSystem,
                  List<String> selectedFeatures,
                  OutputHandler outputHandler,
                  ConsoleOutput consoleOutput) throws Exception

    /**
     * Generates the project of a context. The features of the context are applied unless they already
     * were: a context is applied once, so what is changed on it afterwards is not applied again.
     */
    void generate(
            ApplicationType applicationType,
            Project project,
            OutputHandler outputHandler,
            GeneratorContext generatorContext) throws Exception

    GeneratorContext createGeneratorContext(
            ApplicationType applicationType,
            Project project,
            Options options,
            @Nullable OperatingSystem operatingSystem,
            List<String> selectedFeatures,
            ConsoleOutput consoleOutput)

    /**
     * Creates the context of a project that can have the features of feature packs besides those of
     * the framework. The packs are known to this context only.
     *
     * @param selectedFeatures the names of the selected features, of the framework or of a pack
     * @param packs the feature packs to choose from
     * @since 9.0
     */
    default GeneratorContext createGeneratorContext(
            ApplicationType applicationType,
            Project project,
            Options options,
            @Nullable OperatingSystem operatingSystem,
            List<String> selectedFeatures,
            ConsoleOutput consoleOutput,
            Collection<Pack> packs) {
        if (packs.isEmpty()) {
            return createGeneratorContext(applicationType, project, options, operatingSystem, selectedFeatures, consoleOutput)
        }
        throw new UnsupportedOperationException('This generator does not take feature packs')
    }

    /**
     * Creates the context of a project that is not to have some features it would have by default.
     *
     * @param selectedFeatures the names of the selected features, of the framework or of a pack
     * @param packs the feature packs to choose from
     * @param excludedFeatures the names of the features to leave out. A feature that is applied by
     * default and is named here is not applied. A name that is no feature, that cannot be selected by
     * name, that is selected too or that a feature of the application requires is refused with an
     * {@link IllegalArgumentException}
     * @since 9.0
     */
    default GeneratorContext createGeneratorContext(
            ApplicationType applicationType,
            Project project,
            Options options,
            @Nullable OperatingSystem operatingSystem,
            List<String> selectedFeatures,
            ConsoleOutput consoleOutput,
            Collection<Pack> packs,
            Collection<String> excludedFeatures) {
        if (excludedFeatures.isEmpty()) {
            return createGeneratorContext(applicationType, project, options, operatingSystem, selectedFeatures, consoleOutput, packs)
        }
        throw new UnsupportedOperationException('This generator does not take excluded features')
    }

    /**
     * The features an application of a type can have when the given feature packs are there to choose
     * from besides the features of the framework, for listing them.
     *
     * @since 9.0
     */
    default AvailableFeatures availableFeatures(ApplicationType applicationType, Collection<Pack> packs) {
        throw new UnsupportedOperationException('This generator does not take feature packs')
    }
}
