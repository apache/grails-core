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
package org.grails.forge.api.create;

import org.grails.forge.api.UserAgentParser;
import org.grails.forge.api.event.ApplicationGeneratingEvent;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.OperatingSystem;
import org.grails.forge.application.Project;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.application.generator.ProjectGenerator;
import org.grails.forge.io.ConsoleOutput;
import org.grails.forge.options.BuildTool;
import org.grails.forge.options.DevelopmentReloading;
import org.grails.forge.options.GormImpl;
import org.grails.forge.options.JdkVersion;
import org.grails.forge.options.Options;
import org.grails.forge.options.ServletImpl;
import org.grails.forge.util.NameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class CreateProjectService {

    private static final Logger LOG = LoggerFactory.getLogger(CreateProjectService.class);

    private final ProjectGenerator projectGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public CreateProjectService(ProjectGenerator projectGenerator, ApplicationEventPublisher eventPublisher) {
        this.projectGenerator = projectGenerator;
        this.eventPublisher = eventPublisher;
    }

    public ProjectGenerator getProjectGenerator() {
        return projectGenerator;
    }

    public GeneratorContext createProjectGeneratorContext(
            ApplicationType type,
            String name,
            List<String> features,
            BuildTool buildTool,
            DevelopmentReloading reloading,
            GormImpl gorm,
            ServletImpl servlet,
            JdkVersion javaVersion,
            String userAgent) {
        Project project = NameUtils.parse(name);
        Options options = new Options(
                reloading != null ? reloading : DevelopmentReloading.DEFAULT_OPTION,
                gorm != null ? gorm : GormImpl.DEFAULT_OPTION,
                servlet != null ? servlet : ServletImpl.DEFAULT_OPTION,
                javaVersion != null ? javaVersion : JdkVersion.DEFAULT_OPTION,
                UserAgentParser.getOperatingSystem(userAgent)
        );
        GeneratorContext generatorContext = projectGenerator.createGeneratorContext(
                type,
                project,
                options,
                getOperatingSystem(userAgent),
                features != null ? features : Collections.emptyList(),
                ConsoleOutput.NOOP
        );
        try {
            eventPublisher.publishEvent(new ApplicationGeneratingEvent(generatorContext));
        } catch (Exception e) {
            LOG.warn("Error firing application generated event: {}", e.getMessage(), e);
        }
        return generatorContext;
    }

    public OperatingSystem getOperatingSystem(String userAgent) {
        return UserAgentParser.getOperatingSystem(userAgent);
    }
}
