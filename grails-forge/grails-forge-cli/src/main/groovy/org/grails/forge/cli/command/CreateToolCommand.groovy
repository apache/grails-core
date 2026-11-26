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

import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.function.Supplier
import groovy.transform.CompileStatic
import jakarta.inject.Inject
import org.apache.grails.forge.cli.artefact.ArtefactTemplate
import org.apache.grails.forge.cli.artefact.ArtefactTemplates
import org.apache.grails.forge.cli.artefact.ProjectFiles
import org.apache.grails.forge.cli.console.UsageHelp
import org.grails.forge.application.Project
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.OutputHandler
import org.grails.forge.template.DefaultTemplateRenderer
import org.grails.forge.util.NameUtils
import org.grails.forge.util.ThrowingSupplier
import picocli.CommandLine

/**
 * {@code create-tool}: a tool of the application, which the build runs, under {@code src/cli/groovy};
 * with {@code --standalone} a tool that runs as a process of its own, without Gradle and without the
 * application, under {@code src/standalone/groovy}. Nothing is written to {@code grails-app/commands},
 * where tools were before and which is deprecated, and no class of a deprecated type is written.
 *
 * <p>The template is {@code artifacts/Tool.groovy}, or {@code artifacts/StandaloneTool.groovy} with
 * {@code --standalone}, of the project when it has one under {@code src/main/templates}. A project
 * that has {@code artifacts/Command.groovy} there, the template of {@code create-command} of earlier
 * versions, and not the one of a tool, has a tool that the build runs written from it, after a
 * warning, into a file of the name that template gives its class.</p>
 *
 * <p>A name without a package is a tool of the default package of an application. A plugin publishes
 * its tools in two jars beside its own, {@code -cli} and {@code -cli-standalone}, and no package of a
 * plugin is to be in two of its jars: there a tool of {@code src/cli} given without a package goes into
 * {@code <package>.cli}, and a standalone tool into {@code <package>.cli.standalone}. A name with a
 * package is taken as it is given, with a warning in a plugin when a source of that package is in a
 * folder of another of its jars.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = CreateToolCommand.NAME, description = [
        'Creates a tool of the application: a class the grails command runs by its name.',
        'A tool is written to src/cli/groovy and is run by the build, with the application started around it unless it says otherwise.',
        'With --standalone it is written to src/standalone/groovy and runs as a process of its own, without Gradle and without the application, on the files of the project.'
])
@CompileStatic
class CreateToolCommand extends CodeGenCommand {

    public static final String NAME = 'create-tool'

    /** Where a tool the build runs is written. */
    public static final String CLI_SOURCES = 'src/cli/groovy'

    /** Where a tool that runs as a process of its own is written. */
    public static final String STANDALONE_SOURCES = 'src/standalone/groovy'

    /** The deprecated folder of tools, which is never written to. */
    public static final String LEGACY_SOURCES = 'grails-app/commands'

    /** The template of create-command of earlier versions, which a project may have. */
    public static final String COMMAND_TEMPLATE = ArtefactTemplates.DIRECTORY + '/artifacts/Command.groovy'

    /** What the default package of a plugin is followed by for a tool of {@code src/cli} given without a package. */
    public static final String CLI_PACKAGE = '.cli'

    /** What the default package of a plugin is followed by for a standalone tool given without a package. */
    public static final String STANDALONE_PACKAGE = '.cli.standalone'

    private static final String STANDALONE_FOLDER = 'src/standalone/'

    private static final String PLUGIN_JAR = 'the jar of the plugin'
    private static final String CLI_JAR = 'its companion -cli'
    private static final String STANDALONE_JAR = 'its companion -cli-standalone'

    // The folders whose classes a plugin publishes in a jar, by what the jar is called in a message: the plugin's own,
    // which has src/main and the folders of grails-app that are sources of the plugin as well, and each companion.
    private static final Map<String, List<String>> JARS = [
            (PLUGIN_JAR): ['src/main/groovy', 'src/main/java'] as List<String>,
            (CLI_JAR): [CLI_SOURCES, 'src/cli/java', LEGACY_SOURCES] as List<String>,
            (STANDALONE_JAR): [STANDALONE_SOURCES, 'src/standalone/java'] as List<String>,
    ].asImmutable() as Map<String, List<String>>

    // The folders of grails-app that are no sources of the jar of a plugin: its tools, and what it has as resources.
    private static final Set<String> NOT_OF_THE_JAR = ['commands', 'assets', 'migrations', 'views', 'i18n', 'conf'].toSet().asImmutable()

    @CommandLine.Parameters(paramLabel = 'TOOL-NAME', description = 'The name of the tool to create')
    String toolName

    @CommandLine.Option(names = ['--standalone'], description = 'Creates a tool that runs as a process of its own, without Gradle and without the application, in src/standalone/groovy')
    boolean standalone

    @Inject
    CreateToolCommand(CodeGenConfig config) {
        super(config)
    }

    CreateToolCommand(CodeGenConfig config,
            ThrowingSupplier<OutputHandler, IOException> outputHandlerSupplier,
            ConsoleOutput consoleOutput) {
        super(config, outputHandlerSupplier, consoleOutput)
    }

    @Override
    boolean applies() {
        return true
    }

    @Override
    List<ArtefactTemplate> getTemplates() {
        return [ArtefactTemplate.TOOL, ArtefactTemplate.STANDALONE_TOOL]
    }

    @Override
    Integer call() throws Exception {
        String folder = standalone ? STANDALONE_SOURCES : CLI_SOURCES
        Project names = namesIn(folder)
        File project = new File(outputHandler().getOutputLocation())
        ArtefactTemplate template = standalone ? ArtefactTemplate.STANDALONE_TOOL : ArtefactTemplate.TOOL
        // the template of create-command a project has, for a tool the build runs, where it has none of a tool
        ArtefactTemplates.Source command = standalone || ArtefactTemplates.findInProject(project, ArtefactTemplates.projectPath(template)) != null ? null :
                ArtefactTemplates.findInProject(project, COMMAND_TEMPLATE)
        String ending = command == null ? 'Tool' : 'Command'
        String path = DefaultTemplateRenderer.resolvePath("${folder}/{packagePath}/{className}${ending}.groovy", names.getProperties()).replace('//', '/')
        String other = otherSource(project, ending)
        if (other != null) {
            err("${other} is a tool of the name ${NameUtils.getScriptName(names.getClassName())} already, and a project has one tool of a name. " +
                    "${other.startsWith(LEGACY_SOURCES + '/') ? "${LEGACY_SOURCES} is deprecated: move that tool to ${CLI_SOURCES} as it is" : 'Rename one of them'}. Nothing was written")
            return 1
        }
        Supplier<String> text
        if (command == null) {
            text = { -> artefact(template, names) } as Supplier<String>
        } else {
            warning("Using the template ${COMMAND_TEMPLATE} of the project, the template of create-command, which is deprecated: " +
                    "a tool is written from ${ArtefactTemplates.projectPath(ArtefactTemplate.TOOL)}, which install-templates copies into the project")
            text = { -> ArtefactTemplates.render(command, ArtefactTemplate.TOOL, names) } as Supplier<String>
        }
        Integer exit = write('Rendered tool to ', [(path): text] as Map<String, Supplier<String>>, overwrite)
        if (exit == 0) {
            String shared = sharedPackage(project, names)
            if (shared != null) {
                warning(shared)
            }
            // the name the tool has by its class: the build lists it when it compiles the project, and
            // the command line has the build list it when the name is typed before that
            out("@|blue ||@ Run it with: ${UsageHelp.toType(spec, NameUtils.getScriptName(names.getClassName()))}")
        }
        return exit
    }

    /**
     * The package a tool of a folder is written into when its name is given without one. In an
     * application it is the default package of the project. A plugin publishes its tools in jars of
     * their own beside its own jar, {@code -cli} with those of {@code src/cli} and
     * {@code -cli-standalone} with those of {@code src/standalone}, and no package is to be in two of
     * these jars: a tool of {@code src/cli} goes into {@code <package>.cli} and a standalone tool into
     * {@code <package>.cli.standalone}, beside the default package, which is that of the plugin class.
     * What {@code grails-app/commands} has was written into the default package by
     * {@code create-command} of earlier versions.
     *
     * @param folder the folder of tools, as {@link #CLI_SOURCES}
     * @return the package, or {@code null} for a project without a default package
     */
    private String packageOf(String folder) {
        String project = config?.getDefaultPackage()
        if (project == null || !config.getApplicationType()?.isPlugin() || folder == LEGACY_SOURCES) {
            return project
        }
        return project + (folder.startsWith(STANDALONE_FOLDER) ? STANDALONE_PACKAGE : CLI_PACKAGE)
    }

    // The names of the tool in a folder of tools: given with a package, in that package; given without one, in the
    // package a tool of the folder is written into.
    private Project namesIn(String folder) {
        return newArtefact(toolName, 'Tool', packageOf(folder))
    }

    // The source of a tool of the same name in another folder of tools, in the package a tool of that name is written
    // into there, where the build would have two tools of the name, or two classes of the name: its path in the
    // project, or null.
    private String otherSource(File project, String written) {
        String own = standalone ? STANDALONE_SOURCES : CLI_SOURCES
        for (String folder : [CLI_SOURCES, 'src/cli/java', STANDALONE_SOURCES, 'src/standalone/java', LEGACY_SOURCES]) {
            Map<String, String> names = namesIn(folder).getProperties()
            for (String ending : ['Tool.groovy', 'Command.groovy', 'Tool.java', 'Command.java']) {
                String path = DefaultTemplateRenderer.resolvePath("${folder}/{packagePath}/{className}${ending}", names).replace('//', '/')
                // what is in the folder the tool is written to is replaced with -f, or refused as being there already
                if (folder == own && ending == written + '.groovy') {
                    continue
                }
                if (ProjectFiles.isThere(project, path)) {
                    return path
                }
            }
        }
        return null
    }

    // What is said when the package of a tool of a plugin has a class in another jar of the plugin, which a name given
    // with its package can do: the package would then be in two jars. Only the directory of the package is looked at,
    // in each folder of sources of the other jars. Nothing is said in an application, which publishes no tool, and for
    // a tool in the unnamed package.
    private String sharedPackage(File project, Project names) {
        if (!config?.getApplicationType()?.isPlugin() || !names.getPackageName()) {
            return null
        }
        String own = standalone ? STANDALONE_JAR : CLI_JAR
        Map<String, List<String>> jars = new LinkedHashMap<>(JARS)
        jars.put(PLUGIN_JAR, JARS[PLUGIN_JAR] + sourcesOfGrailsApp(project))
        for (Map.Entry<String, List<String>> jar : jars) {
            if (jar.key == own) {
                continue
            }
            String found = jar.value.find { String folder -> hasSource(project.toPath().resolve(folder).resolve(names.getPackagePath())) }
            if (found != null) {
                return "The package ${names.getPackageName()} has classes in ${found}, which are in ${jar.key}, and the tool puts it into ${own} as well. " +
                        'No package of a plugin is to be in two of its jars (the plugin, -cli and -cli-standalone), so that each jar can be a module of its own: ' +
                        "give the tool a package of its own, as ${packageOf(standalone ? STANDALONE_SOURCES : CLI_SOURCES)}"
            }
        }
        return null
    }

    // the folders of grails-app that are sources of the jar of the plugin, in the order of their names
    private static List<String> sourcesOfGrailsApp(File project) {
        Path grailsApp = project.toPath().resolve('grails-app')
        if (!Files.isDirectory(grailsApp, LinkOption.NOFOLLOW_LINKS)) {
            return Collections.emptyList()
        }
        List<String> folders = []
        Files.newDirectoryStream(grailsApp).withCloseable { DirectoryStream<Path> entries ->
            for (Path entry : entries) {
                String name = entry.getFileName().toString()
                if (!name.startsWith('.') && !NOT_OF_THE_JAR.contains(name) && Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    folders.add('grails-app/' + name)
                }
            }
        }
        return folders.sort()
    }

    // whether a directory of a package has a source of Groovy or Java of its own, not of a package below it
    private static boolean hasSource(Path directory) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return false
        }
        return Files.newDirectoryStream(directory).withCloseable { DirectoryStream<Path> entries ->
            entries.any { Path entry ->
                String name = entry.getFileName().toString()
                (name.endsWith('.groovy') || name.endsWith('.java')) && Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)
            }
        }
    }
}
