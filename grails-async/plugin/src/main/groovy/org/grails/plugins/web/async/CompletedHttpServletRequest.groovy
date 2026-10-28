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

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.Principal
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic

import jakarta.servlet.AsyncContext
import jakarta.servlet.DispatcherType
import jakarta.servlet.ReadListener
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.ServletConnection
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletInputStream
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletRequestWrapper
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletMapping
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import jakarta.servlet.http.HttpUpgradeHandler
import jakarta.servlet.http.MappingMatch
import jakarta.servlet.http.Part

import org.springframework.util.LinkedCaseInsensitiveMap

import org.grails.web.util.GrailsApplicationAttributes

/**
 * A copy of a request, taken as the request completed, which a task that outlives the request reads in its place.
 *
 * <p>The container recycles a request once it completes, so a task that read the request itself from then on
 * would fail. One copy is taken as the request completes, and each task reads it through a {@link #newView view}
 * of its own, so each has the part of the body nothing had read, served through one stream or reader whose
 * position persists, as the live request serves it. The copy holds the attributes, parameters, headers, locales
 * and cookies of the request, its session and what identifies it. Attributes a task sets stay with the copy,
 * shared by every view, as they are shared on the live request. The request itself and the web request of the
 * request thread are not carried over, so the copy holds nothing the container recycles. What only the live
 * request can do, such as dispatching, processing asynchronously, creating a session or checking a role, is not
 * available.</p>
 *
 * @since 8.0.1
 */
@CompileStatic
class CompletedHttpServletRequest implements HttpServletRequest {

    private static final byte[] NO_BODY = new byte[0]
    private static final List<String> DATE_HEADER_FORMS = List.of(
            'EEE, dd MMM yyyy HH:mm:ss zzz',
            'EEEE, dd-MMM-yy HH:mm:ss zzz',
            'EEE MMM dd HH:mm:ss yyyy')

    private final Map<String, Object> attributes
    private final byte[] body
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
    private ServletInputStream inputStream
    private BufferedReader reader

