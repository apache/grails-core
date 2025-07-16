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

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.exceptions.HttpStatusException;
import org.grails.forge.api.TestFramework;
import org.grails.forge.api.UserAgentParser;
import org.grails.forge.api.event.ApplicationGeneratingEvent;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.OperatingSystem;
import org.grails.forge.application.Project;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.application.generator.ProjectGenerator;
import org.grails.forge.io.ConsoleOutput;
import org.grails.forge.options.BuildTool;
import org.grails.forge.options.GormImpl;
import org.grails.forge.options.JdkVersion;
import org.grails.forge.options.Options;
import org.grails.forge.options.ServletImpl;
import org.grails.forge.util.NameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.Pattern;
import java.util.Collections;
import java.util.List;

/**
 * Abstract implementation of a create controller.
 *
 * @author graemerocher
 * @since 6.0.0
 */
public abstract class AbstractCreateController {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCreateController.class);
    protected final ProjectGenerator projectGenerator;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Abstract implementation for create controllers.
     * @param projectGenerator The project generator
     * @param eventPublisher The event publisher
     */
    protected AbstractCreateController(
            ProjectGenerator projectGenerator,
            ApplicationEventPublisher eventPublisher) {
        this.projectGenerator = projectGenerator;
        this.eventPublisher = eventPublisher;
    }

    public GeneratorContext createProjectGeneratorContext(
            ApplicationType type,
            @Pattern(regexp = "[\\w\\d-_\\.]+") String name,
            @Nullable List<String> features,
            @Nullable BuildTool buildTool,
            @Nullable TestFramework testFramework,
            @Nullable GormImpl gorm,
            @Nullable ServletImpl servlet,
            @Nullable JdkVersion javaVersion,
            @Nullable @Header(HttpHeaders.USER_AGENT) String userAgent) {
        Project project;
        try {
            project = NameUtils.parse(name);
        } catch (IllegalArgumentException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Invalid project name: " + e.getMessage());
        }

        GeneratorContext generatorContext;
        try {
            GormImpl gormImpl = gorm != null ? gorm : GormImpl.DEFAULT_OPTION;
            ServletImpl servletImpl = servlet != null ? servlet : ServletImpl.DEFAULT_OPTION;
            generatorContext = projectGenerator.createGeneratorContext(
                    type,
                    project,
                    new Options(testFramework != null ? testFramework.toTestFramework() : org.grails.forge.options.TestFramework.DEFAULT_OPTION,
                            gormImpl == null ? GormImpl.DEFAULT_OPTION : gormImpl,
                            servletImpl == null ? ServletImpl.DEFAULT_OPTION : servletImpl,
                            javaVersion == null ? JdkVersion.DEFAULT_OPTION : javaVersion,
                            getOperatingSystem(userAgent)),
                    getOperatingSystem(userAgent),
                    features != null ? features : Collections.emptyList(),
                    ConsoleOutput.NOOP
            );

            try {
                eventPublisher.publishEvent(new ApplicationGeneratingEvent(generatorContext));
            } catch (Exception e) {
                LOG.warn("Error firing application generated event: " + e.getMessage(), e);
            }
        } catch (IllegalArgumentException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        return generatorContext;
    }

    protected OperatingSystem getOperatingSystem(String userAgent) {
        return UserAgentParser.getOperatingSystem(userAgent);
    }
}
