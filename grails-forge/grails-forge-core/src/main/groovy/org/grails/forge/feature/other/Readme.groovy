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
package org.grails.forge.feature.other

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import org.springframework.stereotype.Component
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.DefaultFeature
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeaturePhase
import org.grails.forge.options.Options
import org.grails.forge.template.Template
import org.grails.forge.template.Writable
import org.grails.forge.template.GspView
import java.util.stream.Collectors

@Component
@CompileStatic
class Readme implements DefaultFeature {

    @Override
    boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return true
    }

    @Nonnull
    @Override
    String getName() {
        return 'readme'
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        List<Feature> featuresWithDocumentationLinks = generatorContext.getFeatures().getFeatures().stream().filter(feature -> feature.getDocumentation() != null || feature.getThirdPartyDocumentation() != null).collect(Collectors.toList())
        List<Writable> helpTemplates = generatorContext.getHelpTemplates()
        if (!helpTemplates.isEmpty() || !featuresWithDocumentationLinks.isEmpty()) {
            generatorContext.addTemplate('readme', new Template() {
                @Override
                String getPath() {
                    return 'README.md'
                }

                @Override
                void write(OutputStream outputStream) throws IOException {
                    Writable mainDocsWritable = GspView.of('/forge/feature/other/template/maindocs.gsp', [:])
                    mainDocsWritable.write(outputStream)

                    for (Writable writable : generatorContext.getHelpTemplates()) {
                        writable.write(outputStream)
                    }

                    for (Feature feature : featuresWithDocumentationLinks) {
                        Writable writable = GspView.of('/forge/feature/other/template/readme.gsp', [feature: feature])
                        writable.write(outputStream)
                    }
                }
            })
        }
    }

    @Override
    boolean supports(ApplicationType applicationType) {
        return true
    }

    @Override
    boolean isVisible() {
        return false
    }

    @Override
    int getOrder() {
        return FeaturePhase.HIGHEST.getOrder()
    }

}
