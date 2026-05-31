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
package org.grails.forge.feature.spring;

import java.util.Set;

import jakarta.inject.Singleton;

import io.micronaut.core.annotation.NonNull;

import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.Feature;
import org.grails.forge.options.Options;
import org.grails.forge.options.ServletImpl;

@Singleton
public class SpringBootUndertowFeature extends SpringBootEmbeddedServlet {

    @Override
    public String getTitle() {
        return "Embedded Undertow";
    }

    @NonNull
    @Override
    public String getName() {
        return "spring-boot-starter-undertow";
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        // Undertow does not yet support Servlet 6.1, which Spring Boot 4 requires.
        // Hidden from the visible feature list. Explicit selection via the API/CLI
        // is rejected by ContextFactory.determineServletImpl() with a clear message.
        return false;
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.springframework.boot")
                .artifactId("spring-boot-starter-undertow")
                .implementation());
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.jboss.threads")
                .lookupArtifactId("jboss-threads")
                .runtimeOnly());
    }

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return options.getServletImpl() == ServletImpl.UNDERTOW;
    }
}
