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
package org.grails.forge.feature.asciidoctor;

import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Coordinate;
import org.grails.forge.build.dependencies.CoordinateResolver;
import org.grails.forge.build.gradle.GradlePlugin;
import org.grails.forge.feature.Category;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.asciidoctor.template.asciidocGradle;
import org.grails.forge.feature.asciidoctor.template.indexAdoc;
import org.grails.forge.template.RockerTemplate;

@Singleton
public class Asciidoctor implements Feature {

    private final CoordinateResolver coordinateResolver;

    public Asciidoctor(CoordinateResolver coordinateResolver) {
        this.coordinateResolver = coordinateResolver;
    }

    @Override
    public String getName() {
        return "asciidoctor";
    }

    @Override
    public String getTitle() {
        return "Asciidoctor Documentation";
    }

    @Override
    public String getDescription() {
        return "Adds support for creating Asciidoctor documentation";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        final String asciidoctorjVersion = coordinateResolver.resolve("asciidoctorj")
                .map(Coordinate::getVersion).orElse("3.0.0");
        final String asciidoctorjDiagramVersion = coordinateResolver.resolve("asciidoctorj-diagram")
                .map(Coordinate::getVersion).orElse("2.3.1");
        generatorContext.addTemplate("asciidocGradle", new RockerTemplate("gradle/asciidoc.gradle", asciidocGradle.template(asciidoctorjVersion, asciidoctorjDiagramVersion)));

        generatorContext.addBuildPlugin(GradlePlugin.builder()
                .id("org.asciidoctor.jvm.convert")
                .lookupArtifactId("asciidoctor-gradle-jvm")
                .build());

        generatorContext.addTemplate("indexAdoc", new RockerTemplate("src/docs/asciidoc/index.adoc", indexAdoc.template()));
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }

    @Override
    public String getCategory() {
        return Category.DOCUMENTATION;
    }
}
