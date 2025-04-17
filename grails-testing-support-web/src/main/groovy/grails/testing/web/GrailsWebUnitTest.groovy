/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.testing.web

import grails.artefact.TagLibrary
import grails.core.GrailsClass
import grails.core.GrailsControllerClass
import grails.core.gsp.GrailsTagLibClass
import grails.util.GrailsNameUtils
import grails.web.mvc.FlashScope
import grails.web.servlet.mvc.GrailsParameterMap
import groovy.text.Template
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
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
import org.grails.testing.GrailsUnitTest
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockServletContext

@CompileStatic
@Slf4j
trait GrailsWebUnitTest implements GrailsUnitTest {

    private Set<Class> loadedCodecs = new HashSet<Class>()
    static Map<String, String> groovyPages = [:]
    GrailsWebRequest webRequest

    GrailsMockHttpServletRequest getRequest() {
        return (GrailsMockHttpServletRequest) getWebRequest().getCurrentRequest()
    }

    GrailsMockHttpServletResponse getResponse() {
        return (GrailsMockHttpServletResponse) getWebRequest().getCurrentResponse()
    }

    MockServletContext getServletContext() {
        (MockServletContext)optionalServletContext
    }

    Map<String, String> getViews() {
        groovyPages
    }

    /**
     * The {@link org.springframework.mock.web.MockHttpSession} instance
     */
    MockHttpSession getSession() {
        (MockHttpSession) request.session
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
        webRequest.getParams()
    }

    /**
     * The Grails 'flash' object
     * @return
     */
    FlashScope getFlash() {
        webRequest.getFlashScope()
    }

    @CompileDynamic
    Object mockTagLib(Class<?> tagLibClass) {
        GrailsTagLibClass tagLib = grailsApplication.addArtefact(TagLibArtefactHandler.TYPE, tagLibClass)
        final tagLookup = applicationContext.getBean(TagLibraryLookup)


        defineBeans {
            "${tagLib.fullName}"(tagLibClass) { bean ->
                bean.autowire = true
            }
        }

        tagLookup.registerTagLib(tagLib)

        def taglibObject = applicationContext.getBean(tagLib.fullName)
        if(taglibObject instanceof TagLibrary) {
            ((TagLibrary)taglibObject).setTagLibraryLookup(tagLookup)
        }
        taglibObject
    }

    @CompileDynamic
    Object mockController(Class<?> controllerClass) {
        createAndEnhanceController(controllerClass)
        defineBeans {
            "$controllerClass.name"(controllerClass) { bean ->
                bean.scope = 'prototype'
                bean.autowire = true
            }
        }

        def controller = applicationContext.getBean(controllerClass.name)

        if (webRequest == null) {
            throw new IllegalAccessException("Cannot access the controller outside of a request. Is the controller referenced in a where: block?")
        }

        webRequest.request.setAttribute(GrailsApplicationAttributes.CONTROLLER, controller)
        webRequest.controllerName = GrailsNameUtils.getLogicalPropertyName(controller.class.name, ControllerArtefactHandler.TYPE)

        controller
    }

    private GrailsClass createAndEnhanceController(Class controllerClass) {
        final GrailsControllerClass controllerArtefact = (GrailsControllerClass) grailsApplication.addArtefact(ControllerArtefactHandler.TYPE, controllerClass)
        controllerArtefact.initialize()
        return controllerArtefact
    }


    void mockTagLibs(Class<?>... tagLibClasses) {
        for(Class c : tagLibClasses) {
            mockTagLib c
        }
    }

    void mockCodec(Class<?> codecClass, boolean reinitialize = true) {
        if (loadedCodecs.contains(codecClass)) {
            return
        }
        loadedCodecs << codecClass
        DefaultGrailsCodecClass grailsCodecClass = new DefaultGrailsCodecClass(codecClass)
        grailsCodecClass.configureCodecMethods()
        grailsApplication.addArtefact(CodecArtefactHandler.TYPE, grailsCodecClass)
        if (reinitialize) {
            applicationContext.getBean(DefaultCodecLookup).reInitialize()
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
            model = (Map)args.model
        } else {
            model = [:]
        }
        final attributes = webRequest.attributes
        if (args.template) {
            uri = attributes.getTemplateUri(args.template as String, request)
        }
        else if (args.view) {
            uri = attributes.getViewUri(args.view as String, request)
        }
        if (uri != null) {
            GroovyPagesTemplateEngine engine = applicationContext.getBean(GroovyPagesTemplateEngine)
            final Template t = engine.createTemplate(uri)
            if (t != null) {
                def sw = new StringWriter()
                renderTemplateToStringWriter(sw, t, model)
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
        applyTemplate sw, contents, model
        return sw.toString()
    }

    /**
     * Renders a template for the given contents and model to the provided writer
     *
     * @param sw The write to write the rendered template to
     * @param contents The contents
     * @param model The model
     */
    void applyTemplate(StringWriter sw, String template, Map params = [:]) {
        def engine = applicationContext.getBean(GroovyPagesTemplateEngine)

        def t = engine.createTemplate(template, "test_" + System.currentTimeMillis())
        renderTemplateToStringWriter(sw, t, params)
    }

    private renderTemplateToStringWriter(StringWriter sw, Template t, Map params) {
        if (!webRequest.controllerName) {
            webRequest.controllerName = 'test'
        }
        if (!webRequest.actionName) {
            webRequest.actionName = 'index'
        }
        def w = t.make(params)
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
