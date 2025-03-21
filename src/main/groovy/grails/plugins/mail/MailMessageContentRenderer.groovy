/*
 * Copyright 2010-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugins.mail

import grails.core.GrailsApplication
import grails.plugins.GrailsPluginManager
import grails.web.mapping.LinkGenerator
import grails.web.pages.GroovyPagesUriService
import groovy.text.Template
import groovy.transform.CompileStatic
import org.grails.gsp.GroovyPageTemplate
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.web.servlet.WrappedResponseHolder
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.context.ApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.i18n.FixedLocaleResolver
import org.springframework.web.servlet.support.RequestContextUtils

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap

/**
 * Renders a GSP into the content of a mail message.
 */
@CompileStatic
class MailMessageContentRenderer {

    GroovyPagesTemplateEngine groovyPagesTemplateEngine
    GroovyPagesUriService groovyPagesUriService
    GrailsApplication grailsApplication
    GrailsPluginManager pluginManager

    MailMessageContentRenderer() {}

    /**
     * @param groovyPagesTemplateEngine The GSP template engine to use
     * @param groovyPagesUriService The GSP URI service to use
     * @param grailsApplication The Grails application
     * @param pluginManager The plugin manager
     * @since 4.0.0
     */
    MailMessageContentRenderer(GroovyPagesTemplateEngine groovyPagesTemplateEngine,
                               GroovyPagesUriService groovyPagesUriService,
                               GrailsApplication grailsApplication,
                               GrailsPluginManager pluginManager) {
        this.groovyPagesTemplateEngine = groovyPagesTemplateEngine
        this.groovyPagesUriService = groovyPagesUriService
        this.grailsApplication = grailsApplication
        this.pluginManager = pluginManager
    }

    MailMessageContentRender render(Writer out, String templateName, Map model, Locale locale, String pluginName = null) {
        RenderEnvironment.with(grailsApplication.mainContext, out, locale) { RenderEnvironment env ->
            Template template = createTemplate(templateName, env.controllerName, pluginName)
            if (model instanceof Map) {
                template.make(model).writeTo(out)
            } else {
                template.make().writeTo(out)
            }
            new MailMessageContentRender(out, template.metaInfo.contentType)
        } as MailMessageContentRender
    }

    protected GroovyPageTemplate createTemplate(String templateName, String controllerName, String pluginName) {
        if (templateName.startsWith('/')) {
            if (!controllerName) {
                controllerName = ''
            }
        } else {
            if (!controllerName) {
                throw new IllegalArgumentException('Mail views cannot be loaded from relative view paths when there is no current HTTP request')
            }
        }

        String contextPath = getContextPath(pluginName)
        String templateUri = contextPath ?
            contextPath + groovyPagesUriService.getViewURI(controllerName, templateName) :
            groovyPagesUriService.getDeployedViewURI(controllerName, templateName)

        def template = groovyPagesTemplateEngine.createTemplateForUri(templateUri)
        if (!template) {
            if (pluginName) {
                throw new IllegalArgumentException("Could not locate email view ${templateName} in plugin [$pluginName]")
            } else {
                throw new IllegalArgumentException("Could not locate mail body ${templateName}. Is it in a plugin? If so you must pass the plugin name in the [plugin] variable")
            }
        }
        template as GroovyPageTemplate
    }

    protected String getContextPath(String pluginName) {
        String contextPath = ''
        if (pluginName) {
            def plugin = pluginManager.getGrailsPlugin(pluginName)
            if (plugin && !plugin.isBasePlugin()) {
                contextPath = "${plugin.pluginPath}/grails-app/views"
            }
        }
        contextPath
    }

    private static class RenderEnvironment {

        final PrintWriter out
        final Locale locale
        final ApplicationContext applicationContext
        final LinkGenerator grailsLinkGenerator

        private GrailsWebRequest originalRequestAttributes
        private GrailsWebRequest renderRequestAttributes
        private HttpServletResponse originalWrappedResponse

        RenderEnvironment(ApplicationContext applicationContext, Writer out, Locale locale = null) {
            this.out = out instanceof PrintWriter ? out as PrintWriter : new PrintWriter(out)
            this.locale = locale
            this.applicationContext = applicationContext
            this.grailsLinkGenerator = applicationContext.getBean('grailsLinkGenerator', LinkGenerator)
        }

        private void init() {
            originalRequestAttributes = RequestContextHolder.getRequestAttributes() as GrailsWebRequest
            originalWrappedResponse = WrappedResponseHolder.wrappedResponse

            def renderLocale = Locale.getDefault()
            if (locale) {
                renderLocale = locale
            } else if (originalRequestAttributes) {
                renderLocale = RequestContextUtils.getLocale(originalRequestAttributes.request)
            }

            renderRequestAttributes = new GrailsWebRequest(
                    PageRenderRequestCreator.createInstance(grailsLinkGenerator.serverBaseURL, '/mail/render', renderLocale),
                    PageRenderResponseCreator.createInstance(out, renderLocale),
                    null,
                    applicationContext
            )

            if (originalRequestAttributes) {
                renderRequestAttributes.controllerName = originalRequestAttributes.controllerName
            }

            RequestContextHolder.requestAttributes = renderRequestAttributes
            renderRequestAttributes.request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, new FixedLocaleResolver(defaultLocale: renderLocale))
            renderRequestAttributes.setOut(out)
            WrappedResponseHolder.wrappedResponse = renderRequestAttributes.currentResponse
        }

