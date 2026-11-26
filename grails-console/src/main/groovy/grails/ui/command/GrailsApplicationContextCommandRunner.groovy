/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package grails.ui.command

import groovy.transform.CompileStatic

import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ConfigurableApplicationContext

import grails.config.Settings
import org.apache.grails.core.cli.ApplicationContextCommandRegistry
import org.apache.grails.core.cli.ApplicationTool
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.GrailsTool
import org.apache.grails.core.cli.StandaloneTool
import org.apache.grails.core.cli.ApplicationCommandTargetAware
import grails.ui.support.DevelopmentGrailsApplication
import org.apache.grails.gradle.model.cli.CommandArgumentsFile
import org.grails.build.parsing.CommandLine
import org.grails.build.parsing.CommandLineParser

/**
 * Runs a tool of the application or of one of its plugins, as the {@code runTool} task of a build
 * (and {@code runCommand}, its deprecated other name) starts it. The application is started around
 * an {@link ApplicationTool}, which is given its application context, unless the tool says it does
 * not start the application ({@code startsApplication = false}). A tool that does not start it, and
 * a {@link StandaloneTool}, are run without the application: nothing of it is started, nothing is
 * injected into the tool, and no {@code skipBootstrap} of the tool is read. A {@link StandaloneTool}
 * needs no build either, and the command line starts it without one; a build that is asked for it
 * runs it here. What the tool says is read from the instance the registry makes; for an adapter
 * ({@link ApplicationCommandTargetAware}), from the tool it adapts.
 *
 * @since 3.0
 */
@CompileStatic
class GrailsApplicationContextCommandRunner extends DevelopmentGrailsApplication {

    String commandName

    protected GrailsApplicationContextCommandRunner(String commandName, Class<?>... sources) {
        super(sources)
        this.commandName = commandName
    }

    @Override
    ConfigurableApplicationContext run(String... args) {
        ApplicationContextCommandRegistry commandRegistry = ApplicationContextCommandRegistry.instance
        GrailsTool command = commandRegistry.findCommand(commandName)
        if (command) {
            Object autowireTarget = resolveAutowireTarget(command)

            // a tool that says it needs no application is run as it is: nothing is started for it
            if (!startsApplication(command)) {
                try {
                    def result = command.handle(new ExecutionContext(new CommandLineParser().parse(args)))
                    result ? System.exit(0) : System.exit(1)
                } catch (Throwable e) {
                    System.err.println("Command execution error: $e.message")
                    System.exit(1)
                }
                return null
            }

            Boolean skipBootstrap = resolveSkipBootstrap(command)
            if (skipBootstrap != null && !System.getProperty(Settings.SETTING_SKIP_BOOTSTRAP)) {
                System.setProperty(Settings.SETTING_SKIP_BOOTSTRAP, skipBootstrap.toString())
            }

            // Filter out command-specific options (--key=value, --flag) before passing to
            // Spring Boot. These args are intended for the Grails command (parsed by
            // CommandLineParser below), NOT for Spring Boot's property override mechanism.
            //
            // Without this filtering, Spring Boot's SpringApplication.run() interprets
            // --dataSource=analytics as a property override, setting dataSource="analytics"
            // (a String) which corrupts GORM's datasource configuration (expects a Map).
            // This breaks Gradle dbm* tasks with --dataSource parameter:
            //   ./gradlew dbmStatus -Pargs="--dataSource=analytics"
            String[] springBootArgs = filterCommandOptions(args)

            ConfigurableApplicationContext ctx = null
            try {
                ctx = super.run(springBootArgs)
            } catch (Throwable e) {
                System.err.println("Context failed to load: $e.message")
                System.exit(1)
            }

            try {
                // Parse the FULL args (including --options) for the command
                CommandLine commandLine = new CommandLineParser().parse(args)
                ctx.autowireCapableBeanFactory.autowireBeanProperties(autowireTarget, AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE, false)
                ((ApplicationTool) command).applicationContext = ctx
                def result = command.handle(new ExecutionContext(commandLine))
                result ? System.exit(0) : System.exit(1)
            } catch (Throwable e) {
                System.err.println("Command execution error: $e.message")
                System.exit(1)
            }
            finally {
                try {
                    ctx?.close()
                } catch (Throwable ignored) {
                }
            }
        }
        else {
            System.err.println(unknownCommandMessage(commandName, commandRegistry.missingCommandHint))
            System.exit(1)
        }
        return null
    }