    private CompletedHttpServletRequest(HttpServletRequest request) {
        this.attributes = new ConcurrentHashMap<>()
        for (String name : Collections.list(request.attributeNames)) {
            Object value = request.getAttribute(name)
            // Not the request itself nor the web request holding it, so the copy holds nothing the container recycles
            if (value != null && !value.is(request) && GrailsApplicationAttributes.WEB_REQUEST != name) {
                attributes.put(name, value)
            }
        }
        this.parameters = Collections.unmodifiableMap(copyParameters(request))
        // After the parameters: parsing them consumes a form body, on the live request too
        this.body = copyBody(request)
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
        // Values only: the container's own objects can reference the request it recycles
        this.httpServletMapping = copyMapping(request.httpServletMapping)
        this.servletConnection = copyConnection(request.servletConnection)
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

    // Field by field: the getters serve tasks, so some, such as getSession and getLocales, do not return the field
    private CompletedHttpServletRequest(CompletedHttpServletRequest copy) {
        this.attributes = copy.@attributes
        this.body = copy.@body
        this.parameters = copy.@parameters
        this.headers = copy.@headers
        this.locales = copy.@locales
        this.cookies = copy.@cookies
        this.session = copy.@session
        this.servletContext = copy.@servletContext
        this.userPrincipal = copy.@userPrincipal
        this.dispatcherType = copy.@dispatcherType
        this.httpServletMapping = copy.@httpServletMapping
        this.servletConnection = copy.@servletConnection
        this.authType = copy.@authType
        this.characterEncoding = copy.@characterEncoding
        this.contentType = copy.@contentType
        this.contentLength = copy.@contentLength
        this.method = copy.@method
        this.requestURI = copy.@requestURI
        this.requestURL = copy.@requestURL
        this.queryString = copy.@queryString
        this.contextPath = copy.@contextPath
        this.servletPath = copy.@servletPath
        this.pathInfo = copy.@pathInfo
        this.pathTranslated = copy.@pathTranslated
        this.protocol = copy.@protocol
        this.scheme = copy.@scheme
        this.serverName = copy.@serverName
        this.serverPort = copy.@serverPort
        this.secure = copy.@secure
        this.remoteAddr = copy.@remoteAddr
        this.remoteHost = copy.@remoteHost
        this.remotePort = copy.@remotePort
        this.localName = copy.@localName
        this.localAddr = copy.@localAddr
        this.localPort = copy.@localPort
        this.remoteUser = copy.@remoteUser
        this.requestedSessionId = copy.@requestedSessionId
        this.requestedSessionIdValid = copy.@requestedSessionIdValid
        this.requestedSessionIdFromCookie = copy.@requestedSessionIdFromCookie
        this.requestedSessionIdFromURL = copy.@requestedSessionIdFromURL
        this.requestId = copy.@requestId
        this.protocolRequestId = copy.@protocolRequestId
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

    /**
     * A view of the copy for one task. Every view shares the copied request, including the attributes, and reads
     * the one copied body through a stream or reader of its own, so what one task reads is still there for the next.
     *
     * @return the view
     */
    CompletedHttpServletRequest newView() {
        return new CompletedHttpServletRequest(this)
    }

    private static String[] copyValues(String[] values) {
        return values == null ? null : Arrays.copyOf(values, values.length)
    }

    private static Map<String, String[]> copyParameters(HttpServletRequest request) {
        ServletRequest source = request
        while (true) {
            try {
                Map<String, String[]> parameters = new LinkedHashMap<>()
                for (Map.Entry<String, String[]> parameter : source.parameterMap.entrySet()) {
                    parameters.put(parameter.key, copyValues(parameter.value))
                }
                return parameters
            }
            catch (RuntimeException failure) {
                if (source instanceof ServletRequestWrapper) {
                    // A multipart wrapper resolving lazily parses the body for its parameters, which a request
                    // that is completing no longer offers, so the parameters come from the request it wraps
                    source = ((ServletRequestWrapper) source).request
                }
                else {
                    throw failure
                }
            }
        }
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

    private static byte[] copyBody(HttpServletRequest request) {
        try {
            InputStream remaining = request.inputStream
            return remaining == null ? NO_BODY : remaining.readAllBytes()
        }
        catch (IllegalStateException ignored) {
            // The body was being read through getReader, so what remains of it is there
            return copyBodyOfReader(request)
        }
        catch (IOException ignored) {
            return NO_BODY
        }
    }

    private static byte[] copyBodyOfReader(HttpServletRequest request) {
        try {
            BufferedReader reader = request.reader
            return reader == null ? NO_BODY : reader.text.getBytes(charset(request.characterEncoding))
        }
        catch (IllegalStateException | IOException ignored) {
            return NO_BODY
        }
    }

    private static Charset charset(String encoding) {
        if (encoding == null) {
            // The servlet default when no encoding is specified
            return StandardCharsets.ISO_8859_1
        }
        try {
            return Charset.forName(encoding)
        }
        catch (IllegalArgumentException ignored) {
            return StandardCharsets.ISO_8859_1
        }
    }

    private static HttpServletMapping copyMapping(HttpServletMapping mapping) {
        return mapping == null ? null : new CopiedHttpServletMapping(mapping)
    }

    private static ServletConnection copyConnection(ServletConnection connection) {
        return connection == null ? null : new CopiedServletConnection(connection)
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
    synchronized ServletInputStream getInputStream() {
        if (reader != null) {
            throw new IllegalStateException(
                    'getReader() has already been called, so the body is not available as a stream, as on the live request')
        }
        if (inputStream == null) {
            inputStream = new CopiedServletInputStream(body)
        }
        return inputStream
    }

    @Override
    synchronized BufferedReader getReader() {
        if (inputStream != null) {
            throw new IllegalStateException(
                    'getInputStream() has already been called, so the body is not available as a reader, as on the live request')
        }
        if (reader == null) {
            reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), charset(characterEncoding)))
        }
        return reader
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
        for (String form : DATE_HEADER_FORMS) {
            SimpleDateFormat parser = new SimpleDateFormat(form, Locale.US)
            parser.timeZone = TimeZone.getTimeZone('GMT')
            try {
                return parser.parse(value).time
            }
            catch (ParseException ignored) {
                // An HTTP date can be RFC 1123, RFC 850 or asctime, so every form is tried
            }
        }
        throw new IllegalArgumentException("The header [$name] holds [$value], which is not a date".toString())
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

    private static final class CopiedServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream body

        private CopiedServletInputStream(byte[] body) {
            this.body = new ByteArrayInputStream(body)
        }

        @Override
        boolean isFinished() {
            return body.available() == 0
        }

        @Override
        boolean isReady() {
            return true
        }

        @Override
        void setReadListener(ReadListener readListener) {
            throw unavailable('setReadListener(ReadListener)')
        }

        @Override
        int read() {
            return body.read()
        }

        @Override
        int read(byte[] destination, int offset, int length) {
            return body.read(destination, offset, length)
        }

        @Override
        int available() {
            return body.available()
        }
    }

    private static final class CopiedHttpServletMapping implements HttpServletMapping {

        private final String matchValue
        private final String pattern
        private final String servletName
        private final MappingMatch mappingMatch

        private CopiedHttpServletMapping(HttpServletMapping mapping) {
            this.matchValue = mapping.matchValue
            this.pattern = mapping.pattern
            this.servletName = mapping.servletName
            this.mappingMatch = mapping.mappingMatch
        }

        @Override
        String getMatchValue() {
            return matchValue
        }

        @Override
        String getPattern() {
            return pattern
        }

        @Override
        String getServletName() {
            return servletName
        }

        @Override
        MappingMatch getMappingMatch() {
            return mappingMatch
        }
    }

    private static final class CopiedServletConnection implements ServletConnection {

        private final String connectionId
        private final String protocol
        private final String protocolConnectionId
        private final boolean secure

        private CopiedServletConnection(ServletConnection connection) {
            this.connectionId = connection.connectionId
            this.protocol = connection.protocol
            this.protocolConnectionId = connection.protocolConnectionId
            this.secure = connection.secure
        }

        @Override
        String getConnectionId() {
            return connectionId
        }

        @Override
        String getProtocol() {
            return protocol
        }

        @Override
        String getProtocolConnectionId() {
            return protocolConnectionId
        }

        @Override
        boolean isSecure() {
            return secure
        }
    }
}
