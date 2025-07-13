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
package org.grails.forge.cli.command;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.ContextFactory;
import org.grails.forge.application.Project;
import org.grails.forge.application.generator.ProjectGenerator;
import org.grails.forge.feature.AvailableFeatures;
import org.grails.forge.io.FileSystemOutputHandler;
import org.grails.forge.io.OutputHandler;
import org.grails.forge.options.*;
import org.grails.forge.util.NameUtils;
import picocli.CommandLine;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public abstract class CreateCommand extends BaseCommand implements Callable<Integer> {

    protected final AvailableFeatures availableFeatures;

    @ReflectiveAccess
    @CommandLine.Parameters(arity = "0..1", paramLabel = "NAME", description = "The name of the application to create.")
    String name;

    @ReflectiveAccess
    @CommandLine.Option(names = {"-t", "--test"}, paramLabel = "TEST", description = "Which test framework to use. Possible values: ${COMPLETION-CANDIDATES}.", completionCandidates = TestFrameworkCandidates.class, converter = TestFrameworkConverter.class)
    TestFramework test;

    @ReflectiveAccess
    @CommandLine.Option(names = {"-g", "--gorm"}, paramLabel = "GORM Implementation", description = "Which GORM Implementation to configure. Possible values: ${COMPLETION-CANDIDATES}.", completionCandidates = GormImplCandidates.class, converter = GormImplConverter.class)
    GormImpl gormImpl;

    @ReflectiveAccess
    @CommandLine.Option(names = {"-s", "--servlet"}, paramLabel = "Servlet Implementation", description = "Which Servlet Implementation to configure. Possible values: ${COMPLETION-CANDIDATES}.", completionCandidates = ServletImplCandidates.class, converter = ServletImplConverter.class)
    ServletImpl servletImpl;

    @ReflectiveAccess
    @CommandLine.Option(names = {"-i", "--inplace"}, description = "Create a service using the current directory")
    boolean inplace;

    @ReflectiveAccess
    @CommandLine.Option(names = {"--list-features"}, description = "Output the available features and their descriptions")
    boolean listFeatures;

    @ReflectiveAccess
    @CommandLine.Option(names = {"--jdk", "--java-version"}, description = "The JDK version the project should target")
    Integer javaVersion;

    private final ContextFactory contextFactory;
    private final ApplicationType applicationType;
    private final ProjectGenerator projectGenerator;

    public CreateCommand(AvailableFeatures availableFeatures,
                         ContextFactory contextFactory,
                         ApplicationType applicationType,
                         ProjectGenerator projectGenerator) {
        this.availableFeatures = availableFeatures;
        this.contextFactory = contextFactory;
        this.applicationType = applicationType;
        this.projectGenerator = projectGenerator;
    }

    /**
     * @return The selected features.
     */
    protected abstract @NonNull List<String> getSelectedFeatures();

    protected Map<String, Object> getAdditionalOptions() {
        return Collections.emptyMap();
    }

    @Override
    public Integer call() throws Exception {
        if (listFeatures) {
            new ListFeatures(availableFeatures,
                    new Options(test, gormImpl, servletImpl, getJdkVersion(), getOperatingSystem()),
                    applicationType,
                    getOperatingSystem(),
                    contextFactory).output(this);
            return 0;
        }
        Project project;
        try {
            project = NameUtils.parse(name);
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), StringUtils.isEmpty(name) ? "Specify an application name or use --inplace to create an application in the current directory" : e.getMessage());
        }

        OutputHandler outputHandler = new FileSystemOutputHandler(project, inplace, this);

        generate(project, outputHandler);

        out("@|blue ||@ Application created at " + outputHandler.getOutputLocation());
        return 0;
    }

    public void generate(OutputHandler outputHandler) throws Exception {
        generate(NameUtils.parse(name), outputHandler);
    }

    public void generate(Project project, OutputHandler outputHandler) throws Exception {
        Options options = new Options(test, gormImpl, servletImpl, getJdkVersion(), getOperatingSystem(), getAdditionalOptions());

        projectGenerator.generate(applicationType, project, options, getOperatingSystem(), getSelectedFeatures(), outputHandler, this);
    }

    private JdkVersion getJdkVersion() {
        if (javaVersion == null) {
            return JdkVersion.DEFAULT_OPTION;
        } else {
            return JdkVersion.valueOf(javaVersion);
        }
    }
}
