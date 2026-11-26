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

import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.artefact.ArtefactName
import org.apache.grails.forge.cli.artefact.ArtefactTemplate
import org.apache.grails.forge.cli.artefact.ArtefactTemplates
import org.apache.grails.forge.cli.artefact.ProjectFiles
import org.apache.grails.forge.cli.artefact.ProjectOutputHandler
import org.apache.grails.forge.cli.console.ProjectCommand
import org.grails.forge.util.ThrowingSupplier
import org.grails.forge.application.Project
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.io.OutputHandler
import org.grails.forge.template.DefaultTemplateRenderer
import org.grails.forge.template.TemplateRenderer
import picocli.CommandLine
import java.util.concurrent.Callable
import java.util.function.Supplier

@CompileStatic
abstract class CodeGenCommand extends BaseCommand implements Callable<Integer>, ProjectCommand {

    protected final CodeGenConfig config

    @CommandLine.Option(names = ['-f', '--force'], description = 'Whether to overwrite existing files')
    protected boolean overwrite

    private final ThrowingSupplier<OutputHandler, IOException> outputHandlerSupplier
    private final ConsoleOutput consoleOutput

    CodeGenCommand(CodeGenConfig config) {
        this.config = config
        this.outputHandlerSupplier = () -> new ProjectOutputHandler(getProjectDirectory(), this)
        this.consoleOutput = null
    }

    CodeGenCommand(CodeGenConfig config,
                          ThrowingSupplier<OutputHandler, IOException> outputHandlerSupplier,
                          ConsoleOutput consoleOutput) {
        this.config = config
        this.outputHandlerSupplier = outputHandlerSupplier
        this.consoleOutput = consoleOutput
    }

    abstract boolean applies()

    /**
     * @return whether the record of a project was read for the command: without one it is the command
     * by its name and its options, and says that it is a command of a project
     */
    boolean hasRecord() {
        return config != null
    }

    /**
     * @return the directory of the project the command writes in: the one its record was read in,
     * which is the current directory or one above it
     */
    protected File getProjectDirectory() throws IOException {
        File directory = config == null ? null : config.getProjectDirectory()
        return directory == null ? FileSystemOutputHandler.getDefaultBaseDirectory() : directory
    }

    /**
     * The names of a class from a name given on the command line, for the commands that add to the
     * name what their kind of class is called whatever the name ends with.
     *
     * @see #newArtefact(String, String)
     */
    protected Project getProject(String name) {
        return getProject(name, '')
    }

    /**
     * @param added what the names of the files of the class have after the name of the class
     * @see #getProject(String)
     */
    protected Project getProject(String name, String added) {
        try {
            return ArtefactName.parse(name, config?.getDefaultPackage(), null, added)
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(spec.commandLine(), e.getMessage())
        }
    }

    protected TemplateRenderer getTemplateRenderer(Project project) throws IOException {
        return TemplateRenderer.create(project, outputHandlerSupplier.get())
    }

    protected TemplateRenderer getTemplateRenderer() throws IOException {
        return TemplateRenderer.create(outputHandlerSupplier.get())
    }

    /**
     * What the command writes the files of the project through.
     */
    protected OutputHandler outputHandler() throws IOException {
        return outputHandlerSupplier.get()
    }

    /**
     * The templates this command writes classes from. A project can have its own of each under
     * {@code src/main/templates}, and {@code install-templates} copies them there.
     */
    List<ArtefactTemplate> getTemplates() {
        return Collections.emptyList()
    }

    /**
     * The text of a class: rendered from the template of the project when it has one under
     * {@code src/main/templates}, which is then said, and from the template of this program otherwise.
     *
     * @param names the names of the class
     */
    protected String artefact(ArtefactTemplate template, Project names) throws IOException {
        ArtefactTemplates.Source source = ArtefactTemplates.find(new File(outputHandler().getOutputLocation()), template)
        if (source.projectPath != null) {
            out('@|blue ||@ Using the template ' + source.projectPath + ' of the project')
        }
        return ArtefactTemplates.render(source, template, names)
    }

