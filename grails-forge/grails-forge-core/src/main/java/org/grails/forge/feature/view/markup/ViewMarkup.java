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
package org.grails.forge.feature.view.markup;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.build.gradle.GradlePlugin;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.view.GrailsViews;
import org.grails.forge.feature.view.markup.templates.*;
import org.grails.forge.feature.web.GrailsWeb;
import org.grails.forge.template.RockerTemplate;

import java.util.Arrays;
import java.util.Map;

@Singleton
public class ViewMarkup extends GrailsViews implements Feature {

    public ViewMarkup(GrailsWeb grailsWeb) {
        super(grailsWeb);
    }

    @Override
    @NonNull
    public String getName() {
        return "views-markup";
    }

    @Override
    public String getTitle() {
        return "Markup Views";
    }

    @Override
    @NonNull
    public String getDescription() {
        return "Markup views are written in Groovy, end with the file extension gml and reside in the grails-app/views directory. They provide a DSL for producing output in the XML.";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        final Map<String, Object> config = generatorContext.getConfiguration();
        config.put("grails.mime.disable.accept.header.userAgents", Arrays.asList("Gecko", "WebKit", "Presto", "Trident"));
        config.put("grails.mime.types.xml", Arrays.asList("text/xml", "application/xml"));
        config.put("grails.mime.types.atom", "application/atom+xml");
        config.put("grails.mime.types.json", Arrays.asList("application/json", "text/json"));
        config.put("grails.mime.types.hal", Arrays.asList("application/hal+json", "application/hal+xml"));
        config.put("grails.mime.types.css", "text/css");
        config.put("grails.mime.types.csv", "text/csv");
        config.put("grails.mime.types.js", "text/javascript");
        config.put("grails.mime.types.rss", "application/rss+xml");
        config.put("grails.mime.types.text", "text/plain");
        config.put("grails.mime.types.all", "*/*");

        generatorContext.addBuildPlugin(GradlePlugin.builder()
                .id("org.apache.grails.gradle.grails-markup")
                .useApplyPlugin(true)
                .build());

        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-views-markup")
                .implementation());

        generatorContext.addTemplate("application_index_gml", new RockerTemplate(getViewFolderPath() + "application/index.gml", index.template()));
        generatorContext.addTemplate("_errors_gml", new RockerTemplate(getViewFolderPath() + "errors/_errors.gml", _errors.template()));
        generatorContext.addTemplate("_object_gml", new RockerTemplate(getViewFolderPath() + "object/_object.gml", _object.template()));
        generatorContext.addTemplate("error_gml", new RockerTemplate(getViewFolderPath() + "error.gml", error.template()));
        generatorContext.addTemplate("notFound_gml", new RockerTemplate(getViewFolderPath() + "notFound.gml", notFound.template()));
    }

}
