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
import groovy.transform.CompileStatic
import org.grails.encoder.CodecLookup
import org.grails.encoder.Encoder
import org.grails.exceptions.ExceptionUtils
import org.grails.web.errors.ErrorsViewStackTracePrinter
import org.grails.web.gsp.GroovyPagesTemplateRenderer
import org.grails.web.util.WebUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.util.StringUtils

/**
 * Tags to help rendering of views.
 *
 * @author Graeme Rocher
 */
@CompileStatic
@TagLib
class RenderTagLib implements TagLibrary {

    RenderTagLib() {}

    @Autowired
    RenderTagLib(GroovyPagesTemplateRenderer groovyPagesTemplateRenderer, @Autowired(required = false) ErrorsViewStackTracePrinter errorsViewStackTracePrinter, CodecLookup codecLookup) {
        this.groovyPagesTemplateRenderer = groovyPagesTemplateRenderer
        this.errorsViewStackTracePrinter = errorsViewStackTracePrinter
        this.codecLookup = codecLookup
    }

    GroovyPagesTemplateRenderer groovyPagesTemplateRenderer
    ErrorsViewStackTracePrinter errorsViewStackTracePrinter
    CodecLookup codecLookup

    /**
     * Renders a template inside views for collections, models and beans. Examples:<br/>
     *
     * &lt;g:render template="atemplate" collection="${users}" /&gt;<br/>
     * &lt;g:render template="atemplate" model="[user:user,company:company]" /&gt;<br/>
     * &lt;g:render template="atemplate" bean="${user}" /&gt;<br/>
     *
     * @attr template REQUIRED The name of the template to apply
     * @attr optional if true, this tag will be ignored when the template does not exist.
     * @attr contextPath the context path to use (relative to the application context path). Defaults to "" or path to the plugin for a plugin view or template.
     * @attr bean The bean to apply the template against
     * @attr model The model to apply the template against as a java.util.Map
     * @attr collection A collection of model objects to apply the template to
     * @attr var The variable name of the bean to be referenced in the template
     * @attr plugin The plugin to look for the template in
     */
    Closure render = { Map attrs, body ->
        groovyPagesTemplateRenderer.render(getWebRequest(), getPageScope(), attrs, body, getOut())
        null
    }

    /**
     * Renders an exception for the errors view
     *
     * @attr exception REQUIRED The exception to render
     */
    Closure renderException = { Map attrs ->
        if (!(attrs?.exception instanceof Throwable)) {
            return
        }
        Throwable exception = (Throwable)attrs.exception

        Encoder htmlEncoder = codecLookup.lookupEncoder('HTML')

        def currentOut = out
        int statusCode = request.getAttribute('jakarta.servlet.error.status_code') as int
        currentOut << """<h1>Error ${prettyPrintStatus(statusCode)}</h1>
<dl class="${attrs['detailsClass'] ?: 'error-details'}">
<dt>URI</dt><dd>${htmlEncoder.encode(WebUtils.getForwardURI(request) ?: request.getAttribute('jakarta.servlet.error.request_uri'))}</dd>
"""

        def root = ExceptionUtils.getRootCause(exception)
        currentOut << "<dt>Class</dt><dd>${root?.getClass()?.name ?: exception.getClass().name}</dd>"
        currentOut << "<dt>Message</dt><dd>${htmlEncoder.encode(exception.message)}</dd>"
        if (root != null && root != exception && root.message != exception.message) {
            currentOut << "<dt>Caused by</dt><dd>${htmlEncoder.encode(root.message)}</dd>"
        }
        currentOut << "</dl>"

        currentOut << errorsViewStackTracePrinter.prettyPrintCodeSnippet(exception, attrs)

        def trace = errorsViewStackTracePrinter.prettyPrint(exception.cause ?: exception, attrs)
        if (StringUtils.hasText(trace.trim())) {
            currentOut << "<h2>Trace</h2>"
            currentOut << """<pre class="${attrs['stackClass'] ?: 'stack'}">"""
            currentOut << htmlEncoder.encode(trace)
            currentOut << '</pre>'
        }
    }

    private String prettyPrintStatus(int statusCode) {
        String httpStatusReason = HttpStatus.valueOf(statusCode).getReasonPhrase()
        "$statusCode: ${httpStatusReason}"
    }
}
