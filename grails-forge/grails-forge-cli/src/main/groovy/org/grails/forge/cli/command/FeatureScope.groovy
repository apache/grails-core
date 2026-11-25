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
package org.grails.forge.cli.command

import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import groovy.transform.CompileStatic
import jakarta.annotation.Nullable
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.RecordedPackOptions
import org.apache.grails.forge.core.change.FeatureAddition
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackDescriptorException
import org.apache.grails.forge.core.pack.PackLoader
import org.apache.grails.forge.core.state.GenerationOptions
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.options.OptionName
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import picocli.CommandLine

/**
 * The application the commands {@code feature search}, {@code feature info} and {@code feature diff}
 * speak of: the one of the current directory, by its record, or a new application of a type. It is
 * the application of the current directory when that is in a project with a record and no type is
 * named; with {@code --type}, and anywhere else, it is a new application of the type, which is
 * {@code web} when none is named, as {@code create-app} creates it.
 *
 * @since 9.0
 */
@CompileStatic
final class FeatureScope {

    /** What a text to search by, and a line of what is written, is held to. */
    public static final int WIDTH = 100

    private static final int NAME_COLUMN = 30
    private static final String NEW_APPLICATION = 'com.example.demo'

    /** The directory of the project, or {@code null} for a new application. */
    final Path directory

    /** The record of the project as far as it says what the application has, or {@code null} for a new application. */
    final ProjectState state

    /** The type of the application. */
    final ApplicationType type

    /** The feature packs that are not part of Grails, as they were given to the command. */
    final List<Pack> givenPacks

    /** Those of them the application is generated with: for a project, the ones its record has. */
    final List<Pack> packs

    /** The features there are for the application. */
    final AvailableFeatures available

    private final ProjectGenerator generator
    private final OperatingSystem operatingSystem
    private Set<String> defaults
    private boolean defaultsAsked

    private FeatureScope(ProjectGenerator generator, Path directory, ProjectState state, ApplicationType type, List<Pack> givenPacks, List<Pack> packs,
                         @Nullable OperatingSystem operatingSystem) {
        this.generator = generator
        this.directory = directory
        this.state = state
        this.type = type
        this.givenPacks = givenPacks
        this.packs = packs
        this.operatingSystem = operatingSystem
        this.available = generator.availableFeatures(type, packs)
    }

    /**
     * @param commandLine the command the type was given to
     * @param given what {@code --type} was given, or {@code null}
     * @return the type, or {@code null} when none was given
     * @throws CommandLine.ParameterException when it is no type of application
     */
    static ApplicationType typeOf(CommandLine commandLine, String given) {
        if (given == null) {
            return null
        }
        ApplicationType type = ApplicationType.values().find { ApplicationType each -> written(each) == given.toLowerCase(Locale.ROOT).replace('_', '-') }
        if (type == null) {
            throw new CommandLine.ParameterException(commandLine, "${FeatureSelectionException.shown(given)} is no type of application: --type is one of " +
                    ProjectMigrateCommand.TYPES.join(', '))
        }
        return type
    }

    /**
     * @param generator the generator of the applications
     * @param given the type that was named, or {@code null}
     * @param current the current directory
     * @param packOptions the feature packs that were given to the command
     * @param artifacts what fetches the jar of a plugin the application has the feature pack of
     * @param grailsRepoUrl the repositories that are asked for such a jar
     * @param operatingSystem the operating system the generator is told of
     * @param said where it is said which jar is fetched again
     * @param warned where a repository that is not asked is named
     * @return the application the command speaks of
     * @throws org.apache.grails.forge.core.state.ProjectStateException when the record of the project cannot be read
     * @throws org.apache.grails.forge.core.change.ProjectChangeException when a pack of the record is not to be had, or another was given
     * @throws IllegalArgumentException when a pack that was given is none
     */
    static FeatureScope of(ProjectGenerator generator, ApplicationType given, Path current, RecordedPackOptions packOptions, PackArtifacts artifacts,
                           String grailsRepoUrl, @Nullable OperatingSystem operatingSystem, Consumer<String> said, Consumer<String> warned) {
        ConsoleProject.Search search = ConsoleProject.search(current)
        if (given == null && search.project != null && ProjectState.exists(search.project.directory)) {
            Path directory = search.project.directory
            ProjectState state = ProjectState.readSelection(directory, true)
            // an application no feature is added to as it is has its features of Grails said all the same, without its packs being asked for
            boolean asIs = FeatureAvailability.refusal(state) == null
            List<Pack> givenPacks = asIs ? packOptions.packs(directory, artifacts, grailsRepoUrl, said, warned) : Collections.<Pack>emptyList()
            List<Pack> ofTheLock = asIs ? FeatureAddition.recordedPacks(state.lock, state.manifest.preset, givenPacks) : Collections.<Pack>emptyList()
            return new FeatureScope(generator, directory, state, state.manifest.applicationType, givenPacks, ofTheLock, operatingSystem)
        }
        List<Pack> givenPacks = newApplicationPacks(packOptions)
        return new FeatureScope(generator, null, null, given ?: ApplicationType.DEFAULT_OPTION, givenPacks, givenPacks, operatingSystem)
    }

