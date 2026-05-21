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
import org.grails.encoder.CodecLookup
import org.grails.encoder.Encoder
import org.grails.plugins.sitemesh3.GrailsSiteMeshViewContext
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

    // Dispatches via GrailsSiteMeshViewContext so the layout is rendered
    // through Spring's View API rather than RequestDispatcher.forward().
    // Using the default WebAppContext here would re-enter the servlet
    // pipeline on every <g:applyLayout> call, and nesting (applyLayout
    // inside applyLayout inside a Sitemesh3LayoutView render) would tear
    // down the outer request scope before the outer render finished —
    // causing "request is not active anymore" errors.
    Closure applyLayout = { Map attrs, body ->
        String savedAttribute = request.getAttribute(WebUtils.LAYOUT_ATTRIBUTE)
        GrailsSiteMeshViewContext context = new GrailsSiteMeshViewContext(
                'text/html', request, response, servletContext,
                contentProcessor, new ResponseMetaData(), false,
                viewResolver, request.getLocale())
        try {
            Content content = contentProcessor.build(CharBuffer.wrap(body()), context)
            if (attrs.name) {
                request.setAttribute(WebUtils.LAYOUT_ATTRIBUTE, attrs.name)
            }
            String[] decoratorPaths = decoratorSelector.selectDecoratorPaths(content, context)
            for (String decoratorPath : decoratorPaths) {
                Content next = context.decorate(decoratorPath, content)
                if (next == null) {
                    break
                }
                content = next
            }
            if (content != null) {
                content.getData().writeValueTo(out)
            }
        } finally {
            if (savedAttribute != null) {
                request.setAttribute(WebUtils.LAYOUT_ATTRIBUTE, savedAttribute)
            } else {
                request.removeAttribute(WebUtils.LAYOUT_ATTRIBUTE)
            }
        }
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
    Closure pageProperty = { attrs ->
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
    Closure ifPageProperty = { Map attrs, body ->
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
    Closure layoutTitle = { attrs ->
        ContentProperty titleProp = getContentProperty('title')
        String defaultValue = attrs.default?.toString() ?: ''
        if (titleProp?.hasValue()) {
            titleProp.writeValueTo(out)
        } else if (defaultValue) {
            out << defaultValue
        }
    }

    Closure layoutHead = { attrs, body ->
        ContentProperty headProp = getContentProperty('head')
        if (headProp?.hasValue()) {
            headProp.writeValueTo(out)
        } else if (body) {
            out << body()
        }
    }

    Closure layoutBody = { attrs, body ->
        ContentProperty bodyProp = getContentProperty('body')
        if (bodyProp?.hasValue()) {
            bodyProp.writeValueTo(out)
        } else if (body) {
            out << body()
        }
    }

    Closure content = { attrs, body ->
        Encoder htmlEncoder = codecLookup?.lookupEncoder('HTML')
        out << '<content tag="'
        out << (htmlEncoder != null ? htmlEncoder.encode(attrs.tag) : attrs.tag)
        out << '">'
        out << body()
        out << '</content>'
    }
}
