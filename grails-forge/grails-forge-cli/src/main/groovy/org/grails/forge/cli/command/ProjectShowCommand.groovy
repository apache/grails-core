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

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.console.ProjectCommand
import org.apache.grails.forge.core.state.GenerationOptions
import org.apache.grails.forge.core.state.ProjectLock
import org.apache.grails.forge.core.state.ProjectLock.LockedFeature
import org.apache.grails.forge.core.state.ProjectLock.LockedRepository
import org.apache.grails.forge.core.state.ProjectManifest
import org.apache.grails.forge.core.state.ProjectManifest.RequestedFeature
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateException
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.VersionInfo
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import picocli.CommandLine
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.regex.Pattern

/**
 * Says how the application in the current directory was generated, from the record the generator
 * left under {@code .grails}, and with which command line it is created again.
 *
 * @since 9.0
 */
@CommandLine.Command(name = ProjectShowCommand.NAME, description = [
        'Says how the application in the current directory was generated: its type, name and package, its options, the features that were asked for ' +
                'and left out, and every feature it has.',
        'The last line is the command that creates the application again, with the version of the command line that generated it.'])
@Component
@Scope('prototype')
@CompileStatic
class ProjectShowCommand extends BaseCommand implements Callable<Integer>, ProjectCommand {

    public static final String NAME = 'show'

    private static final Map<ApplicationType, String> CREATE = [(ApplicationType.WEB): CreateAppCommand.NAME, (ApplicationType.REST_API): CreateRestApiCommand.NAME,
                                                                (ApplicationType.WEB_PLUGIN): CreateWebPluginCommand.NAME,
                                                                (ApplicationType.PLUGIN): CreatePluginCommand.NAME] as Map<ApplicationType, String>

    // what a shell takes as it is
    private static final Pattern PLAIN = Pattern.compile('[A-Za-z0-9_@%+=:,./-]+')

    @CommandLine.Option(names = ['--json'], description = 'Write the record and the command as one JSON document for a tool to read.')
    boolean json

    @Override
    Integer call() throws Exception {
        Path current = FileSystemOutputHandler.getDefaultBaseDirectory().toPath()
        ConsoleProject.Search search = ConsoleProject.search(current)
        if (search.project == null) {
            err(search.none('project show'))
            return 1
        }
        ProjectState state
        try {
            state = ProjectState.readSelection(search.project.directory)
        } catch (ProjectStateException e) {
            err(GradleRunner.line(e.getMessage() ?: e.getClass().getSimpleName()))
            if (showStacktrace()) {
                e.printStackTrace(spec.commandLine().getErr())
            }
            return 1
        }
        List<String> arguments = arguments(state)
        List<String> notes = notes(state)
        String line = commandLine(spec.root().name(), arguments)
        if (json) {
            // written as it is: a document is not markup of the command line, and JSON writes a control character as an escape
            spec.commandLine().getOut().println(JsonOutput.toJson(document(state, arguments, line, notes)))
            return 0
        }
        describe(state)
        for (String note : notes) {
            out("@|blue ||@ ${GradleRunner.line(note)}")
        }
        out('@|blue ||@ The application is created again with:')
        // what a shell reads, and no markup of this command
        spec.commandLine().getOut().println(line)
        return 0
    }

