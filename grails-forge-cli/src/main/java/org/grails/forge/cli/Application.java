/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.forge.cli;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.inject.BeanDefinition;
import org.grails.forge.cli.command.*;
import org.grails.forge.io.ConsoleOutput;
import picocli.CommandLine;

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
        "*  @|bold create-webplugin|@ @|yellow NAME|@"
},
        synopsisHeading = "@|bold,underline Usage:|@ ",
        optionListHeading = "%n@|bold,underline Options:|@%n",
        commandListHeading = "%n@|bold,underline Commands:|@%n",
        subcommands = {
                // Creation commands
                CreateAppCommand.class,
                CreateWebappCommand.class,
                CreatePluginCommand.class,
                CreateWebPluginCommand.class,
                CreateRestApiCommand.class
        })
@Prototype
@TypeHint({
        Application.class,
        GormImplCandidates.class,
        GormImplConverter.class,
        ServletImplCandidates.class,
        ServletImplConverter.class,
        CommonOptionsMixin.class,
        TestFrameworkCandidates.class,
        TestFrameworkConverter.class
})
public class Application extends BaseCommand implements Callable<Integer> {

    private static Boolean interactiveShell = false;

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
            //The first command line isn't technically in the shell yet so this is called
            //before setting the static flag
            CommandLine commandLine = createCommandLine();
            Application.interactiveShell = true;
            new InteractiveShell(commandLine, Application::execute, EXCEPTION_HANDLER).start();
        } else {
            System.exit(execute(args));
        }
    }

    static CommandLine createCommandLine() {
        boolean noOpConsole = Application.interactiveShell;
        try (BeanContext beanContext = ApplicationContext.builder().deduceEnvironment(false).start()) {
            return createCommandLine(beanContext, noOpConsole);
        }
    }

    static int execute(String[] args) {
        boolean noOpConsole = args.length > 0 && args[0].startsWith("update-cli-config");
        try (BeanContext beanContext = ApplicationContext.builder().deduceEnvironment(false).start()) {
            return createCommandLine(beanContext, noOpConsole).execute(args);
        }
    }

    private static CommandLine createCommandLine(BeanContext beanContext, boolean noOpConsole) {
        Application application = beanContext.getBean(Application.class);
        CommandLine commandLine = new CommandLine(application, new GrailsPicocliFactory(beanContext));
        commandLine.setExecutionExceptionHandler((ex, commandLine1, parseResult) -> EXCEPTION_HANDLER.apply(ex, commandLine1));
        commandLine.setUsageHelpWidth(100);

        CodeGenConfig codeGenConfig = CodeGenConfig.load(beanContext, noOpConsole ? ConsoleOutput.NOOP : application);
        if (codeGenConfig != null) {
            beanContext.getBeanDefinitions(CodeGenCommand.class).stream()
                    .map(BeanDefinition::getBeanType)
                    .map(bt -> beanContext.createBean(bt, codeGenConfig))
                    .filter(CodeGenCommand::applies)
                    .forEach(commandLine::addSubcommand);
        }

        return commandLine;
    }

    @Override
    public Integer call() throws Exception {
        throw new CommandLine.ParameterException(spec.commandLine(), "No command specified");
    }
}
