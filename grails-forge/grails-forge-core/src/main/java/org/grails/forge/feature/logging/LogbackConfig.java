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
package org.grails.forge.feature.logging;

import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.OperatingSystem;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.logging.template.logback;
import org.grails.forge.template.RockerTemplate;

/**
 * Opt-in logging feature that generates an editable
 * {@code grails-app/conf/logback-spring.xml} configuration file.
 *
 * <p>Logback is already present in every generated application: the default
 * {@link Logback} feature adds {@code grails-logging} (which brings Spring Boot's
 * logging starter), so logging works out of the box using Spring Boot's zero-config
 * Logback defaults with no configuration file. This feature does not change that
 * dependency; it simply emits a starter {@code logback-spring.xml} for projects that
 * prefer to manage their logging configuration in XML. Because it belongs to the same
 * {@link LoggingFeature} group, selecting it supersedes the default feature, so it
 * re-declares {@code grails-logging} to keep Logback on the classpath.</p>
 */
@Singleton
public class LogbackConfig implements LoggingFeature {

    @Override
    public String getName() {
        return "logback-config";
    }

    @Override
    public String getTitle() {
        return "Logback Configuration";
    }

    @Override
    public String getDescription() {
        return "Generates an editable grails-app/conf/logback-spring.xml. Logback logging already works out of the box via Spring Boot's defaults; add this only to customize logging configuration in XML.";
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        boolean jansi = generatorContext.getOperatingSystem() != OperatingSystem.WINDOWS;

        String projectName = generatorContext.getProject().getName();
        String packageName = generatorContext.getProject().getPackageName();

        generatorContext.addTemplate("loggingConfig", new RockerTemplate("grails-app/conf/logback-spring.xml",
                logback.template(projectName, packageName, jansi)));
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-logging")
                .implementation());
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }
}
