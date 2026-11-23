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

import java.util.regex.Pattern
import groovy.transform.CompileStatic
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.cli.command.CodeGenCommand
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.FileSystemOutputHandler
import picocli.CommandLine

/**
 * How the commands of the CLI say how they are used, where that is not how picocli says it.
 *
 * <p>The usage of a command shows a system property as it is typed, {@code -DNAME=VALUE}, and a
 * project property likewise: picocli puts the sign that stands between an option and its value
 * between them, which is right for {@code --tests=TEST} and gives {@code -D=NAME=VALUE} for these.</p>
 *
 * <p>A mistake in the use of a command is answered with what is wrong, one line that shows how the
 * command is used, and where its help is: not with the whole help, in which what is wrong is some
 * sixty lines above the prompt.</p>
 *
 * @since 9.0
 */
@CompileStatic
class UsageHelp extends CommandLine.Help {

    // the columns of an option in the list of options: its one letter, and its long names with the label
    private static final int LETTER = 1
    private static final int LABEL = 3

    UsageHelp(CommandLine.Model.CommandSpec spec, CommandLine.Help.ColorScheme scheme) {
        super(spec, scheme)
    }

    // picocli lists the options by their names in upper case, and takes the upper case of the language
    // of the computer: in Turkish that of i is not I, and -i was listed after -x. These are its two
    // orders with the upper case of no language, so the help of a command is the same text everywhere.
    private static final Comparator<CommandLine.Model.OptionSpec> BY_NAME = new ByName()
    private static final Comparator<CommandLine.Model.OptionSpec> BY_ARITY_AND_NAME = new ByArityAndName()

    @CompileStatic
    private static class ByName implements Comparator<CommandLine.Model.OptionSpec> {

        @Override
        int compare(CommandLine.Model.OptionSpec one, CommandLine.Model.OptionSpec other) {
            String first = withoutPrefix(one.shortestName())
            String second = withoutPrefix(other.shortestName())
            int result = first.toUpperCase(Locale.ROOT) <=> second.toUpperCase(Locale.ROOT)
            // lower case before upper case, and the options that show help last
            result = result == 0 ? second <=> first : result
            return one.help() == other.help() ? result : other.help() ? -1 : 1
        }
    }

    @CompileStatic
    private static class ByArityAndName extends ByName {

        @Override
        int compare(CommandLine.Model.OptionSpec one, CommandLine.Model.OptionSpec other) {
            int result = one.arity().max() - other.arity().max()
            if (result == 0) {
                result = one.arity().min() - other.arity().min()
            }
            if (result == 0 && one.isMultiValue() != other.isMultiValue()) {
                result = one.isMultiValue() ? 1 : -1
            }
            return result == 0 ? super.compare(one, other) : result
        }
    }

    // The help of a command ends its description with the shortest form it can be typed in, as Short form: ra
    // for run-app: what is typed has to be told from the other commands there are, and is worked out then.
    @Override
    String description(Object... params) {
        String described = super.description(params)
        String form = shortForm(commandSpec())
        return form == null ? described : described + "Short form: ${form}" + String.format('%n')
    }

    /**
     * @param spec a command
     * @return what is typed for the command in its shortest form, the commands it is a command of
     * among it, as {@code f a} for {@code feature add}; {@code null} for the command line itself, and
     * where no name in it can be typed shorter, see {@link CommandCatalog#shortestForm}
     */
    static String shortForm(CommandLine.Model.CommandSpec spec) {
        List<String> typed = []
        boolean shorter = false
        CommandLine.Model.CommandSpec each = spec
        while (each.parent() != null) {
            String form = CommandCatalog.shortestForm(each.commandLine())
            boolean shortened = form != null && form.length() < each.name().length()
            shorter = shorter || shortened
            typed.add(0, shortened ? form : each.name())
            each = each.parent()
        }
        return shorter ? typed.join(' ') : null
    }

    @Override
    Comparator<CommandLine.Model.OptionSpec> createDefaultOptionSort() {
        return commandSpec().usageMessage().sortOptions() ? BY_NAME : super.createDefaultOptionSort()
    }

    @Override
    String synopsis(int synopsisHeadingLength) {
        CommandLine.Model.UsageMessageSpec usage = commandSpec().usageMessage()
        if (usage.customSynopsis() || usage.abbreviateSynopsis() || !usage.sortSynopsis()) {
            return super.synopsis(synopsisHeadingLength)
        }
        return detailedSynopsis(synopsisHeadingLength, BY_ARITY_AND_NAME, commandSpec().parser().posixClusteredShortOptionsAllowed())
    }

