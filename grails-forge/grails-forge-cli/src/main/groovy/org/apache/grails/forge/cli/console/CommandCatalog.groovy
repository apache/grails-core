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
import java.util.function.Function
import java.util.function.Supplier
import groovy.transform.CompileStatic
import org.grails.forge.cli.ForgeContext
import org.grails.forge.cli.command.CodeGenCommand
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.FileSystemOutputHandler
import picocli.CommandLine

/**
 * The commands a command line has, read from its picocli model: every command that is registered
 * with the command line is in the catalog, with nothing else to do for it. The catalog finds a
 * command by its name or by the short form of its name, and describes the commands for a tool.
 *
 * <p>A short form is read as {@link ShortForms} says: as the Grails shell read it, so that {@code cA}
 * and {@code creA} are {@code create-app} and {@code cDC} is {@code create-domain-class}, and, when it
 * is of lower-case letters only, also as the starts of the words of the name, so that {@code ra} is
 * {@code run-app} and {@code ccon} is {@code create-controller}. The name of a command, or another
 * name it has, is that command whatever it is short for; at the top of the command line a name of
 * {@link FormerCommands} is short for nothing, and is answered with what there is in its place. The
 * commands of the CLI itself come first: a short form that fits one of them is not one of a command
 * of the application or of its plugins.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class CommandCatalog {

    /** The version of the document {@link #describe} gives. */
    public static final int FORMAT = 1

    /** The origin of a command that is part of the command line itself. */
    public static final String BUILT_IN = 'built-in'

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
        Known known = new Known(parent)
        List<CommandLine> candidates = known.shortFor(typed)
        if (candidates.size() > 1) {
            throw new CommandLine.ParameterException(parent, ambiguous(known, typed, candidates))
        }
        return candidates.isEmpty() ? null : candidates.get(0)
    }

    // A short form that fits several commands runs none of them, and names them, each with the
    // shortest form that is short for it alone, which is what is then typed.
    private static String ambiguous(Known known, String typed, List<CommandLine> candidates) {
        boolean anyForm = false
        List<String> named = []
        for (CommandLine candidate : candidates) {
            String form = ShortForms.shortest(candidate, { String each -> known.resolve(each) } as Function<String, CommandLine>)
            anyForm = anyForm || form != null
            named.add(FeatureSelectionException.shown(candidate.getCommandName()) + (form == null ? '' : " (${form})"))
        }
        return "${FeatureSelectionException.shown(typed)} is short for more than one command: ${named.join(', ')}. " +
                'Type more of the name of the one that is meant' + (anyForm ? ', or the short form in brackets after it' : '')
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
        // what was typed is compared, and nothing else: not what stands for an empty name or for one that cannot be shown as it is
        List<String> similar = typed == null || typed.isEmpty() || given != typed ? Collections.<String>emptyList() :
                new CommandLine.UnmatchedArgumentException(parent, Collections.singletonList(typed)).getSuggestions()
        // it may be a command of the application, where the build of the project has not listed those
        String listed = parent.getParent() == null ? ApplicationCommands.whenUnknown(parent, given) : null
        // below a project that is not used its commands are not there, which is then why there is none of that name
        String untrusted = parent.getParent() == null ? ConsoleProject.search(FileSystemOutputHandler.getDefaultBaseDirectory().toPath()).untrusted : null
        return new UsageHelp.Lines(parent, "There is no command ${given}" +
                (parent.getParent() == null ? '' : " of ${parent.getCommandName()}") +
                (similar.isEmpty() ? '' : ". Did you mean: ${similar.join(' or ')}?") +
                (untrusted == null ? '' : "${System.lineSeparator()}The commands of a project are not there: ${untrusted}") +
                (listed == null ? '' : "${System.lineSeparator()}${listed}") +
                FormerCommands.line(parent, typed))
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
        boolean help = false
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
                    help = help || (1..<arg.length()).any { int at -> asksForHelp(spec.findOption('-' + arg.charAt(at))) }
                    continue
                }
                int assigned = arg.indexOf('=')
                CommandLine.Model.OptionSpec option = spec.findOption(assigned < 0 ? arg : arg.substring(0, assigned))
                if (option == null) {
                    break
                }
                help = help || asksForHelp(option)
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
                    // the help or the version was asked for: that is the answer, whatever else was typed
                    if (help) {
                        break
                    }
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

    private static boolean asksForHelp(CommandLine.Model.OptionSpec option) {
        return option != null && (option.usageHelp() || option.versionHelp())
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
     * @param diagnostics what there is to say about the commands of the project, one line each
     * @param complete whether every command of the project is among the commands: false when the
     * record of the project could not be used, or the project is not used. A diagnostic alone, as
     * that the two records of a project do not say the same, leaves the catalog complete
     * @return the document, as maps, lists, strings, numbers and booleans
     */
    Map<String, Object> describe(Path projectDirectory, List<String> diagnostics, boolean complete) {
        Map<String, Object> document = new LinkedHashMap<>()
        document.put('format', FORMAT)
        document.put('complete', complete)
        document.put('diagnostics', new ArrayList<String>(diagnostics))
        document.put('projectDirectory', projectDirectory == null ? null : projectDirectory.toString())
        // what the command line takes in front of a command
        document.put('options', options(root.getCommandSpec()))
        document.put('commands', commands(root))
        return document
    }

    private List<Map<String, Object>> commands(CommandLine parent) {
        List<Map<String, Object>> described = []
        for (CommandLine command : listed(parent)) {
            CommandLine.Model.CommandSpec spec = command.getCommandSpec()
            if (spec.userObject() instanceof ApplicationCommandCommand) {
                // a command of the application is described by the build of the project
                described.add(((ApplicationCommandCommand) spec.userObject()).describe())
                continue
            }
            Map<String, Object> each = new LinkedHashMap<>()
            each.put('name', spec.name())
            each.put('aliases', spec.aliases().toList())
            each.put('description', text(spec.usageMessage().description()))
            each.put('project', spec.userObject() instanceof ProjectCommand)
            each.put('origin', BUILT_IN)
            each.put('options', options(spec))
            List<Map<String, Object>> parameters = []
            for (CommandLine.Model.PositionalParamSpec parameter : spec.positionalParameters()) {
                if (parameter.hidden()) {
                    continue
                }
                Map<String, Object> entry = new LinkedHashMap<>()
                entry.put('label', parameter.paramLabel())
                entry.put('arity', parameter.arity().toString())
                entry.put('description', text(parameter.renderedDescription()))
                entry.put('repeatable', parameter.isMultiValue())
                entry.put('split', parameter.splitRegex() ?: null)
                entry.put('choices', choices(parameter))
                parameters.add(entry)
            }
            each.put('parameters', parameters)
            each.put('relations', spec.userObject() instanceof OptionRelation.Declared ?
                    ((OptionRelation.Declared) spec.userObject()).getOptionRelations().collect { OptionRelation relation -> relation.describe() } :
                    Collections.<Map<String, Object>>emptyList())
            each.put('commands', commands(command))
            described.add(each)
        }
        return described
    }

    private static final Comparator<CommandLine.Model.OptionSpec> OPTION_ORDER = new ByLongestName()

    // by the longest name of an option without its hyphens, whatever its case, and then as it is written
    @CompileStatic
    private static class ByLongestName implements Comparator<CommandLine.Model.OptionSpec> {

        @Override
        int compare(CommandLine.Model.OptionSpec one, CommandLine.Model.OptionSpec other) {
            String first = one.longestName().replaceFirst(/^-+/, '')
            String second = other.longestName().replaceFirst(/^-+/, '')
            int ignoringCase = String.CASE_INSENSITIVE_ORDER.compare(first, second)
            return ignoringCase != 0 ? ignoringCase : one.longestName() <=> other.longestName()
        }
    }

    private static List<Map<String, Object>> options(CommandLine.Model.CommandSpec spec) {
        List<Map<String, Object>> options = []
        // Reflection does not promise field order; use an explicit, locale-independent order.
        for (CommandLine.Model.OptionSpec option : spec.options().toSorted(OPTION_ORDER)) {
            if (option.hidden()) {
                continue
            }
            Map<String, Object> entry = new LinkedHashMap<>()
            entry.put('names', option.names().toList())
            entry.put('arity', option.arity().toString())
            entry.put('label', option.arity().max() > 0 ? option.paramLabel() : null)
            entry.put('required', option.required())
            entry.put('description', description(option))
            entry.put('repeatable', option.isMultiValue())
            entry.put('split', option.splitRegex() ?: null)
            entry.put('choices', choices(option))
            entry.put('default', defaultOf(option))
            options.add(entry)
        }
        return options
    }

    // Every value an option or a parameter takes, where those are all it takes: the constants of an
    // enum, or values that say they are all there are. Values that are only suggestions are none, and
    // neither are the features, which only the generator knows and a tool asks --list-features for.
    private static List<String> choices(CommandLine.Model.ArgSpec arg) {
        Iterable<String> candidates = arg.completionCandidates()
        if (candidates == null || candidates instanceof ForgeContext.Candidates || !(arg.type().isEnum() || candidates instanceof OptionChoices)) {
            return null
        }
        List<String> choices = []
        for (String each : candidates) {
            choices.add(each)
        }
        return choices
    }

    // The value an option that takes one has when it is not given, as the command declares it; null where
    // it declares none, or where what the command does without the option is not a value, as for a switch.
    private static String defaultOf(CommandLine.Model.OptionSpec option) {
        if (option.arity().max() == 0) {
            return null
        }
        if (option.defaultValue() != null) {
            return option.defaultValue()
        }
        Object initial = option.hasInitialValue() ? option.initialValue() : null
        return initial == null || initial instanceof Collection || initial instanceof Map || initial.getClass().isArray() ? null : String.valueOf(initial)
    }

    // The values an option can have are part of its description, except those only the generator
    // knows, the features: the document is written without the generator, and is the same whether
    // or not something started it before.
    private static String description(CommandLine.Model.OptionSpec option) {
        Iterable<String> candidates = option.completionCandidates()
        if (candidates instanceof ForgeContext.Candidates) {
            return ((ForgeContext.Candidates) candidates).withoutListing({ text(option.renderedDescription()) } as Supplier<String>)
        }
        return text(option.renderedDescription())
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

    // whether what was typed is the name of a command the Grails shell had, which is answered with what there is in its place and is short for nothing
    private static boolean isFormer(CommandLine parent, String typed) {
        return parent.getParent() == null && FormerCommands.find(typed) != null
    }

    /**
     * The commands of a command that a short form is looked up among: those the help lists, and outside
     * a project the commands that create the classes of a project, which are not listed there; they are
     * commands there by their names, and so by their short forms. The commands of the CLI itself come
     * first: what a project or one of its plugins calls a command does not change what a short form of
     * theirs means, or make it stand for two.
     */
    @CompileStatic
    private static final class Known {

        private final CommandLine parent
        private final List<CommandLine> own = []
        private final List<CommandLine> ofTheBuild = []

        Known(CommandLine parent) {
            this.parent = parent
            List<CommandLine> known = listed(parent)
            for (CommandLine command : new LinkedHashSet<CommandLine>(parent.getSubcommands().values())) {
                if (command.getCommandSpec().usageMessage().hidden() && command.getCommandSpec().userObject() instanceof CodeGenCommand) {
                    known.add(command)
                }
            }
            for (CommandLine command : known) {
                (command.getCommandSpec().userObject() instanceof ApplicationCommandCommand ? ofTheBuild : own).add(command)
            }
        }

        // what a form is short for; nothing for anything that cannot be a short form, and for a name of the Grails shell
        List<CommandLine> shortFor(String typed) {
            if (!ShortForms.FORM.matcher(typed).matches() || isFormer(parent, typed)) {
                return Collections.emptyList()
            }
            List<CommandLine> fits = ShortForms.fitting(typed, own)
            return fits.isEmpty() ? ShortForms.fitting(typed, ofTheBuild) : fits
        }

        // the command what was typed stands for alone, by its name or as a short form; null for none and for several
        CommandLine resolve(String typed) {
            CommandLine named = parent.getSubcommands().get(typed)
            if (named != null) {
                return named
            }
            List<CommandLine> fits = shortFor(typed)
            return fits.size() == 1 ? fits.get(0) : null
        }
    }

    // the lines of a description as plain text: without the markup of the help, which a tool would show as it is
    private static String text(String[] lines) {
        return lines.collect { String line -> CommandLine.Help.Ansi.OFF.string(line).trim() }.findAll { String line -> !line.isEmpty() }.join(' ')
    }
}
