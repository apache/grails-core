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
package grails.testing.web

import groovy.text.Template
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockServletContext

import grails.artefact.Controller
import grails.artefact.TagLibrary
import grails.core.GrailsClass
import grails.core.GrailsControllerClass
import grails.core.gsp.GrailsTagLibClass
import grails.util.GrailsNameUtils
import grails.web.mvc.FlashScope
import grails.web.servlet.mvc.GrailsParameterMap
import org.grails.buffer.GrailsPrintWriter
import org.grails.commons.CodecArtefactHandler
import org.grails.commons.DefaultGrailsCodecClass
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.core.artefact.gsp.TagLibArtefactHandler
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.plugins.codecs.DefaultCodecLookup
import org.grails.plugins.testing.GrailsMockHttpServletRequest
import org.grails.plugins.testing.GrailsMockHttpServletResponse
import org.grails.taglib.TagLibraryLookup
import org.grails.taglib.TagLibraryMetaUtils
import org.grails.testing.GrailsUnitTest
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes

@CompileStatic
trait GrailsWebUnitTest implements GrailsUnitTest {

    private Set<Class> loadedCodecs = new HashSet<Class>()
    static Map<String, String> groovyPages = [:]
    GrailsWebRequest webRequest

    GrailsMockHttpServletRequest getRequest() {
        webRequest.currentRequest as GrailsMockHttpServletRequest
    }

    GrailsMockHttpServletResponse getResponse() {
        webRequest.currentResponse as GrailsMockHttpServletResponse
    }

    MockServletContext getServletContext() {
        optionalServletContext as MockServletContext
    }

    Map<String, String> getViews() {
        groovyPages
    }

    /**
     * The {@link org.springframework.mock.web.MockHttpSession} instance
     */
    MockHttpSession getSession() {
        request.session as MockHttpSession
    }

    /**
     * @return The status code of the response
     */
    int getStatus() {
        response.status
    }

    /**
     * The Grails 'params' object which is an instance of {@link grails.web.servlet.mvc.GrailsParameterMap}
     */
    GrailsParameterMap getParams() {
        webRequest.params
    }

    /**
     * The Grails 'flash' object
     * @return
     */
    FlashScope getFlash() {
        webRequest.flashScope
    }

    @CompileDynamic
    <T> T mockTagLib(Class<T> tagLibClass) {
        def tagLib = grailsApplication.addArtefact(TagLibArtefactHandler.TYPE, tagLibClass) as GrailsTagLibClass
        def tagLookup = applicationContext.getBean(TagLibraryLookup)

        if (!applicationContext.containsBean(tagLib.fullName)) {
            defineBeans {
                "${tagLib.fullName}"(tagLibClass) { bean ->
                    bean.autowire = true
                }
            }
        }

        tagLookup.registerTagLib(tagLib)

        def taglibObject = applicationContext.getBean(tagLib.fullName)
        // Kept for tests, which call tag methods directly: the installed methods substitute an empty
        // body for a missing one, so tagLib.someTag(attrs, null) works. A running application does not
        // rely on these, resolving tags through the lookup instead.
        TagLibraryMetaUtils.enhanceTagLibMetaClass(tagLib, tagLookup)
        TagLibraryMetaUtils.enhanceTagLibMetaClass(taglibObject.metaClass, tagLookup, tagLib.namespace)
        if (taglibObject instanceof TagLibrary) {
            ((TagLibrary) taglibObject).tagLibraryLookup = tagLookup
        }
        taglibObject as T
    }

    @CompileDynamic
    <T extends Controller> T mockController(Class<T> controllerClass) {
        createAndEnhanceController(controllerClass)
        defineBeans {
            "$controllerClass.name"(controllerClass) { bean ->
                bean.scope = 'prototype' // A new instance is created for each request in tests to avoid state leakage between tests
                bean.autowire = true
            }
        }

        def controller = applicationContext.getBean(controllerClass.name)

        if (webRequest == null) {
            throw new IllegalAccessException(
                    'Cannot access the controller outside of a request. ' +
                    'Is the controller referenced in a where: block?'
            )
        }

        webRequest.request.setAttribute(GrailsApplicationAttributes.CONTROLLER, controller)
        webRequest.controllerName = GrailsNameUtils.getLogicalPropertyName(controller.class.name, ControllerArtefactHandler.TYPE)

        controller as T
    }

    private GrailsClass createAndEnhanceController(Class controllerClass) {
        (grailsApplication.addArtefact(ControllerArtefactHandler.TYPE, controllerClass) as GrailsControllerClass).tap {
            initialize()
        }
    }

    void mockTagLibs(Class<?>... tagLibClasses) {
        for (def c : tagLibClasses) {
            mockTagLib(c)
        }
    }

    void mockCodec(Class<?> codecClass, boolean reinitialize = true) {
        if (loadedCodecs.contains(codecClass)) {
            return
        }
        loadedCodecs << codecClass
        DefaultGrailsCodecClass grailsCodecClass = new DefaultGrailsCodecClass(codecClass)
        grailsApplication.addArtefact(CodecArtefactHandler.TYPE, grailsCodecClass)
        if (reinitialize) {
            applicationContext.getBean(DefaultCodecLookup).reInitialize()
        } else {
            grailsCodecClass.configureCodecMethods()
        }
    }

    /**
     * Mimics the behavior of the render method in controllers but returns the rendered contents directly
     *
     * @param args The same arguments as the controller render method accepts
     * @return The resulting rendering GSP
     */
    String render(Map args) {
        String uri = null
        Map model
        if (args.containsKey('model')) {
            model = args.model as Map
        } else {
            model = [:]
        }
        def attributes = webRequest.attributes
        if (args.template) {
            uri = attributes.getTemplateUri(args.template as String, request)
        } else if (args.view) {
            uri = attributes.getViewUri(args.view as String, request)
        }
        if (uri != null) {
            def engine = applicationContext.getBean(GroovyPagesTemplateEngine)
            def template = engine.createTemplate(uri)
            if (template != null) {
                def sw = new StringWriter()
                renderTemplateToStringWriter(sw, template, model)
                return sw.toString()
            }
        }
        return null
    }

    /**
     * Renders a template for the given contents and model
     *
     * @param contents The contents
     * @param model The model
     * @return The rendered template
     */
    String applyTemplate(String contents, Map model = [:]) {
        def sw = new StringWriter()
        applyTemplate(sw, contents, model)
        return sw.toString()
    }

    /**
     * Renders a template for the given contents and model to the provided writer
     *
     * @param sw The write to write the rendered template to
     * @param contents The contents
     * @param model The model
     */
    void applyTemplate(StringWriter sw, String templateText, Map params = [:]) {
        def engine = applicationContext.getBean(GroovyPagesTemplateEngine)
        def template = engine.createTemplate(templateText, 'test_' + System.currentTimeMillis(), false)
        renderTemplateToStringWriter(sw, template, params)
    }

    private renderTemplateToStringWriter(StringWriter sw, Template template, Map params) {
        if (!webRequest.controllerName) {
            webRequest.controllerName = 'test'
        }
        if (!webRequest.actionName) {
            webRequest.actionName = 'index'
        }
        def w = template.make(params)
        def previousOut = webRequest.out
        try {
            def out = new GrailsPrintWriter(sw)
            webRequest.out = out
            w.writeTo(out)

        }
        finally {
            webRequest.out = previousOut
        }
    }
}
