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
package org.grails.plugins.web.async

import java.security.Principal
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic

import jakarta.servlet.AsyncContext
import jakarta.servlet.DispatcherType
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.ServletConnection
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletInputStream
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletMapping
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import jakarta.servlet.http.HttpUpgradeHandler
import jakarta.servlet.http.Part

import org.springframework.util.LinkedCaseInsensitiveMap

/**
 * A copy of a request, taken as the request completed, which a task that outlives the request reads in its place.
 *
 * <p>The container recycles a request once it completes, so a task that read the request itself from then on
 * would fail. The copy holds the attributes, parameters, headers, locales and cookies of the request, its session
 * and what identifies it. Attributes a task sets stay with the copy. What only the live request can do, such as
 * reading the body, dispatching, processing asynchronously, creating a session or checking a role, is not
 * available.</p>
 *
 * @since 8.0.1
 */
@CompileStatic
class CompletedHttpServletRequest implements HttpServletRequest {

    private final Map<String, Object> attributes = new ConcurrentHashMap<>()
    private final Map<String, String[]> parameters
    private final Map<String, List<String>> headers
    private final List<Locale> locales
    private final Cookie[] cookies
    private final HttpSession session
    private final ServletContext servletContext
    private final Principal userPrincipal
    private final DispatcherType dispatcherType
    private final HttpServletMapping httpServletMapping
    private final ServletConnection servletConnection
    private final String authType
    private final String contentType
    private final long contentLength
    private final String method
    private final String requestURI
    private final String requestURL
    private final String queryString
    private final String contextPath
    private final String servletPath
    private final String pathInfo
    private final String pathTranslated
    private final String protocol
    private final String scheme
    private final String serverName
    private final int serverPort
    private final boolean secure
    private final String remoteAddr
    private final String remoteHost
    private final int remotePort
    private final String localName
    private final String localAddr
    private final int localPort
    private final String remoteUser
    private final String requestedSessionId
    private final boolean requestedSessionIdValid
    private final boolean requestedSessionIdFromCookie
    private final boolean requestedSessionIdFromURL
    private final String requestId
    private final String protocolRequestId
    private volatile String characterEncoding

    private CompletedHttpServletRequest(HttpServletRequest request) {
        for (String name : Collections.list(request.attributeNames)) {
            Object value = request.getAttribute(name)
            if (value != null) {
                attributes.put(name, value)
            }
        }
        Map<String, String[]> parameters = new LinkedHashMap<>()
        for (Map.Entry<String, String[]> parameter : request.parameterMap.entrySet()) {
            parameters.put(parameter.key, copyValues(parameter.value))
        }
        this.parameters = Collections.unmodifiableMap(parameters)
        Map<String, List<String>> headers = new LinkedCaseInsensitiveMap<>(Locale.ENGLISH)
        for (String name : Collections.list(request.headerNames)) {
            headers.put(name, Collections.unmodifiableList(Collections.list(request.getHeaders(name))))
        }
        this.headers = Collections.unmodifiableMap(headers)
        this.locales = Collections.unmodifiableList(Collections.list(request.locales))
        this.cookies = copyCookies(request.cookies)
        this.session = request.getSession(false)
        this.servletContext = request.servletContext
        this.userPrincipal = request.userPrincipal
        this.dispatcherType = request.dispatcherType
        this.httpServletMapping = request.httpServletMapping
        this.servletConnection = request.servletConnection
        this.authType = request.authType
        this.characterEncoding = request.characterEncoding
        this.contentType = request.contentType
        this.contentLength = request.contentLengthLong
        this.method = request.method
        this.requestURI = request.requestURI
        this.requestURL = request.requestURL?.toString()
        this.queryString = request.queryString
        this.contextPath = request.contextPath
        this.servletPath = request.servletPath
        this.pathInfo = request.pathInfo
        this.pathTranslated = request.pathTranslated
        this.protocol = request.protocol
        this.scheme = request.scheme
        this.serverName = request.serverName
        this.serverPort = request.serverPort
        this.secure = request.secure
        this.remoteAddr = request.remoteAddr
        this.remoteHost = request.remoteHost
        this.remotePort = request.remotePort
        this.localName = request.localName
        this.localAddr = request.localAddr
        this.localPort = request.localPort
        this.remoteUser = request.remoteUser
        this.requestedSessionId = request.requestedSessionId
        this.requestedSessionIdValid = request.requestedSessionIdValid
        this.requestedSessionIdFromCookie = request.requestedSessionIdFromCookie
        this.requestedSessionIdFromURL = request.requestedSessionIdFromURL
        this.requestId = request.requestId
        this.protocolRequestId = request.protocolRequestId
    }

