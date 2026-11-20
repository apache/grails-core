package example.grails

import org.apache.grails.core.cli.GrailsApplicationCommand

class FooBarCommand implements GrailsApplicationCommand {

    boolean handle() {
        return false
    }
}
