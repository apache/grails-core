/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.plugins.web.taglib

import com.opensymphony.module.sitemesh.Decorator
import com.opensymphony.module.sitemesh.DecoratorMapper
import com.opensymphony.module.sitemesh.HTMLPage
import com.opensymphony.module.sitemesh.Page
import com.opensymphony.module.sitemesh.PageParser
import com.opensymphony.module.sitemesh.RequestConstants
import com.opensymphony.module.sitemesh.Factory

import grails.artefact.TagLibrary
import grails.core.GrailsApplication
import grails.core.support.GrailsApplicationAware
import grails.gsp.TagLib
import grails.util.TypeConvertingMap
import groovy.text.Template
import groovy.transform.CompileStatic
import org.apache.grails.web.layout.FactoryHolder
import org.apache.grails.web.layout.GSPGrailsLayoutPage
import org.apache.grails.web.layout.GrailsHTMLPageParser
import org.apache.grails.web.layout.EmbeddedGrailsLayoutView
import org.apache.grails.web.layout.GroovyPageLayoutFinder
import org.apache.grails.web.layout.SpringMVCViewDecorator
import org.grails.buffer.FastStringWriter
import org.grails.buffer.StreamCharBuffer
import org.grails.gsp.GroovyPageTemplate
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.gsp.compiler.GroovyPageParser
import org.grails.taglib.TagLibraryLookup
import org.grails.taglib.TagOutput
import org.grails.taglib.encoder.OutputContextLookupHelper
import jakarta.servlet.http.HttpServletRequest

import java.nio.charset.StandardCharsets

/**
 * Tags to help rendering of views and layouts.
 *
 * @author Graeme Rocher
 */
@CompileStatic
@TagLib
class RenderGrailsLayoutTagLib implements RequestConstants, TagLibrary, GrailsApplicationAware {
    GroovyPagesTemplateEngine groovyPagesTemplateEngine
    TagLibraryLookup gspTagLibraryLookup
    GroovyPageLayoutFinder groovyPageLayoutFinder
    protected boolean grailsLayoutPreprocessMode = true

    protected HTMLPage getPage() {
        (HTMLPage) getRequest().getAttribute(PAGE)
    }

    @Override
    void setGrailsApplication(GrailsApplication grailsApplication) {
        grailsLayoutPreprocessMode = grailsApplication.config.getProperty(GroovyPageParser.CONFIG_PROPERTY_GSP_GRAILS_LAYOUT_PREPROCESS, Boolean, true)
    }

    protected boolean isGrailsLayoutPreprocessMode() {
        grailsLayoutPreprocessMode
    }


