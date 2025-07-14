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
package org.grails.web.errors;

import grails.config.Config;
import grails.config.Settings;
import grails.core.GrailsApplication;
import grails.core.support.GrailsApplicationAware;
import grails.util.Environment;
import grails.web.mapping.UrlMappingInfo;
import grails.web.mapping.UrlMappingsHolder;
import grails.web.mapping.exceptions.UrlMappingException;
import grails.web.mvc.GrailsResponseMutator;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.grails.core.exceptions.GrailsRuntimeException;
import org.grails.exceptions.ExceptionUtils;
import org.grails.exceptions.reporting.DefaultStackTraceFilterer;
import org.grails.exceptions.reporting.StackTraceFilterer;
import org.grails.web.mapping.DefaultUrlMappingInfo;
import org.grails.web.mapping.UrlMappingUtils;
import org.grails.web.servlet.mvc.exceptions.GrailsMVCException;
import org.grails.web.util.GrailsApplicationAttributes;
import org.grails.web.util.WebUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.handler.SimpleMappingExceptionResolver;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps any runtime exceptions with a GrailsWrappedException instance.
 *
 * @author Graeme Rocher
 */
public class GrailsExceptionResolver extends SimpleMappingExceptionResolver implements ServletContextAware, GrailsApplicationAware {

    public static final String EXCEPTION_ATTRIBUTE = WebUtils.EXCEPTION_ATTRIBUTE;

    protected static final Log LOG = LogFactory.getLog(GrailsExceptionResolver.class);
    protected static final String LINE_SEPARATOR = System.getProperty("line.separator");

    protected ServletContext servletContext;
    protected GrailsApplication grailsApplication;
    protected StackTraceFilterer stackFilterer;

