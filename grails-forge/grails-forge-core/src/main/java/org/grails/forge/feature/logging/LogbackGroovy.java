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
import org.grails.forge.feature.logging.template.logbackGroovy;
import org.grails.forge.template.RockerTemplate;

@Singleton
public class LogbackGroovy implements LoggingFeature {

    @Override
    public String getName() {
        return "logbackGroovy";
    }

    @Override
    public String getTitle() {
        return "Logback Logging with Groovy config";
    }

    @Override
    public String getDescription() {
        return "Gives you the ability to use groovy to configure logback instead of XML.";
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        OperatingSystem operatingSystem = generatorContext.getOperatingSystem();

        boolean jansi = false;

        if (operatingSystem != OperatingSystem.WINDOWS) {
            jansi = true;
        }

        String projectName = generatorContext.getProject().getName();
        String packageName = generatorContext.getProject().getPackageName();

        generatorContext.addTemplate("loggingConfig", new RockerTemplate("grails-app/conf/logback-config.groovy", logbackGroovy.template(projectName, packageName, jansi)));

        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-logging")
                .implementation());

        generatorContext.addDependency(Dependency.builder()
                .groupId("io.github.virtualdogbert")
                .artifactId("logback-groovy-config")
                .version("1.12.4")
                .implementation());
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }
}
