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
package grails.doc.filters

import org.radeox.filter.regex.RegexTokenFilter
import org.radeox.regex.MatchResult
import org.radeox.filter.context.FilterContext
import org.radeox.api.engine.WikiRenderEngine
import org.radeox.util.StringBufferWriter
import org.radeox.filter.interwiki.InterWiki
import org.radeox.util.Encoder

/**
 * @author Graeme Rocher
 * @since 1.1
 */
class LinkTestFilter extends RegexTokenFilter {

    LinkTestFilter() {
        super(/\[(.*?)\]/)
    }

    /**
     * Returns the view of the wiki name that is shown to the
     * user. Overwrite to support other views for example
     * transform "WikiLinking" to "Wiki Linking".
     * Does nothing by default.
     *
     * @return view The view of the wiki name
     */
    protected String getWikiView(String name) {
        return name
    }

    void handleMatch(StringBuffer buffer, MatchResult result, FilterContext context) {
        def engine = context.getRenderContext().getRenderEngine()

        if (!(engine instanceof WikiRenderEngine)) {
            return
        }

        Writer writer = new StringBufferWriter(buffer)

        String name = result.group(1)
        String original = name

        if (name == null) {
           buffer.append(Encoder.escape(result.group(0)))
           return
        }

        // trim the name and unescape it
        name = Encoder.unescape(name.trim())

        // Is there an alias like [alias|link] ?
        int pipeIndex = name.indexOf('|')
        String alias = ""
        if (-1 != pipeIndex) {
            alias = name.substring(0, pipeIndex)
            name = name.substring(pipeIndex + 1)
        }

        int hashIndex = name.lastIndexOf('#')

        String hash = ""
        if (-1 != hashIndex && hashIndex != name.length() - 1) {
            hash = name.substring(hashIndex + 1)
            name = name.substring(0, hashIndex)
        }

        if (name.indexOf("http://")>-1 || name.indexOf("https://")>-1) {
            buffer << "<a href=\"${name}${hash ? '#'+hash:''}\" target=\"blank\">${Encoder.escape(alias)}</a>"
            return
        }

        // internal link
        if (engine.exists(original)) {
            String view = getWikiView(name)
            if (-1 != pipeIndex) {
                view = alias
            }
            // Do not add hash if an alias was given
            if (-1 != hashIndex) {
                engine.appendLink(buffer, name, view, hash)
            }
            else {
                engine.appendLink(buffer, name, view)
            }
        }
        else if (engine.showCreate()) {
            engine.appendCreateLink(buffer, name, getWikiView(name))
            // links with "create" are not cacheable because
            // a missing wiki could be created
            context.getRenderContext().setCacheable(false)
        }
        else {
            // cannot display/create wiki, so just display the text
            buffer.append(name)
        }
    }
}
