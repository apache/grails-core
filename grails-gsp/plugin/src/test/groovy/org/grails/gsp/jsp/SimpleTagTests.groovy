package org.grails.gsp.jsp

import grails.core.DefaultGrailsApplication
import grails.util.GrailsWebMockUtil
import jakarta.servlet.jsp.JspException
import jakarta.servlet.jsp.JspWriter
import jakarta.servlet.jsp.tagext.SimpleTagSupport
import org.grails.web.pages.GroovyPagesServlet
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class SimpleTagTests {

    GrailsWebRequest webRequest

    @BeforeEach
    protected void setUp() {
        webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.getCurrentRequest().setAttribute(GroovyPagesServlet.SERVLET_INSTANCE, new GroovyPagesServlet())
    }

    @AfterEach
    protected void tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    void testSimpleTagWithBodyUsage() {
        def resolver = new TagLibraryResolverImpl()
        resolver.servletContext = new MockServletContext()
        resolver.grailsApplication = new DefaultGrailsApplication()

        JspTag jspTag = new JspTagImpl(BodySimpleTagSupport)
        def sw = new StringWriter()
        jspTag.doTag(sw, [:]) {
            "testbody"
        }

        assertEquals "bodySimpleTagSupport:testbody", sw.toString().trim()
    }

    @Test
    void testSimpleTagUsage() {

        def resolver = new TagLibraryResolverImpl()
        resolver.servletContext = new MockServletContext()
        resolver.grailsApplication = new DefaultGrailsApplication()

        JspTag jspTag = new JspTagImpl(ExtendsSimpleTagSupport)
        def sw = new StringWriter()
        jspTag.doTag(sw, [:])

        assertEquals "extendsSimpleTagSupport:output", sw.toString().trim()
    }
}

class ExtendsSimpleTagSupport extends SimpleTagSupport {
    @Override
    void doTag() throws JspException, IOException {
        getJspContext().getOut().println("extendsSimpleTagSupport:output");
    }
}

class BodySimpleTagSupport extends SimpleTagSupport {
    @Override
    void doTag() throws JspException, IOException {
        JspWriter out = getJspContext().getOut()
        out.print("bodySimpleTagSupport:");
        super.getJspBody().invoke(out)
    }
}
