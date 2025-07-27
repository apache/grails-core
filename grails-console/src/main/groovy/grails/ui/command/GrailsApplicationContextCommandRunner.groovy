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
        if(command) {

            Object skipBootstrap = command.hasProperty("skipBootstrap")?.getProperty(command)
            if (skipBootstrap instanceof Boolean && !System.getProperty(Settings.SETTING_SKIP_BOOTSTRAP)) {
                System.setProperty(Settings.SETTING_SKIP_BOOTSTRAP, skipBootstrap.toString())
            }

            ConfigurableApplicationContext ctx = null
            try {
                ctx = super.run(args)
            } catch (Throwable e) {
                System.err.println("Context failed to load: $e.message")
                System.exit(1)
            }

            try {
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
     * Main method to run an existing Application class
     *
     * @param args The first argument is the Command name, the last argument is the Application class name
     */
    static void main(String[] args) {
        if(args.size() > 1) {
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
            System.err.println("Missing application class name and script name arguments")
            System.exit(1)
        }
    }
}
