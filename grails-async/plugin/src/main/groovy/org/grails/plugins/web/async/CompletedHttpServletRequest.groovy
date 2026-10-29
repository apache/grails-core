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
import groovy.transform.PackageScope

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

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.util.LinkedCaseInsensitiveMap
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest

import org.grails.web.util.WebUtils

/**
 * A copy of a request, taken as the request completed, which a task that outlives the request reads in its place.
 *
 * <p>The container recycles a request once it completes, so a task that read the request itself from then on
 * would fail. As the request completes, a copy is taken of each request its tasks were given, such as the request
 * and the request of a forward, and each task reads the copy of its request through a {@link #newView view} of its
 * own, so each has the part of the body nothing had read, served through one stream or reader whose position
 * persists, as the live request serves it. A copy holds the attributes, parameters, headers, locales and cookies of
 * its request, the session and what identifies the request. The request, its response and what holds either, such
 * as the web request of the request thread, are not carried over, so the copy holds nothing the container recycles.
 * What only the live request can do, such as dispatching, processing asynchronously, creating a session or checking
 * a role, is not available.</p>
 *
 * <p>A copy keeps how its request was dispatched when its task was given it, its path, forward and include
 * attributes and, for a forward or an include, its parameters, though the container restores a request it wrapped
 * for a forward as the forward returns. A copy taken as asynchronous processing completes, outside the filters,
 * keeps who made the request as its last dispatch told, since a security filter clears that as the dispatch
 * ends.</p>
 *
 * <p>The copies share the attributes of the request the container created and the body, as the requests share
 * them, so attributes a task sets stay with the copies, shared by every task. What a request keeps itself, such as
 * the forward attributes of the request a forward dispatched, stays with its own copy, as do the attributes it
 * hides, and setting or removing one of them changes that copy only, as on the request.</p>
 *
 * <p>The parameters are those of the request. A multipart body the container could not parse leaves the copy
 * without parameters, as params has none, and any other failure to read them is thrown to a task that reads them,
 * as the live request throws it. Only a multipart request resolving lazily that can no longer parse the body as the
 * request completes gives way to the request it wraps.</p>
 *
 * <p>The uploads of a multipart request are deleted as the request ends, so the copy answers as a multipart
 * request without uploads when the request was a multipart request, and a copy of a multipart request an attribute
 * held, such as the one the dispatcher resolved, takes its place, with its parameters, such as the text fields only
 * it has. Reading params then finds the parameters it found on the live request and no uploads.</p>
 *
 * <p>The body is kept up to {@value #BODY_LIMIT} bytes. Reading a longer one from the copy throws, so the copy is
 * not what holds an upload that was never read in memory. The body of a multipart request is not kept: its uploads
 * go with the request, and its text fields are in the parameters.</p>
 *
 * @since 8.0.1
 */
@CompileStatic
class CompletedHttpServletRequest implements MultipartHttpServletRequest {

    /**
     * The longest body, in bytes, a copy keeps: what remains of a longer one is not read.
     */
    static final int BODY_LIMIT = 2 * 1024 * 1024

    private static final byte[] NO_BODY = new byte[0]
    private static final int READ_CHUNK = 8192
    // An attribute the request of a copy hides, though the request the container created has it
    private static final Object HIDDEN = new Object()
    private static final List<String> DATE_HEADER_FORMS = List.of(
            'EEE, dd MMM yyyy HH:mm:ss zzz',
            'EEEE, dd-MMM-yy HH:mm:ss zzz',
            'EEE MMM dd HH:mm:ss yyyy')

    private final Map<String, Object> attributes
    private final Map<String, Object> ownAttributes
    // Null when the body was longer than the copy keeps
    private final byte[] body
    private final Map<String, String[]> parameters
    private final RuntimeException parameterFailure
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

