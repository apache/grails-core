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
package org.grails.gsp.jsp

import org.grails.gsp.GroovyPageBinding
import org.grails.web.pages.GroovyPagesServlet
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes as GAA
import org.springframework.web.context.request.RequestContextHolder

import jakarta.servlet.ServletContext
import jakarta.servlet.jsp.PageContext as PC

/**
 * Obtains a reference to the GroovyPagesPageContext class.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class PageContextFactory {

    static GroovyPagesPageContext getCurrent() {
        GrailsWebRequest webRequest = RequestContextHolder.currentRequestAttributes()

        def request = webRequest.getCurrentRequest()

        def pageContext = request.getAttribute(PC.PAGECONTEXT)
        if (pageContext instanceof GroovyPagesPageContext) return pageContext

        ServletContext servletContext = webRequest.getServletContext()
        def gspServlet = servletContext.getAttribute(GroovyPagesServlet.SERVLET_INSTANCE)
        if (!gspServlet) {
            gspServlet = new GroovyPagesServlet()
            servletContext.setAttribute GroovyPagesServlet.SERVLET_INSTANCE, gspServlet
        }
        def pageScope = request.getAttribute(GAA.PAGE_SCOPE)
        if (!pageScope) {
            pageScope = new GroovyPageBinding()
            request.setAttribute(GAA.PAGE_SCOPE, pageScope)
        }

        pageContext = new GroovyPagesPageContext(gspServlet, pageScope)
        request.setAttribute(PC.PAGECONTEXT, pageContext)

        return pageContext
    }
}
