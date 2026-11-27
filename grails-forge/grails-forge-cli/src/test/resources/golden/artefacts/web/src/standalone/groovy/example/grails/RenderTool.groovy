package example.grails

import org.apache.grails.core.cli.StandaloneTool
import org.apache.grails.core.cli.ToolHints
import org.apache.grails.core.cli.ToolParameter
import org.apache.grails.core.cli.ToolSupport

@ToolHints(readOnlyHint = false, destructiveHint = true)   // say what the tool does: readOnlyHint = true when it changes nothing, destructiveHint = false when it only adds
class RenderTool implements StandaloneTool, ToolSupport {

    String description = 'Render'           // one line that says what the tool does

    @ToolParameter(index = 0, label = 'NAME', description = 'What the tool is run for', required = false)
    String target

    boolean handle() {
        // The declared options and parameters are set before handle() runs. The tool runs without the application
        // and without its classes, on the files of the project (executionContext.baseDir). Return true when the
        // tool has done its work, and false to end it with exit code 1.
        println "Ran ${name} for ${target ?: 'nothing'}"
        return true
    }
}
