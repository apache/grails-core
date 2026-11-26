package other.pkg

import org.apache.grails.core.cli.ApplicationTool
import org.apache.grails.core.cli.ToolSupport

class BazTool implements ApplicationTool, ToolSupport {

    boolean handle() {
        // args holds what was given after the name of the tool, and applicationContext the running
        // application. Return true when the tool has done its work, and false to end it with exit code 1.
        println "Ran ${name} with the arguments ${args}"
        return true
    }
}
