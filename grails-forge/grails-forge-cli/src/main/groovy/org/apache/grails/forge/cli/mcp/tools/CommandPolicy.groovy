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

import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.mcp.Lane
import org.apache.grails.forge.cli.mcp.McpNames

/**
 * How {@code grails mcp} offers one command of the command line itself: whether it is offered at all,
 * and if so which of its options are left out and which are given on every call, whether its output is
 * a JSON document, the lane its calls run in, its annotations, and a note its description ends with.
 * There is one row for every command of the command line, offered or not, and a command the server has
 * no row for is not offered: a new command is offered once it has one.
 *
 * <p>The rows follow three rules besides what each command needs. Nothing an agent can name over MCP is
 * a new source of code: feature packs, feature catalogs, plugin indexes and presets are not offered, and
 * a feature named by the coordinates of a plugin is refused. A command that reads what is typed, or that
 * stays with the application, is not offered. And a command is offered by its own name only, never by
 * another name or a deprecated one.</p>
 *
 * <p>A command whose JSON document is a report can end with exit code 1 although the report is what was
 * asked for: {@code project regenerate --diff --json} when a file differs from what the application was
 * generated with, {@code project health --json} when it has an error finding, and
 * {@code feature add --dry-run --json} when the features would conflict with the files of the project.
 * Its row names the {@linkplain #reportOutcomes outcomes} of its document that are such a report. Each of
 * these commands writes a document for a failure too, with another outcome, so it is the outcome, and not
 * the exit code, that tells a report from a failure.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class CommandPolicy {

    /** Whether the output of a command is one JSON document. */
    enum JsonForm {

        /** Never: the output is text. */
        NEVER('never'),

        /** Always: the command is given {@code --json} on every call. */
        ALWAYS('always'),

        /** When the argument {@code dry-run} is true and {@code diff} is not: the command is then given {@code --json}. */
        WITH_DRY_RUN('with dry-run')

        /** How the form is named in the {@code _meta} of a tool. */
        final String described

        JsonForm(String described) {
            this.described = described
        }
    }

    /** The option that has a command write one JSON document. */
    public static final String JSON = '--json'

    /** The argument that has {@code feature add} only say what it would do, and with which it writes JSON. */
    public static final String DRY_RUN = 'dry-run'

    /** The argument that has {@code feature add} show a diff, with which it writes no JSON. */
    public static final String DIFF = 'diff'

    /** The options that name a new source of code: feature packs, feature catalogs and presets. */
    public static final List<String> SOURCES_OF_CODE = ['--feature-catalog', '--feature-pack-dir', '--feature-pack-repository', '--feature-pack-sha256', '--preset'].asImmutable()

    /** The member of the JSON document of a command that says what the command came to. */
    public static final String OUTCOME = 'outcome'

    /** The outcome of {@code project regenerate --diff --json} whose files differ from what the application was generated with. */
    public static final String CHANGES = 'changes'

    /** The outcome of {@code feature add --dry-run --json} whose features would conflict with the files of the project. */
    public static final String CONFLICTS = 'conflicts'

    /** The outcome of {@code project health --json} whose checks ran, whatever they found. */
    public static final String CHECKED = 'checked'

    /** The option that names a plugin index other than the one the environment configures. */
    public static final String PLUGIN_INDEX = '--plugin-index'

    /** The annotations of {@code refresh_tools}, the tool of the server itself. */
    public static final Map<String, Object> REFRESH_TOOLS_ANNOTATIONS = changes(false, true, true)

    private static final String FEATURES = 'features'
    private static final String EXCLUDE_FEATURES = 'exclude-features'
    private static final String FEATURE = 'feature'

    private static final List<String> CREATE_COMMANDS = ['create-app', 'create-restapi', 'create-plugin', 'create-web-plugin', 'create-rest-api-plugin'].asImmutable()

    private static final List<String> CODE_GENERATION = ['create-controller', 'create-service', 'create-domain-class', 'create-taglib', 'create-interceptor', 'create-job',
                                                         'add-property', 'create-unit-test', 'create-integration-test', 'create-functional-test', 'create-restful-controller',
                                                         'create-domain-resource', 'create-tool', 'install-templates'].asImmutable()

    private static final String NO_NEW_SOURCE = 'Feature packs and feature catalogs cannot be named, and a feature named by the coordinates of a plugin is refused.'

    private static final List<CommandPolicy> ROWS = table()

    private static final CommandPolicy FEATURE_COMMAND = new CommandPolicy([], true, null, Lane.DIRECT, changes(true, false, false), [], [], JsonForm.NEVER, null, [], [], [], false)

    /** The words of the command, as {@code ['feature', 'add']}; none for the row of the commands the features of an application give. */
    final List<String> path

    /** Whether the command is offered. */
    final boolean exposed

    /** Why the command is not offered; {@code null} for one that is. */
    final String reason

    /** The lane its calls run in. */
    final Lane lane

    /** Its annotations: the hints of the protocol, as the command does what it does. */
    final Map<String, Object> annotations

    /** The options it is given on every call, after its words and before the options of the call. */
    final List<String> forcedOptions

    /** The options that are not offered, each by one of its names; the options of the command line in front of a command are never offered. */
    final List<String> leftOutOptions

    /** Whether its output is a JSON document. */
    final JsonForm json

    /** What its description ends with, or {@code null}. */
    final String note

    /** The arguments, by their names in the input schema, whose values name features, and refuse the coordinates of a plugin. */
    final List<String> featureValued

    /** The parameters, by their names in the input schema, whose values are passed as they are: they are another program's arguments, also where they begin with {@code -}. */
    final List<String> passedAsIs

    /**
     * The outcomes of its JSON document, the member {@value #OUTCOME}, that are the report the command was
     * asked for, also where the command ends with exit code 1: a result with such a document is no error,
     * and its {@code _meta} keeps the exit code. Empty for a command whose exit code 1 is always a failure.
     */
    final List<String> reportOutcomes

    /**
     * Whether the command is run in a process of its own rather than in the server's. Reserved: no command
     * needs it, and the server has no such process yet.
     */
    final boolean isolated

    private CommandPolicy(List<String> path, boolean exposed, String reason, Lane lane, Map<String, Object> annotations, List<String> forcedOptions,
                          List<String> leftOutOptions, JsonForm json, String note, List<String> featureValued, List<String> passedAsIs,
                          List<String> reportOutcomes, boolean isolated) {
        this.path = Collections.unmodifiableList(new ArrayList<String>(path))
        this.exposed = exposed
        this.reason = reason
        this.lane = lane
        this.annotations = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(annotations))
        this.forcedOptions = Collections.unmodifiableList(new ArrayList<String>(forcedOptions))
        this.leftOutOptions = Collections.unmodifiableList(new ArrayList<String>(leftOutOptions))
        this.json = json
        this.note = note
        this.featureValued = Collections.unmodifiableList(new ArrayList<String>(featureValued))
        this.passedAsIs = Collections.unmodifiableList(new ArrayList<String>(passedAsIs))
        this.reportOutcomes = Collections.unmodifiableList(new ArrayList<String>(reportOutcomes))
        this.isolated = isolated
    }

    /**
     * @param path the words of a command of the command line itself, as {@code ['feature', 'add']}
     * @return its row, or {@code null} when the server has none, and does not offer the command
     */
    static CommandPolicy of(List<String> path) {
        return ROWS.find { CommandPolicy row -> row.path == path }
    }

    /**
     * @return the row of the commands the features of an application give it, {@code create-<name>}: code
     * generation from the templates the feature wrote into the project, offered as the commands of the
     * command line that generate code are
     */
    static CommandPolicy ofFeatureCommand() {
        return FEATURE_COMMAND
    }

    /**
     * @return every row of the commands of the command line itself, in the order of the design's table
     */
    static List<CommandPolicy> rows() {
        return ROWS
    }

    private static List<CommandPolicy> table() {
        List<CommandPolicy> rows = []
        String creates = 'Feature packs, feature catalogs and presets cannot be named, and a feature named by the coordinates of a plugin is refused. ' +
                '--list-features and --list-catalog-features are not offered: feature_find with type lists the features a new application of the type can be created with.'
        for (String command : CREATE_COMMANDS) {
            rows.add(exposed([command], Lane.GENERATOR, changes(false, false, true), [], SOURCES_OF_CODE + ['--list-features', '--list-catalog-features'], JsonForm.NEVER,
                    creates, [FEATURES, EXCLUDE_FEATURES], []))
        }
        rows.add(notExposed(['create-webapp'], 'another name of create-app, which is offered'))
        rows.add(exposed(['feature', 'add'], Lane.GENERATOR, changes(true, true, true), [], SOURCES_OF_CODE + [JSON], JsonForm.WITH_DRY_RUN,
                'With dry-run the result is a JSON document. A dry run that finds that the features would conflict with the files of the project ' +
                        'is a result and not an error, with the outcome conflicts and the files that conflict, though the command ends with exit code 1. ' +
                        NO_NEW_SOURCE, [FEATURE], []).reporting([CONFLICTS]))
        rows.add(exposed(['feature', 'list'], Lane.GENERATOR, readOnly(true), [], SOURCES_OF_CODE, JsonForm.NEVER, 'Feature packs cannot be named.', [], []))
        rows.add(exposed(['feature', 'find'], Lane.GENERATOR, readOnly(true), [JSON], SOURCES_OF_CODE + [JSON], JsonForm.ALWAYS,
                'The result is a JSON document. Feature packs and feature catalogs cannot be named.', [], []))
        rows.add(exposed(['feature', 'show'], Lane.GENERATOR, readOnly(true), [JSON], SOURCES_OF_CODE + [JSON], JsonForm.ALWAYS,
                'The result is a JSON document. ' + NO_NEW_SOURCE, [FEATURE], []))
        rows.add(exposed(['feature', 'diff'], Lane.GENERATOR, readOnly(true), [], SOURCES_OF_CODE, JsonForm.NEVER, NO_NEW_SOURCE, [FEATURE], []))
        rows.add(exposed(['plugin', 'list'], Lane.DIRECT, readOnly(true), [JSON], [PLUGIN_INDEX, JSON], JsonForm.ALWAYS,
                'The result is a JSON document. The plugin index is the one the environment configures.', [], []))
        rows.add(exposed(['plugin', 'find'], Lane.DIRECT, readOnly(true), [JSON], [PLUGIN_INDEX, JSON], JsonForm.ALWAYS,
                'The result is a JSON document. The plugin index is the one the environment configures.', [], []))
        rows.add(exposed(['plugin', 'show'], Lane.DIRECT, readOnly(true), [JSON], [PLUGIN_INDEX, JSON], JsonForm.ALWAYS,
                'The result is a JSON document. The plugin index is the one the environment configures.', [], []))
        rows.add(notExposed(['list-plugins'], 'the same command as plugin list, which is offered as plugin_list'))
        rows.add(notExposed(['plugin-info'], 'the same command as plugin show, which is offered as plugin_show'))
        rows.add(exposed(['project', 'show'], Lane.DIRECT, readOnly(false), [JSON], [JSON], JsonForm.ALWAYS, 'The result is a JSON document.', [], []))
        rows.add(exposed(['project', 'health'], Lane.DIRECT, readOnly(false), [JSON], [JSON], JsonForm.ALWAYS,
                'The result is a JSON document. A project with error findings is a result and not an error, with the outcome checked and passed false, ' +
                        'though the command ends with exit code 1.', [], []).reporting([CHECKED]))
        rows.add(exposed(['project', 'migrate'], Lane.GENERATOR, changes(true, false, false), [], SOURCES_OF_CODE, JsonForm.NEVER, NO_NEW_SOURCE,
                [FEATURES, EXCLUDE_FEATURES], []))
        rows.add(exposed(['project', 'regenerate'], Lane.GENERATOR, readOnly(false), ['--diff', JSON], SOURCES_OF_CODE + ['--diff', JSON, '--output', '--force'], JsonForm.ALWAYS,
                'It runs only as --diff --json: it writes nothing, and its result is a JSON document of the files that are not what the application was generated with. ' +
                        'Files that differ, as in any project that was edited, are a result and not an error, with the outcome changes, ' +
                        'though the command ends with exit code 1.', [], []).reporting([CHANGES]))
        for (String command : CODE_GENERATION) {
            rows.add(exposed([command], Lane.DIRECT, changes(true, false, false), [], [], JsonForm.NEVER,
                    command == 'create-tool' ? 'A new tool is offered once the project is compiled: call compile or refresh_tools.' : null, [], []))
        }
        for (String command : ['compile', 'clean', 'assemble', 'package-plugin']) {
            rows.add(exposed([command], Lane.BUILD, changes(false, true, true), [], [], JsonForm.NEVER, null, [], []))
        }
        rows.add(exposed(['dependency-report'], Lane.BUILD, readOnly(true), [], [], JsonForm.NEVER, null, [], []))
        rows.add(exposed(['test-app'], Lane.BUILD, changes(false, false, true), [], ['--continuous', '--debug-jvm'], JsonForm.NEVER,
                'continuous and debug-jvm are not offered.', [], []))
        rows.add(exposed(['run-app'], Lane.BUILD, changes(false, false, true), ['--detach'], ['--detach', '--foreground', '--debug-jvm'], JsonForm.NEVER,
                'It always runs detached: the call returns once the application answers, and the application keeps running after the call and after grails mcp ' +
                        'have ended, until stop-app stops it. detach, foreground and debug-jvm are not offered.', [], []))
        rows.add(exposed(['stop-app'], Lane.DIRECT, changes(false, true, false), [], [], JsonForm.NEVER, null, [], []))
        rows.add(exposed(['run-tool'], Lane.BUILD, changes(true, false, true), [], [], JsonForm.NEVER,
                'It is the way to run a tool for another environment, as with system-properties {"grails.env": "production"}, and the way to run a tool ' +
                        'whose name a command of grails has. Each of arg is given to the tool as it is.', [], ['arg']))
        rows.add(exposed(['publish-plugin'], Lane.BUILD, changes(true, false, true), [], [], JsonForm.NEVER, null, [], []))
        rows.add(exposed(['stats'], Lane.DIRECT, readOnly(false), [], [], JsonForm.NEVER, null, [], []))
        rows.add(exposed(['update-wrapper'], Lane.DIRECT, changes(true, true, false), [], [], JsonForm.NEVER, null, [], []))
        rows.add(notExposed(['gradle'], 'Gradle is driven through its own tooling; the build commands of grails are offered'))
        rows.add(notExposed(['shell'], 'it reads what is typed'))
        rows.add(notExposed(['console'], 'it opens a window that a user types into'))
        rows.add(notExposed(['run-script'], 'it is deprecated; the scripts of src/main/scripts are offered by their names'))
        rows.add(notExposed(['bug-report'], 'it makes a file for a person to attach to an issue'))
        rows.add(notExposed(['help'], 'the resource grails://commands is the document of help --json'))
        rows.add(notExposed(['install'], 'it is hidden: it installs the command line'))
        rows.add(notExposed(['mcp'], 'it is hidden: it is this server'))
        return Collections.unmodifiableList(rows)
    }

    /**
     * @return the name of the tool of the command: its words joined by {@code _}; {@code null} for the row
     * of the commands the features of an application give, which are named by their own words
     */
    String getName() {
        return path.isEmpty() ? null : McpNames.toolName(path)
    }

    /**
     * @param names the names of an option, as {@code help --json} lists them
     * @return whether the option is not offered as an argument: it is left out, or given on every call
     */
    boolean leavesOut(List<String> names) {
        return names.any { String name -> leftOutOptions.contains(name) || forcedOptions.contains(name) }
    }

    @Override
    String toString() {
        return path.join(' ')
    }

    private static CommandPolicy exposed(List<String> path, Lane lane, Map<String, Object> annotations, List<String> forced, List<String> leftOut, JsonForm json, String note,
                                         List<String> featureValued, List<String> passedAsIs) {
        return new CommandPolicy(path, true, null, lane, annotations, forced, leftOut, json, note, featureValued, passedAsIs, [], false)
    }

    // the same row, whose JSON document is a report with these outcomes
    private CommandPolicy reporting(List<String> outcomes) {
        return new CommandPolicy(path, exposed, reason, lane, annotations, forcedOptions, leftOutOptions, json, note, featureValued, passedAsIs, outcomes, isolated)
    }

    private static CommandPolicy notExposed(List<String> path, String reason) {
        return new CommandPolicy(path, false, reason, Lane.DIRECT, Collections.<String, Object>emptyMap(), [], [], JsonForm.NEVER, null, [], [], [], false)
    }

    // a command that changes nothing: the other two hints mean nothing then, and are not given
    private static Map<String, Object> readOnly(boolean openWorld) {
        Map<String, Object> hints = new LinkedHashMap<>()
        hints.put('readOnlyHint', Boolean.TRUE)
        hints.put('openWorldHint', openWorld)
        return Collections.unmodifiableMap(hints)
    }

    private static Map<String, Object> changes(boolean destructive, boolean idempotent, boolean openWorld) {
        Map<String, Object> hints = new LinkedHashMap<>()
        hints.put('readOnlyHint', Boolean.FALSE)
        hints.put('destructiveHint', destructive)
        hints.put('idempotentHint', idempotent)
        hints.put('openWorldHint', openWorld)
        return Collections.unmodifiableMap(hints)
    }
}