        private void close() {
            RequestContextHolder.requestAttributes = originalRequestAttributes // null ok
            WrappedResponseHolder.wrappedResponse = originalWrappedResponse
        }

        /**
         * Establish an environment inheriting the locale of the current request if there is one
         */
        static Object with(ApplicationContext applicationContext, Writer out, Closure block) {
            with(applicationContext, out, null, block)
        }

        /**
         * Establish an environment with a specific locale
         */
         static Object with(ApplicationContext applicationContext, Writer out, Locale locale, Closure block) {
            def env = new RenderEnvironment(applicationContext, out, locale)
            env.init()
            try {
                block(env)
            } finally {
                env.close()
            }
        }

        String getControllerName() {
            renderRequestAttributes.controllerName
        }
    }

   /*
     * Creates the request object used during the GSP rendering pipeline for render operations outside a web request.
     * Created dynamically to avoid issues with different servlet API spec versions.
     */
    static class PageRenderRequestCreator {

        static HttpServletRequest createInstance(final String serverBaseURL, final String requestURI, Locale localeToUse = Locale.getDefault()) {
            final URI serverBaseURI = serverBaseURL != null ? new URI(serverBaseURL) : null

            def params = new ConcurrentHashMap()
            def attributes = new ConcurrentHashMap()

            String contentType = null
            String characterEncoding = 'UTF-8'

            Proxy.newProxyInstance(HttpServletRequest.classLoader, [HttpServletRequest] as Class[], new InvocationHandler() {
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
                    if (methodName == 'getRealPath') {
                        return requestURI
                    }
                    if (methodName == 'getLocalName') {
                        return 'localhost'
                    }
                    if (methodName == 'getLocalAddr') {
                        return '127.0.0.1'
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
                        throw new UnsupportedOperationException('You cannot use the session in non-request rendering operations')
                    }
                    if (methodName == 'getInputStream') {
                        throw new UnsupportedOperationException('You cannot read the input stream in non-request rendering operations')
                    }
                    if (methodName == 'getProtocol') {
                        throw new UnsupportedOperationException('You cannot read the protocol in non-request rendering operations')
                    }
                    if (methodName == 'getScheme') {
                        if (serverBaseURI == null) {
                            throw new UnsupportedOperationException('You cannot read the scheme in non-request rendering operations')
                        }
                        return serverBaseURI.scheme
                    }
                    if (methodName == 'getServerName') {
                        if(serverBaseURI == null) {
                            throw new UnsupportedOperationException('You cannot read the servername in non-request rendering operations')
                        }
                        return serverBaseURI.host
                    }
                    if (methodName == 'getServerPort') {
                        if (serverBaseURI == null) {
                            throw new UnsupportedOperationException('You cannot read the server port in non-request rendering operations')
                        }
                        int port = serverBaseURI.port
                        if (port == -1) {
                            switch (serverBaseURI.scheme?.toLowerCase()) {
                                case 'https':
                                    port = 443
                                    break
                                default:
                                    port = 80
                            }
                        }
                        return port
                    }
                    if (methodName == 'getReader') {
                        throw new UnsupportedOperationException('You cannot read input in non-request rendering operations')
                    }
                    if (methodName == 'getRemoteAddr') {
                        throw new UnsupportedOperationException('You cannot read the remote address in non-request rendering operations')
                    }
                    if (methodName == 'getRemoteHost') {
                        throw new UnsupportedOperationException('You cannot read the remote host in non-request rendering operations')
                    }
                    if (methodName == 'getRequestDispatcher') {
                        throw new UnsupportedOperationException('You cannot use the request dispatcher in non-request rendering operations')
                    }
                    if (methodName == 'getRemotePort') {
                        throw new UnsupportedOperationException('You cannot read the remote port in non-request rendering operations')
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
                            attributes.remove(name)
                        } else {
                            attributes[name] = o
                        }
                        return null
                    }
                    if (methodName == 'removeAttribute') {
                        attributes.remove(args[0])
                        return null
                    }
                    if (methodName == 'getLocale') {
                        return localeToUse
                    }
                    if (methodName == 'getLocales') {
                        def iterator = [localeToUse].iterator()
                        //noinspection UnnecessaryQualifiedReference
                        return PageRenderRequestCreator.iteratorAsEnumeration(iterator)
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
                    if ('getHeaderNames' == methodName || 'getHeaders' == methodName) {
                        return Collections.enumeration(Collections.emptySet())
                    }
                    return null
                }
            }) as HttpServletRequest
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

            String characterEncoding = 'UTF-8'
            String contentType = null
            int bufferSize = 0

            Proxy.newProxyInstance(HttpServletResponse.classLoader, [HttpServletResponse] as Class[], new InvocationHandler() {
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
                        bufferSize = args[0] as Integer
                        return null
                    }
                    if (methodName == 'containsHeader' || methodName == 'isCommitted') {
                        return false
                    }
                    if (methodName in ['encodeURL', 'encodeRedirectURL', 'encodeUrl', 'encodeRedirectUrl']) {
                        return args[0]
                    }
                    if (methodName == 'getWriter') {
                        return writer
                    }
                    if (methodName == 'getOutputStream') {
                        throw new UnsupportedOperationException('You cannot use the OutputStream in non-request rendering operations. Use getWriter() instead')
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
            }) as HttpServletResponse
        }
    }
}
