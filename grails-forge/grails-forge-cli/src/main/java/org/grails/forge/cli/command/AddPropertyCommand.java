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

import java.io.File;
import java.io.IOException;

import jakarta.inject.Inject;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.functional.ThrowingSupplier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import grails.codegen.model.AbstractMemberDefinition;
import grails.codegen.model.DomainFieldModifier;
import grails.codegen.model.PropertyDefinition;

import org.grails.forge.cli.CodeGenConfig;
import org.grails.forge.io.ConsoleOutput;
import org.grails.forge.io.FileSystemOutputHandler;
import org.grails.forge.io.OutputHandler;

/**
 * CLI command to add a property to an existing domain class.
 *
 * @since 7.1
 */
@Command(name = AddPropertyCommand.NAME, description = "Adds a property to an existing domain class")
public class AddPropertyCommand extends CodeGenCommand {

    public static final String NAME = "add-property";

    @ReflectiveAccess
    @Parameters(index = "0", paramLabel = "DOMAIN-CLASS",
            description = "The name of the domain class (e.g., Book)")
    String domainClassName;

    @ReflectiveAccess
    @Parameters(index = "1", paramLabel = "PROPERTY:TYPE",
            description = "The property specification in name:Type format (e.g., title:String)")
    String propertySpec;

    @ReflectiveAccess
    @Option(names = {"--nullable"}, description = "Mark the property as nullable")
    boolean nullableFlag;

    @ReflectiveAccess
    @Option(names = {"--not-nullable"}, description = "Mark the property as NOT nullable (generates @NotNull)")
    boolean notNullableFlag;

    @ReflectiveAccess
    @Option(names = {"--blank"}, description = "Allow blank values (String properties only)")
    boolean blankFlag;

    @ReflectiveAccess
    @Option(names = {"--not-blank"}, description = "Disallow blank values (generates @NotBlank)")
    boolean notBlankFlag;

    @ReflectiveAccess
    @Option(names = {"--max-size"}, description = "Maximum size constraint (String properties only)")
    Integer maxSize;

    @ReflectiveAccess
    @Option(names = {"--min-size"}, description = "Minimum size constraint (String properties only)")
    Integer minSize;

    @ReflectiveAccess
    @Option(names = {"--constraint-style"}, description = "Constraint style: grails (default), jakarta, or both")
    String constraintStyle;

    private final DomainFieldModifier domainFieldModifier;

    @Inject
    public AddPropertyCommand(@Parameter CodeGenConfig config) {
        super(config);
        this.domainFieldModifier = new DomainFieldModifier();
    }

    public AddPropertyCommand(CodeGenConfig config,
                              ThrowingSupplier<OutputHandler, IOException> outputHandlerSupplier,
                              ConsoleOutput consoleOutput) {
        super(config, outputHandlerSupplier, consoleOutput);
        this.domainFieldModifier = new DomainFieldModifier();
    }

    @Override
    public boolean applies() {
        return true;
    }

    @Override
    public Integer call() throws Exception {
        PropertyDefinition property;
        try {
            property = PropertyDefinition.parse(propertySpec);
        } catch (IllegalArgumentException e) {
            err("Invalid property specification: " + e.getMessage());
            return 1;
        }

        if (nullableFlag) {
            property.setNullable(true);
        } else if (notNullableFlag) {
            property.setNullable(false);
        }
        if (blankFlag) {
            property.setBlank(true);
        } else if (notBlankFlag) {
            property.setBlank(false);
        }
        if (maxSize != null) {
            property.setMaxSize(maxSize);
        }
        if (minSize != null) {
            property.setMinSize(minSize);
        }
        if (constraintStyle != null) {
            try {
                property.setConstraintStyle(AbstractMemberDefinition.ConstraintStyle.valueOf(constraintStyle.toUpperCase()));
            } catch (IllegalArgumentException e) {
                err("Invalid constraint style: " + constraintStyle + ". Use: grails, jakarta, or both");
                return 1;
            }
        }

        try {
            property.validate();
        } catch (IllegalArgumentException e) {
            err("Invalid property definition: " + e.getMessage());
            return 1;
        }

        File projectDir = FileSystemOutputHandler.getDefaultBaseDirectory();
        File domainFile = domainFieldModifier.findDomainFile(projectDir, domainClassName);

        if (domainFile == null || !domainFile.exists()) {
            err("Domain class not found: " + domainClassName);
            err("Run 'grails create-domain-class " + domainClassName + "' first.");
            return 1;
        }

        if (domainFieldModifier.memberExists(domainFile, property.getName())) {
            err("Property '" + property.getName() + "' already exists in " + domainClassName);
            return 1;
        }

        try {
            domainFieldModifier.addProperty(domainFile, property);
            out("@|blue ||@ Added property '" + property.getName() + "' to " + getRelativePath(projectDir, domainFile));
        } catch (Exception e) {
            err("Failed to add property: " + e.getMessage());
            return 1;
        }

        return 0;
    }

    private String getRelativePath(File base, File file) {
        try {
            return base.toPath().relativize(file.toPath()).toString();
        } catch (Exception e) {
            return file.getAbsolutePath();
        }
    }
}
