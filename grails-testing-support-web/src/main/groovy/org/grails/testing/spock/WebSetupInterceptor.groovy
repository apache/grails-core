package org.grails.testing.spock

import grails.testing.web.GrailsWebUnitTest
import grails.util.GrailsWebMockUtil
import groovy.transform.CompileStatic
import org.grails.plugins.testing.GrailsMockHttpServletRequest
import org.grails.plugins.testing.GrailsMockHttpServletResponse
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.springframework.web.servlet.DispatcherServlet

import jakarta.servlet.ServletContext

@CompileStatic
class WebSetupInterceptor implements IMethodInterceptor {
    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        GrailsWebUnitTest test = (GrailsWebUnitTest)invocation.instance
        setup(test)
        invocation.proceed()
    }

    void setup(GrailsWebUnitTest test) {
        def applicationContext = test.applicationContext
        GrailsMockHttpServletRequest request = new GrailsMockHttpServletRequest((ServletContext)test.servletContext)
        request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, applicationContext.getBean('localeResolver'))
        request.method = 'GET'
        GrailsMockHttpServletResponse response = new GrailsMockHttpServletResponse()
        GrailsWebRequest webRequest = GrailsWebMockUtil.bindMockWebRequest(applicationContext, request, response)
        test.webRequest = webRequest
    }


}