    /**
     * Copies a request that is completing. It has to be called before the container recycles the request.
     *
     * @param request the request
     * @return the copy
     */
    static CompletedHttpServletRequest copyOf(HttpServletRequest request) {
        return new CompletedHttpServletRequest(request)
    }

    private static String[] copyValues(String[] values) {
        return values == null ? null : Arrays.copyOf(values, values.length)
    }

    private static Cookie[] copyCookies(Cookie[] cookies) {
        if (cookies == null) {
            return null
        }
        Cookie[] copies = new Cookie[cookies.length]
        for (int i = 0; i < cookies.length; i++) {
            copies[i] = (Cookie) cookies[i].clone()
        }
        return copies
    }

    private static IllegalStateException unavailable(String operation) {
        return new IllegalStateException("The request has completed, so a task that outlives it cannot call $operation".toString())
    }

    @Override
    Object getAttribute(String name) {
        return attributes.get(name)
    }

    @Override
    Enumeration<String> getAttributeNames() {
        return Collections.enumeration(new ArrayList<String>(attributes.keySet()))
    }

    @Override
    void setAttribute(String name, Object value) {
        if (value == null) {
            attributes.remove(name)
        }
        else {
            attributes.put(name, value)
        }
    }

    @Override
    void removeAttribute(String name) {
        attributes.remove(name)
    }

    @Override
    String getCharacterEncoding() {
        return characterEncoding
    }

    @Override
    void setCharacterEncoding(String encoding) {
        this.characterEncoding = encoding
    }

    @Override
    int getContentLength() {
        return contentLength > Integer.MAX_VALUE ? -1 : (int) contentLength
    }

    @Override
    long getContentLengthLong() {
        return contentLength
    }

    @Override
    String getContentType() {
        return contentType
    }

    @Override
    ServletInputStream getInputStream() {
        throw unavailable('getInputStream()')
    }

    @Override
    BufferedReader getReader() {
        throw unavailable('getReader()')
    }

    @Override
    String getParameter(String name) {
        String[] values = parameters.get(name)
        return values ? values[0] : null
    }

