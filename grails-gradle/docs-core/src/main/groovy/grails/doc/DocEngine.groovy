/* Copyright 2004-2005 the original author or authors.
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
package grails.doc

import grails.doc.filters.HeaderFilter
import grails.doc.filters.LinkTestFilter
import grails.doc.filters.ListFilter

import java.util.regex.Pattern

import org.radeox.api.engine.WikiRenderEngine
import org.radeox.api.engine.context.InitialRenderContext
import org.radeox.engine.BaseRenderEngine
import org.radeox.filter.context.FilterContext
import org.radeox.filter.regex.RegexFilter
import org.radeox.filter.regex.RegexTokenFilter
import org.radeox.macro.BaseMacro
import org.radeox.macro.CodeMacro
import org.radeox.macro.MacroLoader
import org.radeox.macro.parameter.BaseMacroParameter
import org.radeox.macro.parameter.MacroParameter
import org.radeox.regex.MatchResult
import org.radeox.filter.*
import org.radeox.util.Encoder

/**
 * A Radeox Wiki engine for generating documentation using a confluence style syntax.
 *
 * @author Graeme Rocher
 * @since 1.2
 */
class DocEngine extends BaseRenderEngine implements WikiRenderEngine {

    static final CONTEXT_PATH = "contextPath"
    static final SOURCE_FILE = "sourceFile"
    static final BASE_DIR = "base.dir"
    static final API_BASE_PATH = "apiBasePath"
    static final API_CONTEXT_PATH = "apiContextPath"
    static final RESOURCES_CONTEXT_PATH = "resourcesContextPath"

    static EXTERNAL_DOCS = [:]
    static ALIAS = [:]

    private basedir
    private macroFilter
    private macroLoader

    Properties engineProperties = new Properties()

    DocEngine(InitialRenderContext context) {
        super(context)
        basedir = context.get(BASE_DIR) ?: "."
    }

    boolean exists(String name) {
        int barIndex = name.indexOf('|')
        if (barIndex > -1) {
            def refItem = name[0..barIndex-1]
            def refCategory = name[barIndex + 1..-1]

            if (refCategory.startsWith("http://") || refCategory.startsWith("https://")) {
                return true
            }

            if (refCategory.startsWith("guide:")) {
                def alias = refCategory[6..-1]

                if (ALIAS[alias]) {
                    alias = ALIAS[alias]
                }
                def ref = "${basedir}/guide/${alias}.gdoc"
                def file = new File(ref)
                if (file.exists()) {
                    return true
                }

                emitWarning(name,ref,"page")
            }
            else if (refCategory.startsWith("api:")) {
                def ref = refCategory[4..-1]
                if (EXTERNAL_DOCS.keySet().find { ref.startsWith(it) }) {
                    return true
                }

                ref = ref.replace('.' as char, '/' as char)
                if (ref.indexOf('#') > -1) {
                    ref = ref[0..ref.indexOf("#")-1]
                }

                def apiBase = initialContext.get(API_BASE_PATH)
                if (apiBase) {
                    def apiDocExists = [ "api", "gapi" ].any { dir ->
                        def path = "${apiBase}/${dir}/${ref}.html"
                        new File(path).exists()
                    }
                    if (apiDocExists) return true
                }

                emitWarning(name,ref,"class")
            }
            else {
                String dir = getNaturalName(refCategory)
                def ref = "${basedir}/ref/${dir}/${refItem}.gdoc"
                File file = new File(ref)
                if (file.exists()) {
                    return true
                }

                emitWarning(name,ref,"page")
            }
        }

        return false
    }

    private void emitWarning(String name, String ref, String type) {
        println "WARNING: ${initialContext.get(SOURCE_FILE)}: Link '$name' refers to non-existent $type $ref!"
    }

    boolean showCreate() { false }

    void addMacro(macro) {
        macroLoader.add(macroFilter.macroRepository, macro)
    }

    protected void init() {
        engineProperties?.findAll { it.key?.startsWith("api.")}?.each {
            EXTERNAL_DOCS[it.key[4..-1]] = it.value
        }
        engineProperties?.findAll { it.key?.startsWith("alias.")}?.each {
            ALIAS[it.key[6..-1]] = it.value
        }

        if (null == fp) {
            fp = new FilterPipe(initialContext)

            def filters = [ParamFilter,
                           MacroFilter,
                           TextileLinkFilter,
                           HeaderFilter,
                           BlockQuoteFilter,
                           ListFilter,
                           LineFilter,
                           StrikeThroughFilter,
                           NewlineFilter,
                           ParagraphFilter,
                           BoldFilter,
                           CodeFilter,
                           ItalicFilter,
                           LinkTestFilter,
                           ImageFilter,
                           MarkFilter,
                           KeyFilter,
                           TypographyFilter,
                           EscapeFilter]

            for (f in filters) {
                RegexFilter filter = f.getDeclaredConstructor().newInstance()
                fp.addFilter(filter)

                if (filter instanceof MacroFilter) {
                    macroFilter = filter
                    macroLoader = new MacroLoader()

                    // Add the macros provided by Grails.
                    def repository = filter.macroRepository
                    macroLoader.add(repository, new WarningMacro())
                    macroLoader.add(repository, new NoteMacro())
                }
            }
            fp.init()
        }
    }