    /**
     * The names of a class that is to be created, from a name given on the command line, which is
     * held to what the name of a new class can be.
     *
     * @param convention what the kind of class adds to its name, or null
     * @throws CommandLine.ParameterException when the name cannot be the name of a class
     */
    protected Project newArtefact(String name, String convention) {
        return newArtefact(name, convention, config?.getDefaultPackage())
    }

    /**
     * The names of a class that is to be created, as {@link #newArtefact(String, String)} gives them,
     * for a command that puts a class given without a package into another package than the default
     * package of the project.
     *
     * @param defaultPackage the package of a name that has none, which can be missing
     */
    protected Project newArtefact(String name, String convention, String defaultPackage) {
        try {
            return ArtefactName.parse(name, defaultPackage, convention)
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(spec.commandLine(), e.getMessage())
        }
    }

    /**
     * Writes the files of a class from their templates, all of them or none.
     *
     * @param what what the class is, for the messages
     * @param names the names of the class
     * @param files the template of each file, by the path of the file, in which {@code {packagePath}},
     * {@code {className}} and the other names of {@link Project#getProperties()} are placeholders
     * @return the exit code of the command
     * @see #write(String, Map, boolean)
     */
    protected Integer generate(String what, Project names, Map<String, ArtefactTemplate> files) throws IOException {
        Map<String, Supplier<String>> texts = new LinkedHashMap<>()
        files.each { String path, ArtefactTemplate template ->
            texts.put(DefaultTemplateRenderer.resolvePath(path, names.getProperties()).replace('//', '/'), { -> artefact(template, names) } as Supplier<String>)
        }
        return write('Rendered ' + what + ' to ', texts, overwrite)
    }

    /**
     * Writes files into the project, all of them or none. Nothing is written when one of them cannot
     * be, which is found out before the first is written where it can be: when a directory on the way
     * to a file is a link or a file, or is there under a name written differently, see
     * {@link ProjectOutputHandler}; when a file is there already and is not to be replaced; and when
     * the text of a file cannot be made, as from a template of the project that cannot be rendered.
     * When a file cannot be written after all, those written before it are taken back. Each failure
     * is said in one line with the path of the file in the project.
     *
     * @param said what is said of each file that was written, in front of its path
     * @param texts what gives the text of each file, by the path of the file in the project
     * @param replace whether a file that is there is replaced
     * @return the exit code of the command
     */
    protected Integer write(String said, Map<String, Supplier<String>> texts, boolean replace) throws IOException {
        OutputHandler output = outputHandler()
        File project = new File(output.getOutputLocation())
        Map<String, String> refusals = ProjectFiles.refusals(project, texts.keySet())
        if (refusals) {
            refusals.values().each { String refusal -> err(refusal) }
            return 1
        }
        List<String> there = texts.keySet().findAll { String path -> ProjectFiles.isThere(project, path) }.toList()
        if (there && !replace) {
            there.each { String path -> err(path + ' already exists. Run again with -f to overwrite.') }
            return 1
        }
        Map<String, String> rendered = new LinkedHashMap<>()
        texts.each { String path, Supplier<String> text -> rendered.put(path, text.get()) }
        try {
            ProjectFiles.write(project, output, rendered)
        } catch (ProjectFiles.Failed e) {
            err(e.getMessage())
            return 1
        }
        rendered.keySet().each { String path -> out('@|blue ||@ ' + said + path) }
        return 0
    }

    protected <T extends CodeGenCommand> T getCommand(Class<T> clazz) {
        T bean
        try {
            bean = clazz.getConstructor(CodeGenConfig).newInstance(config)
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException('Unable to create command ' + clazz.getName(), e)
        }
        bean.overwrite = overwrite
        bean.spec = spec
        bean.commonOptions = commonOptions
        return bean
    }

    @Override
    void out(String message) {
        if (consoleOutput == null) {
            super.out(message)
        } else {
            consoleOutput.out(message)
        }
    }

    @Override
    void err(String message) {
        if (consoleOutput == null) {
            super.err(message)
        } else {
            consoleOutput.err(message)
        }
    }

    @Override
    void warning(String message) {
        if (consoleOutput == null) {
            super.warning(message)
        } else {
            consoleOutput.warning(message)
        }
    }
}
