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
package org.grails.forge.cli;

import org.grails.forge.ForgeContexts;
import org.grails.forge.cli.command.BaseCommand;
import org.grails.forge.cli.command.CodeGenCommand;
import org.grails.forge.cli.command.CreateAppCommand;
import org.grails.forge.cli.command.CreatePluginCommand;
import org.grails.forge.cli.command.CreateRestApiCommand;
import org.grails.forge.cli.command.CreateWebPluginCommand;
import org.grails.forge.cli.command.CreateWebappCommand;
import org.grails.forge.io.ConsoleOutput;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

@CommandLine.Command(name = "grails-forge-cli", description = {
        "Grails Forge CLI command line interface for generating projects and services.",
        "Application generation commands are:",
        "",
        "*  @|bold create-app|@ @|yellow NAME|@",
        "*  @|bold create-webapp|@ @|yellow NAME|@",
        "*  @|bold create-restapi|@ @|yellow NAME|@",
        "*  @|bold create-plugin|@ @|yellow NAME|@",
        "*  @|bold create-web-plugin|@ @|yellow NAME|@"
},
        synopsisHeading = "@|bold,underline Usage:|@ ",
        optionListHeading = "%n@|bold,underline Options:|@%n",
        commandListHeading = "%n@|bold,underline Commands:|@%n",
        subcommands = {
                CreateAppCommand.class,
                CreateWebappCommand.class,
                CreatePluginCommand.class,
                CreateWebPluginCommand.class,
                CreateRestApiCommand.class
        })
@Component
@Scope("prototype")
public class Application extends BaseCommand implements Callable<Integer> {

    private static Boolean interactiveShell = false;

    private static final List<Class<? extends CodeGenCommand>> CODE_GEN_COMMANDS = List.of(
            org.grails.forge.cli.command.CreateControllerCommand.class,
            org.grails.forge.cli.command.CreateServiceCommand.class,
            org.grails.forge.cli.command.CreateDomainClassCommand.class,
            org.grails.forge.cli.command.CreateTagLibCommand.class,
            org.grails.forge.cli.command.CreateInterceptorCommand.class,
            org.grails.forge.cli.command.CreateJobCommand.class,
            org.grails.forge.cli.command.AddPropertyCommand.class
    );

    private static final BiFunction<Throwable, CommandLine, Integer> EXCEPTION_HANDLER = (e, commandLine) -> {
        BaseCommand command = commandLine.getCommand();
        command.err(e.getMessage());
        if (command.showStacktrace()) {
            e.printStackTrace(commandLine.getErr());
        }
        return 1;
    };

    public static void main(String[] args) {
        if (args.length == 0) {
            CommandLine commandLine = createCommandLine();
            Application.interactiveShell = true;
            new InteractiveShell(commandLine, Application::execute, EXCEPTION_HANDLER).start();
        } else {
            System.exit(execute(args));
        }
    }

    static CommandLine createCommandLine() {
        boolean noOpConsole = Application.interactiveShell;
        try (AnnotationConfigApplicationContext beanContext = ForgeContexts.create()) {
            return createCommandLine(beanContext, noOpConsole);
        }
    }

    static int execute(String[] args) {
        boolean noOpConsole = args.length > 0 && args[0].startsWith("update-cli-config");
        try (AnnotationConfigApplicationContext beanContext = ForgeContexts.create()) {
            return createCommandLine(beanContext, noOpConsole).execute(args);
        }
    }

    private static CommandLine createCommandLine(AnnotationConfigApplicationContext beanContext, boolean noOpConsole) {
        Application application = beanContext.getBean(Application.class);
        CommandLine commandLine = new CommandLine(application, new GrailsPicocliFactory(beanContext));
        commandLine.setExecutionExceptionHandler((ex, commandLine1, parseResult) -> EXCEPTION_HANDLER.apply(ex, commandLine1));
        commandLine.setUsageHelpWidth(100);

        CodeGenConfig codeGenConfig = CodeGenConfig.load(beanContext, noOpConsole ? ConsoleOutput.NOOP : application);
        if (codeGenConfig != null) {
            for (Class<? extends CodeGenCommand> type : CODE_GEN_COMMANDS) {
                try {
                    CodeGenCommand command = type.getConstructor(CodeGenConfig.class).newInstance(codeGenConfig);
                    if (command.applies()) {
                        commandLine.addSubcommand(command);
                    }
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Unable to create command " + type.getName(), e);
                }
            }
        }

        return commandLine;
    }

    @Override
    public Integer call() throws Exception {
        throw new CommandLine.ParameterException(spec.commandLine(), "No command specified");
    }
}