    @Override
    Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameters.keySet())
    }

    @Override
    String[] getParameterValues(String name) {
        return copyValues(parameters.get(name))
    }

    @Override
    Map<String, String[]> getParameterMap() {
        return parameters
    }

    @Override
    String getProtocol() {
        return protocol
    }

    @Override
    String getScheme() {
        return scheme
    }

    @Override
    String getServerName() {
        return serverName
    }

    @Override
    int getServerPort() {
        return serverPort
    }

    @Override
    String getRemoteAddr() {
        return remoteAddr
    }

    @Override
    String getRemoteHost() {
        return remoteHost
    }

    @Override
    int getRemotePort() {
        return remotePort
    }

    @Override
    String getLocalName() {
        return localName
    }

    @Override
    String getLocalAddr() {
        return localAddr
    }

    @Override
    int getLocalPort() {
        return localPort
    }

    @Override
    Locale getLocale() {
        return locales ? locales[0] : Locale.getDefault()
    }

    @Override
    Enumeration<Locale> getLocales() {
        return Collections.enumeration(locales ?: [Locale.getDefault()])
    }

    @Override
    boolean isSecure() {
        return secure
    }

    @Override
    RequestDispatcher getRequestDispatcher(String path) {
        throw unavailable('getRequestDispatcher(String)')
    }

    @Override
    ServletContext getServletContext() {
        return servletContext
    }

    @Override
    AsyncContext startAsync() {
        throw unavailable('startAsync()')
    }

    @Override
    AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) {
        throw unavailable('startAsync(ServletRequest, ServletResponse)')
    }

    @Override
    boolean isAsyncStarted() {
        return false
    }

    @Override
    boolean isAsyncSupported() {
        return false
    }

    @Override
    AsyncContext getAsyncContext() {
        throw unavailable('getAsyncContext()')
    }

    @Override
    DispatcherType getDispatcherType() {
        return dispatcherType
    }

    @Override
    String getRequestId() {
        return requestId
    }

    @Override
    String getProtocolRequestId() {
        return protocolRequestId
    }

    @Override
    ServletConnection getServletConnection() {
        return servletConnection
    }

    @Override
    String getAuthType() {
        return authType
    }

    @Override
    Cookie[] getCookies() {
        return copyCookies(cookies)
    }

    @Override
    long getDateHeader(String name) {
        String value = getHeader(name)
        if (value == null) {
            return -1L
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }
        catch (DateTimeParseException e) {
            throw new IllegalArgumentException("The header [$name] holds [$value], which is not a date".toString(), e)
        }
    }

    @Override
    String getHeader(String name) {
        List<String> values = headers.get(name)
        return values ? values[0] : null
    }

    @Override
    Enumeration<String> getHeaders(String name) {
        return Collections.enumeration(headers.get(name) ?: Collections.<String>emptyList())
    }

    @Override
    Enumeration<String> getHeaderNames() {
        return Collections.enumeration(headers.keySet())
    }

    @Override
    int getIntHeader(String name) {
        String value = getHeader(name)
        return value == null ? -1 : Integer.parseInt(value)
    }

    @Override
    HttpServletMapping getHttpServletMapping() {
        return httpServletMapping
    }

    @Override
    String getMethod() {
        return method
    }

    @Override
    String getPathInfo() {
        return pathInfo
    }

    @Override
    String getPathTranslated() {
        return pathTranslated
    }

    @Override
    String getContextPath() {
        return contextPath
    }

    @Override
    String getQueryString() {
        return queryString
    }

    @Override
    String getRemoteUser() {
        return remoteUser
    }

    @Override
    boolean isUserInRole(String role) {
        throw unavailable('isUserInRole(String)')
    }

    @Override
    Principal getUserPrincipal() {
        return userPrincipal
    }

    @Override
    String getRequestedSessionId() {
        return requestedSessionId
    }

    @Override
    String getRequestURI() {
        return requestURI
    }

    @Override
    StringBuffer getRequestURL() {
        return requestURL == null ? null : new StringBuffer(requestURL)
    }

    @Override
    String getServletPath() {
        return servletPath
    }

    @Override
    HttpSession getSession(boolean create) {
        if (session == null && create) {
            throw unavailable('getSession(true) to create a session')
        }
        return session
    }

    @Override
    HttpSession getSession() {
        return getSession(true)
    }

    @Override
    String changeSessionId() {
        throw unavailable('changeSessionId()')
    }

    @Override
    boolean isRequestedSessionIdValid() {
        return requestedSessionIdValid
    }

    @Override
    boolean isRequestedSessionIdFromCookie() {
        return requestedSessionIdFromCookie
    }

    @Override
    boolean isRequestedSessionIdFromURL() {
        return requestedSessionIdFromURL
    }

    @Override
    boolean authenticate(HttpServletResponse response) {
        throw unavailable('authenticate(HttpServletResponse)')
    }

    @Override
    void login(String username, String password) {
        throw unavailable('login(String, String)')
    }

    @Override
    void logout() {
        throw unavailable('logout()')
    }

    @Override
    Collection<Part> getParts() {
        throw unavailable('getParts()')
    }

    @Override
    Part getPart(String name) {
        throw unavailable('getPart(String)')
    }

    @Override
    <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) {
        throw unavailable('upgrade(Class)')
    }
}