    // the name of an option from its first letter or digit on
    private static String withoutPrefix(String name) {
        for (int index = 0; index < name.length(); index++) {
            if (Character.isJavaIdentifierPart(name.charAt(index))) {
                return name.substring(index)
            }
        }
        return name
    }

    @Override
    CommandLine.Help.IParamLabelRenderer parameterLabelRenderer() {
        return asTyped(super.parameterLabelRenderer())
    }

    @Override
    CommandLine.Help.IParamLabelRenderer createDefaultParamLabelRenderer() {
        return asTyped(super.createDefaultParamLabelRenderer())
    }

    // Among the options picocli takes the sign out of the label itself, for an option that has a short
    // name only, and writes it after the name: for such an option the label is given without the
    // sign, and the sign is said to be nothing.
    @Override
    CommandLine.Help.IOptionRenderer createDefaultOptionRenderer() {
        CommandLine.Help.IOptionRenderer usual = super.createDefaultOptionRenderer()
        return { CommandLine.Model.OptionSpec option, CommandLine.Help.IParamLabelRenderer labels, CommandLine.Help.ColorScheme scheme ->
            if (!isSystemProperty(option)) {
                return usual.render(option, labels, scheme)
            }
            CommandLine.Help.Ansi.Text[][] rows = usual.render(option, withoutSign(labels), scheme)
            // The letter, the sign and the label each have a column, and what does not fit its column moves
            // the description to a line of its own: the letter is put in front of the label, where the
            // long names of the other options are.
            if (rows.length > 0 && rows[0].length > LABEL) {
                rows[0][LABEL] = rows[0][LETTER].concat(rows[0][LABEL])
                rows[0][LETTER] = scheme.text('')
            }
            return rows
        } as CommandLine.Help.IOptionRenderer
    }

    // an option that is typed with its value right after its one letter, as -Dname=value and -Pname=value
    private static boolean isSystemProperty(CommandLine.Model.ArgSpec argSpec) {
        if (!(argSpec instanceof CommandLine.Model.OptionSpec)) {
            return false
        }
        CommandLine.Model.OptionSpec option = (CommandLine.Model.OptionSpec) argSpec
        return Map.isAssignableFrom(option.type()) && option.names().every { String name -> name.length() == 2 }
    }

    private static CommandLine.Help.IParamLabelRenderer asTyped(CommandLine.Help.IParamLabelRenderer usual) {
        return new CommandLine.Help.IParamLabelRenderer() {

            @Override
            CommandLine.Help.Ansi.Text renderParameterLabel(CommandLine.Model.ArgSpec argSpec, CommandLine.Help.Ansi ansi, List<CommandLine.Help.Ansi.IStyle> styles) {
                CommandLine.Help.Ansi.Text label = usual.renderParameterLabel(argSpec, ansi, styles)
                String plain = label.plainString()
                // written again, without the sign: a part of a text that is styled is not cut at the place it is asked for
                return isSystemProperty(argSpec) && plain.startsWith(separator()) ? ansi.apply(plain.substring(separator().length()), styles) : label
            }

            @Override
            String separator() {
                return usual.separator()
            }
        }
    }

    private static CommandLine.Help.IParamLabelRenderer withoutSign(CommandLine.Help.IParamLabelRenderer typed) {
        return new CommandLine.Help.IParamLabelRenderer() {

            @Override
            CommandLine.Help.Ansi.Text renderParameterLabel(CommandLine.Model.ArgSpec argSpec, CommandLine.Help.Ansi ansi, List<CommandLine.Help.Ansi.IStyle> styles) {
                return typed.renderParameterLabel(argSpec, ansi, styles)
            }

            @Override
            String separator() {
                return ''
            }
        }
    }

    /** How many characters of a mistake are said, at most: what was typed can be of any length. */
    public static final int MAX_MISTAKE = 600

