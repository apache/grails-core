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
package org.apache.grails.forge.cli.console

import groovy.transform.CompileStatic
import picocli.CommandLine

/**
 * The commands of the Grails shell and of its profiles that are no command of this command line
 * under the name they had, or not in every directory, each with what there is in its place, and
 * the one name the Grails Forge CLI of Grails 7 told to type that it did not have. The table is fixed: it is read from no profile, no project and no build, and what was typed is only
 * looked up in it and never repeated from it.
 *
 * <p>Beside the table are the names of the environments that stand in front of a command where a
 * command is expected: {@code dev}, {@code prod} and {@code test}, which the Grails shell read there
 * as the environment, and {@code development} and {@code production}. Each is answered with the
 * command line to type, with the environment after the command as {@code -Dgrails.env}: the
 * command that follows is named in it only by a name it has here.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class FormerCommands {

    /** What became of a command. */
    static enum Status {

        /** A command of this command line does it, under another name. */
        REPLACED('replaced'),
        /** It is a command of this command line where a project of some kind is, and not elsewhere. */
        ELSEWHERE('kept, in some directories'),
        /** A tool of a plugin does it, which is a command here once the build of the project has listed it. */
        PLUGIN('a tool of a plugin'),
        /** It is gone and nothing takes its place. */
        REMOVED('removed'),
        /** Nothing takes its place today, and none is recorded as decided. */
        NO_REPLACEMENT('no replacement')

        final String shown

        Status(String shown) {
            this.shown = shown
        }
    }

    /** One command of the table. */
    static final class Former {

        final String name
        /** Where the Grails shell had it. */
        final String origin
        final Status status
        /**
         * What is said for it: with {@code {cli}} and a space in front of a command line to type, which
         * stand for the name of this command line on the command line of a shell and for nothing at the
         * prompt of the interactive console, and with {@code {name}} where the text names this command line.
         */
        final String hint

        Former(String name, String origin, Status status, String hint) {
            this.name = name
            this.origin = origin
            this.status = status
            this.hint = hint
        }
    }

    private static final String PROFILES_GONE = 'Profiles are replaced by application types, features and feature packs, and no command creates any of these'
    private static final String REST_API = 'is a command in the directory of a REST API application or REST API plugin (create-restapi, create-rest-api-plugin), and the current directory is in none'
    private static final String PLUGIN_PROJECT = 'is a command in the directory of a Grails plugin, which has a class whose name ends with GrailsPlugin under src/main/groovy, and the current directory is in none'
    private static final String SCAFFOLDING = 'is a tool of the scaffolding plugin (grails-scaffolding): where the application has the plugin, ' +
            'it is a command here by its name. In a REST API application or REST API plugin, which has the tools of the plugin with the scaffolding feature, ' +
            'it generates what the REST API profile generated (a REST controller and JSON views)'

    // What ends the interactive console, as both ended the interactive mode of the Grails shell. The table has a line for
    // each, the same but for the name, as each is no command outside the console.
    private static final List<String> ENDS_THE_CONSOLE = Collections.unmodifiableList(['exit', 'quit'])

    private static final Map<String, Former> TABLE = table()

    // What the Grails shell read in front of a command as the environment it ran for, and the environments
    // they name; and the names of those environments, which a Grails application has for them.
    private static final Map<String, String> SHELL_ENVIRONMENTS = Collections.unmodifiableMap([dev: 'development', prod: 'production', test: 'test'])
    private static final Map<String, String> ENVIRONMENTS = Collections.unmodifiableMap(SHELL_ENVIRONMENTS + [development: 'development', production: 'production'])

    // what follows a command for its environment, as the commands that run the build take it
    private static final String ENVIRONMENT_OPTION = '-Dgrails.env='
    private static final String SYSTEM_PROPERTY = '-D'

    private FormerCommands() {
    }

    /**
     * @return every command of the table, in the order of their names
     */
    static List<Former> all() {
        return new ArrayList<Former>(TABLE.values())
    }

    /**
     * What is added to the refusal of something that was typed for a command and is none, when the
     * Grails shell had a command by that name. Only a command at the top of the command line is
     * looked up, by the name as it was typed.
     *
     * @param parent the command a command was expected of
     * @param typed what was typed
     * @return a line break and one line, or nothing
     */
    static String line(CommandLine parent, String typed) {
        return line(parent, typed, null)
    }

    /**
     * What is added to the refusal of something that was typed for a command and is none, as
     * {@link #line(CommandLine, String)} says, where a command may follow it. A name of an environment
     * in front of a command is answered with the command line that gives that command the environment;
     * {@code test} alone, which was also another name of {@code test-app}, with its line of the table.
     *
     * @param parent the command a command was expected of
     * @param typed what was typed
     * @param following the name of the command of {@code parent} that was typed after it, by a name
     * the command has here, or {@code null} where what followed is no command or nothing followed
     * @return a line break and one line, or nothing
     */
    static String line(CommandLine parent, String typed, String following) {
        if (parent == null || parent.getParent() != null || typed == null) {
            return ''
        }
        Former former = find(typed)
        String environment = ENVIRONMENTS.get(typed)
        CommandLine command = following == null ? null : parent.getSubcommands().get(following)
        String hint = environment != null && (former == null || command != null) ? environmentHint(typed, environment, command == null ? null : following, command) :
                former?.hint
        if (hint == null) {
            return ''
        }
        CommandLine.Model.CommandSpec root = parent.getCommandSpec().root()
        return System.lineSeparator() + hint.replace('{cli} ', UsageHelp.toType(root, '')).replace('{name}', root.name())
    }

    /**
     * @param typed what was typed where a command is expected
     * @return whether it is the name of an environment that is answered in front of a command, see {@link #line(CommandLine, String, String)}
     */
    static boolean isEnvironment(String typed) {
        return typed != null && ENVIRONMENTS.containsKey(typed)
    }

    // The line for an environment in front of a command: what the environment is, and the command line to type. A command that runs the build
    // takes the environment after its name; a tool of the application takes everything after its name as its own, and is given it by
    // run-tool; a command that runs for no environment is typed without it.
    private static String environmentHint(String typed, String environment, String name, CommandLine command) {
        String option = ENVIRONMENT_OPTION + environment
        boolean ofShell = SHELL_ENVIRONMENTS.containsKey(typed)
        String start = ofShell ? "${typed} in front of a command named the environment ${environment} in the Grails shell" : "${typed} is the name of an environment"
        String follows = "The environment ${ofShell ? 'now ' : ''}follows the command as ${option}"
        if (command == null) {
            return "${start}. ${follows}. Type: {cli} COMMAND ${option}"
        }
        if (command.getCommandSpec().userObject() instanceof ToolCommand) {
            return "${start}. A tool of the application is given the environment by run-tool, in front of the name of the tool. " +
                    "Type: {cli} run-tool ${option} ${name}"
        }
        if (command.getCommandSpec().findOption(SYSTEM_PROPERTY) == null) {
            return "${start}. ${name} runs for no environment. Type: {cli} ${name}"
        }
        return "${start}. ${follows}. Type: {cli} ${name} ${option}"
    }

    /**
     * Whether a line of the interactive console ends it: {@code exit} or {@code quit}, in lower case,
     * with nothing else on the line but spaces around it, as in the Grails shell.
     *
     * @param line the line as it was read
     * @return whether the console ends at it
     */
    static boolean endsTheConsole(String line) {
        return line != null && ENDS_THE_CONSOLE.contains(line.trim())
    }

    /**
     * @param typed what was typed
     * @return the command of the table by that name, or {@code null}
     */
    static Former find(String typed) {
        if (typed == null) {
            return null
        }
        return TABLE.get(typed)
    }

    private static Map<String, Former> table() {
        Map<String, Former> table = new TreeMap<String, Former>()
        Closure<Void> add = { String name, String origin, Status status, String hint ->
            if (table.put(name, new Former(name, origin, status, hint)) != null) {
                throw new IllegalStateException("${name} is in the table twice")
            }
            return null
        }

        // run and stop are other names of run-app and stop-app here too; test is not, because the shell also read it as the environment
        add('test', 'base profile, another name of test-app, and the environment in front of another command', Status.REPLACED,
                'test was another name of test-app in the Grails shell, and in front of another command it named the environment test, ' +
                        'which now follows the command as -Dgrails.env=test. Type: {cli} test-app')
        add('create-tag-lib', 'web profile, written as another name of create-taglib where the shell did not read it', Status.REPLACED,
                'create-tag-lib is written in the web profile of the Grails shell as another name of create-taglib, in a place the shell did not read, ' +
                        'so it was no command there either. Type: {cli} create-taglib')
        for (String ends : ENDS_THE_CONSOLE) {
            add(ends, 'shell, interactive mode', Status.REPLACED,
                    ends + ' ends the interactive mode, which {name} starts when it is given no command. It is no command outside it')
        }
        add('list-profiles', 'shell', Status.REPLACED,
                'list-profiles listed the profiles of the Grails shell. Profiles are replaced by application types and features: {cli} help lists the create commands, ' +
                'and each lists its features with --list-features')
        add('profile-info', 'shell', Status.REPLACED,
                'profile-info described a profile of the Grails shell. Profiles are replaced by application types and features: {cli} create-app --list-features ' +
                'lists the features of an application, and the other create commands those of their types')

        add('update-cli-config', 'the command line of Grails 7, which named it in a warning and did not have it', Status.REPLACED,
                'update-cli-config is what the command line of Grails 7 told to run for a grails-cli.yml or grails-forge-cli.yml that names a profile, ' +
                'and it never was a command. In the project, {cli} project migrate writes the record of this version from what the project has')
        add('create-script', 'base profile', Status.REPLACED,
                'create-script wrote a script command into src/main/scripts. Script commands are deprecated, and the scripts a project has still run: ' +
                        'a new tool is a class of the application. Type: {cli} create-tool NAME')
        add('create-command', 'base profile', Status.REPLACED,
                'create-command wrote a command of the application into grails-app/commands. What an application adds to the command line is now a tool, ' +
                        'which is written to src/cli/groovy, or with --standalone to src/standalone/groovy. Type: {cli} create-tool NAME')

        add('generate-all', 'rest-api profile', Status.PLUGIN, 'generate-all ' + SCAFFOLDING)
        add('generate-controller', 'rest-api profile', Status.PLUGIN, 'generate-controller ' + SCAFFOLDING)
        add('generate-views', 'rest-api profile', Status.PLUGIN, 'generate-views ' + SCAFFOLDING)

        add('query-tail', 'script command of Grails Data', Status.PLUGIN,
                'query-tail is a tool of Grails Data (grails-datamapping-core), which the build of a project runs without starting the application: ' +
                'where the application has GORM, it is a command here by its name')

        add('open', 'shell', Status.NO_REPLACEMENT,
                'open opened a file with the program the desktop has for it in the Grails shell. It has no replacement in {name}')

        add('create-profile', 'shell', Status.REMOVED, 'create-profile created a profile for the Grails shell. ' + PROFILES_GONE)
        add('create-creator-command', 'profile profile', Status.REMOVED, 'create-creator-command wrote a command of a profile for the Grails shell. ' + PROFILES_GONE)
        add('create-generator-command', 'profile profile', Status.REMOVED, 'create-generator-command wrote a command of a profile for the Grails shell. ' + PROFILES_GONE)
        add('create-gradle-command', 'profile profile', Status.REMOVED, 'create-gradle-command wrote a command of a profile for the Grails shell. ' + PROFILES_GONE)
        add('create-feature', 'profile profile', Status.REMOVED, 'create-feature wrote a feature of a profile for the Grails shell. ' + PROFILES_GONE)
        add('create-template', 'profile profile', Status.REMOVED, 'create-template wrote a template of a profile for the Grails shell. ' + PROFILES_GONE)

        add('create-integration-test', 'web and rest-api profiles', Status.ELSEWHERE,
                'create-integration-test is a command in the directory of a Grails application, web plugin or REST API plugin whose record is read, not of a plain plugin, and the current directory is in none')
        add('create-job', 'Quartz plugin', Status.ELSEWHERE,
                'create-job is a command in the directory of a Grails project that has the grails-quartz feature, which {cli} feature add grails-quartz adds, ' +
                'and the current directory is in none')
        add('create-domain-resource', 'rest-api profile', Status.ELSEWHERE, 'create-domain-resource ' + REST_API)
        add('create-functional-test', 'rest-api profile, and in a web application a script of the Geb plugin', Status.ELSEWHERE,
                'create-functional-test is a command in the directory of a REST API application or REST API plugin, and of a web application or web plugin ' +
                        'that has a Geb feature (geb-with-testcontainers or geb-with-local-browsers), which {cli} feature add geb-with-testcontainers adds, ' +
                        'and the current directory is in none')
        add('create-restful-controller', 'rest-api profile', Status.ELSEWHERE, 'create-restful-controller ' + REST_API)
        add('package-plugin', 'plugin profile', Status.ELSEWHERE, 'package-plugin ' + PLUGIN_PROJECT)
        add('publish-plugin', 'plugin profile', Status.ELSEWHERE, 'publish-plugin ' + PLUGIN_PROJECT)
        return Collections.unmodifiableMap(table)
    }
}
