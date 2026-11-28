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

import java.nio.file.Files
import java.util.regex.Pattern
import groovy.transform.CompileStatic
import picocli.CommandLine

/**
 * {@code test-app}: the Gradle task {@value #TASK} of the Grails Gradle plugin, which runs the test
 * phases of the project, for the test environment unless another one is given. A phase is a
 * {@code Test} task of the build, and without a switch every phase is run, also the ones the
 * project adds. The names of tests are the filter of every phase that is run: a phase without such
 * a test passes, and the build fails when none ran a test.
 *
 * <p>A phase is selected by a switch of its name: {@code --unit} is the task {@code test},
 * {@code --integration} the task {@code integrationTest}, and any other switch {@code --<name>} the
 * task {@code <name>Test}, or the task {@code <name>} where there is no such task, with the hyphens
 * of the switch for the capitals of the task: {@code --end-to-end} is {@code endToEndTest}. Only the
 * build knows which phases there are. Where it has listed them ({@link ToolCatalogFile}),
 * each is an option of the command, and a switch that is none of them is refused. Where it has not,
 * a switch that is no option of the command is taken for a phase, unless it is one letter away from
 * an option, and the task {@value #TASK} fails when the project has no such phase, with the phases
 * it has. An option of the command is never a phase: a phase whose switch would be one is run by
 * its task.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'test-app',
        description = [
                'Runs the tests of the Grails project.',
                'Runs every test phase of the project, which are the Test tasks of its build, the ones the project adds among them, ' +
                        'for the test environment unless -Dgrails.env names another one: the Gradle task testApp.',
                'A phase is selected by a switch of its name: @|bold --unit|@ and @|bold --integration|@, and for another phase the name of its task ' +
                        'without the ending Test, as @|bold --functional|@ for functionalTest. Several can be given.',
                'What follows @|bold --|@ is given to Gradle as it is.'
        ],
        preprocessor = TestAppCommand.PhaseSwitches)
@CompileStatic
class TestAppCommand extends GradleEnvironmentCommand {

    /** The task of the Grails Gradle plugin that runs the test phases of a project. */
    static final String TASK = 'testApp'

    private static final String INTEGRATION_TESTS = 'src/integration-test'
    private static final String UNIT = 'unit'
    private static final String INTEGRATION = 'integration'
    private static final String LONG = '--'
    // what can be the switch of a phase: the name of a task in lower case, its words joined by hyphens
    private static final Pattern SWITCH = Pattern.compile('--[a-z][a-z0-9]{0,63}(-[a-z0-9]{1,63}){0,15}')

    @CommandLine.Parameters(arity = '0..*', paramLabel = 'TEST',
            description = 'The tests to run, as the filter of Gradle: a class name or a pattern such as example.BookSpec, *BookSpec or "*BookSpec.saves a book".')
    List<String> names = []

    @CommandLine.Option(names = ['--tests'], paramLabel = 'TEST', description = 'A test to run, as TEST above; can be given more than once.')
    List<String> tests = []

    @CommandLine.Option(names = ['--unit', '-unit'], description = 'Run the unit tests: the task test.')
    boolean unit

    @CommandLine.Option(names = ['--integration', '-integration'], description = 'Run the integration tests: the task integrationTest.')
    boolean integration

    @CommandLine.Option(names = ['--clean', '-clean'], description = 'Run every test again, also where nothing changed.')
    boolean clean

    @CommandLine.Option(names = ['--continuous', '-continuous'], description = 'Run the tests again whenever a file they depend on changes: the continuous build of Gradle.')
    boolean continuous

    @CommandLine.Option(names = ['--debug-jvm', '-debug-jvm'],
            description = 'Start the JVM of the tests suspended, waiting for a debugger on port 5005. Where several phases run tests, they wait one after the other.')
    boolean debugJvm

    // the phases that were asked for beside the unit and the integration tests, by their switches without the dashes, in the order they were typed
    private final List<String> phases = []
    // those of them that are no option of the command, and were taken for a phase by their form alone
    private final List<String> taken = []
    // what the build listed of the project; null where it was not looked at
    private ToolCatalogFile listed

    @Override
    protected String defaultEnvironment() {
        return 'test'
    }

    /**
     * Gives the command what the build of the project listed: each phase the build names, beside
     * the unit and the integration tests, becomes an option of the command, which the help and the
     * completion then show. A phase whose switch is an option the command has already gets none,
     * which the help says with the task that runs it.
     *
     * @param root the command line of the CLI
     * @param file the file of the build
     */
    static void phasesOf(CommandLine root, ToolCatalogFile file) {
        CommandLine command = root.getSubcommands().get('test-app')
        if (command == null || !(command.getCommandSpec().userObject() instanceof TestAppCommand)) {
            return
        }
        CommandLine.Model.CommandSpec spec = command.getCommandSpec()
        TestAppCommand testApp = (TestAppCommand) spec.userObject()
        testApp.listed = file
        List<String> footer = ['']
        Map<String, String> listedPhases = file.testPhases == null ? Collections.<String, String>emptyMap() : file.testPhases
        for (Map.Entry<String, String> phase : listedPhases.entrySet()) {
            String name = phase.key
            if (name == UNIT || name == INTEGRATION) {
                continue
            }
            if (spec.findOption(LONG + name) != null) {
                footer.add("The test phase ${name} (the task ${phase.value}) has no switch, since --${name} is an option of test-app. It is run with: ${UsageHelp.toType(root, "gradle ${phase.value}")}".toString())
                continue
            }
            spec.addOption(CommandLine.Model.OptionSpec.builder(LONG + name)
                    .type(boolean)
                    .arity('0')
                    .description("Run the test phase ${name}: the task ${phase.value}.".toString())
                    .setter(new Selected(testApp.phases, name))
                    .build())
        }
        String gradle = UsageHelp.toType(root, "gradle ${ToolCatalogFile.TASK}")
        if (file.testPhases == null) {
            footer.add("The test phases the project adds have a switch each, and are listed here once the build has listed them: ${gradle}".toString())
        } else if (file.state == ToolCatalogFile.State.STALE) {
            footer.add("The test phases may have changed: ${file.reason} is newer than ${ToolCatalogFile.LOCATION}. To list them again: ${gradle}".toString())
        }
        if (footer.size() > 1) {
            spec.usageMessage().footer(footer as String[])
        }
    }

    @Override
    protected List<String> gradleArguments(ConsoleProject project) {
        if (integration && !unit && phases.isEmpty() && !Files.isDirectory(project.directory.resolve(INTEGRATION_TESTS))) {
            err("The project in ${GradleRunner.line(project.directory)} has no integration tests: there is no ${INTEGRATION_TESTS} directory in it. Gradle was not run")
            return null
        }
        List<String> arguments = [project.task(TASK)]
        for (String phase : selected()) {
            arguments.add('--phase')
            arguments.add(phase)
        }
        for (String test : names + tests) {
            arguments.add('--tests')
            arguments.add(test)
        }
        if (clean) {
            arguments.add('--rerun-tests')
        }
        if (debugJvm) {
            arguments.add('--debug-jvm')
        }
        if (continuous) {
            arguments.add('--continuous')
        }
        arguments.addAll(systemPropertyArguments())
        return arguments
    }

    // the phases that were asked for: the unit tests, the integration tests, then the others as they were typed, each once
    private List<String> selected() {
        Set<String> selected = new LinkedHashSet<>()
        if (unit) {
            selected.add(UNIT)
        }
        if (integration) {
            selected.add(INTEGRATION)
        }
        selected.addAll(phases)
        return new ArrayList<String>(selected)
    }

    @Override
    protected void failed(ConsoleProject project) {
        // Which of the reasons a build fails for it was is in what Gradle wrote, and is not read from there
        if (!taken.isEmpty()) {
            List<String> switches = taken.collect { String phase -> LONG + phase }
            note("${switches.join(', ')} ${switches.size() == 1 ? 'is no option of test-app and was taken for a test phase' : 'are no options of test-app and were taken for test phases'} " +
                    "of the project: the task ${taskOf(taken.get(0))} for ${switches.get(0)}. If Gradle said that the project has no such phase, it named the phases there are; " +
                    "an option of Gradle is given after --, as in ${UsageHelp.toType(spec, 'test-app -- --info')}")
        }
    }

    private void note(String text) {
        spec.commandLine().getErr().println(CommandLine.Help.Ansi.AUTO.string('@|bold | Note|@ ' + GradleRunner.line(text)))
    }

    @Override
    protected void succeeded(ConsoleProject project) {
        out('| Tests PASSED')
    }

    /**
     * Takes the switches of test phases the command has no option for from the command line before
     * it is read, where the build has not listed the phases of the project: only the build knows
     * them. Where it has listed them, a switch that is none of them is refused.
     */
    private void takePhases(List<String> args, CommandLine.Model.CommandSpec commandSpec) {
        phases.clear()
        taken.clear()
        boolean known = listed != null && listed.testPhases != null && listed.state == ToolCatalogFile.State.CURRENT
        // the next argument is the top of the stack
        for (int i = args.size() - 1; i >= 0; i--) {
            String arg = args.get(i)
            int assigned = arg.indexOf('=')
            CommandLine.Model.OptionSpec option = arg.startsWith('-') ? commandSpec.findOption(assigned < 0 ? arg : arg.substring(0, assigned)) : null
            if (option != null) {
                if (assigned < 0) {
                    // the value of an option is no switch, whatever it looks like
                    i -= option.arity().min()
                }
                continue
            }
            if (!SWITCH.matcher(arg).matches()) {
                continue
            }
            String name = arg.substring(LONG.length())
            String similar = similarTo(arg, commandSpec, known ? 2 : 1)
            String meant = similar == null ? '' : " Did you mean: ${similar}?"
            if (known) {
                // the phases that have a switch: one whose switch would be an option of the command has none
                List<String> there = listed.testPhases.keySet().findAll { String phase ->
                    CommandLine.Model.OptionSpec of = commandSpec.findOption(LONG + phase)
                    of != null && (phase == UNIT || phase == INTEGRATION || of.setter() instanceof Selected)
                }.collect { String phase -> LONG + phase }
                throw new UsageHelp.Lines(commandSpec.commandLine(), "test-app has no option ${arg}, and the project has no test phase of that name: " +
                        "its build lists ${there.join(', ')}.${meant}${System.lineSeparator()}" +
                        "A phase that was added since is listed with: ${UsageHelp.toType(commandSpec, "gradle ${ToolCatalogFile.TASK}")}")
            }
            if (similar != null) {
                throw new UsageHelp.Lines(commandSpec.commandLine(), "test-app has no option ${arg}.${meant}${System.lineSeparator()}" +
                        "If the project has a test phase of that name, the task ${taskOf(name)}, the switch is taken once the build has listed its phases " +
                        "(${UsageHelp.toType(commandSpec, "gradle ${ToolCatalogFile.TASK}")}), and the phase is run by its task with: ${UsageHelp.toType(commandSpec, "gradle ${taskOf(name)}")}")
            }
            phases.add(name)
            taken.add(name)
            args.remove(i)
        }
    }

    // the option of the command a switch is nearly: the one with the fewest letters changed, left out, added or swapped, of those within a number
    private static String similarTo(String typed, CommandLine.Model.CommandSpec commandSpec, int within) {
        String similar = null
        int fewest = within + 1
        for (CommandLine.Model.OptionSpec option : commandSpec.options()) {
            if (option.hidden()) {
                continue
            }
            for (String name : option.names()) {
                int changes = name.startsWith(LONG) ? changes(typed, name) : fewest
                if (changes < fewest) {
                    fewest = changes
                    similar = name
                }
            }
        }
        return similar
    }

    // how many single letters are changed, left out or added, or neighbours swapped, between two words
    private static int changes(String one, String other) {
        int[][] distance = new int[one.length() + 1][other.length() + 1]
        for (int i = 0; i <= one.length(); i++) {
            distance[i][0] = i
        }
        for (int j = 0; j <= other.length(); j++) {
            distance[0][j] = j
        }
        for (int i = 1; i <= one.length(); i++) {
            for (int j = 1; j <= other.length(); j++) {
                int cost = one.charAt(i - 1) == other.charAt(j - 1) ? 0 : 1
                distance[i][j] = Math.min(Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1), distance[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && one.charAt(i - 1) == other.charAt(j - 2) && one.charAt(i - 2) == other.charAt(j - 1)) {
                    distance[i][j] = Math.min(distance[i][j], distance[i - 2][j - 2] + 1)
                }
            }
        }
        return distance[one.length()][other.length()]
    }

    // the task a switch stands for where the project has no task of the name of the switch alone: end-to-end is endToEndTest
    private static String taskOf(String phase) {
        if (phase == UNIT) {
            return 'test'
        }
        StringBuilder task = new StringBuilder()
        boolean upper = false
        for (int i = 0; i < phase.length(); i++) {
            char each = phase.charAt(i)
            if (each == ('-' as char)) {
                upper = true
                continue
            }
            task.append(upper ? Character.toUpperCase(each) : each)
            upper = false
        }
        return task.append('Test').toString()
    }

    /** Takes the switches of test phases from the command line, after what follows {@code --} was taken from it. */
    static class PhaseSwitches extends GradleTaskCommand.PassedOn {

        @Override
        boolean preprocess(Stack<String> args, CommandLine.Model.CommandSpec commandSpec, CommandLine.Model.ArgSpec argSpec, Map<String, Object> info) {
            boolean done = super.preprocess(args, commandSpec, argSpec, info)
            ((TestAppCommand) commandSpec.userObject()).takePhases(args, commandSpec)
            return done
        }
    }

    // what the option of a phase the build listed does when it is given: the phase is one that was asked for
    private static class Selected implements CommandLine.Model.ISetter {

        private final List<String> phases
        private final String name

        Selected(List<String> phases, String name) {
            this.phases = phases
            this.name = name
        }

        @Override
        <T> T set(T value) {
            if (Boolean.TRUE == value && !phases.contains(name)) {
                phases.add(name)
            }
            return null
        }
    }
}
