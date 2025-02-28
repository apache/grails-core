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
package org.grails.web.util;

import grails.config.Config;
import grails.core.GrailsApplication;
import grails.util.GrailsStringUtils;
import grails.util.GrailsWebUtil;
import grails.web.mime.MimeType;
import grails.web.servlet.mvc.GrailsParameterMap;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.servlet.view.CompositeViewResolver;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.WebRequestInterceptor;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.handler.WebRequestHandlerInterceptorAdapter;
import org.springframework.web.servlet.view.UrlBasedViewResolver;
import org.springframework.web.util.UrlPathHelper;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;

/**
 * Utility methods to access commons objects and perform common
 * web related functions for the internal framework.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class WebUtils extends org.springframework.web.util.WebUtils {

    public static final char SLASH = '/';
    public static final String ENABLE_FILE_EXTENSIONS = "grails.mime.file.extensions";
    public static final String DISPATCH_ACTION_PARAMETER = "_action_";
    public static final String SEND_ALLOW_HEADER_FOR_INVALID_HTTP_METHOD = "grails.http.invalid.method.allow.header";
    public static final String LAYOUT_ATTRIBUTE = "org.grails.layout.name";
    public static final String RENDERING_VIEW = "org.grails.rendering.view";
    public static final String GRAILS_DISPATCH_EXTENSION = ".dispatch";
    public static final String GRAILS_SERVLET_PATH = "/grails";
    public static final String EXCEPTION_ATTRIBUTE = "exception";
    public static final String ASYNC_REQUEST_URI_ATTRIBUTE = "jakarta.servlet.async.request_uri";

    public static final int SC_METHOD_NOT_ALLOWED = HttpServletResponse.SC_METHOD_NOT_ALLOWED;

    public static ViewResolver lookupViewResolver(ServletContext servletContext) {
        WebApplicationContext wac = WebApplicationContextUtils
                .getRequiredWebApplicationContext(servletContext);
        return lookupViewResolver(wac);
    }

    public static ViewResolver lookupViewResolver(ApplicationContext wac) {
        final CompositeViewResolver viewResolver = wac.getBean(CompositeViewResolver.BEAN_NAME, CompositeViewResolver.class);

        return new ViewResolver() {
            @Override
            public View resolveViewName(String viewName, Locale locale) throws Exception {
                return viewResolver.resolveView(viewName, locale);
            }
        };
    }

    /**
     * Looks up all of the HandlerInterceptor instances registered for the application
     *
     * @param servletContext The ServletContext instance
     * @return An array of HandlerInterceptor instances
     */
    public static HandlerInterceptor[] lookupHandlerInterceptors(ServletContext servletContext) {
        WebApplicationContext wac = WebApplicationContextUtils.getRequiredWebApplicationContext(servletContext);

        final Collection<HandlerInterceptor> allHandlerInterceptors = new ArrayList<HandlerInterceptor>();

        WebRequestInterceptor[] webRequestInterceptors = lookupWebRequestInterceptors(servletContext);
        for (WebRequestInterceptor webRequestInterceptor : webRequestInterceptors) {
            allHandlerInterceptors.add(new WebRequestHandlerInterceptorAdapter(webRequestInterceptor));
        }
        final Collection<HandlerInterceptor> handlerInterceptors = wac.getBeansOfType(HandlerInterceptor.class).values();

        allHandlerInterceptors.addAll(handlerInterceptors);
        return allHandlerInterceptors.toArray(new HandlerInterceptor[allHandlerInterceptors.size()]);
    }

    /**
     * Looks up all of the WebRequestInterceptor instances registered with the application
     *
     * @param servletContext The ServletContext instance
     * @return An array of WebRequestInterceptor instances
     */
    public static WebRequestInterceptor[] lookupWebRequestInterceptors(ServletContext servletContext) {
        WebApplicationContext wac = WebApplicationContextUtils.getRequiredWebApplicationContext(servletContext);

        final Collection<WebRequestInterceptor> webRequestInterceptors = wac.getBeansOfType(WebRequestInterceptor.class).values();
        return webRequestInterceptors.toArray(new WebRequestInterceptor[webRequestInterceptors.size()]);
    }

    /**
     * The Grails dispatch servlet maps URIs like /app/grails/example/index.dispatch. This method infers the
     * controller URI for the dispatch URI so that /app/grails/example/index.dispatch becomes /app/example/index
     *
     * @param request The request
     */
    public static String getRequestURIForGrailsDispatchURI(HttpServletRequest request) {
        UrlPathHelper pathHelper = new UrlPathHelper();
        if (request.getRequestURI().endsWith(GRAILS_DISPATCH_EXTENSION)) {
            String path = pathHelper.getPathWithinApplication(request);
            if (path.startsWith(GRAILS_SERVLET_PATH)) {
                path = path.substring(GRAILS_SERVLET_PATH.length(),path.length());
            }
            return path.substring(0, path.length() - GRAILS_DISPATCH_EXTENSION.length());
        }
        return pathHelper.getPathWithinApplication(request);
    }

    /**
     * Looks up the GrailsApplication instance
     *
     * @return The GrailsApplication instance
     */
    public static GrailsApplication lookupApplication(ServletContext servletContext) {
        WebApplicationContext wac = WebApplicationContextUtils.getRequiredWebApplicationContext(servletContext);
        return (GrailsApplication)wac.getBean(GrailsApplication.APPLICATION_ID);
    }

    /**
     * Looks up the GrailsApplication instance
     *
     * @return The GrailsApplication instance
     */
    public static GrailsApplication findApplication(ServletContext servletContext) {
        ApplicationContext wac = findApplicationContext(servletContext);
        if(wac != null) {
            return (GrailsApplication)wac.getBean(GrailsApplication.APPLICATION_ID);
        }
        return null;
    }


    /**
     * Locates the ApplicationContext, returns null if not found
     * @param servletContext The servlet context
     * @return The ApplicationContext
     */
    public static ApplicationContext findApplicationContext(ServletContext servletContext) {
        if(servletContext == null) {
            return ContextLoader.getCurrentWebApplicationContext();
        }
        return WebApplicationContextUtils.getWebApplicationContext(servletContext);
    }

    /**
     * Resolves a view for the given view name and controller name
     * @param request The request
     * @param viewName The view name
     * @param controllerName The controller name
     * @param viewResolver The resolver
     * @return A View or null
     * @throws Exception Thrown if an error occurs
     */
    public static View resolveView(HttpServletRequest request, String viewName, String controllerName, ViewResolver viewResolver) throws Exception {
        GrailsWebRequest webRequest = GrailsWebRequest.lookup(request);
        Locale locale = webRequest != null ? webRequest.getLocale() : Locale.getDefault() ;
        return viewResolver.resolveViewName(addViewPrefix(viewName, controllerName), locale);
    }

    /**
     * @deprecated Does not take into account the url converter
     */
    @Deprecated
    public static String addViewPrefix(String viewName) {
        GrailsWebRequest webRequest = GrailsWebRequest.lookup();
        return addViewPrefix(viewName, webRequest != null ? webRequest.getControllerName() : null);
    }

    public static String addViewPrefix(String viewName, String controllerName) {
        if (!viewName.startsWith(String.valueOf(SLASH))) {
            if(viewName.startsWith(UrlBasedViewResolver.REDIRECT_URL_PREFIX) || viewName.startsWith(UrlBasedViewResolver.FORWARD_URL_PREFIX)) {
                return viewName;
            }
            StringBuilder buf = new StringBuilder();
            buf.append(SLASH);
            if (controllerName != null) {
                buf.append(controllerName).append(SLASH);
            }
            buf.append(viewName);
            return buf.toString();
        }
        return viewName;
    }

    public static Map<String, Object> exposeRequestAttributesAndReturnOldValues(HttpServletRequest request, Map<String, ?> attributes) {
        Assert.notNull(request, "Request must not be null");
        Assert.notNull(attributes, "Attributes Map must not be null");
        Map<String, Object> originalValues = new HashMap<String, Object>();
        for (Map.Entry<String, ?> entry : attributes.entrySet()) {
            String name = entry.getKey();
            Object current = request.getAttribute(name);
            request.setAttribute(name, entry.getValue());
            if (current != null) {
                originalValues.put(name, current);
            }
        }

        return originalValues;
    }

    public static void cleanupIncludeRequestAttributes(HttpServletRequest request, Map<String, Object> toRestore) {
        request.removeAttribute(INCLUDE_REQUEST_URI_ATTRIBUTE);
        request.removeAttribute(INCLUDE_CONTEXT_PATH_ATTRIBUTE);
        request.removeAttribute(INCLUDE_SERVLET_PATH_ATTRIBUTE);
        request.removeAttribute(INCLUDE_PATH_INFO_ATTRIBUTE);
        request.removeAttribute(INCLUDE_QUERY_STRING_ATTRIBUTE);

        for (Map.Entry<String, Object> entry : toRestore.entrySet()) {
            request.setAttribute(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Expose the current request URI and paths as {@link jakarta.servlet.http.HttpServletRequest}
     * attributes under the keys defined in the Servlet 2.4 specification,
     * for containers that implement 2.3 or an earlier version of the Servlet API:
     * <code>jakarta.servlet.forward.request_uri</code>,
     * <code>jakarta.servlet.forward.context_path</code>,
     * <code>jakarta.servlet.forward.servlet_path</code>,
     * <code>jakarta.servlet.forward.path_info</code>,
     * <code>jakarta.servlet.forward.query_string</code>.
     * <p>Does not override values if already present, to not cause conflicts
     * with the attributes exposed by Servlet 2.4+ containers themselves.
     * @param request current servlet request
     */

    public static void exposeIncludeRequestAttributes(HttpServletRequest request) {
        exposeRequestAttributeIfNotPresent(request, INCLUDE_REQUEST_URI_ATTRIBUTE, request.getRequestURI());
        exposeRequestAttributeIfNotPresent(request, INCLUDE_CONTEXT_PATH_ATTRIBUTE, request.getContextPath());
        exposeRequestAttributeIfNotPresent(request, INCLUDE_SERVLET_PATH_ATTRIBUTE, request.getServletPath());
        exposeRequestAttributeIfNotPresent(request, INCLUDE_PATH_INFO_ATTRIBUTE, request.getPathInfo());
        exposeRequestAttributeIfNotPresent(request, INCLUDE_QUERY_STRING_ATTRIBUTE, request.getQueryString());
    }

    /**
     * Expose the specified request attribute if not already present.
     * @param request current servlet request
     * @param name the name of the attribute
     * @param value the suggested value of the attribute
     */
    private static void exposeRequestAttributeIfNotPresent(ServletRequest request, String name, Object value) {
        if (request.getAttribute(name) == null) {
            request.setAttribute(name, value);
        }
    }

    /**
     * Takes a query string and returns the results as a map where the values are either a single entry or a list of values
     *
     * @param queryString The query String
     * @return A map
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Map<String, Object> fromQueryString(String queryString) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (queryString.startsWith("?")) queryString = queryString.substring(1);

        String[] pairs = queryString.split("&");

        for (String pair : pairs) {
            int i = pair.indexOf('=');
            if (i > -1) {
                try {
                    String name = URLDecoder.decode(pair.substring(0, i), "UTF-8");
                    String value = URLDecoder.decode(pair.substring(i+1, pair.length()), "UTF-8");

                    Object current = result.get(name);
                    if (current instanceof List) {
                        ((List)current).add(value);
                    }
                    else if (current != null) {
                        List multi = new ArrayList();
                        multi.add(current);
                        multi.add(value);
                        result.put(name, multi);
                    }
                    else {
                        result.put(name, value);
                    }
                } catch (UnsupportedEncodingException e) {
                    // ignore
                }
            }
        }

        return result;
    }

    /**
     * Converts the given params into a query string started with ?
     * @param params The params
     * @param encoding The encoding to use
     * @return The query string
     * @throws UnsupportedEncodingException If the given encoding is not supported
     */
    @SuppressWarnings("rawtypes")
    public static String toQueryString(Map params, String encoding) throws UnsupportedEncodingException {
        if (encoding == null) encoding = "UTF-8";
        StringBuilder queryString = new StringBuilder("?");

        for (Iterator i = params.entrySet().iterator(); i.hasNext();) {
            Map.Entry entry = (Map.Entry) i.next();
            boolean hasMore = i.hasNext();
            boolean wasAppended = appendEntry(entry, queryString, encoding, "");
            if (hasMore && wasAppended) queryString.append('&');
        }
        return queryString.toString();
    }

    /**
     * Converts the given parameters to a query string using the default  UTF-8 encoding
     * @param parameters The parameters
     * @return The query string
     * @throws UnsupportedEncodingException If UTF-8 encoding is not supported
     */
    @SuppressWarnings("rawtypes")
    public static String toQueryString(Map parameters) throws UnsupportedEncodingException {
        return toQueryString(parameters, "UTF-8");
    }

    @SuppressWarnings("rawtypes")
    private static boolean appendEntry(Map.Entry entry, StringBuilder queryString, String encoding, String path) throws UnsupportedEncodingException {
        String name = entry.getKey().toString();
        Object value = entry.getValue();

        if (name.indexOf(".") > -1 && (value instanceof GrailsParameterMap)) return false; // multi-d params handled by recursion
        else if (value == null) value = "";
        else if (value instanceof GrailsParameterMap) {
            GrailsParameterMap child = (GrailsParameterMap)value;
            Set nestedEntrySet = child.entrySet();
            for (Iterator i = nestedEntrySet.iterator(); i.hasNext();) {
                Map.Entry childEntry = (Map.Entry) i.next();
                appendEntry(childEntry, queryString, encoding, entry.getKey().toString() + '.');
                boolean hasMore = i.hasNext();
                if (hasMore) queryString.append('&');
            }
        }
        else {
            queryString.append(URLEncoder.encode(path + name, encoding))
                       .append('=')
                       .append(URLEncoder.encode(value.toString(), encoding));
        }
        return true;
    }

    /**
     * Obtains the format from the URI. The format is the string following the . file extension in the last token of the URI.
     * If nothing comes after the ".", this method assumes that there is no format and returns <code>null</code>.
     *
     * @param uri The URI
     * @return The format or null if none
     */
    public static String getFormatFromURI(String uri) {
        return getFormatFromURI(uri, MimeType.getConfiguredMimeTypes());
    }

    /**
     * Obtains the format from the URI. The format is the string following the . file extension in the last token of the URI.
     * If nothing comes after the ".", this method assumes that there is no format and returns <code>null</code>.
     *
     * @param uri The URI
     * @param mimeTypes The configured mime types
     * @return The format or null if none
     */
    public static String getFormatFromURI(String uri, MimeType[] mimeTypes) {
        if (uri.endsWith("/")) {
            return null;
        }

        int idx = uri.lastIndexOf('/');
        if (idx > -1) {
            String lastToken = uri.substring(idx+1, uri.length());
            idx = lastToken.lastIndexOf('.');
            if (idx > -1 && idx != lastToken.length() - 1) {
                String extension =  lastToken.substring(idx+1, lastToken.length());
                if (mimeTypes != null) {
                    for (MimeType mimeType : mimeTypes) {
                        if (mimeType.getExtension().equals(extension)) return extension;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns the value of the "grails.mime.file.extensions" setting configured in application.groovy
     *
     * @return true if file extensions are enabled
     */
    @SuppressWarnings("rawtypes")
    public static boolean areFileExtensionsEnabled() {
        Config config = GrailsWebUtil.currentApplication().getConfig();
        return config.getProperty(ENABLE_FILE_EXTENSIONS, Boolean.class, true);
    }

    /**
     * Returns the GrailsWebRequest associated with the current request.
     * This is the preferred means of accessing the GrailsWebRequest
     * instance. If the exception is undesired, you can use
     * RequestContextHolder.getRequestAttributes() instead.
     * @throws IllegalStateException if this is called outside of a
     * request.
     */
    public static GrailsWebRequest retrieveGrailsWebRequest() {
        RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
        if(attributes instanceof GrailsWebRequest) {
            return (GrailsWebRequest)attributes;
        }
        return null;
    }

    /**
     * Helper method to store the given GrailsWebRequest for the current
     * request. Ensures consistency between RequestContextHolder and the
     * relevant request attribute. This is the preferred means of updating
     * the current web request.
     */
    public static void storeGrailsWebRequest(GrailsWebRequest webRequest) {
        RequestContextHolder.setRequestAttributes(webRequest);
        webRequest.getRequest().setAttribute(GrailsApplicationAttributes.WEB_REQUEST, webRequest);
    }

    /**
     * Removes any GrailsWebRequest instance from the current request.
     */
    public static void clearGrailsWebRequest() {
        RequestAttributes reqAttrs = RequestContextHolder.getRequestAttributes();
        if (reqAttrs != null) {
            // First remove the web request from the HTTP request attributes.
            GrailsWebRequest webRequest = (GrailsWebRequest) reqAttrs;
            webRequest.getRequest().removeAttribute(GrailsApplicationAttributes.WEB_REQUEST);

            // Now remove it from RequestContextHolder.
            RequestContextHolder.resetRequestAttributes();
        }
    }

    /**
     * Obtains the forwardURI from the request, since Grails uses a forwarding technique for URL mappings. The actual
     * request URI is held within a request attribute
     *
     * @param request The request
     * @return The forward URI
     */
    public static String getForwardURI(HttpServletRequest request) {
        String result = (String) request.getAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE);
        if (GrailsStringUtils.isBlank(result)) result = request.getRequestURI();
        return result;
    }

    /**
     * Check whether the given request is a forward request
     *
     * @param request The request
     * @return True if it is a forward request
     */
    public static boolean isForward(HttpServletRequest request) {
        return request.getAttribute(FORWARD_REQUEST_URI_ATTRIBUTE) != null;
    }

    /**
     * Check whether the given request is a forward request
     *
     * @param request The request
     * @return True if it is a forward request
     */
    public static boolean isAsync(HttpServletRequest request) {
        return request.getAttribute(ASYNC_REQUEST_URI_ATTRIBUTE) != null;
    }

    /**
     * Check whether the given request is a forward request
     *
     * @param request The request
     * @return True if it is a forward request
     */
    public static boolean isError(HttpServletRequest request) {
        return request.getAttribute(ERROR_STATUS_CODE_ATTRIBUTE) != null;
    }
    /**
     * Check whether the given request is an include request
     *
     * @param request The request
     * @return True if it is an include request
     */
    public static boolean isInclude(HttpServletRequest request) {
        return request.getAttribute(INCLUDE_REQUEST_URI_ATTRIBUTE) != null;
    }

    /**
     * Check whether the given request is an include or forward request
     *
     * @param request The request
     * @return True if it is an include or forward request
     */
    public static boolean isForwardOrInclude(HttpServletRequest request) {
        return isForward(request) || isInclude(request);
    }

}
