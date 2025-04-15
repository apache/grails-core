/*
 * Copyright 2004-2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.doc.macros

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.radeox.api.engine.context.InitialRenderContext

import java.util.regex.Pattern
import org.radeox.macro.BaseMacro
import org.radeox.macro.CodeMacro
import org.radeox.macro.parameter.BaseMacroParameter
import org.radeox.macro.parameter.MacroParameter
import org.radeox.util.Encoder

class GspTagSourceMacro extends BaseMacro implements Serializable {

    @Internal
    protected String description = " ";
    @Internal
    protected String[] paramDescription = {};

    private static final long serialVersionUID = 0L;



    @Input
    List baseDirs

    GspTagSourceMacro(basedir) {
        if (!(basedir instanceof Collection || basedir.class.array)) basedir = [ basedir ]
        baseDirs = basedir.collect { f -> f as File }
    }

    @Input
    String getName() { "source" }

    void execute(Writer out, MacroParameter params) {
        def source = params.params.get("0")

        def i = source.indexOf('=')
        def type = source[0..i-1]
        def name = source[i+1..-1]

        switch (type) {
            case "tag":
                def j = name.indexOf('.')
                def className = name[0..j-1]
                def tagName = name[j+1..-1]

                // Recursively search for the tag library source file in the
                // configured base directory.
                def tagLibFile = null
                baseDirs.find { dir ->
                    dir.traverse(nameFilter: /${className}.groovy/) { tagLibFile = it }
                    return tagLibFile
                }

                def text = tagLibFile?.getText("UTF-8") ?: ""
                String closureSource = extractTagClosureSource(tagName, text)
                if (closureSource) {
                    out << '<p><a href="#' + tagName +
                            '" onclick="document.getElementById(\'' + tagName +
                            '\').style.display=\'inline\'">Show Source</a></p>'
                    out << "<div id=\"$tagName\" style=\"display:none;\">"
                    text = Encoder.escape(closureSource)

                    def macro = new CodeMacro()
                    macro.setInitialContext(initialContext)
                    def macroParams = new BaseMacroParameter()
                    macroParams.content = text
                    macro.execute(out, macroParams)
                    out << "</div>"
                }
                break
        }
    }

    /**
     * Extracts the Closure source code for a given tag name.
     *
     * @param tagName name of the tag method to extract the source code for
     * @param sourceText    the source code to search
     * @return  source code for the tag Closure or an empty string if not found
     */
    String extractTagClosureSource(String tagName, String sourceText) {
        if (!sourceText) return ''
        String source = ''
        Pattern regex = ~/(?s)(?:\s*\n)*(\s*?(?:Closure)\s+?${tagName}\s*?=\s*?\{.*?->.+?)(.*)/
        def matcher = regex.matcher(sourceText)
        if (matcher.find() && matcher.groupCount() == 2) {
            String signature = matcher.group(1)
            String remaining = matcher.group(2)
            source = signature + substringToClosingBrace(remaining)
        }
        return source
    }

    //
    // Returns a substring of the text up to and including the closing brace '}'.
    // Assumes that opening brace count is 1 and counts '{' and '}' until the
    // count is 0.  Be sure to NOT include the opening brace in the text.
    //
    // Given:
    //          def foo = bar.find { it == 2 }
    //      }
    //      private void doSomething() { doSomethingElse() }
    //
    // Returns:
    //          def foo = bar.find { it == 2 }
    //      }
    //
    private String substringToClosingBrace(String text) {
        StringBuilder sb = new StringBuilder()
        int braceCount = 1
        char[] chars = text.toCharArray()
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i]
            switch (c) {
                case '{':
                    ++braceCount
                    break
                case '}':
                    --braceCount
                    break
            }
            sb.append(c)
            if (braceCount == 0) {
                break
            }
        }
        return sb.toString()
    }

}