    /**
     * Filters out command-specific options (arguments starting with '--') from the
     * args array before they are passed to Spring Boot's {@code SpringApplication.run()}.
     *
     * <p>Spring Boot interprets {@code --key=value} arguments as property overrides via
     * {@code CommandLinePropertySource}. When Grails command options like
     * {@code --dataSource=analytics} are passed through, Spring Boot sets
     * {@code dataSource=analytics} as a top-level property, corrupting GORM's datasource
     * configuration which expects {@code dataSource} to be a Map containing url, username, etc.</p>
     *
     * <p>Command options are still available to the Grails command via
     * {@code CommandLineParser.parse(args)} which receives the unfiltered args.</p>
     *
     * @param args the full argument array including command options
     * @return a filtered array with command options removed, safe for Spring Boot
     */
    static String[] filterCommandOptions(String[] args) {
        args.findAll { it != null && !it.startsWith('--') } as String[]
    }

    /**
     * Whether the application is started around a tool: it is for an {@link ApplicationTool} that does not
     * say otherwise, and is not for a {@link StandaloneTool}, for an {@link ApplicationTool} whose
     * {@code startsApplication} is {@code false}, or for a tool that is neither. Of an adapter, the tool it
     * adapts says, when that one is a {@link GrailsTool}.
     *
     * @param command the tool the registry found
     * @return whether the application is started around it
     */
    static boolean startsApplication(GrailsTool command) {
        Object target = resolveAutowireTarget(command)
        Object tool = target instanceof GrailsTool ? target : command
        tool instanceof ApplicationTool && !(tool instanceof StandaloneTool) && ((ApplicationTool) tool).startsApplication
    }

    static String unknownCommandMessage(String commandName, String missingCommandHint) {
        String message = "Command not found for name: $commandName"
        missingCommandHint ? "${message}\n${missingCommandHint}" : message
    }

    /**
     * Resolves the object that Spring should autowire and inspect for the {@code skipBootstrap}
     * property. For a command a provider of the deprecated registry SPI contributed as an adapter the
     * registry returns an {@link ApplicationCommandTargetAware} adapter; the target it wraps - not
     * the adapter - is what must be autowired and queried, because the adapter forwards
     * {@code applicationContext} and {@code handle} to that same instance. Any other command is
     * returned unchanged.
     *
     * @param command the command resolved from the registry
     * @return the adapter target when {@code command} is {@link ApplicationCommandTargetAware}, otherwise {@code command}
     */
    static Object resolveAutowireTarget(Object command) {
        command instanceof ApplicationCommandTargetAware ?
            ((ApplicationCommandTargetAware) command).target : command
    }

    /**
     * Reads the optional {@code skipBootstrap} flag declared by a command. The flag is looked up on
     * the autowire target rather than on {@code command} itself, so a command reached through an
     * adapter keeps its flag: the adapter forwards {@code handle} but not the arbitrary properties a
     * command declares.
     *
     * @param command the command resolved from the registry
     * @return the declared flag, or {@code null} when the target declares no {@code skipBootstrap} property or declares one that is not a {@link Boolean}
     */
    static Boolean resolveSkipBootstrap(Object command) {
        Object autowireTarget = resolveAutowireTarget(command)
        Object skipBootstrap = autowireTarget.hasProperty('skipBootstrap')?.getProperty(autowireTarget)
        skipBootstrap instanceof Boolean ? (Boolean) skipBootstrap : null
    }

    /**
     * Main method to run an existing Application class
     *
     * @param args The first argument is the Command name, the last argument is the Application class name;
     * or, after the Application class name, one more argument that names a file with arguments of the command
     * (see {@link CommandArgumentsFile}), which then come before the Application class name
     */
    static void main(String[] args) {
        try {
            // a build gives the arguments of the command in a file when they are not for a command line
            args = CommandArgumentsFile.expand(args)
        } catch (CommandArgumentsFile.Refused refused) {
            System.err.println("runTool: The file of arguments ${refused.message}")
            System.exit(1)
        }
        if (args.size() > 1) {
            Class applicationClass = null
            String className = args.last()
            try {
                applicationClass = Thread.currentThread().contextClassLoader.loadClass(className)
            } catch (Throwable e) {
                System.err.println("runTool: Application class ${className} not found")
                System.exit(1)
            }

            def runner = new GrailsApplicationContextCommandRunner(args[0], applicationClass)
            runner.run(args.init() as String[])
        }
        else {
            System.err.println('Missing application class name and script name arguments')
            System.exit(1)
        }
    }
}
