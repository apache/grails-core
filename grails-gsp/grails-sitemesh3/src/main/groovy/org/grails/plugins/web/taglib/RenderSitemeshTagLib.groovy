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

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

import org.sitemesh.DecoratorSelector
import org.sitemesh.SiteMeshContext
import org.sitemesh.content.Content
import org.sitemesh.content.ContentProcessor
import org.sitemesh.content.ContentProperty
import org.sitemesh.webapp.WebAppContext
import org.sitemesh.webapp.contentfilter.ResponseMetaData

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import org.springframework.web.servlet.ViewResolver

import grails.artefact.TagLibrary
import grails.gsp.TagLib
import grails.util.TypeConvertingMap
import org.grails.buffer.FastStringWriter
import org.grails.buffer.StreamCharBuffer
import org.grails.encoder.CodecLookup
import org.grails.encoder.Encoder
import org.grails.plugins.sitemesh3.GrailsSiteMeshViewContext
import org.grails.plugins.sitemesh3.Sitemesh3CapturedPage
import org.grails.taglib.TagLibraryLookup
import org.grails.taglib.TagOutput
import org.grails.taglib.encoder.OutputContextLookupHelper
import org.grails.web.util.WebUtils

/**
 * Tags rendered by SiteMesh itself (not the Grails taglib pipeline) when a
 * layout GSP is being processed. Kept in the {@code sitemesh} namespace.
 */
@TagLib
class RenderSitemeshTagLib implements TagLibrary {

    @Autowired
    CodecLookup codecLookup

    @Autowired
    ContentProcessor contentProcessor

    @Autowired
    DecoratorSelector<SiteMeshContext> decoratorSelector

    // Break the circular dependency
    // RenderSitemeshTagLib -> ViewResolver -> groovyPagesTemplateEngine ->
    // gspTagLibraryLookup -> RenderSitemeshTagLib by deferring resolution.
    // @Qualifier is required because the context has several ViewResolver
    // beans (mvcViewResolver, beanNameViewResolver, groovyMarkupViewResolver,
    // jspViewResolver, gspViewResolver) and autowiring by type is ambiguous.
    @Autowired
    @Lazy
    @Qualifier('jspViewResolver')
    ViewResolver viewResolver

    // Used to invoke <g:include> and <g:render> for the url/action/template
    // content sources, exactly as SiteMesh 2's RenderGrailsLayoutTagLib does.
    // @Lazy for the same circular-dependency reason as the ViewResolver above:
    // gspTagLibraryLookup depends on every taglib bean, including this one.
    @Autowired
    @Lazy
    TagLibraryLookup gspTagLibraryLookup

    /**
     * Apply a layout to a particular block of text or to the given view or template.<br/>
     *
     * &lt;g:applyLayout name="myLayout"&gt;some text&lt;/g:applyLayout&gt;<br/>
     * &lt;g:applyLayout name="myLayout" template="mytemplate" /&gt;<br/>
     * &lt;g:applyLayout name="myLayout" url="https://www.google.com" /&gt;<br/>
     * &lt;g:applyLayout name="myLayout" action="myAction" controller="myController"&gt;<br/>
     *
     * @attr name The name of the layout
     * @attr template Optional. The template to apply the layout to
     * @attr url Optional. The URL to retrieve the content from and apply a layout to
     * @attr action Optional. The action to be called to generate the content to apply the layout to
     * @attr controller Optional. The controller that contains the action that will generate the content to apply the layout to
     * @attr contentType Optional. The content type to use, default is 'text/html'
     * @attr params Optional. The params to pass onto the page object
     * @attr model Optional. The model to pass to the template, include and layout renders as a java.util.Map
     * @attr parse Optional. If true, the content is always parsed by the SiteMesh content processor instead of reusing the GSP-captured page
     */
    // Dispatches via GrailsSiteMeshViewContext so the layout is rendered
    // through Spring's View API rather than RequestDispatcher.forward().
    // Using the default WebAppContext here would re-enter the servlet
    // pipeline on every <g:applyLayout> call, and nesting (applyLayout
    // inside applyLayout inside a Sitemesh3LayoutView render) would tear
    // down the outer request scope before the outer render finished —
    // causing "request is not active anymore" errors.
    def applyLayout(Map attrs, Closure body) {
        String savedAttribute = request.getAttribute(WebUtils.LAYOUT_ATTRIBUTE)
        // Save the request-scoped captured page (the one being decorated by the
        // outer SiteMesh render) and push a fresh one for the duration of the
        // content render. The content of <g:applyLayout> may be a full GSP
        // document (whose <head>/<body> the compile-time capture taglibs record
        // into the fresh page) or plain markup with no capture taglibs at all
        // (e.g. the grails-fields embedded fieldset content, or the
        // already-decorated output of a nested <g:applyLayout>). Restoring the
        // outer page afterwards prevents the content fragment from clobbering
        // the outer page's body/title/properties.
        Object savedCapturedPage = request.getAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE)
        String contentType = attrs.contentType ? attrs.contentType as String : 'text/html'
        Map pageParams = attrs.params instanceof Map ? (Map) attrs.params : [:]
        Map viewModel = attrs.model instanceof Map ? (Map) attrs.model : [:]
        GrailsSiteMeshViewContext context = new GrailsSiteMeshViewContext(
                contentType, request, response, servletContext,
                contentProcessor, new ResponseMetaData(), false,
                viewResolver, request.getLocale())
        // SiteMesh 2 renders the decorator template with the supplied model
        // (template.make(viewModel) in RenderGrailsLayoutTagLib); mirror that
        // by handing the model to the ViewResolver dispatch that renders the
        // layout view.
        context.setViewModel(viewModel)
        try {
            Sitemesh3CapturedPage bodyPage = new Sitemesh3CapturedPage()
            request.setAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE, bodyPage)