    private CompletedHttpServletRequest(HttpServletRequest request, Map<String, Object> attributes,
            Map<String, Object> ownAttributes, CopiedParameters parameters, byte[] body, DispatchView view,
            Identity identity) {
        this.attributes = attributes
        this.ownAttributes = ownAttributes
        this.parameters = parameters.values
        this.parameterFailure = parameters.failure
        this.body = body
        Map<String, List<String>> headers = new LinkedCaseInsensitiveMap<>(Locale.ENGLISH)
        for (String name : Collections.list(request.headerNames)) {
            headers.put(name, Collections.unmodifiableList(Collections.list(request.getHeaders(name))))
        }
        this.headers = Collections.unmodifiableMap(headers)
        this.locales = Collections.unmodifiableList(Collections.list(request.locales))
        this.cookies = copyCookies(request.cookies)
        this.session = request.getSession(false)
        this.servletContext = request.servletContext
        // As the request was dispatched when the task was given it, though the container restores a request it
        // wrapped for a forward as the forward returns
        DispatchView dispatch = view ?: new DispatchView(request)
        // Who made the request, as the filters processing it told, though a security filter clears it
        Identity user = identity ?: new Identity(request)
        this.userPrincipal = user.userPrincipal
        this.dispatcherType = dispatch.dispatcherType
        this.httpServletMapping = dispatch.httpServletMapping
        this.servletConnection = copyConnection(request.servletConnection)
        this.authType = user.authType
        this.characterEncoding = request.characterEncoding
        this.contentType = request.contentType
        this.contentLength = request.contentLengthLong
        this.method = request.method
        this.requestURI = dispatch.requestURI
        this.requestURL = dispatch.requestURL
        this.queryString = dispatch.queryString
        this.contextPath = dispatch.contextPath
        this.servletPath = dispatch.servletPath
        this.pathInfo = dispatch.pathInfo
        this.pathTranslated = dispatch.pathTranslated
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
        this.remoteUser = user.remoteUser
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
        this.ownAttributes = copy.@ownAttributes
        this.body = copy.@body
        this.parameters = copy.@parameters
        this.parameterFailure = copy.@parameterFailure
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
     * Copies the requests the tasks of a request that is completing were given: the request, and any other request
     * a later task was given, such as the request of a forward. It has to be called before the container recycles
     * the request. Each copy holds what its request shows, and the copies share the attributes of the request the
     * container created and the body, as the requests share them.
     *
     * @param requests the requests, each the request the container created or a wrapper of it, the first being the
     *        one whose body the copies share
     * @param views how each request was dispatched when a task was given it
     * @param identities who made the request, as the filters processing it told, for each request, or {@code null}
     *        entries when the request itself still tells
     * @return the copies, in the order of the requests, with {@code null} for a request after the first that could
     *         not be copied
     */
    static List<CompletedHttpServletRequest> copyOf(List<HttpServletRequest> requests, List<DispatchView> views,
            List<Identity> identities) {
        Map<String, Object> underlying = attributesOf(TaskRequest.underlying(requests.get(0)))
        // The requests to copy: those given, then each multipart request an attribute holds, such as the one the
        // dispatcher resolved, which wraps the request, so its copy, which has no uploads either, takes its place
        List<HttpServletRequest> copied = new ArrayList<>(requests)
        Map<Object, Integer> multipartRequests = new IdentityHashMap<>()
        for (int i = 0; i < requests.size(); i++) {
            if (requests.get(i) instanceof MultipartHttpServletRequest) {
                multipartRequests.putIfAbsent(requests.get(i), i)
            }
        }
        addMultipartRequests(underlying, copied, multipartRequests)
        List<Map<String, Object>> shownOtherwise = new ArrayList<>()
        for (int i = 0; i < copied.size(); i++) {
            Map<String, Object> shown = differences(attributesOf(copied.get(i)), underlying, viewOf(views, i))
            shownOtherwise.add(shown)
            addMultipartRequests(shown, copied, multipartRequests)
        }
        // Every parameter map before the body: parsing the parameters consumes a form body, on the live request too
        List<CopiedParameters> parameters = new ArrayList<>()
        for (int i = 0; i < copied.size(); i++) {
            parameters.add(viewOf(views, i)?.parameters ?: copyParameters(copied.get(i)))
        }
        byte[] body = copyBody(requests.get(0))
        Map<String, Object> attributes = new ConcurrentHashMap<>()
        List<Map<String, Object>> ownAttributes = new ArrayList<>()
        List<CompletedHttpServletRequest> copies = new ArrayList<>()
        for (int i = 0; i < copied.size(); i++) {
            Map<String, Object> own = new ConcurrentHashMap<>()
            ownAttributes.add(own)
            Identity identity = i < identities.size() ? identities.get(i) : null
            copies.add(i == 0 ?
                    new CompletedHttpServletRequest(copied.get(0), attributes, own, parameters.get(0), body,
                            viewOf(views, 0), identity) :
                    copyAlongside(copied.get(i), attributes, own, parameters.get(i), body, viewOf(views, i), identity))
        }
        putAttributes(underlying, attributes, multipartRequests, copies, false)
        for (int i = 0; i < copied.size(); i++) {
            putAttributes(shownOtherwise.get(i), ownAttributes.get(i), multipartRequests, copies, true)
        }
        return new ArrayList<>(copies.subList(0, requests.size()))
    }

    // A multipart request an attribute holds was not given to a task, so it has no view
    private static DispatchView viewOf(List<DispatchView> views, int index) {
        return index < views.size() ? views.get(index) : null
    }

    // A request that can no longer be read leaves its own tasks without a copy, not the tasks of the other requests
    private static CompletedHttpServletRequest copyAlongside(HttpServletRequest request, Map<String, Object> attributes,
            Map<String, Object> ownAttributes, CopiedParameters parameters, byte[] body, DispatchView view,
            Identity identity) {
        try {
            return new CompletedHttpServletRequest(request, attributes, ownAttributes, parameters, body, view, identity)
        }
        catch (RuntimeException ignored) {
            return null
        }
    }

    private static Map<String, Object> attributesOf(ServletRequest request) {
        Map<String, Object> attributes = new LinkedHashMap<>()
        for (String name : Collections.list(request.attributeNames)) {
            Object value = request.getAttribute(name)
            if (value != null) {
                attributes.put(name, value)
            }
        }
        return attributes
    }

    // What a request shows otherwise than the request the container created, such as the forward attributes a
    // forward's request keeps itself: its own values, and the names it hides. The forward and include attributes
    // are those it showed when a task was given it, as the container restores a request it wrapped for a forward
    private static Map<String, Object> differences(Map<String, Object> shown, Map<String, Object> underlying,
            DispatchView view) {
        Map<String, Object> differences = new LinkedHashMap<>()
        Map<String, Object> dispatched = view == null ? shown : view.attributes
        for (Map.Entry<String, Object> attribute : shown.entrySet()) {
            if (!(view != null && DispatchView.dispatchAttribute(attribute.key)) &&
                    !Objects.equals(attribute.value, underlying.get(attribute.key))) {
                differences.put(attribute.key, attribute.value)
            }
        }
        if (view != null) {
            for (Map.Entry<String, Object> attribute : dispatched.entrySet()) {
                if (!Objects.equals(attribute.value, underlying.get(attribute.key))) {
                    differences.put(attribute.key, attribute.value)
                }
            }
        }
        for (String name : underlying.keySet()) {
            boolean shownByView = view != null && DispatchView.dispatchAttribute(name)
            if (!(shownByView ? dispatched : shown).containsKey(name)) {
                differences.put(name, HIDDEN)
            }
        }
        return differences
    }

    private static void addMultipartRequests(Map<String, Object> attributes, List<HttpServletRequest> copied,
            Map<Object, Integer> multipartRequests) {
        for (Object value : attributes.values()) {
            if (value instanceof MultipartHttpServletRequest && !multipartRequests.containsKey(value)) {
                multipartRequests.put(value, copied.size())
                copied.add((HttpServletRequest) value)
            }
        }
    }

    // The attributes as the copies keep them: a multipart request is replaced by its copy, and what is recycled with
    // the request is left out, so a request that showed it itself hides it
    private static void putAttributes(Map<String, Object> attributes, Map<String, Object> kept,
            Map<Object, Integer> multipartRequests, List<CompletedHttpServletRequest> copies, boolean own) {
        for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
            Object value = attribute.value
            if (value instanceof MultipartHttpServletRequest) {
                value = copies.get(multipartRequests.get(value))
            }
            else if (recycledWithRequest(value)) {
                value = null
            }
            if (value != null) {
                kept.put(attribute.key, value)
            }
            else if (own) {
                kept.put(attribute.key, HIDDEN)
            }
        }
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

    // The request, its response and what holds either, such as the web request, are recycled with the request
    private static boolean recycledWithRequest(Object value) {
        return value instanceof ServletRequest || value instanceof ServletResponse || value instanceof RequestAttributes
    }

    private static String[] copyValues(String[] values) {
        return values == null ? null : Arrays.copyOf(values, values.length)
    }

    /**
     * The parameters of a request, as WebUtils.readParameterMap reads them: a multipart body the container cannot
     * parse leaves the request without parameters, and any other failure reaches the task that reads them, as on
     * the request.
     *
     * @param request the request
     * @return its parameters, or the failure reading them
     */
    @PackageScope
    static CopiedParameters copyParameters(HttpServletRequest request) {
        try {
            Map<String, String[]> parameters = new LinkedHashMap<>()
            for (Map.Entry<String, String[]> parameter : ownParameters(request).entrySet()) {
                parameters.put(parameter.key, copyValues(parameter.value))
            }
            return new CopiedParameters(Collections.unmodifiableMap(parameters), null)
        }
        catch (RuntimeException unreadable) {
            return new CopiedParameters(Collections.<String, String[]> emptyMap(),
                    WebUtils.isMultipartContentType(request) ? null : unreadable)
        }
    }

    // The parameters of the request or, when a multipart wrapper resolving lazily can no longer parse the body for
    // them as the request completes, of the request it wraps. Any other wrapper that fails keeps its failure, as it
    // fails on the live request
    private static Map<String, String[]> ownParameters(HttpServletRequest request) {
        RuntimeException failure = null
        for (ServletRequest source = request; source != null; source = wrapped(source)) {
            try {
                return source.parameterMap
            }
            catch (RuntimeException unreadable) {
                failure = failure ?: unreadable
                if (!(source instanceof MultipartHttpServletRequest)) {
                    throw failure
                }
            }
        }
        throw failure
    }

    private static ServletRequest wrapped(ServletRequest request) {
        return request instanceof ServletRequestWrapper ? ((ServletRequestWrapper) request).request : null
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

    // What remains of the body, or null when it is longer than the copy keeps
    private static byte[] copyBody(HttpServletRequest request) {
        if (WebUtils.isMultipartContentType(request)) {
            // Its uploads go with the request and its text fields are in the parameters, so a multipart body the
            // request did not read, such as one rejected for its size, is left unread
            return NO_BODY
        }
        try {
            InputStream remaining = request.inputStream
            if (remaining == null) {
                return NO_BODY
            }
            byte[] read = remaining.readNBytes(BODY_LIMIT + 1)
            return read.length > BODY_LIMIT ? null : read
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
            if (reader == null) {
                return NO_BODY
            }
            StringBuilder text = new StringBuilder()
            char[] chunk = new char[READ_CHUNK]
            for (int read = reader.read(chunk); read != -1; read = reader.read(chunk)) {
                text.append(chunk, 0, read)
                if (text.length() > BODY_LIMIT) {
                    return null
                }
            }
            byte[] body = text.toString().getBytes(charset(request.characterEncoding))
            return body.length > BODY_LIMIT ? null : body
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

    @PackageScope
    static HttpServletMapping copyMapping(HttpServletMapping mapping) {
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
        Object own = ownAttributes.get(name)
        if (own != null) {
            return own.is(HIDDEN) ? null : own
        }
        return attributes.get(name)
    }

    @Override
    Enumeration<String> getAttributeNames() {
        Set<String> names = new LinkedHashSet<>(attributes.keySet())
        for (Map.Entry<String, Object> own : ownAttributes.entrySet()) {
            if (own.value.is(HIDDEN)) {
                names.remove(own.key)
            }
            else {
                names.add(own.key)
            }
        }
        return Collections.enumeration(names)
    }

    @Override
    void setAttribute(String name, Object value) {
        if (ownAttributes.containsKey(name)) {
            // The request kept it itself, as a forward's request keeps the forward attributes
            ownAttributes.put(name, value == null ? HIDDEN : value)
        }
        else if (value == null) {
            attributes.remove(name)
        }
        else {
            attributes.put(name, value)
        }
    }

    @Override
    void removeAttribute(String name) {
        if (ownAttributes.containsKey(name)) {
            ownAttributes.put(name, HIDDEN)
        }
        else {
            attributes.remove(name)
        }
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
        checkBodyKept()
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
        checkBodyKept()
        if (inputStream != null) {
            throw new IllegalStateException(
                    'getInputStream() has already been called, so the body is not available as a reader, as on the live request')
        }
        if (reader == null) {
            reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), charset(characterEncoding)))
        }
        return reader
    }

    private void checkBodyKept() {
        if (body == null) {
            throw new IllegalStateException('The request has completed, and the part of its body nothing had read ' +
                    "was longer than the $BODY_LIMIT bytes its copy keeps, so a task that outlives it cannot read the body")
        }
    }

    @Override
    String getParameter(String name) {
        String[] values = readParameters().get(name)
        return values ? values[0] : null
    }

    @Override
    Enumeration<String> getParameterNames() {
        return Collections.enumeration(readParameters().keySet())
    }

    @Override
    String[] getParameterValues(String name) {
        return copyValues(readParameters().get(name))
    }

    @Override
    Map<String, String[]> getParameterMap() {
        return readParameters()
    }

    // Parameters the request could not read fail as they did on the live request
    private Map<String, String[]> readParameters() {
        if (parameterFailure != null) {
            throw parameterFailure
        }
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

    @Override
    HttpMethod getRequestMethod() {
        return method == null ? null : HttpMethod.valueOf(method)
    }

    @Override
    HttpHeaders getRequestHeaders() {
        HttpHeaders requestHeaders = new HttpHeaders()
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            requestHeaders.addAll(header.key, header.value)
        }
        return requestHeaders
    }

    @Override
    HttpHeaders getMultipartHeaders(String paramOrFileName) {
        return null
    }

    @Override
    Iterator<String> getFileNames() {
        return Collections.emptyIterator()
    }

    @Override
    MultipartFile getFile(String name) {
        return null
    }

    @Override
    List<MultipartFile> getFiles(String name) {
        return Collections.emptyList()
    }

    @Override
    Map<String, MultipartFile> getFileMap() {
        return Collections.emptyMap()
    }

    @Override
    MultiValueMap<String, MultipartFile> getMultiFileMap() {
        return new LinkedMultiValueMap<String, MultipartFile>()
    }

    @Override
    String getMultipartContentType(String paramOrFileName) {
        return null
    }

    // The parameters of a request, or the failure that kept the request from reading them
    @PackageScope
    static final class CopiedParameters {

        @PackageScope
        final Map<String, String[]> values

        @PackageScope
        final RuntimeException failure

        @PackageScope
        CopiedParameters(Map<String, String[]> values, RuntimeException failure) {
            this.values = values
            this.failure = failure
        }
    }

    // Who made a request, which a security filter answers only on the thread processing the request, and only
    // until it ends
    @PackageScope
    static final class Identity {

        @PackageScope
        final Principal userPrincipal

        @PackageScope
        final String remoteUser

        @PackageScope
        final String authType

        @PackageScope
        Identity(HttpServletRequest request) {
            this.userPrincipal = request.userPrincipal
            this.remoteUser = request.remoteUser
            this.authType = request.authType
        }
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
