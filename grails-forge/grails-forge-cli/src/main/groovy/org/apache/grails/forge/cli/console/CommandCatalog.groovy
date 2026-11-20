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

import java.nio.file.Path
import java.util.regex.Pattern
import groovy.transform.CompileStatic
import org.grails.build.parsing.ScriptNameResolver
import org.grails.forge.feature.FeatureSelectionException
import picocli.CommandLine

/**
 * The commands a command line has, read from its picocli model: every command that is registered
 * with the command line is in the catalog, with nothing else to do for it. The catalog finds a
 * command by its name or by the short form of its name, and describes the commands for a tool.
 *
 * <p>The short form is the one the Grails shell takes, by the rule of {@link ScriptNameResolver}:
 * the first letters of each word of the name, the first letter of every word after the first in
 * upper case, so that {@code cA} and {@code creA} are {@code create-app} and {@code cDC} is
 * {@code create-domain-class}. There have to be as many parts as the name has words.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class CommandCatalog {

    /** The version of the document {@link #describe} gives. */
    public static final int FORMAT = 1

    /** The origin of a command that is part of the command line itself. */
    public static final String BUILT_IN = 'built-in'

    // What can be the short form of a name. Anything else, a path or an option among them, is not
    // looked at: the rule of the shell takes the letters out of whatever it is given.
    private static final Pattern SHORT_FORM = Pattern.compile('[A-Za-z][A-Za-z-]{0,63}')

    private final CommandLine root

    /**
     * @param root the command line, with the commands it has here
     */
    CommandCatalog(CommandLine root) {
        this.root = root
    }

    /**
     * @return the command line the catalog is of
     */
    CommandLine getRoot() {
        return root
    }

    /**
     * Finds a command of a command by what was typed for it. The name of a command, or another name
     * it has, is that command whatever it is short for.
     *
     * @param parent the command the command is one of
     * @param typed what was typed
     * @return the command, or {@code null} when there is none by that name or short form
     * @throws CommandLine.ParameterException when it is the short form of several commands
     */
    CommandLine find(CommandLine parent, String typed) {
        CommandLine named = parent.getSubcommands().get(typed)
        if (named != null) {
            return named
        }
        List<CommandLine> candidates = shortFor(parent, typed)
        if (candidates.size() > 1) {
            throw new CommandLine.ParameterException(parent, "${FeatureSelectionException.shown(typed)} is short for more than one command: " +
                    "${candidates.collect { CommandLine each -> FeatureSelectionException.shown(each.getCommandName()) }.join(', ')}. " +
                    'Type more of the name of the one that is meant')
        }
        return candidates.isEmpty() ? null : candidates.get(0)
    }

    /**
     * The refusal of what was typed for a command and is none, with the commands that are most like it.
     *
     * @param parent the command a command was expected of
     * @param typed what was typed
     * @return the mistake in the command line, which is answered with how the command is used
     */
    static CommandLine.ParameterException unknown(CommandLine parent, String typed) {
        String given = FeatureSelectionException.shown(typed)
        List<String> similar = new CommandLine.UnmatchedArgumentException(parent, Collections.singletonList(given)).getSuggestions()
        return new CommandLine.ParameterException(parent, "There is no command ${given}" +
                (parent.getParent() == null ? '' : " of ${parent.getCommandName()}") +
                (similar.isEmpty() ? '' : ". Did you mean: ${similar.join(' or ')}?"))
    }

    /**
     * Replaces the short form of the name of a command by the name, in a command line that is about
     * to be read. Only what stands where the name of a command is expected is looked at: the first
     * argument that is not an option of the command line, and after a command that has commands of
     * its own the next one. Whatever follows the last command is the command's and is left as it is,
     * and so is the whole command line when it cannot be told where the name stands.
     *
     * @param args the command line
     * @return the command line with the names of the commands
     * @throws CommandLine.ParameterException when a short form is that of several commands, and when
     * what stands where a command is expected is neither a command nor the short form of one
     */
    String[] expand(String... args) {
        String[] expanded = args.clone()
        CommandLine current = root
        for (int i = 0; i < expanded.length; i++) {
            String arg = expanded[i]
            CommandLine.Model.CommandSpec spec = current.getCommandSpec()
            // a command without commands takes everything that follows, and so may one that has parameters
            if (arg == null || spec.subcommands().isEmpty() || !spec.positionalParameters().isEmpty()) {
                break
            }
            // after -- nothing is a command, and what a file of arguments holds is not known here
            if (arg == '--' || isArgumentFile(arg)) {
                break
            }
            if (arg.startsWith('-')) {
                if (isSwitches(spec, arg)) {
                    continue
                }
                int assigned = arg.indexOf('=')
                CommandLine.Model.OptionSpec option = spec.findOption(assigned < 0 ? arg : arg.substring(0, assigned))
                if (option == null) {
                    break
                }
                if (assigned < 0 && option.arity().max() > 0) {
                    // an option whose value may be left out: the next argument is its value or a command
                    if (option.arity().min() == 0) {
                        break
                    }
                    i += option.arity().min()
                }
                continue
            }
            CommandLine named = current.getSubcommands().get(arg)
            if (named == null) {
                named = find(current, arg)
                if (named == null) {
                    throw unknown(current, arg)
                }
                expanded[i] = named.getCommandName()
            }
            current = named
        }
        return expanded
    }

    // @file is read as a file of arguments when there is such a file, and is an argument like any other when there is none
    private boolean isArgumentFile(String arg) {
        return arg.length() > 1 && arg.startsWith('@') && root.isExpandAtFiles() && new File(arg.substring(1)).exists()
    }

    // several options that take no value written as one, as -xv
    private static boolean isSwitches(CommandLine.Model.CommandSpec spec, String arg) {
        if (arg.length() < 3 || arg.charAt(1) == ('-' as char)) {
            return false
        }
        for (int i = 1; i < arg.length(); i++) {
            CommandLine.Model.OptionSpec option = spec.findOption('-' + arg.charAt(i))
            if (option == null || option.arity().max() > 0) {
                return false
            }
        }
        return true
    }

    /**
     * Describes the commands for a tool, as the document {@code help --json} writes. The document
     * is described in the design of the command line; a reader ignores what it does not know.
     *
     * @param projectDirectory the directory of the project the commands are those of, or {@code null} outside a project
     * @param diagnostics what kept commands from being found, one line each; the catalog is complete when there is none
     * @return the document, as maps, lists, strings, numbers and booleans
     */
    Map<String, Object> describe(Path projectDirectory, List<String> diagnostics) {
        Map<String, Object> document = new LinkedHashMap<>()
        document.put('format', FORMAT)
        document.put('complete', diagnostics.isEmpty())
        document.put('diagnostics', new ArrayList<String>(diagnostics))
        document.put('projectDirectory', projectDirectory == null ? null : projectDirectory.toString())
        document.put('commands', commands(root))
        return document
    }

    private List<Map<String, Object>> commands(CommandLine parent) {
        List<Map<String, Object>> described = []
        for (CommandLine command : listed(parent)) {
            CommandLine.Model.CommandSpec spec = command.getCommandSpec()
            Map<String, Object> each = new LinkedHashMap<>()
            each.put('name', spec.name())
            each.put('aliases', spec.aliases().toList())
            each.put('description', text(spec.usageMessage().description()))
            each.put('project', spec.userObject() instanceof ProjectCommand)
            each.put('origin', BUILT_IN)
            List<Map<String, Object>> options = []
            for (CommandLine.Model.OptionSpec option : spec.options()) {
                if (option.hidden()) {
                    continue
                }
                Map<String, Object> entry = new LinkedHashMap<>()
                entry.put('names', option.names().toList())
                entry.put('arity', option.arity().toString())
                entry.put('label', option.arity().max() > 0 ? option.paramLabel() : null)
                entry.put('required', option.required())
                entry.put('description', text(option.renderedDescription()))
                options.add(entry)
            }
            each.put('options', options)
            List<Map<String, Object>> parameters = []
            for (CommandLine.Model.PositionalParamSpec parameter : spec.positionalParameters()) {
                if (parameter.hidden()) {
                    continue
                }
                Map<String, Object> entry = new LinkedHashMap<>()
                entry.put('label', parameter.paramLabel())
                entry.put('arity', parameter.arity().toString())
                entry.put('description', text(parameter.renderedDescription()))
                parameters.add(entry)
            }
            each.put('parameters', parameters)
            each.put('commands', commands(command))
            described.add(each)
        }
        return described
    }

    // the commands the help lists, once each: a command is registered under every name it has
    private static List<CommandLine> listed(CommandLine parent) {
        Set<CommandLine> commands = Collections.newSetFromMap(new IdentityHashMap<CommandLine, Boolean>())
        List<CommandLine> listed = []
        for (CommandLine command : parent.getSubcommands().values()) {
            if (!command.getCommandSpec().usageMessage().hidden() && commands.add(command)) {
                listed.add(command)
            }
        }
        return listed
    }

    private static List<CommandLine> shortFor(CommandLine parent, String typed) {
        if (!SHORT_FORM.matcher(typed).matches()) {
            return Collections.emptyList()
        }
        return listed(parent).findAll { CommandLine command ->
            CommandLine.Model.CommandSpec spec = command.getCommandSpec()
            ScriptNameResolver.resolvesTo(typed, spec.name()) || spec.aliases().any { String alias -> ScriptNameResolver.resolvesTo(typed, alias) }
        }
    }

    // the lines of a description as plain text: without the markup of the help, which a tool would show as it is
    private static String text(String[] lines) {
        return lines.collect { String line -> CommandLine.Help.Ansi.OFF.string(line).trim() }.findAll { String line -> !line.isEmpty() }.join(' ')
    }
}