    // the packs of directories and jars that were given for a new application, which has no record to hold them to
    private static List<Pack> newApplicationPacks(RecordedPackOptions packOptions) {
        PackLoader loader = new PackLoader()
        List<Pack> packs = []
        try {
            for (File directory : packOptions.directories) {
                packs << loader.fromDirectory(directory.toPath().toAbsolutePath().normalize())
            }
            for (File jar : packOptions.jars) {
                Path path = jar.toPath().toAbsolutePath().normalize()
                byte[] bytes
                try {
                    bytes = Files.readAllBytes(path)
                } catch (IOException e) {
                    throw new IllegalArgumentException("The jar ${path} cannot be read: ${e.class.simpleName}")
                }
                packs << loader.fromJarBytes(bytes, bytes.length, path.toString())
            }
        } catch (PackDescriptorException e) {
            throw new IllegalArgumentException(e.message)
        }
        return packs
    }

    /**
     * @return whether the application is that of the current directory
     */
    boolean isOfProject() {
        return state != null
    }

    /**
     * @return the type of the application as it is written
     */
    String getTypeName() {
        return written(type)
    }

    /**
     * @return the application as a message names it
     */
    String getDescribed() {
        return isOfProject() ? "the application ${FeatureSelectionException.shown(state.manifest.name)} (${typeName})".toString() :
                "a new application of type ${typeName}".toString()
    }

    /**
     * @return the features of the application by name, or {@code null} for a new application
     */
    List<String> getHas() {
        return isOfProject() ? state.lock.featureNames : null
    }

    /**
     * @return the features that are shown for the application, by name
     */
    List<Feature> getVisible() {
        return available.getFeatures().sorted(Comparator.comparing(Feature::getName)).toList()
    }

    /**
     * @return the project the generator is given: that of the record, or one that stands for a new application
     */
    Project getProject() {
        return NameUtils.parse(isOfProject() ? "${state.manifest.defaultPackage}.${state.manifest.name}".toString() : NEW_APPLICATION)
    }

    /**
     * @return the options the application is generated with: those of the record, each a choice a
     * feature does not change, or the defaults for a new application
     */
    Options getOptions() {
        if (!isOfProject()) {
            return new Options()
        }
        GenerationOptions recorded = state.manifest.options
        return new Options(recorded.reloading, recorded.data, recorded.servlet, recorded.jdk, operatingSystem)
                .withGiven(EnumSet.allOf(OptionName))
                .withSubproject(recorded.subproject)
    }

    /**
     * @return the names of the features an application of the type, with these options, has without
     * any being asked for; {@code null} when they cannot be worked out
     */
    Set<String> getDefaults() {
        if (!defaultsAsked) {
            defaultsAsked = true
            try {
                defaults = new LinkedHashSet<String>(generator.createGeneratorContext(type, project, options, operatingSystem, Collections.<String>emptyList(),
                        ConsoleOutput.NOOP, packs).getFeatures().getFeatures()*.getName())
            } catch (RuntimeException ignored) {
                defaults = null
            }
        }
        return defaults
    }

    /**
     * @param what what a row is of
     * @param value what is said of it
     * @return the row as lines, the value broken between words where it does not fit the width
     */
    static List<String> row(String what, String value) {
        String start = "    ${what.padRight(NAME_COLUMN)} "
        List<String> lines = []
        StringBuilder line = new StringBuilder(start)
        boolean first = true
        for (String word : value.split(' ')) {
            if (!first && line.length() + 1 + word.length() > WIDTH) {
                lines << line.toString()
                line = new StringBuilder(' ' * start.length())
                first = true
            }
            line.append(first ? word : ' ' + word)
            first = false
        }
        lines << line.toString().stripTrailing()
        return lines
    }

    static String written(ApplicationType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', '-')
    }
}
