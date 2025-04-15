package grails.doc.macros

import org.gradle.api.tasks.Input
import org.radeox.macro.BaseMacro
import org.radeox.macro.parameter.MacroParameter

class HiddenMacro extends BaseMacro implements Serializable {

    private static final long serialVersionUID = 0L;

    @Input
    String getName() { "hidden" }

    void execute(Writer out, MacroParameter params) {
        out << '<div class="hidden-block">' << params.content << '</div>'
    }
}
