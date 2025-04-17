package grails.views.mvc

import grails.config.Config
import grails.core.support.GrailsConfigurationAware
import grails.views.ResolvableGroovyTemplateEngine
import grails.views.api.GrailsView
import grails.views.api.HttpView
import grails.views.api.http.Request
import grails.views.api.http.Response
import grails.views.mvc.http.DelegatingParameters
import grails.web.http.HttpHeaders
import grails.web.mime.MimeType
import groovy.text.Template
import groovy.transform.CompileStatic
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.view.AbstractUrlBasedView

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * An implementation of the Spring AbstractUrlBaseView class for ResolvableGroovyTemplateEngine
 *
 * @author Graeme Rocher
 */
@CompileStatic
class GenericGroovyTemplateView extends AbstractUrlBasedView {

    ResolvableGroovyTemplateEngine templateEngine
    LocaleResolver localeResolver
    Config configuration

    private String defaultEncoding = "UTF-8"

    void setTemplateEngine(ResolvableGroovyTemplateEngine templateEngine) {
        this.templateEngine = templateEngine
        this.defaultEncoding = templateEngine.viewConfiguration?.encoding ?: defaultEncoding
    }

    void setLocaleResolver(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver
    }

    @Override
    protected void renderMergedOutputModel(Map<String, Object> map, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Exception {
        def locale = localeResolver?.resolveLocale(httpServletRequest) ?: Locale.ENGLISH
        def qualifiers = []
        def v = httpServletRequest.getHeader(HttpHeaders.ACCEPT_VERSION)
        MimeType mimeType = GrailsWebRequest.lookup(httpServletRequest) != null ? httpServletResponse.mimeType : null
        if(mimeType != null && mimeType != MimeType.ALL) {
            qualifiers.add(mimeType.extension)
        }
        if(v != null) {
            qualifiers.add(v)
        }
        Template template = templateEngine.resolveTemplate(url, locale, qualifiers as String[])
        if(template != null) {

            if (!httpServletResponse.contentType) {
                httpServletResponse.setContentType( getContentType() )
            }
            httpServletResponse.setCharacterEncoding( defaultEncoding )

            def writable = template.make(map)
            prepareWritable(writable, httpServletRequest, httpServletResponse, locale)
            def writer = httpServletResponse.writer
            try {
                // set the default encoding
                // now write the writable
                writable.writeTo(writer)
            } catch (RuntimeException e) {
                if(!httpServletResponse.isCommitted()) {
                    // set back to HTML to errors are rendered correctly
                    httpServletResponse.setContentType(MimeType.HTML.name)
                }
                // clear model and view
                httpServletRequest.removeAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)
                throw e
            }
            writer.flush()
        }
    }

    protected void prepareWritable(Writable writable, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Locale locale) {
        final webRequest = GrailsWebRequest.lookup(httpServletRequest)
        if (writable instanceof GrailsView) {
            def grailsView = (GrailsView) writable
            grailsView.setLocale(
                locale
            )
            grailsView.setConfig(
                configuration
            )
            if (webRequest != null) {
                grailsView.setActionName(
                        webRequest.actionName
                )
                grailsView.setControllerName(
                        webRequest.controllerName
                )
                grailsView.setControllerNamespace(
                        webRequest.controllerNamespace
                )
            }
        }
        if (writable instanceof HttpView) {
            def httpView = (HttpView) writable
            httpView.setResponse(new HttpViewResponse(httpServletResponse))
            httpView.setRequest(new HttpViewRequest(httpServletRequest))
            if(webRequest != null) {
                httpView.setParams(new DelegatingParameters(webRequest.getParams()))
            }
        }
    }

    @CompileStatic
    private static class HttpViewRequest implements Request {
        final HttpServletRequest request;

        HttpViewRequest(HttpServletRequest request) {
            this.request = request
        }

        @Override
        String getContextPath() {
            request.contextPath
        }

        @Override
        String getMethod() {
            request.method
        }

        @Override
        String getUri() {
            return request.getRequestURI()
        }

        @Override
        String getContentType() {
            return request.contentType
        }

        @Override
        String getCharacterEncoding() {
            return request.getCharacterEncoding()
        }

        @Override
        String getHeader(String name) {
            return request.getHeader(name)
        }

        @Override
        Collection<String> getHeaders(String name) {
            request.getHeaders(name).toList()
        }
        /**
         * @return The header for the request
         */
        @Lazy Collection<String> headerNames =  {
            request.getHeaderNames().toList()
        }()

        @Override
        Object getAttribute(String name) {
            request.getAttribute(name)
        }

        @Lazy Collection<String> attributeNames = {
            request.getAttributeNames().toList()
        }()
    }

    @CompileStatic
    private static class HttpViewResponse implements Response {
        final HttpServletResponse httpServletResponse

        HttpViewResponse(HttpServletResponse httpServletResponse) {
            this.httpServletResponse = httpServletResponse
        }

        @Override
        void header(Map<String, String> nameAndValue) {
            headers(nameAndValue)
        }

        @Override
        void headers(Map<String, String> namesAndValues) {
            for (entry in namesAndValues.entrySet()) {
                header entry.key, entry.value
            }
        }

        @Override
        void header(String name, String value) {
            httpServletResponse.addHeader(name, value)
        }

        @Override
        void contentType(String contentType) {
            httpServletResponse.setContentType(contentType)
        }

        @Override
        void encoding(String encoding) {
            httpServletResponse.setCharacterEncoding(encoding)
        }

        @Override
        void status(int status) {
            httpServletResponse.setStatus(status)
        }

        @Override
        void status(int status, String message) {
            httpServletResponse.sendError(status, message)
        }

        @Override
        void status(HttpStatus s) {
            status(s.value())
        }

        @Override
        void status(HttpStatus s, String message) {
            status(s.value(), message)
        }
    }

}
