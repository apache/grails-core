/*
 * Copyright 2011-2024 the original author or authors.
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
package grails.gsp

import org.grails.buffer.FastStringWriter
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import org.grails.gsp.io.GroovyPageScriptSource
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.web.context.ServletContextAware
import org.springframework.web.context.request.RequestContextHolder

import jakarta.servlet.ServletContext
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap

/**
 * Simplified API for rendering GSP pages from services, jobs and other non-request classes.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
class PageRenderer implements ApplicationContextAware, ServletContextAware {

    private GroovyPagesTemplateEngine templateEngine
    GrailsConventionGroovyPageLocator groovyPageLocator
    ApplicationContext applicationContext
    ServletContext servletContext
    Locale locale

    PageRenderer(GroovyPagesTemplateEngine templateEngine) {
        this.templateEngine = templateEngine
    }

    /**
     * Renders a page and returns the contents
     *
     * @param args The named arguments
     *
     * @arg view The view to render. Must be an absolute view path since the controller name is unknown.
     * @arg template The template to render. Must be an absolute template path since the controller name is unknown.
     * @arg model The model to use for rendering
     *
     * @return The resulting string contents
     */
    String render(Map args) {
        def fsw = new FastStringWriter()
        renderViewToWriter(args, fsw)
        return fsw.toString()
    }

    /**
     * Renders a page and returns the contents
     *
     * @param args The named arguments
     * @param writer The target writer
     *
     * @arg view The view to render. Must be an absolute view path since the controller name is unknown.
     * @arg template The template to render. Must be an absolute template path since the controller name is unknown.
     * @arg model The model to use for rendering
     *
     * @return The resulting string contents
     */
    void renderTo(Map args, Writer writer) {
        renderViewToWriter(args, writer)
    }

    /**
     * Renders a page and returns the contents
     *
     * @param args The named arguments
     * @param stream The target stream
     *
     * @arg view The view to render. Must be an absolute view path since the controller name is unknown.
     * @arg template The template to render. Must be an absolute template path since the controller name is unknown.
     * @arg model The model to use for rendering
     *
     * @return The resulting string contents
     */
    void renderTo(Map args, OutputStream stream) {
        renderTo(args, new OutputStreamWriter(stream))
    }

    private void renderViewToWriter(Map args, Writer writer) {
        def source
        if (args.view) {
            source = groovyPageLocator.findViewByPath(args.view.toString())
        } else if (args.template) {
            source = groovyPageLocator.findTemplateByPath(args.template.toString())
        }
        if (source == null) {
            return
        }

        def oldRequestAttributes = GrailsWebRequest.lookup()
        try {
            def localeToUse = locale ?: (oldRequestAttributes?.locale ?: Locale.default)
            def webRequest = new GrailsWebRequest(PageRenderRequestCreator.createInstance(source.URI, localeToUse),
                PageRenderResponseCreator.createInstance(writer instanceof PrintWriter ? writer : new PrintWriter(writer), localeToUse),
                servletContext, applicationContext)
            RequestContextHolder.setRequestAttributes(webRequest)
            def template = templateEngine.createTemplate(source)
            if (template != null) {
                template.make(args.model ?: [:]).writeTo(writer)
            }
        } finally {
            RequestContextHolder.setRequestAttributes(oldRequestAttributes)
        }
    }

    protected GroovyPageScriptSource findResource(String basePath) {
        return groovyPageLocator.findViewByPath(basePath)
    }

    /*
     * Creates the request object used during the GSP rendering pipeline for render operations outside a web request.
     * Created dynamically to avoid issues with different servlet API spec versions.
     */
    static class PageRenderRequestCreator {

        static HttpServletRequest createInstance(final String requestURI, Locale localeToUse = Locale.getDefault()) {

            def params = new ConcurrentHashMap()
            def attributes = new ConcurrentHashMap()

            String contentType = null
            String characterEncoding = "UTF-8"

            (HttpServletRequest)Proxy.newProxyInstance(HttpServletRequest.classLoader, [HttpServletRequest] as Class[], new InvocationHandler() {
                Object invoke(proxy, Method method, Object[] args) {

                    String methodName = method.name

                    if (methodName == 'getContentType') {
                        return contentType
                    }
                    if (methodName == 'setContentType') {
                        contentType = args[0]
                        return null
                    }
                    if (methodName == 'getCharacterEncoding') {
                        return characterEncoding
                    }
                    if (methodName == 'setCharacterEncoding') {
                        characterEncoding = args[0]
                    }

                    if (methodName == 'getRealPath') {
                        return requestURI
                    }
                    if (methodName == 'getLocalName') {
                        return "localhost"
                    }
                    if (methodName == 'getLocalAddr') {
                        return "127.0.0.1"
                    }
                    if (methodName == 'getLocalPort') {
                        return 80
                    }

                    if (methodName == 'getCookies') {
                        return ([] as Cookie[])
                    }
                    if (methodName == 'getDateHeader' || methodName == 'getIntHeader') {
                        return -1
                    }
                    if (methodName == 'getMethod') {
                        return 'GET'
                    }
                    if (methodName == 'getContextPath' || methodName == 'getServletPath') {
                        return '/'
                    }

                    if (methodName in ['getPathInfo', 'getPathTranslated', 'getQueryString']) {
                        return ''
                    }

                    if (methodName == 'getRequestURL') {
                        return new StringBuffer(requestURI)
                    }
                    if (methodName == 'getRequestURI') {
                        return requestURI
                    }

                    if (methodName == 'isRequestedSessionIdValid') {
                        return true
                    }
                    if (methodName in [
                        'isRequestedSessionIdFromCookie', 'isRequestedSessionIdFromURL', 'isRequestedSessionIdFromUrl',
                        'authenticate', 'isUserInRole', 'isSecure', 'isAsyncStarted', 'isAsyncSupported']) {
                        return false
                    }

                    if (methodName == 'getSession') {
                        throw new UnsupportedOperationException("You cannot use the session in non-request rendering operations")
                    }
                    if (methodName == 'getInputStream') {
                        throw new UnsupportedOperationException("You cannot read the input stream in non-request rendering operations")
                    }
                    if (methodName == 'getProtocol') {
                        throw new UnsupportedOperationException("You cannot read the protocol in non-request rendering operations")
                    }
                    if (methodName == 'getScheme') {
                        throw new UnsupportedOperationException("You cannot read the scheme in non-request rendering operations")
                    }
                    if (methodName == 'getServerName') {
                        throw new UnsupportedOperationException("You cannot read server name in non-request rendering operations")
                    }
                    if (methodName == 'getServerPort') {
                        throw new UnsupportedOperationException("You cannot read the server port in non-request rendering operations")
                    }
                    if (methodName == 'getReader') {
                        throw new UnsupportedOperationException("You cannot read input in non-request rendering operations")
                    }
                    if (methodName == 'getRemoteAddr') {
                        throw new UnsupportedOperationException("You cannot read the remote address in non-request rendering operations")
                    }
                    if (methodName == 'getRemoteHost') {
                        throw new UnsupportedOperationException("You cannot read the remote host in non-request rendering operations")
                    }
                    if (methodName == 'getRequestDispatcher') {
                        throw new UnsupportedOperationException("You cannot use the request dispatcher in non-request rendering operations")
                    }
                    if (methodName == 'getRemotePort') {
                        throw new UnsupportedOperationException("You cannot read the remote port in non-request rendering operations")
                    }

                    if (methodName == 'getParts') {
                        return []
                    }

                    if (methodName == 'getAttribute') {
                        return attributes[args[0]]
                    }
                    if (methodName == 'getAttributeNames') {
                        return attributes.keys()
                    }
                    if (methodName == 'setAttribute') {
                        String name = args[0]
                        Object o = args[1]
                        if (o == null) {
                            attributes.remove name
                        } else {
                            attributes[name] = o
                        }
                        return null
                    }
                    if (methodName == 'removeAttribute') {
                        attributes.remove args[0]
                        return null
                    }

                    if (methodName == 'getLocale') {
                        return localeToUse
                    }
                    if (methodName == 'getLocales') {
                        def iterator = [localeToUse].iterator()
                        PageRenderRequestCreator.iteratorAsEnumeration(iterator)
                    }

                    if (methodName == 'getParameter') {
                        return params[args[0]]
                    }
                    if (methodName == 'getParameterNames') {
                        return params.keys()
                    }
                    if (methodName == 'getParameterValues') {
                        return [] as String[]
                    }
                    if (methodName == 'getParameterMap') {
                        return params
                    }

                    if (methodName == 'getContentLength') {
                        return 0
                    }

                    if ('getHeaderNames'.equals(methodName) || 'getHeaders'.equals(methodName)) {
                        return Collections.enumeration(Collections.emptySet())
                    }

                    return null
                }
            })
        }
        
        private static Enumeration iteratorAsEnumeration(Iterator iterator) {
            new Enumeration() {
                @Override
                boolean hasMoreElements() {
                    iterator.hasNext()
                }

                @Override
                Object nextElement() {
                    iterator.next()
                }
            }
        }
    }

    static class PageRenderResponseCreator {

        static HttpServletResponse createInstance(final PrintWriter writer, Locale localeToUse = Locale.getDefault()) {

            String characterEncoding = "UTF-8"
            String contentType = null
            int bufferSize = 0

            (HttpServletResponse)Proxy.newProxyInstance(HttpServletResponse.classLoader, [HttpServletResponse] as Class[], new InvocationHandler() {
                Object invoke(proxy, Method method, Object[] args) {

                    String methodName = method.name

                    if (methodName == 'getContentType') {
                        return contentType
                    }
                    if (methodName == 'setContentType') {
                        contentType = args[0]
                        return null
                    }
                    if (methodName == 'getCharacterEncoding') {
                        return characterEncoding
                    }
                    if (methodName == 'setCharacterEncoding') {
                        characterEncoding = args[0]
                        return null
                    }
                    if (methodName == 'getBufferSize') {
                        return bufferSize
                    }
                    if (methodName == 'setBufferSize') {
                        bufferSize = args[0]
                        return null
                    }

                    if (methodName == 'containsHeader' || methodName == 'isCommitted') {
                        return false
                    }

                    if (methodName in ['encodeURL', 'encodeRedirectURL', 'encodeUrl', 'encodeRedirectUrl']) {
                        return args[0]
                    }

                    if (methodName == 'getWriter') {
                        writer
                    }

                    if (methodName == 'getOutputStream') {
                        throw new UnsupportedOperationException("You cannot use the OutputStream in non-request rendering operations. Use getWriter() instead")
                    }

                    if (methodName == 'getHeaderNames') {
                        return []
                    }

                    if (methodName == 'getLocale') {
                        return localeToUse
                    }

                    if (methodName == 'getStatus') {
                        return 0
                    }

                    return null
                }
            })
        }
    }
}