    private static final Pattern NOT_SHOWN = Pattern.compile(/[\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}]/)

    /**
     * Answers a mistake in the use of a command. What is wrong is said in one line, whatever was
     * typed: a message of picocli repeats the argument as it was, with its line breaks and its escape
     * sequences, and is held to one line of bounded length here as the messages of the CLI are.
     *
     * @param mistake the mistake, as picocli or a command found it
     * @param given the command line
     * @return the exit code of a mistake in the use of a command, or of a command of a project that
     * was not given what it needs where there is no project: that there is none is said first
     */
    static int answer(CommandLine.ParameterException mistake, String... given) {
        CommandLine command = mistake.getCommandLine()
        CommandLine.Model.CommandSpec spec = command.getCommandSpec()
        PrintWriter err = command.getErr()
        CommandLine.Help.ColorScheme scheme = command.getColorScheme()
        if (mistake instanceof CommandLine.MissingParameterException) {
            String refusal = withoutProject(spec)
            if (refusal != null) {
                err.println(CommandLine.Help.Ansi.AUTO.string('@|bold,red | Error|@ ' + refusal))
                err.flush()
                return 1
            }
        }
        if (mistake instanceof Lines) {
            err.println(scheme.errorText(mistake.getMessage()))
        } else {
            err.println(scheme.errorText(line(notAnOption(mistake, spec, given) ?: worded(mistake, spec))))
        }
        // for an option that is nearly one of the command's, the ones it could have been
        CommandLine.UnmatchedArgumentException.printSuggestions(mistake, err)
        CommandLine.Help help = command.getHelpFactory().create(spec, scheme)
        String[] own = spec.usageMessage().customSynopsis()
        // every option where that is one line, and where it is more only that there are options
        String every = help.synopsis(0).trim()
        // a usage line a command wrote itself is its lines put together: here it is one
        String synopsis = own != null && own.length > 0 ? own.collect { String each -> scheme.text(each).plainString().trim() }.join(' ') :
                every.contains('\n') || every.length() > spec.usageMessage().width() ? help.abbreviatedSynopsis().trim() : every
        err.println("Usage: ${synopsis}")
        err.println(spec.parent() == null ? "Run '${toType(spec, 'help')}' for the commands" : "Run '${toType(spec, "help ${named(spec)}")}' for the options")
        err.flush()
        return spec.exitCodeOnInvalidInput()
    }

    /**
     * A command line of this program as it is typed where a message shows it: on the command line of a
     * shell after the name of the program, and at the prompt of the interactive console without it,
     * since the prompt takes the commands themselves and refuses the name of the program.
     *
     * @param spec a command of the command line, or the command line itself
     * @param command what is typed after the name of the program, as {@code help create-app}
     * @return the command line to type there
     */
    static String toType(CommandLine.Model.CommandSpec spec, String command) {
        return ConsoleCommands.isInteractive() ? command : "${spec.root().name()} ${command}".toString()
    }

    /**
     * @param root the command line of the CLI
     * @param command what is typed after the name of the program
     * @return the command line to type where the message is shown, see {@link #toType(CommandLine.Model.CommandSpec, String)}
     */
    static String toType(CommandLine root, String command) {
        return toType(root.getCommandSpec(), command)
    }

    /**
     * @param text a message that has something in it that was typed
     * @return the message as one line of bounded length that a terminal and the command line do not act on
     */
    static String line(String text) {
        String all = text ?: ''
        String start = all.length() > MAX_MISTAKE ? all.substring(0, MAX_MISTAKE) + '...' : all
        return NOT_SHOWN.matcher(start).replaceAll('?').replace('@|', '@ |')
    }

    /**
     * @param spec a command
     * @return the command as it is named in a message: by its name, after the names of the commands
     * it is a command of, as {@code feature add}; the command line itself by its own name
     */
    static String named(CommandLine.Model.CommandSpec spec) {
        String root = spec.root().name()
        return spec.parent() == null ? root : spec.qualifiedName(' ').substring(root.length() + 1)
    }

    /**
     * @param spec a command that was run
     * @return the command as it was typed: by the other name it has when that was typed, as
     * {@code war} for {@code assemble}
     */
    static String typed(CommandLine.Model.CommandSpec spec) {
        CommandLine.ParseResult parsed = spec.root().commandLine().getParseResult()
        if (spec.parent() == null || spec.parent().parent() != null || parsed == null) {
            return named(spec)
        }
        List<String> names = [spec.name()]
        names.addAll(spec.aliases().toList())
        String typed = parsed.originalArgs().find { String arg -> arg in names }
        return typed ?: spec.name()
    }

    /**
     * @param spec a command
     * @return for a command of a project where no project is used, the one line every such command
     * says there, and for a command that needs the record of a project where none was read, that;
     * {@code null} for any other command, and in a project
     */
    static String withoutProject(CommandLine.Model.CommandSpec spec) {
        if (!(spec.userObject() instanceof ProjectCommand)) {
            return null
        }
        try {
            ConsoleProject.Search search = ConsoleProject.search(FileSystemOutputHandler.getDefaultBaseDirectory().toPath())
            if (search.project == null) {
                return search.none(typed(spec))
            }
            // a command that writes what the record of a project says, in a project that has none that is read
            if (spec.userObject() instanceof CodeGenCommand && !((CodeGenCommand) spec.userObject()).hasRecord()) {
                return "${typed(spec)} is a command of a Grails project whose record is read; the project in ${GradleRunner.line(search.project.directory)} " +
                        "has no ${ProjectState.MANIFEST} or ${ProjectState.EARLIER_RECORD} that is. ${toType(spec, 'project migrate')} writes the record of " +
                        'an application that has none'
            }
            return null
        } catch (IOException ignored) {
            return null
        }
    }

    // One wording for one mistake: an option a command does not have, and an argument it does not take,
    // are said as the CLI says them, with the command named as it is typed.
    private static String worded(CommandLine.ParameterException mistake, CommandLine.Model.CommandSpec spec) {
        if (mistake instanceof CommandLine.UnmatchedArgumentException) {
            List<String> unmatched = ((CommandLine.UnmatchedArgumentException) mistake).getUnmatched()
            if (unmatched != null && !unmatched.isEmpty()) {
                String first = unmatched.get(0)
                String more = unmatched.size() > 1 ? ", or the ${unmatched.size() - 1} after it" : ''
                if (first.startsWith('-') && first.length() > 1) {
                    return "${named(spec)} has no option ${FeatureSelectionException.shown(optionOf(first))}${more}"
                }
                String advice = spec.userObject() instanceof ArgumentAdvice ? ((ArgumentAdvice) spec.userObject()).adviceFor(first) : null
                return "${named(spec)} takes no argument ${FeatureSelectionException.shown(first)}${more}${advice == null ? '' : '. ' + advice}"
            }
        }
        return mistake.getMessage()
    }

    /**
     * A command that can say, of an argument it does not take, what was meant by it: the refusal of the
     * argument ends with that.
     */
    static interface ArgumentAdvice {

        /**
         * @param argument the first argument the command does not take, as it was typed
         * @return one or two sentences, the last without its full stop, that say what to type instead;
         * {@code null} where nothing can be told of the argument
         */
        String adviceFor(String argument)
    }

    private static String optionOf(String argument) {
        int assigned = argument.indexOf('=')
        return assigned < 0 ? argument : argument.substring(0, assigned)
    }

    /**
     * A mistake whose message is several lines the CLI put together itself, each from text that is
     * its own or was made safe to show: the lines are said as they are.
     */
    static class Lines extends CommandLine.ParameterException {

        Lines(CommandLine commandLine, String message) {
            super(commandLine, message)
        }
    }

    // picocli says of a command that was given an option it does not have, and not what it needs, only
    // that what it needs is missing: the option that is none is the mistake that was made
    private static String notAnOption(CommandLine.ParameterException mistake, CommandLine.Model.CommandSpec spec, String... given) {
        if (!(mistake instanceof CommandLine.MissingParameterException) || spec.parent() == null) {
            return null
        }
        int from = given.toList().indexOf(spec.name())
        for (String alias : spec.aliases()) {
            from = from < 0 ? given.toList().indexOf(alias) : from
        }
        if (from < 0) {
            return null
        }
        for (int i = from + 1; i < given.length; i++) {
            String arg = given[i]
            if (arg == '--') {
                return null
            }
            if (!arg.startsWith('-') || arg.length() < 2) {
                continue
            }
            int assigned = arg.indexOf('=')
            String name = assigned < 0 ? arg : arg.substring(0, assigned)
            boolean known = spec.findOption(name) != null ||
                    (!name.startsWith('--') && (1..<name.length()).every { int at -> spec.findOption('-' + name.charAt(at)) != null }) ||
                    (!name.startsWith('--') && spec.findOption(name.substring(0, 2)) != null)
            if (!known) {
                return "${named(spec)} has no option ${FeatureSelectionException.shown(name)}"
            }
        }
        return null
    }
}
