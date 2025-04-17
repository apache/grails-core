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
package grails.testing.web.controllers

import grails.testing.web.GrailsWebUnitTest
import grails.web.mime.MimeType
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.grails.testing.ParameterizedGrailsUnitTest
import org.grails.web.pages.GroovyPagesUriSupport
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import javassist.util.proxy.ProxyFactory
import org.grails.testing.runtime.support.ActionSettingMethodHandler

@CompileStatic
trait ControllerUnitTest<T> implements ParameterizedGrailsUnitTest<T>, GrailsWebUnitTest {

    static String FORM_CONTENT_TYPE = MimeType.FORM.name
    static String MULTIPART_FORM_CONTENT_TYPE = MimeType.MULTIPART_FORM.name
    static String ALL_CONTENT_TYPE = MimeType.ALL.name
    static String HTML_CONTENT_TYPE = MimeType.HTML.name
    static String XHTML_CONTENT_TYPE = MimeType.XHTML.name
    static String XML_CONTENT_TYPE = MimeType.XML.name
    static String JSON_CONTENT_TYPE = MimeType.JSON.name
    static String TEXT_XML_CONTENT_TYPE = MimeType.TEXT_XML.name
    static String TEXT_JSON_CONTENT_TYPE = MimeType.TEXT_JSON.name
    static String HAL_JSON_CONTENT_TYPE = MimeType.HAL_JSON.name
    static String HAL_XML_CONTENT_TYPE = MimeType.HAL_XML.name
    static String ATOM_XML_CONTENT_TYPE = MimeType.ATOM_XML.name

    private T _proxyInstance

    /**
     * @return The model of the current controller
     */
    @CompileStatic(TypeCheckingMode.SKIP)
    Map getModel() {
        Map model = request.getAttribute(GrailsApplicationAttributes.CONTROLLER)?.modelAndView?.model
        if (model == null) {
            model = request.getAttribute(GrailsApplicationAttributes.TEMPLATE_MODEL)
        }
        return model ?: [:]
    }

    /**
     * @return The view of the current controller
     */
    @CompileStatic(TypeCheckingMode.SKIP)
    String getView() {
        final controller = request.getAttribute(GrailsApplicationAttributes.CONTROLLER)

        final viewName = controller?.modelAndView?.viewName
        if (viewName != null) {
            return viewName
        }

        if (webRequest.controllerName && webRequest.actionName) {
            new GroovyPagesUriSupport().getViewURI(webRequest.controllerName, webRequest.actionName)
        } else {
            return null
        }
    }

    /**
     * Mocks a Grails controller class, providing the needed behavior and defining it in the ApplicationContext
     *
     * @param controllerClass The controller class
     * @return An instance of the controller
     */
    void mockArtefact(Class<?> controllerClass) {
        mockController(controllerClass)
    }

    String getBeanName(Class<?> controllerClass) {
        controllerClass.name
    }

    boolean disableControllerProxy() {
        false
    }

    T getController() {
        if (disableControllerProxy()) {
            getArtefactInstance()
        }
        else {
            if (_proxyInstance == null) {
                T artefact = getArtefactInstance()
                ProxyFactory factory = new ProxyFactory()
                factory.setSuperclass(getTypeUnderTest())
                _proxyInstance = (T)factory.create(new Class<?>[0], new Object[0], new ActionSettingMethodHandler(artefact, getWebRequest()))
            }
            _proxyInstance
        }
    }
}
