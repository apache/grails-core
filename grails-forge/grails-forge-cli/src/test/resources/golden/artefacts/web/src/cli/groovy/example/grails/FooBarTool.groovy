package example.grails

import org.apache.grails.core.cli.GrailsApplicationCommand

class FooBarCommand implements GrailsApplicationCommand {

    boolean handle() {
        // args holds what was given after the name of the command. Return true when the command has
        // done its work, and false to end it with exit code 1.
        println "Ran ${name} with the arguments ${args}"
        return true
    }
}