    /* (non-Javadoc)
     * @see org.springframework.web.servlet.handler.SimpleMappingExceptionResolver#resolveException(jakarta.servlet.http.HttpServletRequest, jakarta.servlet.http.HttpServletResponse, java.lang.Object, java.lang.Exception)
     */
    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
                                         Object handler, Exception ex) {
        // don't reuse cached controller attribute 
        request.removeAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS_AVAILABLE);

        ex = findWrappedException(ex);

        filterStackTrace(ex);

        ModelAndView mv = super.resolveException(request, response, handler, ex);

        setStatus(request, response, mv, ex);

        logStackTrace(ex, request);

        UrlMappingsHolder urlMappings = lookupUrlMappings();
        if (urlMappings != null) {
            mv = resolveViewOrForward(ex, urlMappings, request, response, mv);
        }

        return mv;
    }

    public String getRequestLogMessage(Throwable e, HttpServletRequest request) {
        Throwable cause = getRootCause(e);
        String exceptionName = cause.getClass().getSimpleName();
        return getRequestLogMessage(exceptionName, request, cause.getMessage());
    }

    public String getRequestLogMessage(HttpServletRequest request) {
        return getRequestLogMessage("Exception", request, null);
    }

    public void setServletContext(ServletContext servletContext) {

        this.servletContext = servletContext;
    }

    public void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
        createStackFilterer();
    }

    /**
     * Obtains the root cause of the given exception
     *
     * @param ex The exception
     * @return The root cause
     */
    public static Throwable getRootCause(Throwable ex) {
        return ExceptionUtils.getRootCause(ex);
    }

    public static int extractLineNumber(CompilationFailedException e) {
        return ExceptionUtils.extractLineNumber(e);
    }

    public static RuntimeException getFirstRuntimeException(Throwable e) {
        return ExceptionUtils.getFirstRuntimeException(e);
    }

    protected void filterStackTrace(Exception e) {
        stackFilterer.filter(e, true);
    }

    protected void setStatus(HttpServletRequest request, HttpServletResponse response, ModelAndView mv, Exception e) {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        // expose the servlet 2.3 specs status code request attribute as 500
        request.setAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        final GrailsWrappedRuntimeException gwre = new GrailsWrappedRuntimeException(servletContext, e);
        mv.addObject(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, gwre);
        mv.addObject(WebUtils.EXCEPTION_ATTRIBUTE, gwre);
    }

    protected UrlMappingsHolder lookupUrlMappings() {
        try {
            return UrlMappingUtils.lookupUrlMappings(servletContext);
        } catch (Exception ignored) {
            // ignore, no app ctx in this case.
            return null;
        }
    }

    Map extractRequestParamsWithUrlMappingHolder(UrlMappingsHolder urlMappings, HttpServletRequest request) {
        Map params = new HashMap();
        try {
            UrlMappingInfo requestInfo = urlMappings.match(request.getRequestURI());
            if (requestInfo != null) {
                params.putAll(UrlMappingUtils.findAllParamsNotInUrlMappingKeywords(requestInfo.getParameters()));
            }
        } catch (UrlMappingException ulrMappingException) {
            logger.debug("Could not find urlMapping which matches: " + request.getRequestURI());
        }
        return params;
    }

    protected ModelAndView resolveViewOrForward(Exception ex, UrlMappingsHolder urlMappings, HttpServletRequest request,
                                                HttpServletResponse response, ModelAndView mv) {

        UrlMappingInfo info = matchStatusCode(ex, urlMappings);

        if (info != null) {
            Map params = extractRequestParamsWithUrlMappingHolder(urlMappings, request);
            if (params != null && !params.isEmpty()) {
                Map infoParams = info.getParameters();
                if (infoParams != null) {
                    params.putAll(info.getParameters());
                }
                info = new DefaultUrlMappingInfo(info, params, grailsApplication);
            }
        }

        try {
            if (info != null && info.getViewName() != null) {
                resolveView(request, info, mv);
            } else if (info != null && info.getControllerName() != null) {
                String uri = determineUri(request);
                if (!response.isCommitted()) {
                    if (response instanceof GrailsResponseMutator) {
                        // prevent further mutation of the request since an error page needs rendered instead
                        ((GrailsResponseMutator) response).deactivateResponseMutator();
                    }
                    forwardRequest(info, request, response, mv, uri);
                    // return an empty ModelAndView since the error handler has been processed
                    return new ModelAndView();
                }
            }
            return mv;
        } catch (Exception e) {
            LOG.error("Unable to render errors view: " + e.getMessage(), e);
            throw new GrailsRuntimeException(e);
        }
    }

    protected void forwardRequest(UrlMappingInfo info, HttpServletRequest request, HttpServletResponse response,
                                  ModelAndView mv, String uri) throws ServletException, IOException {
        info.configure(WebUtils.retrieveGrailsWebRequest());
        String forwardUrl = UrlMappingUtils.forwardRequestForUrlMappingInfo(
                request, response, info, mv.getModel(), true);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Matched URI [" + uri + "] to URL mapping [" + info +
                    "], forwarding to [" + forwardUrl + "] with response [" + response.getClass() + "]");
        }
    }

    protected String determineUri(HttpServletRequest request) {
        String uri = (String) request.getAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE);
        if (uri == null) {
            uri = request.getRequestURI();
        }
        return uri;
    }

    protected void resolveView(HttpServletRequest request, UrlMappingInfo info, ModelAndView mv) throws Exception {
        ViewResolver viewResolver = WebUtils.lookupViewResolver(servletContext);
        View v = UrlMappingUtils.resolveView(request, info, info.getViewName(), viewResolver);
        if (v != null) {
            mv.setView(v);
        }
    }

    protected UrlMappingInfo matchStatusCode(Exception ex, UrlMappingsHolder urlMappings) {
        UrlMappingInfo info = urlMappings.matchStatusCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        if (info == null) {
            info = urlMappings.matchStatusCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    getRootCause(ex));
        }
        if (info == null) {
            info = urlMappings.matchStatusCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        return info;
    }

    protected void logStackTrace(Exception e, HttpServletRequest request) {
        LOG.error(getRequestLogMessage(e, request), e);
    }

    protected Exception findWrappedException(Exception e) {
        if ((e instanceof InvokerInvocationException) || (e instanceof GrailsMVCException)) {
            Throwable t = getRootCause(e);
            if (t instanceof Exception) {
                e = (Exception) t;
            }
        }
        return e;
    }

    protected String getRequestLogMessage(String exceptionName, HttpServletRequest request, String message) {
        StringBuilder sb = new StringBuilder();

        sb.append(exceptionName)
                .append(" occurred when processing request: ")
                .append("[").append(request.getMethod().toUpperCase()).append("] ");

        if (request.getAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE) != null) {
            sb.append(request.getAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE));
        } else {
            sb.append(request.getRequestURI());
        }

        Config config = grailsApplication != null ? grailsApplication.getConfig() : null;
        final boolean shouldLogRequestParameters = config != null ? config.getProperty(Settings.SETTING_LOG_REQUEST_PARAMETERS, Boolean.class, Environment.getCurrent() == Environment.DEVELOPMENT) : false;

        if (shouldLogRequestParameters) {
            Enumeration<String> params = request.getParameterNames();

            if (params.hasMoreElements()) {
                String param;
                String[] values;
                int i;

                sb.append(" - parameters:");
                @SuppressWarnings("unchecked")
                List<String> blackList = (config.getProperty(Settings.SETTING_EXCEPTION_RESOLVER_PARAM_EXCLUDES, List.class, Collections.emptyList()));

                if (blackList == null) {
                    blackList = Collections.emptyList();
                }
                while (params.hasMoreElements()) {
                    param = params.nextElement();
                    values = request.getParameterValues(param);

                    if (values != null) {
                        for (i = 0; i < values.length; i++) {
                            sb.append(LINE_SEPARATOR).append(param).append(": ");

                            if (blackList.contains(param)) {
                                sb.append("***");
                            } else {
                                sb.append(values[i]);
                            }
                        }
                    }
                }
            }
        }

        sb.append(LINE_SEPARATOR);
        if (message != null) {
            sb.append(message).append(". ");
        }
        sb.append("Stacktrace follows:");

        return sb.toString();
    }

    protected void createStackFilterer() {
        try {
            Class filtererClass = grailsApplication.getConfig().getProperty(Settings.SETTING_LOGGING_STACKTRACE_FILTER_CLASS, Class.class, DefaultStackTraceFilterer.class);
            stackFilterer = BeanUtils.instantiateClass(filtererClass, StackTraceFilterer.class);
        } catch (Throwable t) {
            logger.error("Problem instantiating StackTracePrinter class, using default: " + t.getMessage());
            stackFilterer = new DefaultStackTraceFilterer();
        }
    }
}
