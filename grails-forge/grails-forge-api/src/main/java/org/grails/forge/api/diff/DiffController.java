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
package org.grails.forge.api.diff;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.exceptions.HttpStatusException;
import org.grails.forge.api.RequestInfo;
import org.grails.forge.api.UserAgentParser;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.Project;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.application.generator.ProjectGenerator;
import org.grails.forge.diff.FeatureDiffer;
import org.grails.forge.io.ConsoleOutput;
import org.grails.forge.options.*;
import org.grails.forge.util.NameUtils;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * A controller for performing Diffs.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@Controller("/diff")
public class DiffController implements DiffOperations {

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private final ProjectGenerator projectGenerator;
    private final FeatureDiffer featureDiffer;
    private final Project project;

    /**
     * Default constructor.
     *
     * @param projectGenerator The project generator
     * @param featureDiffer    The feature differ
     */
    public DiffController(ProjectGenerator projectGenerator, FeatureDiffer featureDiffer) {
        this.projectGenerator = projectGenerator;
        this.featureDiffer = featureDiffer;
        this.project = NameUtils.parse("example");
    }

    /**
     * Returns a diff for the given application type and feature.
     *
     * @param type        The application type
     * @param feature     The feature
     * @param build       The build tool
     * @param test        The test framework
     * @param gorm        The GORM implementation
     * @param servlet     The Servlet implementation
     * @param javaVersion The java version
     * @param requestInfo The request info
     * @return A string representing the difference
     */
    @Get(uri = "/{type}/feature/{feature}{?gorm,servlet,build,test,javaVersion,name}",
            produces = MediaType.TEXT_PLAIN)
    @Override
    @ApiResponse(responseCode = "404", description = "If no difference is found")
    @ApiResponse(responseCode = "400", description = "If the supplied parameters are invalid")
    @ApiResponse(responseCode = "200", description = "A textual diff", content = @Content(mediaType = "text/plain"))
    public Publisher<String> diffFeature(
            @NotNull ApplicationType type,
            @Nullable String name,
            @NonNull @NotBlank String feature,
            @Nullable BuildTool build,
            @Nullable TestFramework test,
            @Nullable GormImpl gorm,
            @Nullable ServletImpl servlet,
            @Nullable JdkVersion javaVersion,
            @Parameter(hidden = true) RequestInfo requestInfo) {

        ProjectGenerator projectGenerator;
        GeneratorContext generatorContext;
        try {
            Project project = name != null ? NameUtils.parse(name) : this.project;
            Options options = new Options(
                    test != null ? test : TestFramework.DEFAULT_OPTION,
                    gorm != null ? gorm : GormImpl.DEFAULT_OPTION,
                    servlet != null ? servlet : ServletImpl.DEFAULT_OPTION,
                    javaVersion != null ? javaVersion : JdkVersion.DEFAULT_OPTION
            );
            projectGenerator = this.projectGenerator;
            generatorContext = projectGenerator.createGeneratorContext(
                    type,
                    project,
                    options,
                    UserAgentParser.getOperatingSystem(requestInfo.getUserAgent()),
                    Collections.singletonList(feature),
                    ConsoleOutput.NOOP
            );
        } catch (IllegalArgumentException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        return diffFlowable(projectGenerator, generatorContext);
    }

    /**
     * Diffs the whole application for all selected features.
     *
     * @param type        The application type
     * @param name        The name of the application
     * @param features    The features
     * @param build       The build tool
     * @param test        The test framework
     * @param servlet     The Servlet implementation
     * @param requestInfo The request info
     * @return An HTTP response that emits a writable
     */
    @Get(uri = "/{type}/{name}{?features,gorm,servlet,build,test,javaVersion}", produces = MediaType.TEXT_PLAIN)
    @Override
    @ApiResponse(responseCode = "404", description = "If no difference is found")
    @ApiResponse(responseCode = "400", description = "If the supplied parameters are invalid")
    @ApiResponse(responseCode = "200", description = "A textual diff", content = @Content(mediaType = "text/plain"))
    public Publisher<String> diffApp(
            ApplicationType type,
            @Pattern(regexp = "[\\w\\d-_\\.]+") String name,
            @Nullable List<String> features,
            @Nullable BuildTool build,
            @Nullable TestFramework test,
            @Nullable GormImpl gorm,
            @Nullable ServletImpl servlet,
            @Nullable JdkVersion javaVersion,
            @Parameter(hidden = true) RequestInfo requestInfo) throws IOException {
        ProjectGenerator projectGenerator;
        GeneratorContext generatorContext;
        try {
            Project project = name != null ? NameUtils.parse(name) : this.project;
            Options options = new Options(
                    test != null ? test : TestFramework.DEFAULT_OPTION,
                    gorm != null ? gorm : GormImpl.DEFAULT_OPTION,
                    servlet != null ? servlet : ServletImpl.DEFAULT_OPTION,
                    javaVersion != null ? javaVersion : JdkVersion.DEFAULT_OPTION
            );
            projectGenerator = this.projectGenerator;
            generatorContext = projectGenerator.createGeneratorContext(
                    type,
                    project,
                    options,
                    UserAgentParser.getOperatingSystem(requestInfo.getUserAgent()),
                    features != null ? features : Collections.emptyList(),
                    ConsoleOutput.NOOP
            );
        } catch (IllegalArgumentException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        return diffFlowable(projectGenerator, generatorContext);
    }

    private Publisher<String> diffFlowable(ProjectGenerator projectGenerator, GeneratorContext generatorContext) {
        return Flux.create(emitter -> {
            try {
                // empty string so there is at least some content
                // if there is no difference
                emitter.next("");
                featureDiffer.produceDiff(
                        projectGenerator,
                        generatorContext,
                        new ConsoleOutput() {
                            @Override
                            public void out(String message) {
                                emitter.next(message + LINE_SEPARATOR);
                            }

                            @Override
                            public void err(String message) {
                                // will never be called
                            }

                            @Override
                            public void warning(String message) {
                                // will never be called
                            }

                            @Override
                            public boolean showStacktrace() {
                                return false;
                            }

                            @Override
                            public boolean verbose() {
                                return false;
                            }
                        }
                );
                emitter.complete();
            } catch (Exception e) {
                emitter.error(new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not produce diff: " + e.getMessage()));
            }
        });
    }
}
