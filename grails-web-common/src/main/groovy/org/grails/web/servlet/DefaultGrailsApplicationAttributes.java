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
package org.grails.web.servlet;

import grails.core.GrailsApplication;
import grails.core.GrailsControllerClass;
import grails.plugins.GrailsPluginManager;
import grails.util.Holders;
import grails.web.mvc.FlashScope;
import grails.web.pages.GroovyPagesUriService;
import groovy.lang.GroovyObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.grails.gsp.ResourceAwareTemplateEngine;
import org.grails.web.pages.DefaultGroovyPagesUriService;
import org.grails.web.util.GrailsApplicationAttributes;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.util.Assert;
import org.springframework.validation.Errors;
import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Writer;

/**
 * Holds knowledge about how to obtain certain attributes from either the ServletContext
 * or the HttpServletRequest instance.
 *
 * @see org.grails.web.servlet.mvc.GrailsWebRequest
 *
 * @author Graeme Rocher
 * @since 0.3
 */
public class DefaultGrailsApplicationAttributes implements GrailsApplicationAttributes {
    protected static final String DEFAULT_NAMESPACE = "g";
    
    private static Log LOG = LogFactory.getLog(DefaultGrailsApplicationAttributes.class);

    private UrlPathHelper urlHelper = new UrlPathHelper();

    private ServletContext context;
    private ApplicationContext appContext;

    // Beans used very often
    private ResourceAwareTemplateEngine pagesTemplateEngine;
    private GrailsApplication grailsApplication;
    private GroovyPagesUriService groovyPagesUriService;
    private MessageSource messageSource;
    private GrailsPluginManager pluginManager;

    public DefaultGrailsApplicationAttributes(ServletContext context) {
        this.context = context;
        if (context != null) {
            appContext = (ApplicationContext)context.getAttribute(APPLICATION_CONTEXT);
            if(appContext == null) {
                appContext = Holders.findApplicationContext();
            }
        }
    }

    public ApplicationContext getApplicationContext() {
        return appContext;
    }

    private GrailsPluginManager getPluginManager() {
        if(pluginManager==null) {
            pluginManager = fetchBeanFromAppCtx(GrailsPluginManager.BEAN_NAME);
        }
        return pluginManager;
    }

    @SuppressWarnings("unchecked")
    private <T> T fetchBeanFromAppCtx(String name) {
        if(appContext==null) {
            return null;
        }
        try {
            if(appContext.containsBean(name)) {
                return (T)appContext.getBean(name);
            } else {
                return null;
            }
        }
        catch(BeansException e) {
            LOG.warn("Bean named '" + name + "' is missing.");
            return null;
        }
    }

    public String getPluginContextPath(HttpServletRequest request) {
        GroovyObject controller = getController(request);
        if (controller != null && getPluginManager() != null) {
            String path = getPluginManager().getPluginPathForInstance(controller);
            return path == null ? "" : path;
        }

        return "";
    }

    public GroovyObject getController(ServletRequest request) {
        return (GroovyObject)request.getAttribute(CONTROLLER);
    }

    public String getControllerUri(ServletRequest request) {
        return "/" + getControllerName(request);
    }

