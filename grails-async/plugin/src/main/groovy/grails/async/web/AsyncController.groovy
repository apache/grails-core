package grails.async.web

import groovy.transform.CompileStatic
import org.grails.plugins.web.async.GrailsAsyncContext
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.async.AsyncWebRequest
import org.springframework.web.context.request.async.WebAsyncManager
import org.springframework.web.context.request.async.WebAsyncUtils

import jakarta.servlet.AsyncContext
import jakarta.servlet.http.HttpServletRequest

/**
 * Exposes a startAsync() method for access to the Servlet 3.x API
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@CompileStatic
trait AsyncController {
    /**
     * Raw access to the Servlet 3.0 startAsync method
     *
     * @return a new {@link javax.servlet.AsyncContext}
     */
    AsyncContext startAsync() {
        GrailsWebRequest webRequest = (GrailsWebRequest)RequestContextHolder.currentRequestAttributes()

        HttpServletRequest request = webRequest.currentRequest
        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request)

        AsyncWebRequest asyncWebRequest = new AsyncGrailsWebRequest(request, webRequest.currentResponse, webRequest.servletContext)
        asyncManager.setAsyncWebRequest(asyncWebRequest)

        asyncWebRequest.startAsync()
        request.setAttribute(GrailsApplicationAttributes.ASYNC_STARTED, true)
        new GrailsAsyncContext(asyncWebRequest.asyncContext, webRequest)
    }
}
