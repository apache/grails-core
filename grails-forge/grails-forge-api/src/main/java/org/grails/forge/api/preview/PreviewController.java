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
package org.grails.forge.api.preview;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.exceptions.HttpStatusException;
import org.grails.forge.api.Relationship;
import org.grails.forge.api.RequestInfo;
import org.grails.forge.api.TestFramework;
import org.grails.forge.api.create.AbstractCreateController;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.Project;
import org.grails.forge.application.generator.ProjectGenerator;
import org.grails.forge.io.ConsoleOutput;
import org.grails.forge.io.MapOutputHandler;
import org.grails.forge.options.*;
import org.grails.forge.util.NameUtils;
import io.swagger.v3.oas.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Previews an application contents.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@Controller("/preview")
public class PreviewController extends AbstractCreateController implements PreviewOperations {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCreateController.class);

    /**
     * Default constructor.
     *
     * @param projectGenerator The project generator
     * @param eventPublisher The event publisher
     */
    public PreviewController(ProjectGenerator projectGenerator, ApplicationEventPublisher eventPublisher) {
        super(projectGenerator, eventPublisher);
    }

    /**
     * Previews the contents of a generated application..
     * @param type The application type The application type
     * @param name The name of the application The name of the application
     * @param features The features The chosen features
     * @param build The build type (optional, defaults to Gradle)
     * @param test The test framework (optional, defaults to JUnit)
     * @param gorm The GORM (optional, defaults to Hibernate)
     * @param servlet The Servlet (optional, defaults to Embedded Tomcat)
     * @return A preview of the application contents.
     */
    @Get(uri = "/{type}/{name}{?features,gorm,servlet,build,test,javaVersion}", produces = MediaType.APPLICATION_JSON)
    @Override
    public PreviewDTO previewApp(
            ApplicationType type,
            String name,
            @Nullable List<String> features,
            @Nullable BuildTool build,
            @Nullable TestFramework test,
            @Nullable GormImpl gorm,
            @Nullable ServletImpl servlet,
            @Nullable JdkVersion javaVersion,
            @Parameter(hidden = true) RequestInfo requestInfo) throws IOException {
        try {
            Project project = NameUtils.parse(name);
            MapOutputHandler outputHandler = new MapOutputHandler();
            projectGenerator.generate(type,
                    project,
                    new Options(
                            test != null ? test.toTestFramework() : null,
                            gorm == null ? GormImpl.DEFAULT_OPTION : gorm,
                            servlet == null ? ServletImpl.DEFAULT_OPTION : servlet,
                            javaVersion == null ? JdkVersion.DEFAULT_OPTION : javaVersion,
                            getOperatingSystem(requestInfo.getUserAgent())),
                    getOperatingSystem(requestInfo.getUserAgent()),
                    features == null ? Collections.emptyList() : features,
                    outputHandler,
                    ConsoleOutput.NOOP);
            Map<String, String> contents = outputHandler.getProject();
            PreviewDTO previewDTO = new PreviewDTO(contents);
            previewDTO.addLink(Relationship.CREATE, requestInfo.link(Relationship.CREATE, type));
            previewDTO.addLink(Relationship.SELF, requestInfo.self());
            return previewDTO;
        } catch (IllegalArgumentException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            LOG.error("Error generating application: " + e.getMessage(), e);
            throw new IOException(e.getMessage(), e);
        }
    }

}