            // Select the content to decorate, mirroring SiteMesh 2's
            // RenderGrailsLayoutTagLib: a remote URL, another controller
            // action's output (via <g:include>), a template or view (via
            // <g:render>) or the tag body. URL and include content never
            // flows through the GSP capture taglibs in SiteMesh 2 (no page
            // is pushed for those paths), so it is always parsed below —
            // the fresh bodyPage is still pushed to keep any capture taglibs
            // that run during the include from clobbering the outer page.
            boolean externalContent = false
            Object renderedBody
            if (attrs.url) {
                externalContent = true
                renderedBody = new URL(attrs.url as String).getText(StandardCharsets.UTF_8.name())
            } else if (attrs.action && attrs.controller) {
                externalContent = true
                Map includeAttrs = [action: attrs.action, controller: attrs.controller,
                                    params: pageParams, model: viewModel]
                renderedBody = TagOutput.captureTagOutput(gspTagLibraryLookup, 'g', 'include',
                        includeAttrs, null, OutputContextLookupHelper.lookupOutputContext())
            } else if (attrs.view || attrs.template) {
                renderedBody = TagOutput.captureTagOutput(gspTagLibraryLookup, 'g', 'render',
                        attrs, null, OutputContextLookupHelper.lookupOutputContext())
            } else {
                renderedBody = body()
            }
            StreamCharBuffer bodyBuffer
            if (renderedBody instanceof StreamCharBuffer) {
                bodyBuffer = (StreamCharBuffer) renderedBody
            } else {
                FastStringWriter stringWriter = new FastStringWriter()
                stringWriter.print(renderedBody)
                bodyBuffer = stringWriter.buffer
            }
            bodyBuffer.setPreferSubChunkWhenWritingToOtherBuffer(true)

            Content content
            // parse="true" forces a SiteMesh parse of the rendered markup even
            // when the GSP capture taglibs populated the page — the same
            // override SiteMesh 2 honors before reusing its GSP-captured page.
            boolean forceParse = externalContent || ((TypeConvertingMap) attrs).boolean('parse')
            if (!forceParse && bodyPage.isUsed()) {
                // The body was a full GSP document: the compile-time capture
                // taglibs have already populated bodyPage with its
                // <head>/<title>/<body>. Only fall back to the whole markup
                // when no <body> tag was present; never overwrite a captured
                // body with the full document (that would nest the document
                // inside the layout's <g:layoutBody> output).
                if (bodyPage.getBodyBuffer() == null) {
                    bodyPage.setBodyBuffer(bodyBuffer)
                }
                content = bodyPage
            } else {
                // No capture taglib ran while rendering the body: it is raw
                // markup — plain text, or the already-decorated output of a
                // nested <g:applyLayout>. Parse it so a full HTML document
                // contributes its head/title/body to the decoration chain
                // (SiteMesh 2 parses in the same situation). Markup without a
                // <body> tag falls back to the whole fragment as the body.
                content = contentProcessor.build(CharBuffer.wrap(bodyBuffer.toString()), context)
            }

