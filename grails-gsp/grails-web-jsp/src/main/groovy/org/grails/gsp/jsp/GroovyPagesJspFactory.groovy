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

import jakarta.servlet.Servlet
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.jsp.JspApplicationContext
import jakarta.servlet.jsp.JspEngineInfo
import jakarta.servlet.jsp.JspFactory
import jakarta.servlet.jsp.PageContext

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class GroovyPagesJspFactory extends JspFactory {

    PageContext getPageContext(Servlet servlet, ServletRequest servletRequest, ServletResponse servletResponse, String s, boolean b, int i, boolean b1) {
         throw new UnsupportedOperationException()
    }

    void releasePageContext(PageContext pageContext) {
         throw new UnsupportedOperationException()
    }

    JspEngineInfo getEngineInfo() {
        return { getSpecificationVersion() } as JspEngineInfo
    }

    protected String getSpecificationVersion() { "2.1" }

    JspApplicationContext getJspApplicationContext(ServletContext servletContext) {
        def jspCtx = servletContext.getAttribute(GroovyPagesJspApplicationContext.getName())

        if (!jspCtx) {
            synchronized (servletContext) {
                if (!servletContext.getAttribute(GroovyPagesJspApplicationContext.getName())) {
                    jspCtx = new GroovyPagesJspApplicationContext()
                    servletContext.setAttribute(GroovyPagesJspApplicationContext.getName(), jspCtx)
                }
            }
        }
        return jspCtx
    }
}