    private String getControllerName(ServletRequest request) {
        String controllerName = (String) request.getAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE);
        if (controllerName == null || controllerName.length() == 0) {
            GroovyObject controller = getController(request);
            if (controller != null) {
                controllerName = (String)controller.getProperty("controllerName");
                request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, controllerName);
                if(controller instanceof GrailsControllerClass) {
                    String namespace = ((GrailsControllerClass)controller).getNamespace();
                    if(namespace != null) {
                        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAMESPACE_ATTRIBUTE, namespace);
                    }
                }

            }
        }
        return controllerName != null ? controllerName : "";
    }

    /**
     * @deprecated Use {@link org.grails.web.servlet.mvc.GrailsWebRequest#getContextPath() instead}
     * @param request The Servlet Reqest
     * @return The Application URI
     */
    @Deprecated
    public String getApplicationUri(ServletRequest request) {
        String appUri = (String) request.getAttribute(GrailsApplicationAttributes.APP_URI_ATTRIBUTE);
        if (appUri == null) {
            appUri = urlHelper.getContextPath((HttpServletRequest)request);
        }
        return appUri;
    }

    public ServletContext getServletContext() {
        return context;
    }

    public FlashScope getFlashScope(ServletRequest request) {
        if (!(request instanceof HttpServletRequest)) {
            return null;
        }

        HttpServletRequest servletRequest = (HttpServletRequest) request;
        HttpSession session = servletRequest.getSession(false);
        FlashScope fs;
        if (session != null) {
            fs = (FlashScope)session.getAttribute(FLASH_SCOPE);
        }
        else {
            fs = (FlashScope)request.getAttribute(FLASH_SCOPE);
        }
        if (fs == null) {
            fs = new GrailsFlashScope();
            if (session!=null) {
                session.setAttribute(FLASH_SCOPE,fs);
            }
            else {
                request.setAttribute(FLASH_SCOPE,fs);
            }
        }
        return fs;
    }

    public String getTemplateUri(CharSequence templateName, ServletRequest request) {
        Assert.notNull(templateName, "Argument [template] cannot be null");
        return getGroovyPagesUriService().getTemplateURI(getControllerName(request), templateName.toString());
    }

    public String getViewUri(String viewName, HttpServletRequest request) {
        Assert.notNull(viewName, "Argument [view] cannot be null");
        return getGroovyPagesUriService().getDeployedViewURI(getControllerName(request), viewName);
    }

    public String getControllerActionUri(ServletRequest request) {
        GroovyObject controller = getController(request);
        return (String)controller.getProperty("actionUri");
    }

    public Errors getErrors(ServletRequest request) {
        return (Errors)request.getAttribute(ERRORS);
    }

    public ResourceAwareTemplateEngine getPagesTemplateEngine() {
        if (pagesTemplateEngine == null) {
            pagesTemplateEngine = fetchBeanFromAppCtx(ResourceAwareTemplateEngine.BEAN_ID);
        }
        if (pagesTemplateEngine == null && LOG.isWarnEnabled()) {
            LOG.warn("No bean named [" + ResourceAwareTemplateEngine.BEAN_ID + "] defined in Spring application context!");
        }
        return pagesTemplateEngine;
    }

    public GrailsApplication getGrailsApplication() {
        if(grailsApplication==null) {
            grailsApplication = fetchBeanFromAppCtx(GrailsApplication.APPLICATION_ID);
            if (grailsApplication == null) {
                grailsApplication = Holders.findApplication();
            }
        }
        return grailsApplication;
    }

    public Writer getOut(HttpServletRequest request) {
        return (Writer)request.getAttribute(OUT);
    }

    public void setOut(HttpServletRequest request, Writer out2) {
        request.setAttribute(OUT, out2);
    }

    public String getNoSuffixViewURI(GroovyObject controller, String viewName) {
   		return getGroovyPagesUriService().getNoSuffixViewURI(controller, viewName);
    }

    public String getTemplateURI(GroovyObject controller, String templateName) {
        return getGroovyPagesUriService().getTemplateURI(controller, templateName);
    }

    @Override
    public String getTemplateURI(GroovyObject controller, String templateName, boolean includeExtension) {
        return getGroovyPagesUriService().getTemplateURI(controller, templateName, includeExtension);
    }

    public GroovyPagesUriService getGroovyPagesUriService() {
        if (groovyPagesUriService == null) {
            groovyPagesUriService = fetchBeanFromAppCtx(GroovyPagesUriService.BEAN_ID);
            if (groovyPagesUriService == null) {
                groovyPagesUriService = new DefaultGroovyPagesUriService();
            }
        }
        return groovyPagesUriService;
    }

    public MessageSource getMessageSource() {
        if(messageSource==null) {
            messageSource = fetchBeanFromAppCtx("messageSource");
        }
        return messageSource;
    }
}
