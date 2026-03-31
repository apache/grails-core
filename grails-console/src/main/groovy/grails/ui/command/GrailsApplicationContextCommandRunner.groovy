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
import grails.dev.commands.ApplicationContextCommandRegistry
import grails.dev.commands.ExecutionContext
import grails.ui.support.DevelopmentGrailsApplication
import org.grails.build.parsing.CommandLine
import org.grails.build.parsing.CommandLineParser

/**
 * @author Graeme Rocher
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
        def command = ApplicationContextCommandRegistry.instance.findCommand(commandName)
        if (command) {

            Object skipBootstrap = command.hasProperty('skipBootstrap')?.getProperty(command)
            if (skipBootstrap instanceof Boolean && !System.getProperty(Settings.SETTING_SKIP_BOOTSTRAP)) {
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
                ctx.autowireCapableBeanFactory.autowireBeanProperties(command, AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE, false)
                command.applicationContext = ctx
                def result = command.handle(new ExecutionContext(commandLine))
                result ? System.exit(0) : System.exit(1)
            } catch (Throwable e) {
                System.err.println("Command execution error: $e.message")
                System.exit(1)
            }
            finally {
                try {
                    ctx?.close()
                } catch (Throwable e) {
                    // ignore
                }
            }
        }
        else {
            System.err.println("Command not found for name: $commandName")
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
     * Main method to run an existing Application class
     *
     * @param args The first argument is the Command name, the last argument is the Application class name
     */
    static void main(String[] args) {
        if (args.size() > 1) {
            Class applicationClass = null
            String className = args.last()
            try {
                applicationClass = Thread.currentThread().contextClassLoader.loadClass(className)
            } catch (Throwable e) {
                System.err.println("runCommand: Application class ${className} not found")
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
