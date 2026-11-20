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
import org.grails.forge.feature.FeatureSelectionException
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

    /**
     * Answers a mistake in the use of a command.
     *
     * @param mistake the mistake, as picocli or a command found it
     * @param given the command line
     * @return the exit code of a mistake in the use of a command
     */
    static int answer(CommandLine.ParameterException mistake, String... given) {
        CommandLine command = mistake.getCommandLine()
        CommandLine.Model.CommandSpec spec = command.getCommandSpec()
        PrintWriter err = command.getErr()
        CommandLine.Help.ColorScheme scheme = command.getColorScheme()
        err.println(scheme.errorText(notAnOption(mistake, spec, given) ?: mistake.getMessage()))
        // for an option that is nearly one of the command's, the ones it could have been
        CommandLine.UnmatchedArgumentException.printSuggestions(mistake, err)
        CommandLine.Help help = command.getHelpFactory().create(spec, scheme)
        String[] own = spec.usageMessage().customSynopsis()
        // every option where that is one line, and where it is more only that there are options
        String every = help.synopsis(0).trim()
        String synopsis = own != null && own.length > 0 ? scheme.text(own[0]).plainString() :
                every.contains('\n') || every.length() > spec.usageMessage().width() ? help.abbreviatedSynopsis().trim() : every
        err.println("Usage: ${synopsis}")
        String root = spec.root().name()
        String path = spec.qualifiedName(' ').substring(root.length())
        err.println(spec.parent() == null ? "Run '${root} help' for the commands" : "Run '${root} help${path}' for the options")
        err.flush()
        return spec.exitCodeOnInvalidInput()
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
                return "${spec.name()} has no option ${FeatureSelectionException.shown(name)}"
            }
        }
        return null
    }
}
