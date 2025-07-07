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
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.Category;
import org.grails.forge.feature.DefaultFeature;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.FeatureContext;
import org.grails.forge.feature.web.GrailsWeb;
import org.grails.forge.options.Options;
import org.grails.forge.template.URLTemplate;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

@Singleton
public class GrailsGsp implements DefaultFeature {

    private final GrailsWeb grailsWeb;

    public GrailsGsp(GrailsWeb grailsWeb) {
        this.grailsWeb = grailsWeb;
    }

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return applicationType == ApplicationType.WEB || applicationType == ApplicationType.WEB_PLUGIN;
    }

    @Override
    public String getTitle() {
        return "Grails GSP";
    }

    @Override
    public String getName() {
        return "grails-gsp";
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }

    @Override
    public String getCategory() {
        return Category.VIEW;
    }

    @Override
    public void processSelectedFeatures(FeatureContext featureContext) {
        if (!featureContext.isPresent(GrailsWeb.class) && grailsWeb != null) {
            featureContext.addFeature(grailsWeb);
        }
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        final Map<String, Object> config = generatorContext.getConfiguration();
        config.put("grails.mime.disable.accept.header.userAgents", Arrays.asList("Gecko", "WebKit", "Presto", "Trident"));
        config.put("grails.mime.types.all", "*/*");
        config.put("grails.mime.types.atom", "application/atom+xml");
        config.put("grails.mime.types.css", "text/css");
        config.put("grails.mime.types.csv", "text/csv");
        config.put("grails.mime.types.form", "application/x-www-form-urlencoded");
        config.put("grails.mime.types.html", Arrays.asList("text/html", "application/xhtml+xml"));
        config.put("grails.mime.types.js", "text/javascript");
        config.put("grails.mime.types.json", Arrays.asList("application/json", "text/json"));
        config.put("grails.mime.types.multipartForm", "multipart/form-data");
        config.put("grails.mime.types.pdf", "application/pdf");
        config.put("grails.mime.types.rss", "application/rss+xml");
        config.put("grails.mime.types.text", "text/plain");
        config.put("grails.mime.types.hal", Arrays.asList("application/hal+json", "application/hal+xml"));
        config.put("grails.mime.types.xml", Arrays.asList("text/xml", "application/xml"));
        config.put("grails.views.gsp.encoding", "UTF-8");
        config.put("grails.views.gsp.htmlcodec", "xml");
        config.put("grails.views.gsp.codecs.expression", "html");
        config.put("grails.views.gsp.codecs.scriptlet", "html");
        config.put("grails.views.gsp.codecs.taglib", "none");
        config.put("grails.views.gsp.codecs.staticparts", "none");
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-gsp")
                .implementation());

        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        generatorContext.addTemplate("mainLayout", new URLTemplate(getViewFolderPath() + "layouts/main.gsp", classLoader.getResource("gsp/main.gsp")));
        generatorContext.addTemplate("index", new URLTemplate(getViewFolderPath() + "index.gsp", classLoader.getResource("gsp/index.gsp")));
        generatorContext.addTemplate("error", new URLTemplate(getViewFolderPath() + "error.gsp", classLoader.getResource("gsp/error.gsp")));
        generatorContext.addTemplate("notFound", new URLTemplate(getViewFolderPath() + "notFound.gsp", classLoader.getResource("gsp/notFound.gsp")));
    }

    protected String getViewFolderPath() {
        return "grails-app/views/";
    }
}
