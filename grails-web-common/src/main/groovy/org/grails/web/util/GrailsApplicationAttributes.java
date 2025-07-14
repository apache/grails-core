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
package org.grails.web.util;

import grails.core.ApplicationAttributes;
import grails.web.mvc.FlashScope;
import grails.web.pages.GroovyPagesUriService;
import groovy.lang.GroovyObject;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.grails.gsp.ResourceAwareTemplateEngine;
import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

import java.io.Writer;

/**
 * Defines the names of and methods to retrieve Grails specific request and servlet attributes.
 *
 * @author Graeme Rocher
 */
public interface GrailsApplicationAttributes extends ApplicationAttributes {

    String PATH_TO_VIEWS = "/WEB-INF/grails-app/views";
    String GSP_TEMPLATE_ENGINE = "org.grails.GSP_TEMPLATE_ENGINE";
    String ASYNC_STARTED = "org.grails.ASYNC_STARTED";
    String CONTENT_FORMAT = "org.grails.CONTENT_FORMAT";
    String RESPONSE_FORMAT = "org.grails.RESPONSE_FORMAT";
    String RESPONSE_MIME_TYPE = "org.grails.RESPONSE_MIME_TYPE";
    String RESPONSE_MIME_TYPES = "org.grails.RESPONSE_MIME_TYPES";
    String REQUEST_FORMATS = "org.grails.REQUEST_FORMATS";
    String RESPONSE_FORMATS = "org.grails.RESPONSE_FORMATS";
    String FLASH_SCOPE = "org.grails.FLASH_SCOPE";
    String PARAMS_OBJECT = "org.grails.PARAMS_OBJECT";
    String CONTROLLER = "org.grails.CONTROLLER";
    String PROPERTY_REGISTRY = "org.grails.PROPERTY_REGISTRY";
    String ERRORS = "org.grails.ERRORS";
    String MODEL_AND_VIEW = "org.grails.MODEL_AND_VIEW";
    String TEMPLATE_MODEL = "org.grails.TEMPLATE_MODEL";
    String OUT = "org.grails.RESPONSE_OUT";
    String TAG_CACHE = "org.grails.TAG_CACHE";
    String ID_PARAM = "id";
    String GSP_TO_RENDER = "org.grails.GSP_TO_RENDER";
    String GSP_CODEC = "org.grails.GSP_CODEC";
    String WEB_REQUEST = "org.grails.WEB_REQUEST";
    String PAGE_SCOPE = "org.grails.PAGE_SCOPE";
    String GSP_TMP_WRITER = "org.grails.GSP_TMP_WRITER";
    String REQUEST_REDIRECTED_ATTRIBUTE = "org.grails.request_redirected";
    String ACTION_NAME_ATTRIBUTE = "org.grails.ACTION_NAME_ATTRIBUTE";
    String CONTROLLER_NAME_ATTRIBUTE = "org.grails.CONTROLLER_NAME_ATTRIBUTE";
    String CONTROLLER_NAMESPACE_ATTRIBUTE = "org.grails.CONTROLLER_NAMESPACE_ATTRIBUTE";
    String GRAILS_CONTROLLER_CLASS = "org.grails.GRAILS_CONTROLLER_CLASS";
    String APP_URI_ATTRIBUTE = "org.grails.APP_URI_ATTRIBUTE";
    String RENDERING_ERROR_ATTRIBUTE = "org.grails.RENDERING_ERROR_ATTRIBUTE";
    String REDIRECT_ISSUED = "org.grails.REDIRECT_ISSUED";
    String FORWARD_ISSUED = "org.grails.FORWARD_CALLED";
    String FORWARD_IN_PROGRESS = "org.grails.FORWARD_CALLED";
    String GRAILS_CONTROLLER_CLASS_AVAILABLE = "org.grails.GRAILS_CONTROLLER_CLASS_AVAILABLE";

    /**
     * Retrieves the plugin context path for the current request. The plugin context path is the path
     * used by plugins to reference resources such as javascript, CSS and so forth
     * <p>
     * It is established by evaluating the current controller, if the current controller is plugin provided
     * then it will attempt to evaluate the path based on the plugin the controller came from
     *
     * @return The plugin context path
     */
    String getPluginContextPath(HttpServletRequest request);

    /**
     * @return The controller for the request
     */
    GroovyObject getController(ServletRequest request);

    /**
     * @param request
     * @return The uri of the controller within the request
     */
    String getControllerUri(ServletRequest request);

    /**
     * @param request
     * @return The uri of the application relative to the server root
     * @deprecated Use {@link org.grails.web.servlet.mvc.GrailsWebRequest#getContextPath() instead}
     */
    @Deprecated
    String getApplicationUri(ServletRequest request);

    /**
     * Resolve the URI for a template
     *
     * @param controller   The controller
     * @param templateName The name of the template
     * @return The template name
     */
    String getTemplateURI(GroovyObject controller, String templateName);

    /**
     * Resolve the URI for a template
     *
     * @param controller       The controller
     * @param templateName     The name of the template
     * @param includeExtension Whether to include the GSP etension
     * @return The template name
     */
    String getTemplateURI(GroovyObject controller, String templateName, boolean includeExtension);

    String getNoSuffixViewURI(GroovyObject controller, String viewName);

    /**
     * Retrieves the servlet context instance
     *
     * @return The servlet context instance
     */
    ServletContext getServletContext();

    /**
     * Retrieves the flash scope instance for the given requeste
     *
     * @param request
     * @return The FlashScope instance
     */
    FlashScope getFlashScope(ServletRequest request);

    /**
     * @param templateName
     * @param request
     * @return The uri of a named template for the current controller
     */
    String getTemplateUri(CharSequence templateName, ServletRequest request);

    /**
     * Retrieves the uri of a named view
     *
     * @param viewName The name of the view
     * @param request  The request instance
     * @return The name of the view
     */
    String getViewUri(String viewName, HttpServletRequest request);

    /**
     * @param request
     * @return The uri of the action called within the controller
     */
    String getControllerActionUri(ServletRequest request);

    /**
     * @param request
     * @return The errors instance contained within the request
     */
    Errors getErrors(ServletRequest request);

    /**
     * @return Retrieves the shared GSP template engine
     */
    ResourceAwareTemplateEngine getPagesTemplateEngine();

    /**
     * Holds the current response write for the request
     *
     * @return The held response writer
     */
    Writer getOut(HttpServletRequest request);

    /**
     * Sets the current write for the request
     *
     * @param currentRequest The request
     * @param out2           The writer
     */
    void setOut(HttpServletRequest currentRequest, Writer out2);

    /**
     * @return The GroovyPageUriService instance
     */
    GroovyPagesUriService getGroovyPagesUriService();

    /**
     * @return The MessageSource instance
     */
    MessageSource getMessageSource();
}
