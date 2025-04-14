package grails.doc.macros

import org.radeox.macro.BaseMacro
import org.radeox.macro.parameter.MacroParameter

class HiddenMacro extends BaseMacro {
    String getName() { "hidden" }

    void execute(Writer out, MacroParameter params) {
        out << '<div class="hidden-block">' << params.content << '</div>'
    }
}
