package example.grails

import org.apache.grails.core.cli.StandaloneTool
import org.apache.grails.core.cli.ToolSupport

class RenderTool implements StandaloneTool, ToolSupport {

    boolean handle() {
        // args holds what was given after the name of the tool. The tool runs without the application and
        // without its classes, on the files of the project (executionContext.baseDir). Return true when the
        // tool has done its work, and false to end it with exit code 1.
        println "Ran ${name} with the arguments ${args}"
        return true
    }
}