    /**
     * @param state the state of a project
     * @return the arguments that create the application again, the name of the create command first:
     * the name with the package, each option that is not what a create command takes when the option
     * is not given, the features that were asked for, a plugin by its coordinates with the SHA-256 of
     * its jar and the repository it came from, and the features that were left out
     */
    static List<String> arguments(ProjectState state) {
        ProjectManifest manifest = state.manifest
        GenerationOptions options = manifest.options
        List<String> arguments = [CREATE.get(manifest.applicationType), "${manifest.defaultPackage}.${manifest.name}".toString()]
        if (options.jdk != JdkVersion.DEFAULT_OPTION) {
            arguments << '--jdk' << String.valueOf(options.jdk.majorVersion())
        }
        if (options.servlet != ServletImpl.DEFAULT_OPTION) {
            arguments << '--servlet' << written(options.servlet)
        }
        if (options.data != GormImpl.DEFAULT_OPTION) {
            arguments << '--data' << written(options.data)
        }
        if (options.reloading != DevelopmentReloading.DEFAULT_OPTION) {
            arguments << '--reloading' << written(options.reloading)
        }
        List<String> features = []
        List<String> digests = []
        List<String> repositories = []
        for (RequestedFeature requested : manifest.requestedFeatures) {
            if (requested.coordinates == null || requested.version == null) {
                features << requested.name
                continue
            }
            String plugin = "${requested.coordinates}:${requested.version}".toString()
            features << plugin
            LockedFeature locked = state.lock.features.find { LockedFeature feature -> feature.name == requested.name }
            if (locked != null && locked.sha256 != null) {
                digests << "${plugin}=${locked.sha256}".toString()
            }
            if (locked != null && locked.origin != null && !repositories.contains(locked.origin)) {
                repositories << locked.origin
            }
        }
        if (!features.isEmpty()) {
            arguments << '--features' << features.join(',')
        }
        for (String repository : repositories) {
            arguments << '--feature-pack-repository' << repository
        }
        for (String digest : digests) {
            arguments << '--feature-pack-sha256' << digest
        }
        if (!manifest.excludedFeatures.isEmpty()) {
            arguments << '--exclude-features' << manifest.excludedFeatures*.name.join(',')
        }
        return arguments
    }

    /**
     * @param cli the name of the command line
     * @param arguments its arguments
     * @return one line that a POSIX shell reads as those arguments: an argument with anything in it
     * that a shell acts on is put between single quotes. What a terminal acts on is not written
     */
    static String commandLine(String cli, List<String> arguments) {
        return ([cli] + arguments).collect { String argument -> quoted(argument.replaceAll(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/, '?')) }.join(' ')
    }

    private static String quoted(String argument) {
        return PLAIN.matcher(argument).matches() ? argument : "'" + argument.replace("'", "'\\''") + "'"
    }

    // what the command line does not say and the command needs all the same to give the same application
    private static List<String> notes(ProjectState state) {
        ProjectLock lock = state.lock
        List<String> notes = []
        List<String> given = lock.features.findAll { LockedFeature feature -> feature.source == LockedFeature.PACK && feature.coordinates == null }
                .collect { LockedFeature feature -> FeatureSelectionException.shown(feature.name) }
        if (!given.isEmpty()) {
            notes << ("The ${given.size() == 1 ? 'feature' : 'features'} ${given.join(', ')} ${given.size() == 1 ? 'is that of a feature pack' : 'are those of feature packs'} " +
                    'given as a directory or a jar, which the record does not name: give it to the command with --feature-pack-dir').toString()
        }
        List<String> unnamed = lock.features.findAll { LockedFeature feature -> feature.coordinates != null && feature.origin == null }
                .collect { LockedFeature feature -> FeatureSelectionException.shown("${feature.coordinates}:${feature.version}") }
        if (!unnamed.isEmpty()) {
            notes << ("The record does not name the repository ${unnamed.join(', ')} was taken from: give it to the command with --feature-pack-repository, " +
                    'unless it is one of those the command asks anyway').toString()
        }
        if (!lock.repositories.isEmpty() || lock.repositoriesNotNamed > 0) {
            notes << 'The application was generated with the repositories of the environment variable GRAILS_REPO_URL, which are written into its build: ' +
                    'set the variable to the same repositories for the command'
        }
        if (lock.generatorVersion != VersionInfo.getVersion()) {
            notes << ("The application was generated by version ${FeatureSelectionException.shown(lock.generatorVersion)} of the command line, and this is " +
                    "${VersionInfo.getVersion()}: the command is for that version").toString()
        }
        return notes
    }

