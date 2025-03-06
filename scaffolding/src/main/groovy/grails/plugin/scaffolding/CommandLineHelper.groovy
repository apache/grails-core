package grails.plugin.scaffolding

import grails.dev.commands.ExecutionContext
import org.grails.build.parsing.CommandLine

trait CommandLineHelper {

    static final boolean SUCCESS = true
    static final boolean FAILURE = false

    abstract ExecutionContext getExecutionContext()

    boolean isFlagPresent(String name) {
        final CommandLine commandLine = executionContext.commandLine
        if (commandLine.hasOption(name)) {
            return commandLine.optionValue(name) ? true : false
        } else {
            def value = commandLine?.undeclaredOptions?.get(name)
            return value ? true : false
        }
    }

}