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

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.feature.DefaultFeature;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.FeaturePhase;
import org.grails.forge.feature.other.template.maindocs;
import org.grails.forge.feature.other.template.readme;
import org.grails.forge.options.Options;
import org.grails.forge.template.RockerWritable;
import org.grails.forge.template.Template;
import org.grails.forge.template.Writable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class Readme implements DefaultFeature {

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return true;
    }

    @NonNull
    @Override
    public String getName() {
        return "readme";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        List<Feature> featuresWithDocumentationLinks = generatorContext.getFeatures().getFeatures().stream().filter(feature -> feature.getDocumentation() != null || feature.getThirdPartyDocumentation() != null).collect(Collectors.toList());
        List<Writable> helpTemplates = generatorContext.getHelpTemplates();
        if (!helpTemplates.isEmpty() || !featuresWithDocumentationLinks.isEmpty()) {
            generatorContext.addTemplate("readme", new Template() {
                @Override
                public String getPath() {
                    return "README.md";
                }

                @Override
                public void write(OutputStream outputStream) throws IOException {
                    Writable mainDocsWritable = new RockerWritable(maindocs.template());
                    mainDocsWritable.write(outputStream);

                    for (Writable writable : generatorContext.getHelpTemplates()) {
                        writable.write(outputStream);
                    }

                    for (Feature feature : featuresWithDocumentationLinks) {
                        Writable writable = new RockerWritable(readme.template(feature));
                        writable.write(outputStream);
                    }
                }
            });
        }
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }

    @Override
    public boolean isVisible() {
        return false;
    }

    @Override
    public int getOrder() {
        return FeaturePhase.HIGHEST.getOrder();
    }

}