    /**
     * Apply a layout to a particular block of text or to the given view or template.<br/>
     *
     * &lt;g:applyLayout name="myLayout"&gt;some text&lt;/g:applyLayout&gt;<br/>
     * &lt;g:applyLayout name="myLayout" template="mytemplate" /&gt;<br/>
     * &lt;g:applyLayout name="myLayout" url="http://www.google.com" /&gt;<br/>
     * &lt;g:applyLayout name="myLayout" action="myAction" controller="myController"&gt;<br/>
     *
     * @attr name The name of the layout
     * @attr template Optional. The template to apply the layout to
     * @attr url Optional. The URL to retrieve the content from and apply a layout to
     * @attr action Optional. The action to be called to generate the content to apply the layout to
     * @attr controller Optional. The controller that contains the action that will generate the content to apply the layout to
     * @attr contentType Optional. The content type to use, default is 'text/html'
     * @attr encoding Optional. The encoding to use
     * @attr params Optional. The params to pass onto the page object
     * @attr parse Optional. If true, Grails Layout parser will always be used to parse the content.
     */
    Closure applyLayout = { Map attrs, body ->
        if (!groovyPagesTemplateEngine) throw new IllegalStateException('Property [groovyPagesTemplateEngine] must be set!')
        def oldPage = getPage()
        String contentType = attrs.contentType ? attrs.contentType as String : 'text/html'

        Map pageParams = attrs.params instanceof Map ? (Map) attrs.params : [:]
        Map viewModel = attrs.model instanceof Map ? (Map) attrs.model : [:]
        Object content = null
        GSPGrailsLayoutPage gspGrailsLayoutPage = null
        if (attrs.url) {
            content = new URL(attrs.url as String).getText(StandardCharsets.UTF_8.name())
        } else if (attrs.action && attrs.controller) {
            def includeAttrs = [action: attrs.action, controller: attrs.controller, params: pageParams, model: viewModel]
            content = TagOutput.captureTagOutput(gspTagLibraryLookup, 'g', 'include', includeAttrs, null, OutputContextLookupHelper.lookupOutputContext())
        } else {
            def oldGspGrailsLayoutPage = request.getAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE)
            try {
                gspGrailsLayoutPage = new GSPGrailsLayoutPage()
                request.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, gspGrailsLayoutPage)
                if (attrs.view || attrs.template) {
                    content = TagOutput.captureTagOutput(gspTagLibraryLookup, 'g', 'render', attrs, null, OutputContextLookupHelper.lookupOutputContext())
                } else {
                    def bodyClosure = TagOutput.createOutputCapturingClosure(this, body, OutputContextLookupHelper.lookupOutputContext())
                    content = bodyClosure()
                }
                if (content instanceof StreamCharBuffer) {
                    gspGrailsLayoutPage.pageBuffer = (StreamCharBuffer) content
                    gspGrailsLayoutPage.used = isGrailsLayoutPreprocessMode()
                } else if (content != null) {
                    def stringWriter = new FastStringWriter()
                    stringWriter.print((Object) content)
                    gspGrailsLayoutPage.pageBuffer = stringWriter.buffer
                    gspGrailsLayoutPage.used = isGrailsLayoutPreprocessMode()
                }
            }
            finally {
                request.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, oldGspGrailsLayoutPage)
            }
        }
        if (content == null) {
            content = ''
        }

        Page page = null
        if (!((TypeConvertingMap) attrs).boolean('parse') && gspGrailsLayoutPage != null && gspGrailsLayoutPage.used) {
            page = gspGrailsLayoutPage
        } else {
            def parser = createPageParser(contentType)
            char[] charArray
            if (content instanceof StreamCharBuffer) {
                charArray = ((StreamCharBuffer) content).toCharArray()
            } else {
                charArray = content.toString().toCharArray()
            }
            page = parser.parse(charArray)
        }

        def decorator = findDecorator(request, attrs.name as String)
        if (decorator && decorator.page) {
            pageParams.each { k, v ->
                page.addProperty(k as String, v as String)
            }
            try {
                request.setAttribute(PAGE, page)
                def template = findTemplate(decorator)
                template.make(viewModel).writeTo(out)
            }
            finally {
                request.setAttribute(PAGE, oldPage)
            }
        } else {
            out << content
        }
    }

    protected Template findTemplate(Decorator decorator) {
        Template template
        if (decorator instanceof SpringMVCViewDecorator) {
            template = ((SpringMVCViewDecorator) decorator).template
            if (template instanceof GroovyPageTemplate) {
                GroovyPageTemplate gpt = (GroovyPageTemplate) template
                gpt = (GroovyPageTemplate) gpt.clone()
                gpt.allowSettingContentType = false
                template = gpt
            }
        } else {
            template = groovyPagesTemplateEngine.createTemplate(decorator.page)
        }
        return template
    }

    protected Decorator findDecorator(HttpServletRequest req, String layoutName) {
        def decoratorMapper = sitemeshFactory?.decoratorMapper
        Decorator d
        if (decoratorMapper) {
            d = decoratorMapper.getNamedDecorator(req, layoutName)
        } else {
            d = groovyPageLayoutFinder.getNamedDecorator(req, layoutName)
        }
        return d
    }

    protected PageParser createPageParser(String contentType) {
        def parser = sitemeshFactory?.getPageParser(contentType)
        if (parser == null) {
            parser = new GrailsHTMLPageParser()
        }
        return parser
    }

    protected Factory getSitemeshFactory() {
        FactoryHolder.grailsLayoutFactory
    }

    /**
     * Used to retrieve a property of the decorated page.<br/>
     *
     * &lt;g:pageProperty default="defaultValue" name="body.onload" /&gt;<br/>
     *
     * @emptyTag
     *
     * @attr REQUIRED name the property name
     * @attr default the default value to use if the property is null
     * @attr writeEntireProperty if true, writes the property in the form 'foo = "bar"', otherwise renders 'bar'
     */
    Closure pageProperty = { Map attrs ->
        if (!attrs.name) {
            throwTagError('Tag [pageProperty] is missing required attribute [name]')
        }

        String propertyName = attrs.name as String
        def htmlPage = page
        def propertyValue = null

        if (htmlPage instanceof GSPGrailsLayoutPage) {
            // check if there is an component content buffer
            propertyValue = ((GSPGrailsLayoutPage) htmlPage).getContentBuffer(propertyName)
        }

        if (!propertyValue) {
            propertyValue = htmlPage.getProperty(propertyName)
        }

        if (!propertyValue) {
            propertyValue = attrs.'default'
        }

        if (propertyValue) {
            if (attrs.writeEntireProperty) {
                out << ' '
                out << propertyName.substring(propertyName.lastIndexOf('.') + 1)
                out << '="'
                out << propertyValue
                out << '"'
            } else {
                out << propertyValue
            }
        }
    }

    /**
     * Invokes the body of this tag if the page property exists:<br/>
     *
     * &lt;g:ifPageProperty name="meta.index"&gt;body to invoke&lt;/g:ifPageProperty&gt;<br/>
     *
     * or it equals a certain value:<br/>
     *
     * &lt;g:ifPageProperty name="meta.index" equals="blah"&gt;body to invoke&lt;/g:ifPageProperty&gt;
     *
     * @attr name REQUIRED the property name
     * @attr equals optional value to test against
     */
    Closure ifPageProperty = { Map attrs, body ->
        if (!attrs.name) {
            return
        }

        def htmlPage = getPage()
        List names = ((attrs.name instanceof List) ? (List) attrs.name : [attrs.name])

        def invokeBody = true
        for (i in 0..<names.size()) {
            String propertyName = names[i] as String
            def propertyValue = null
            if (htmlPage instanceof GSPGrailsLayoutPage) {
                // check if there is an component content buffer
                propertyValue = (htmlPage as GSPGrailsLayoutPage).getContentBuffer(propertyName)
            }

            if (!propertyValue) {
                propertyValue = htmlPage.getProperty(propertyName)
            }

            if (propertyValue) {
                if (attrs.containsKey('equals')) {
                    if (attrs.equals instanceof List) {
                        invokeBody = ((List) attrs.equals)[i] == propertyValue
                    } else {
                        invokeBody = attrs.equals == propertyValue
                    }
                }
            } else {
                invokeBody = false
                break
            }
        }
        if (invokeBody && body instanceof Closure) {
            out << body()
        }
    }

    /**
     * Used in layouts to render the page title from the SiteMesh page.<br/>
     *
     * &lt;g:layoutTitle default="The Default title" /&gt;
     *
     * @emptyTag
     *
     * @attr default the value to use if the title isn't specified in the GSP
     */
    Closure layoutTitle = { Map attrs ->
        String title = page.title
        if (!title && attrs.'default') title = attrs.'default'
        if (title) out << title
    }

    /**
     * Used in layouts to render the body of a SiteMesh layout.<br/>
     *
     * &lt;g:layoutBody /&gt;
     *
     * @emptyTag
     */
    Closure layoutBody = { Map attrs ->
        page.writeBody(out)
    }

    /**
     * Used in layouts to render the head of a SiteMesh layout.<br/>
     *
     * &lt;g:layoutHead /&gt;
     *
     * @emptyTag
     */
    Closure layoutHead = { Map attrs ->
        page.writeHead(out)
    }
}
