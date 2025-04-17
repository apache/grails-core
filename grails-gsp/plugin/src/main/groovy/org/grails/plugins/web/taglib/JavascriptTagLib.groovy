/*
 * Copyright 2004-2024 the original author or authors.
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
package org.grails.plugins.web.taglib

import grails.artefact.TagLibrary
import grails.gsp.TagLib
import grails.plugins.GrailsPluginManager
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

import jakarta.annotation.PostConstruct

/**
 * Javascript tags.
 *
 * @author Graeme Rocher
 */
@TagLib
class JavascriptTagLib implements ApplicationContextAware, TagLibrary {
    ApplicationContext applicationContext

    GrailsPluginManager pluginManager

    boolean hasResourceProcessor = false

    static encodeAsForTags = [escapeJavascript: 'JavaScript', 
                              javascript: [expressionCodec:"JavaScript", scriptletCodec:"JavaScript", replaceOnly:true]]

    @PostConstruct
    private void initHasResourceProcessor() {
        hasResourceProcessor = applicationContext.containsBean('grailsResourceProcessor')
    }

    /**
     * Includes a javascript src file, library or inline script
     * if the tag has no 'src' or 'library' attributes its assumed to be an inline script:<br/>
     *
     * &lt;g:javascript&gt;alert('hello')&lt;/g:javascript&gt;<br/>
     *
     * The 'library' attribute will attempt to use the library mappings defined above to import the
     * right js files and not duplicate imports eg.<br/>
     *
     * &lt;g:javascript library="scriptaculous" /&gt; // imports all the necessary js for the scriptaculous library<br/>
     *
     * The 'src' attribute will merely import the js file but within the right context (ie inside the /js/ directory of
     * the Grails application:<br/>
     *
     * &lt;g:javascript src="myscript.js" /&gt; // actually imports '/app/js/myscript.js'
     *
     * @attr src The name of the javascript file to import. Will look in web-app/js dir
     * @attr library The name of the library to include. e.g. "jquery", "prototype", "scriptaculous", "yahoo" or "dojo"
     * @attr plugin The plugin to look for the javascript in
     * @attr contextPath the context path to use (relative to the application context path). Defaults to "" or path to the plugin for a plugin view or template.
     * @attr base specifies the full base url to prepend to the library name
     */
    Closure javascript = { attrs, body ->
        if (attrs.src) {
            javascriptInclude(attrs)
        } else {
            if (hasResourceProcessor) {
                out << r.script(Collections.EMPTY_MAP, body)
            } else {
                out.println '<script type="text/javascript">'
                out << body()
                out.println()
                out.println '</script>'
            }
        }
    }

    private javascriptInclude(attrs) {
        def requestPluginContext
        if (attrs.plugin) {
            requestPluginContext = pluginManager.getPluginPath(attrs.remove('plugin')) ?: ''
        }
        else {
            if (attrs.contextPath != null) {
                requestPluginContext = attrs.remove('contextPath').toString()
            }
            else {
                requestPluginContext = pageScope.pluginContextPath ?: ''
            }
        }

        if (attrs.base) {
            attrs.uri = attrs.remove('base') + attrs.remove('src')
        } else {
            def appBase = request.contextPath
            if (!appBase.endsWith('/')) {
                appBase += '/'
            }
            def reqResCtx = ''
            if (requestPluginContext) {
                reqResCtx = (requestPluginContext.startsWith("/") ? requestPluginContext.substring(1) : requestPluginContext) + '/'
            }
            attrs.uri = appBase + reqResCtx + 'js/'+attrs.remove('src')
        }
        out << g.external(attrs)
    }

    /**
     * Escapes a javascript string replacing single/double quotes and new lines.<br/>
     *
     * &lt;g:escapeJavascript&gt;This is some "text" to be escaped&lt;/g:escapeJavascript&gt;
     */
    Closure escapeJavascript = { attrs, body ->
        if (body) {
            out << body()
        }
        else if (attrs.value) {
            out << attrs.value
        }
    }
}
