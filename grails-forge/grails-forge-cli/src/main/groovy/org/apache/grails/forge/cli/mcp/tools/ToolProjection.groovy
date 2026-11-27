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
package org.apache.grails.forge.cli.mcp.tools

import java.util.regex.Pattern

import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.artefact.PackArtefactCommand
import org.apache.grails.forge.cli.console.CommandCatalog
import org.apache.grails.forge.cli.console.ScriptCommandFiles
import org.apache.grails.forge.cli.console.ToolCatalogFile
import org.apache.grails.forge.cli.console.ToolCommand
import org.apache.grails.forge.cli.mcp.Lane
import org.apache.grails.forge.cli.mcp.McpNames
import org.apache.grails.forge.cli.mcp.McpTool
import org.apache.grails.forge.cli.mcp.McpToolSet

/**
 * Makes the tools of {@code grails mcp} from the document {@code help --json} writes in the server's
 * directory: one tool for every command of the command line its {@link CommandPolicy} offers, for
 * every command a feature of the application gives it, and for every tool of the application and its
 * plugins; and, in a project, {@code refresh_tools}, the tool of the server itself. It is a function of
 * the document alone, and runs nothing.
 *
 * <ul>
 * <li>A tool is named by the words of its command joined by {@code _}, as {@code feature_add}; a tool
 * of the application keeps its name. A name that is not 1 to {@value #MAX_NAME} letters, digits,
 * {@code _} and {@code -}, or that is taken, is not offered, and is said in the diagnostics.</li>
 * <li>Its title is {@code grails} and the words of its command.</li>
 * <li>Its description is that of the command, and the note of its policy; for a tool of the application,
 * its own description, how it runs and where it comes from, its environment, what of it is deprecated,
 * and what it does not declare. A description is {@value #MAX_DESCRIPTION} characters at most.</li>
 * <li>Its input schema is made by {@link InputSchemas}, and a call's arguments become its command line
 * by {@link ArgumentMapping}.</li>
 * <li>Its annotations are those of its policy, for a command; for a tool, those {@link ProjectToolHints}
 * passes on.</li>
 * <li>Its {@code _meta} has, under {@link McpNames#META_COMMAND}, the whole entry {@code help --json}
 * has for it, without its commands, and under {@link McpNames#META_RUN} the command line each call
 * starts with, its lane, whether its output is JSON, and the outcomes of that JSON that are a report
 * and not an error, where a command has such outcomes ({@link CommandPolicy#reportOutcomes}).</li>
 * </ul>
 *
 * <p>Outside a project the commands of a project are not offered, since none of them can run there. A
 * tool that reads what is typed is not offered, since an agent cannot answer it. What is not offered
 * for a reason an agent or a user should know is added to the {@code diagnostics} of the document the
 * tool set holds, which is the resource {@code grails://commands}. With {@code readOnly}, only the
 * tools whose annotations say they change nothing are offered, and {@code refresh_tools} is not.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ToolProjection {

    /** The options of the command line the server gives every call, in front of its command. */
    public static final List<String> ROOT_OPTIONS = ['--plain-output', '--non-interactive'].asImmutable()

    /** The most characters of the name of a tool. Claude Code drops a tool whose full name, {@code mcp__grails__} and the name, is longer than 128. */
    public static final int MAX_NAME = 64

    /** What the name of a tool is made of. */
    public static final Pattern NAME = Pattern.compile('[A-Za-z0-9_-]{1,64}')

    /** The most characters of the description of a tool. */
    public static final int MAX_DESCRIPTION = 2000

    /** The command line {@code refresh_tools} runs: the build lists the tools of the project again. */
    public static final List<String> REFRESH_COMMAND = ['gradle', 'grailsToolCatalog', '--rerun'].asImmutable()

    /** How the arguments of a tool that declares its options and parameters are given, in {@link McpNames#META_RUN}. */
    public static final String DECLARED = 'declared'

    /** How the arguments of a tool that declares nothing are given, in {@link McpNames#META_RUN}: as they are. */
    public static final String FREE_FORM = 'free-form'

    /**
     * The member of {@link McpNames#META_RUN} with the outcomes of the JSON document of a command that are a
     * report, and make a result that is no error although the command ends with exit code 1; there only for a
     * command that has such outcomes.
     */
    public static final String REPORT_OUTCOMES = 'reportOutcomes'

    /** The diagnostic that says that the commands of a project are not offered outside one. */
    public static final String OUTSIDE_A_PROJECT = 'The commands of a project are not offered: grails mcp serves the directory it was started in, which is in no project. ' +
            'A project gets a grails mcp of its own, started in its directory.'

    private static final String ELLIPSIS = '…'

    private static final String CREATES_A_PROJECT = 'Unless it is created in this directory, with inplace, the new project gets a grails mcp of its own, ' +
            'started in its directory or with grails mcp --directory DIRECTORY.'

    private ToolProjection() {
    }

    /**
     * Makes the tools of a directory.
     *
     * @param helpDocument the document {@code help --json} writes in the directory, as maps, lists,
     * strings, numbers and booleans; the directory is in a project when its {@code projectDirectory} is not {@code null}
     * @param readOnly whether only the tools that change nothing are offered
     * @param fingerprint the fingerprint of the files the tools depend on, which the tool set keeps
     * @return the tools, with the document they are made from and its diagnostics, to which those of the
     * tools that are not offered are added
     */
    static McpToolSet of(Map<String, Object> helpDocument, boolean readOnly, String fingerprint) {
        boolean project = helpDocument.get('projectDirectory') != null
        Set<String> rootNames = new HashSet<>()
        for (Map<String, Object> option : maps(helpDocument.get('options'))) {
            rootNames.addAll(strings(option.get('names')))
        }
        List<String> diagnostics = []
        List<McpTool> tools = []
        Set<String> taken = new HashSet<>()
        if (project) {
            taken.add(McpNames.REFRESH_TOOLS)
        }
        boolean ofAProject = false
        for (Map.Entry<List<String>, Map<String, Object>> leaf : leaves(maps(helpDocument.get('commands')), [])) {
            List<String> path = leaf.getKey()
            Map<String, Object> entry = leaf.getValue()
            if (!project && entry.get('project') == Boolean.TRUE) {
                ofAProject = true
                continue
            }
            McpTool projected = projected(path, entry, rootNames, project, taken, diagnostics)
            if (projected != null) {
                tools.add(projected)
            }
        }
        if (ofAProject) {
            diagnostics.add(OUTSIDE_A_PROJECT)
        }
        if (project) {
            tools.add(refreshTools(helpDocument.get('complete') != Boolean.FALSE))
        }
        if (readOnly) {
            tools = tools.findAll { McpTool tool -> tool.annotations.get('readOnlyHint') == Boolean.TRUE }
        }
        Map<String, Object> document = new LinkedHashMap<>(helpDocument)
        List<Object> said = new ArrayList<Object>(helpDocument.get('diagnostics') instanceof List ? (List<Object>) helpDocument.get('diagnostics') : Collections.emptyList())
        said.addAll(diagnostics)
        document.put('diagnostics', said)
        return new McpToolSet(tools, document, project, fingerprint)
    }

    /**
     * @param description a description
     * @return the description, of {@value #MAX_DESCRIPTION} characters at most: a longer one is cut, and ends with an ellipsis
     */
    static String capped(String description) {
        return cut(description, MAX_DESCRIPTION)
    }

    /**
     * @param own what a command or tool says of itself
     * @param added what the server says of it besides, sentence by sentence
     * @return the two, of {@value #MAX_DESCRIPTION} characters at most: where they are longer, what the
     * command says of itself is cut, and ends with an ellipsis, so that what the server adds is whole
     */
    static String capped(String own, List<String> added) {
        String tail = added.findAll { String part -> !part.isEmpty() }.join(' ')
        String whole = tail.isEmpty() ? own : own.isEmpty() ? tail : own + ' ' + tail
        if (whole.length() <= MAX_DESCRIPTION) {
            return whole
        }
        int room = MAX_DESCRIPTION - tail.length() - 1
        return room < 2 ? cut(whole, MAX_DESCRIPTION) : cut(own, room) + ' ' + tail
    }

    // the text, of at most max characters: a longer one is cut, without halving a character, and ends with an ellipsis
    private static String cut(String text, int max) {
        if (text.length() <= max) {
            return text
        }
        int end = max - 1
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--
        }
        return text.substring(0, end) + ELLIPSIS
    }

    // the tool of one command or tool of the document; null for one that is not offered
    private static McpTool projected(List<String> path, Map<String, Object> entry, Set<String> rootNames, boolean project, Set<String> taken, List<String> diagnostics) {
        String words = path.join(' ')
        String origin = entry.get('origin') instanceof String ? (String) entry.get('origin') : null
        CommandPolicy policy = null
        if (origin == CommandCatalog.BUILT_IN) {
            policy = CommandPolicy.of(path)
            if (policy == null) {
                diagnostics.add("The command ${words} is not offered: grails mcp has no policy for it".toString())
                return null
            }
            if (!policy.exposed) {
                return null
            }
        } else if (origin != null && origin.startsWith(PackArtefactCommand.ORIGIN)) {
            policy = CommandPolicy.ofFeatureCommand()
        } else if (entry.get('readsInput') == Boolean.TRUE) {
            diagnostics.add("The tool ${words} is not offered: it reads what is typed, which an agent cannot answer. It runs at the terminal: grails ${words}".toString())
            return null
        }
        String name = McpNames.toolName(path)
        if (!NAME.matcher(name).matches()) {
            diagnostics.add("The command ${words} is not offered: its name as a tool, ${name}, is not 1 to ${MAX_NAME} letters, digits, _ and -".toString())
            return null
        }
        if (!taken.add(name)) {
            diagnostics.add("The command ${words} is not offered: another tool is named ${name}".toString())
            return null
        }
        return policy != null ? command(name, path, entry, policy, rootNames, project, diagnostics) : projectTool(name, entry, diagnostics)
    }

    // a command of the command line itself, or one a feature of the application gives it
    private static McpTool command(String name, List<String> path, Map<String, Object> entry, CommandPolicy policy, Set<String> rootNames, boolean project,
                                   List<String> diagnostics) {
        String words = path.join(' ')
        Set<String> leftOut = new HashSet<>(rootNames)
        leftOut.addAll(policy.leftOutOptions)
        leftOut.addAll(policy.forcedOptions)
        if (policy.json == CommandPolicy.JsonForm.WITH_DRY_RUN) {
            leftOut.add(CommandPolicy.JSON)
        }
        List<Map<String, Object>> options = maps(entry.get('options'))
        InputSchemas.Schema schema = InputSchemas.of(words, options, maps(entry.get('parameters')), leftOut, policy.featureValued, policy.passedAsIs)
        diagnostics.addAll(schema.diagnostics)
        List<String> command = new ArrayList<>(path)
        command.addAll(policy.forcedOptions)
        List<String> added = []
        if (policy.note != null) {
            added.add(policy.note)
        }
        if (!project && policy.lane == Lane.GENERATOR && path.size() == 1) {
            // a command that creates a project, outside one: the commands of the generator of one word are those of create-app
            added.add(CREATES_A_PROJECT)
        }
        Set<String> listed = new HashSet<>()
        for (Map<String, Object> option : options) {
            listed.addAll(strings(option.get('names')))
        }
        Map<String, Object> runs = run(command, policy.lane, policy.json, DECLARED)
        List<String> notOffered = policy.leftOutOptions.findAll { String option -> listed.contains(option) }
        if (!notOffered.isEmpty()) {
            runs.put('leftOut', notOffered)
        }
        if (!policy.reportOutcomes.isEmpty()) {
            runs.put(REPORT_OUTCOMES, policy.reportOutcomes)
        }
        if (policy.isolated) {
            runs.put('isolated', true)
        }
        return new McpTool(name, "grails ${words}".toString(), capped(sentence(text(entry.get('description'))), added), schema.json, policy.annotations,
                meta(entry, runs), policy.lane, new ArgumentMapping(name, command, schema, policy.json))
    }

    // a tool of the application or of one of its plugins, or a script of the application
    private static McpTool projectTool(String name, Map<String, Object> entry, List<String> diagnostics) {
        List<Map<String, Object>> options = maps(entry.get('options'))
        List<Map<String, Object>> parameters = maps(entry.get('parameters'))
        boolean declares = !options.isEmpty() || !parameters.isEmpty()
        InputSchemas.Schema schema = declares ?
                InputSchemas.of(name, options, parameters, Collections.<String>emptySet(), Collections.<String>emptySet(), Collections.<String>emptySet()) :
                InputSchemas.freeForm()
        diagnostics.addAll(schema.diagnostics)
        String origin = text(entry.get('origin'))
        List<String> incomplete = strings(entry.get('incomplete'))
        if (!incomplete.isEmpty() && origin != ProjectToolHints.APPLICATION) {
            diagnostics.add("The tool ${name} of ${origin} lacks ${incomplete.join(', ')}: its author is told by the compiler".toString())
        }
        List<String> command = [name]
        String own = text(entry.get('description'))
        return new McpTool(name, "grails ${name}".toString(),
                capped(own.isEmpty() ? "The tool ${name} has no description of its own.".toString() : sentence(own), added(entry, declares, incomplete)),
                schema.json, ProjectToolHints.annotations(entry),
                meta(entry, run(command, Lane.BUILD, CommandPolicy.JsonForm.NEVER, declares ? DECLARED : FREE_FORM)), Lane.BUILD,
                new ArgumentMapping(name, command, schema, CommandPolicy.JsonForm.NEVER))
    }

    // what the server says of a tool of the application after what it says of itself: how it runs and where it comes from, its environment, what of it is
    // deprecated, and what it does not declare
    private static List<String> added(Map<String, Object> entry, boolean declares, List<String> incomplete) {
        List<String> parts = []
        String origin = text(entry.get('origin'))
        String from = origin == ToolCatalogFile.APPLICATION ? 'the application' : origin
        if (entry.get('registration') == ScriptCommandFiles.REGISTRATION) {
            parts.add("A script of the application, in ${ScriptCommandFiles.LOCATION}, which the build runs without the application, with the Gradle task ${ScriptCommandFiles.TASK}.".toString())
            parts.add(ToolCommand.deprecatedScripts('create-tool') + '.')
        } else if (entry.get('target') == ToolCatalogFile.STANDALONE) {
            parts.add("A tool of ${from}, which runs as a process of its own, without Gradle and without the application.".toString())
        } else {
            parts.add(entry.get('startsApplication') == Boolean.FALSE ?
                    "A tool of ${from}, which the build runs without the application, with the Gradle task ${ToolCommand.TASK}.".toString() :
                    "A tool of ${from}, which runs in the application, started by the Gradle task ${ToolCommand.TASK}.".toString())
            parts.add(("It runs in the ${ToolCommand.ENVIRONMENT} environment: run-tool with system-properties {\"grails.env\": \"test\"} and the name of the tool " +
                    'runs it in another.').toString())
        }
        List<String> deprecated = strings(entry.get(ToolCatalogFile.DEPRECATED))
        if (deprecated.contains(ToolCatalogFile.DEPRECATED_FOLDER)) {
            parts.add('It is in grails-app/commands, which is deprecated: move it to src/cli/groovy.')
        }
        if (deprecated.contains(ToolCatalogFile.DEPRECATED_TYPE)) {
            parts.add('It implements ApplicationCommand, which is deprecated: implement ApplicationTool.')
        }
        if (!declares) {
            parts.add('Its arguments are not declared: each of arguments is given to it as it is.')
        }
        if (incomplete.contains('hints')) {
            parts.add('It does not say what it changes, so it is taken to change and delete files and data, as the defaults of the protocol say.')
        }
        return parts
    }

    // the tool of the server itself: the build lists the tools of the project again
    private static McpTool refreshTools(boolean complete) {
        String description = 'Has the build of the project list the tools of the application and of its plugins again, with gradle grailsToolCatalog --rerun; ' +
                'grails mcp then offers them as they are listed. Call it when a tool was written or a plugin was added and is not offered yet: ' +
                'the build lists the tools when it compiles the project, and grails mcp never runs a build by itself.'
        if (!complete) {
            description += ' The tools of the project are not all listed now, as grails://commands says: call it to list them.'
        }
        Map<String, Object> meta = new LinkedHashMap<>()
        meta.put(McpNames.META_RUN, run(REFRESH_COMMAND, Lane.BUILD, CommandPolicy.JsonForm.NEVER, DECLARED))
        InputSchemas.Schema none = InputSchemas.none()
        return new McpTool(McpNames.REFRESH_TOOLS, 'grails gradle grailsToolCatalog --rerun', description, none.json, CommandPolicy.REFRESH_TOOLS_ANNOTATIONS, meta,
                Lane.BUILD, new ArgumentMapping(McpNames.REFRESH_TOOLS, REFRESH_COMMAND, none, CommandPolicy.JsonForm.NEVER))
    }

    private static Map<String, Object> run(List<String> command, Lane lane, CommandPolicy.JsonForm json, String arguments) {
        Map<String, Object> run = new LinkedHashMap<>()
        List<String> commandLine = new ArrayList<>(ROOT_OPTIONS)
        commandLine.addAll(command)
        run.put('commandLine', commandLine)
        run.put('arguments', arguments)
        run.put('lane', lane.name().toLowerCase(Locale.ROOT))
        run.put('json', json.described)
        return run
    }

    private static Map<String, Object> meta(Map<String, Object> entry, Map<String, Object> run) {
        Map<String, Object> described = new LinkedHashMap<>()
        entry.each { String member, Object value ->
            if (member != 'commands') {
                described.put(member, value)
            }
        }
        Map<String, Object> meta = new LinkedHashMap<>()
        meta.put(McpNames.META_COMMAND, described)
        meta.put(McpNames.META_RUN, run)
        return meta
    }

    // every command that has no commands of its own, with its words, in the order of the document; twice where it is there twice
    private static List<Map.Entry<List<String>, Map<String, Object>>> leaves(List<Map<String, Object>> commands, List<String> parent) {
        List<Map.Entry<List<String>, Map<String, Object>>> found = []
        for (Map<String, Object> command : commands) {
            if (!(command.get('name') instanceof String)) {
                continue
            }
            List<String> path = new ArrayList<>(parent)
            path.add((String) command.get('name'))
            List<Map<String, Object>> below = maps(command.get('commands'))
            if (below.isEmpty()) {
                found.add(new AbstractMap.SimpleImmutableEntry<List<String>, Map<String, Object>>(Collections.unmodifiableList(path), command))
            } else {
                found.addAll(leaves(below, path))
            }
        }
        return found
    }

    // a description as one sentence or more, ending with a full stop
    private static String sentence(String text) {
        String trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.endsWith('.') || trimmed.endsWith('!') || trimmed.endsWith('?')) {
            return trimmed
        }
        return trimmed + '.'
    }

    private static String text(Object value) {
        return value instanceof String ? (String) value : ''
    }

    private static List<Map<String, Object>> maps(Object value) {
        return value instanceof List ? ((List<Object>) value).findAll { Object each -> each instanceof Map }.collect { Object each -> (Map<String, Object>) each } :
                Collections.<Map<String, Object>>emptyList()
    }

    private static List<String> strings(Object value) {
        return value instanceof List ? ((List<Object>) value).findAll { Object each -> each instanceof String }.collect { Object each -> (String) each } :
                Collections.<String>emptyList()
    }
}