    void appendLink(StringBuffer buffer, String name, String view, String anchor) {
        def contextPath = initialContext.get(CONTEXT_PATH)

        if (name.startsWith("guide:")) {
            def alias = name[6..-1]
            if (ALIAS[alias]) {
                alias = ALIAS[alias]
            }

            // Deal with aliases that include a '/'-separated path.
            def i = alias.lastIndexOf('/')
            if (i >= 0) alias = alias[(i + 1)..-1]

            buffer << "<a href=\"$contextPath/guide/single.html#${alias.encodeAsUrlFragment()}\" class=\"guide\">$view</a>"
        }
        else if (name.startsWith("api:")) {
            def link = name[4..-1]

            def externalKey = EXTERNAL_DOCS.keySet().find { link.startsWith(it) }
            link = link.replace('.' as char, '/' as char) + ".html"

            if (externalKey) {
                buffer << "<a href=\"${EXTERNAL_DOCS[externalKey]}/$link${anchor ? '#' + anchor : ''}\" class=\"api\">$view</a>"
            }
            else {
                def apiBase = initialContext.get(API_BASE_PATH)
                contextPath = initialContext.get(API_CONTEXT_PATH)

                def apiDir = [ "api", "gapi" ].find { dir -> new File("${apiBase}/${dir}/${link}").exists() }
                buffer << "<a href=\"$contextPath/$apiDir/$link${anchor ? '#' + anchor : ''}\" class=\"api\">$view</a>"
            }
        }
        else {
            String dir = getNaturalName(name)
            def link = "$contextPath/ref/${dir}/${view}.html"
            buffer <<  "<a href=\"$link\" class=\"$name\">$view</a>"
        }
    }

    void appendLink(StringBuffer buffer, String name, String view) {
        appendLink(buffer,name,view,"")
    }

    void appendCreateLink(StringBuffer buffer, String name, String view) {
        buffer.append(name)
    }

    /**
     * Converts a property name into its natural language equivalent eg ('firstName' becomes 'First Name')
     * @param name The property name to convert
     * @return The converted property name
     */
    static final nameCache = [:]

    String getNaturalName(String name) {
        if (nameCache[name]) {
            return nameCache[name]
        }

        List words = []
        int i = 0
        char[] chars = name.toCharArray()
        for (int j = 0; j < chars.length; j++) {
            char c = chars[j]
            String w
            if (i >= words.size()) {
                w = ""
                words.add(i, w)
            }
            else {
                w = words.get(i)
            }

            if (Character.isLowerCase(c) || Character.isDigit(c)) {
                if (Character.isLowerCase(c) && w.length() == 0) {
                    c = Character.toUpperCase(c)
                }
                else if (w.length() > 1 && Character.isUpperCase(w.charAt(w.length() - 1))) {
                    w = ""
                    words.add(++i,w)
                }

                words.set(i, w + c)
            }
            else if (Character.isUpperCase(c)) {
                if ((i == 0 && w.length() == 0) || Character.isUpperCase(w.charAt(w.length() - 1))) {
                    words.set(i, w + c)
                }
                else {
                    words.add(++i, String.valueOf(c))
                }
            }
        }

        nameCache[name] = words.join(' ')
        return nameCache[name]
    }
}

class WarningMacro extends BaseMacro {
    String getName() {"warning"}
    void execute(Writer writer, MacroParameter params) {
        writer << '<blockquote class="warning">' << params.content << "</blockquote>"
    }
}

class NoteMacro extends BaseMacro {
    String getName() {"note"}
    void execute(Writer writer, MacroParameter params) {
        writer << '<blockquote class="note">' << params.content << "</blockquote>"
    }
}

class BlockQuoteFilter extends RegexTokenFilter {
    BlockQuoteFilter() {
        super(/(?m)^bc.\s*?(.*?)\n\n/);
    }
    void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
        buffer << "<pre class=\"bq\"><code>${result.group(1)}</code></pre>\n\n"
    }
}

class ItalicFilter extends RegexTokenFilter {
    ItalicFilter() {
        super(/\b_([^\n]*?)_\b/);
    }
    void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
        buffer << " <em class=\"italic\">${result.group(1)}</em> "
    }
}

class BoldFilter extends RegexTokenFilter {
    BoldFilter() {
        super(/\*([^\n]*?)\*/);
    }
    void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
        buffer << "<strong class=\"bold\">${result.group(1)}</strong>"
    }
}

class CodeFilter extends RegexTokenFilter {
    CodeFilter() {
        super(/@([^\n]*?)@/);
    }

    void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
        def text = result.group(1)
        // are we inside a code block?
        if (text.indexOf('class="code"') > -1) {
            buffer << "@$text@"
        }
        else {
            buffer << "<code>${text}</code>"
        }
    }
}

class ImageFilter extends RegexTokenFilter {
    ImageFilter() {
        super(/!([^\n<>=]*?\.(jpg|png|gif))!/);
    }

    void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
        def img = result.group(1)
        if (img.startsWith("http://") || img.startsWith("https://")) {
            buffer << "<img border=\"0\" class=\"center\" src=\"$img\"></img>"
        }
        else {
            def path = context.renderContext.get(DocEngine.RESOURCES_CONTEXT_PATH) ?: "."
            buffer << "<img border=\"0\" class=\"center\" src=\"$path/img/$img\"></img>"
        }
    }
}

class TextileLinkFilter extends RegexTokenFilter {
    TextileLinkFilter() {
        super(/"([^"]+?)":(\S+?)(\s)/);
    }

    void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
        def text = result.group(1)
        def link = result.group(2)
        def space = result.group(3)

        if (link.startsWith("http://") || link.startsWith("https://")) {
            buffer << "<a href=\"$link\" target=\"blank\">$text</a>$space"
        }
        else {
            buffer << "<a href=\"$link\">$text</a>$space"
        }
    }
}