            // Expose <g:applyLayout params="[...]"> entries as page properties so
            // the layout can read them via <g:pageProperty name="..."/>. This
            // mirrors SiteMesh 2's GrailsLayoutTagLib, which calls
            // page.addProperty(k, v) for each params entry.
            pageParams.each { k, v ->
                if (k != null && v != null) {
                    addContentProperty(content, k.toString(), v.toString())
                }
            }

            if (attrs.name) {
                request.setAttribute(WebUtils.LAYOUT_ATTRIBUTE, attrs.name)
            }
            boolean decorated = false
            String[] decoratorPaths = decoratorSelector.selectDecoratorPaths(content, context)
            for (String decoratorPath : decoratorPaths) {
                Content next = context.decorate(decoratorPath, content)
                if (next == null) {
                    break
                }
                content = next
                decorated = true
            }
            if (decorated) {
                content.getData().writeValueTo(out)
            } else {
                // No layout was resolved: emit the undecorated body verbatim,
                // matching SiteMesh 2's GrailsLayoutTagLib which writes the raw
                // content when no decorator is found.
                out << bodyBuffer
            }
        } finally {
            if (savedCapturedPage != null) {
                request.setAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE, savedCapturedPage)
            } else {
                request.removeAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE)
            }
            if (savedAttribute != null) {
                request.setAttribute(WebUtils.LAYOUT_ATTRIBUTE, savedAttribute)
            } else {
                request.removeAttribute(WebUtils.LAYOUT_ATTRIBUTE)
            }
        }
    }

    private void addContentProperty(Content content, String name, String value) {
        if (content instanceof Sitemesh3CapturedPage) {
            ((Sitemesh3CapturedPage) content).addProperty(name, value)
            return
        }
        ContentProperty property = content.getExtractedProperties()
        for (String part : name.split('\\.')) {
            property = property.getChild(part)
        }
        property.setValue(value)
    }

    private ContentProperty getContentProperty(String name) {
        if (!name) {
            return null
        }
        Content content = (Content) request.getAttribute(WebAppContext.CONTENT_KEY)
        if (content == null) {
            return null
        }
        ContentProperty currentProperty = content.getExtractedProperties()
        for (String childPropertyName : name.split('\\.')) {
            currentProperty = currentProperty.getChild(childPropertyName)
        }
        currentProperty
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
    def pageProperty(Map attrs) {
        if (!attrs.name) {
            throwTagError('Tag [pageProperty] is missing required attribute [name]')
        }
        String propertyName = attrs.name as String
        ContentProperty contentProperty = getContentProperty(propertyName)
        def propertyValue = contentProperty?.hasValue() ? contentProperty.getValue() : attrs.'default' ?: null

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
    def ifPageProperty(Map attrs, Closure body) {
        if (!attrs.name) {
            return
        }
        List names = ((attrs.name instanceof List) ? (List) attrs.name : [attrs.name])

        def invokeBody = true
        for (i in 0..<names.size()) {
            def propertyValue = getContentProperty(names[i] as String)?.getValue()
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

    // layoutTitle/layoutHead/layoutBody inline-expand at tag-render time.
    // This avoids emitting <sitemesh:write> placeholders that would otherwise
    // require a second HTML parse of the layout output to expand. The
    // property is pulled directly from the Content being merged (set on the
    // request under WebAppContext.CONTENT_KEY by WebAppContext.decorate).
    def layoutTitle(Map attrs) {
        ContentProperty titleProp = getContentProperty('title')
        String defaultValue = attrs.default?.toString() ?: ''
        if (titleProp?.hasValue()) {
            titleProp.writeValueTo(out)
        } else if (defaultValue) {
            out << defaultValue
        }
    }

    def layoutHead(Map attrs, Closure body) {
        ContentProperty headProp = getContentProperty('head')
        if (headProp?.hasValue()) {
            headProp.writeValueTo(out)
        } else if (body) {
            out << body()
        }
    }

    def layoutBody(Map attrs, Closure body) {
        ContentProperty bodyProp = getContentProperty('body')
        if (bodyProp?.hasValue()) {
            bodyProp.writeValueTo(out)
        } else if (body) {
            out << body()
        }
    }

    def content(Map attrs, Closure body) {
        Encoder htmlEncoder = codecLookup?.lookupEncoder('HTML')
        out << '<content tag="'
        out << (htmlEncoder != null ? htmlEncoder.encode(attrs.tag) : attrs.tag)
        out << '">'
        out << body()
        out << '</content>'
    }
}