    private void describe(ProjectState state) {
        ProjectManifest manifest = state.manifest
        ProjectLock lock = state.lock
        GenerationOptions options = manifest.options
        out("@|blue ||@ How the application ${shown(manifest.name)} was generated, from its record in ${ProjectState.DIRECTORY}")
        row('type', written(manifest.applicationType))
        row('name', shown(manifest.name))
        row('package', shown(manifest.defaultPackage))
        row('Grails', shown(manifest.grailsVersion))
        row('generated by', "${shown(lock.generatorName)} ${shown(lock.generatorVersion)}")
        row('state', shown(manifest.generationState))
        row('jdk', String.valueOf(options.jdk.majorVersion()))
        row('servlet', written(options.servlet))
        row('data', written(options.data))
        row('reloading', written(options.reloading))
        row('asked for', manifest.requestedFeatures.collect { RequestedFeature feature ->
            feature.coordinates == null ? shown(feature.name) : "${shown(feature.name)} (${shown(feature.coordinates + ':' + feature.version)})".toString()
        }.join(', ') ?: '(no features)')
        row('left out', manifest.excludedFeatures.collect { RequestedFeature feature -> shown(feature.name) }.join(', ') ?: '(no features)')
        for (LockedRepository repository : lock.repositories) {
            row('repository', repository.url == null ? shown(repository.kind) : "${shown(repository.kind)} ${shown(repository.url)}")
        }
        if (lock.repositoriesNotNamed > 0) {
            row('repository', "and ${lock.repositoriesNotNamed} more")
        }
        out("@|blue ||@ The ${lock.features.size()} features it has, in the order they are applied")
        for (LockedFeature feature : lock.features) {
            String plugin = feature.coordinates == null ? '' : ", ${shown(feature.coordinates + ':' + feature.version)}, sha256 ${shown(feature.sha256)}" +
                    (feature.origin == null ? '' : ", from ${shown(feature.origin)}")
            out("    ${shown(feature.name)} (${shown(feature.source)}${plugin})")
        }
    }

    private void row(String what, String value) {
        out("    ${what.padRight(13)} ${value}")
    }

    private static Map<String, Object> document(ProjectState state, List<String> arguments, String line, List<String> notes) {
        ProjectManifest manifest = state.manifest
        ProjectLock lock = state.lock
        Map<String, Object> document = new LinkedHashMap<>()
        document.put('format', 1)
        document.put('applicationType', written(manifest.applicationType))
        document.put('name', manifest.name)
        document.put('defaultPackage', manifest.defaultPackage)
        document.put('grailsVersion', manifest.grailsVersion)
        document.put('generator', entries(name: lock.generatorName, version: lock.generatorVersion))
        document.put('templateApi', lock.templateApi)
        document.put('generationState', manifest.generationState)
        document.put('options', entries(jdk: manifest.options.jdk.majorVersion(), servlet: written(manifest.options.servlet), data: written(manifest.options.data),
                reloading: written(manifest.options.reloading)))
        document.put('requestedFeatures', manifest.requestedFeatures.collect { RequestedFeature feature ->
            entries(name: feature.name, coordinates: feature.coordinates, version: feature.version)
        })
        document.put('excludedFeatures', manifest.excludedFeatures*.name)
        document.put('repositories', lock.repositories.collect { LockedRepository repository -> entries(kind: repository.kind, url: repository.url) })
        document.put('repositoriesNotNamed', lock.repositoriesNotNamed)
        document.put('features', lock.features.collect { LockedFeature feature ->
            entries(name: feature.name, source: feature.source, coordinates: feature.coordinates, version: feature.version, sha256: feature.sha256, origin: feature.origin,
                    catalog: feature.catalog, catalogSha256: feature.catalogSha256)
        })
        document.put('command', entries(name: arguments.first(), arguments: arguments.drop(1), line: line))
        document.put('notes', notes)
        return document
    }

    // in the order given, without what there is not
    private static Map<String, Object> entries(Map<String, Object> all) {
        Map<String, Object> entries = new LinkedHashMap<>()
        for (Map.Entry<String, Object> each : all.entrySet()) {
            if (each.value != null) {
                entries.put(each.key, each.value)
            }
        }
        return entries
    }

    private static String written(Enum value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-')
    }

    private static String shown(String text) {
        return FeatureSelectionException.shown(text)
    }
}
