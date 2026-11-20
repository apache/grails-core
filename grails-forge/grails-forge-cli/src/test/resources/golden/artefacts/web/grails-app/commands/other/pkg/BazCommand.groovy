package other.pkg

import org.apache.grails.core.cli.GrailsApplicationCommand

class BazCommand implements GrailsApplicationCommand {

    boolean handle() {
        return false
    }
}
