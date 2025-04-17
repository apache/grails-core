package org.grails.web.taglib


import grails.util.GrailsUtil
import org.grails.core.artefact.UrlMappingsArtefactHandler
import org.grails.gsp.GroovyPageBinding
import org.grails.web.util.GrailsApplicationAttributes
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

class JavascriptTagLibTests extends AbstractGrailsTagTests {

    private static final String EOL = new String([(char)13,(char)10] as char[])

    @BeforeEach
    protected void onSetUp() {
        
        gcl.parseClass('''
class TestController {}
''')
    }

    @BeforeEach
    protected void onInit() {
        if (!grailsApplication.getArtefact(UrlMappingsArtefactHandler.TYPE, 'TestUrlMappings')) {
            def urlMappingsClass = gcl.parseClass('''\
class TestUrlMappings {
    static mappings = {
        "/$controller/$action?/$id?" {}
        "/people/details/$var1"(controller: 'person', action: 'show')
    }
}
''')
            grailsApplication.addArtefact(UrlMappingsArtefactHandler.TYPE, urlMappingsClass)
        }
    }

    @Test
    void testJavascriptIncludeWithPluginAttribute() {
        def template = '<g:javascript src="foo.js" plugin="controllers" />'
        def grailsVersion = GrailsUtil.getGrailsVersion()
        assertOutputContains "<script src=\"/plugins/controllers-$grailsVersion/js/foo.js\" type=\"text/javascript\"></script>", template
    }

    @Test
    void testJavascriptInclude() {
        def template = '<g:javascript src="foo.js" />'
        assertOutputContains '<script src="/js/foo.js" type="text/javascript"></script>', template
    }

    @Test
    void testJavascriptIncludeWithPlugin() {
        def template = '<g:javascript src="foo.js" />'
        request.setAttribute(GrailsApplicationAttributes.PAGE_SCOPE, new GroovyPageBinding("/plugin/one"))
        assertOutputContains '<script src="/plugin/one/js/foo.js" type="text/javascript"></script>', template
    }

    @Test
    void testJavascriptIncludeWithContextPathSpecified() {
        def template = '<g:javascript src="foo.js" />'

        request.setAttribute(GrailsApplicationAttributes.PAGE_SCOPE, new GroovyPageBinding("/plugin/one"))
        assertOutputContains '<script src="/plugin/one/js/foo.js" type="text/javascript"></script>', template

        template = '<g:javascript src="foo.js" contextPath="/foo" />'
        assertOutputContains '<script src="/foo/js/foo.js" type="text/javascript"></script>', template
    }

    @Test
    void testJavascriptIncludeWithPluginNoLeadingSlash() {
        def template = '<g:javascript src="foo.js" />'
        request.setAttribute(GrailsApplicationAttributes.PAGE_SCOPE, new GroovyPageBinding("plugin/one"))
        assertOutputContains '<script src="/plugin/one/js/foo.js" type="text/javascript"></script>' + EOL, template
    }

    @Test
    void testPluginAwareJSSrc() {
        StringWriter sw = new StringWriter()
        withTag("javascript", sw) {tag ->
            setupPluginController(tag)
            def attrs = [src: 'lib.js']
            tag.call(attrs) {}
        }
        assertEquals("<script src=\"/myapp/plugins/myplugin/js/lib.js\" type=\"text/javascript\"></script>" + EOL, sw.toString())
    }

    @Test
    void testPluginAwareJSSrcTrailingSlash() {
        StringWriter sw = new StringWriter()
        withTag("javascript", sw) {tag ->
            setupPluginController(tag)
            setRequestContext('/otherapp/')
            def attrs = [src: 'lib.js']
            tag.call(attrs) {}
        }
        assertEquals("<script src=\"/otherapp/plugins/myplugin/js/lib.js\" type=\"text/javascript\"></script>" + EOL, sw.toString())
    }

    @Test
    void testJSSrc() {
        StringWriter sw = new StringWriter()
        withTag("javascript", sw) {tag ->
            def attrs = [src: 'lib.js']
            setRequestContext()
            tag.call(attrs) {}
        }
        assertEquals("<script src=\"/myapp/js/lib.js\" type=\"text/javascript\"></script>" + EOL, sw.toString())
    }

    @Test
    void testJSSrcTrailingSlash() {
        StringWriter sw = new StringWriter()
        withTag("javascript", sw) {tag ->
            def attrs = [src: 'lib.js']
            setRequestContext('/otherapp/')
            tag.call(attrs) {}
        }
        assertEquals("<script src=\"/otherapp/js/lib.js\" type=\"text/javascript\"></script>" + EOL, sw.toString())
    }

    @Test
    void testJSSrcWithNoController() {
        StringWriter sw = new StringWriter()
        withTag("javascript", sw) {tag ->
            def attrs = [src: 'lib.js']
            setRequestContext()
            request.setAttribute(GrailsApplicationAttributes.CONTROLLER, null)
            tag.call(attrs) {}
        }
        assertEquals("<script src=\"/myapp/js/lib.js\" type=\"text/javascript\"></script>" + EOL, sw.toString())
    }

    @Test
    void testJSWithBody() {
        StringWriter sw = new StringWriter()
        withTag("javascript", sw) {tag ->
            setRequestContext()
            tag.call([:]) {"do.this();"}
        }
        assertEquals("<script type=\"text/javascript\">" + EOL + "do.this();" + EOL + "</script>" + EOL, sw.toString())
    }

    @Test
    void testJSSrcWithBase() {
        StringWriter sw = new StringWriter()
        withTag("javascript", sw) {tag ->
            def attrs = [src: 'mylib.js', base: 'http://testserver/static/']
            setRequestContext()
            tag.call(attrs) {}
        }
        assertEquals("<script src=\"http://testserver/static/mylib.js\" type=\"text/javascript\"></script>" + EOL, sw.toString())
    }

    def setRequestContext() {
        setRequestContext("/myapp")
    }

    def setRequestContext(String path) {
        request.setContextPath(path)
    }

    def setupPluginController(tag) {
        setRequestContext()
        request.setAttribute(GrailsApplicationAttributes.PAGE_SCOPE, new GroovyPageBinding("plugins/myplugin"))
    }

    @Test
    void testEscapeJavascript() {
        StringWriter sw = new StringWriter()

        withTag("escapeJavascript", sw) {tag ->
            tag.call(Collections.EMPTY_MAP, "This is some \"text\" to be 'escaped'")
        }
        assertEquals("This is some \\u0022text\\u0022 to be \\u0027escaped\\u0027", sw.toString())
    }

    @Test
    void testJavascriptExpressionCodec() {
        def template = '''<g:javascript>var value='${'<>'}';</g:javascript>'''
        assertOutputEquals('''<script type="text/javascript">\r\nvar value='\\u003c\\u003e';\r\n</script>\r\n''', template)
    }

    @Test
    void testJavascriptExpressionNoCodec() {
        def template = '''<g:javascript encodeAs="none">var value='${'<>'}';</g:javascript>'''
        assertOutputEquals('''<script type="text/javascript">\r\nvar value='<>';\r\n</script>\r\n''', template)
    }

    @Test
    void testJavascriptExpressionRawCodec() {
        def template = '''<g:javascript encodeAs="raw">var value='${'<>'}';</g:javascript>'''
        assertOutputEquals('''<script type="text/javascript">\r\nvar value='<>';\r\n</script>\r\n''', template)
    }

    @Test
    void testJavascriptExpressionEncodeAsRaw() {
        def template = '''<g:javascript>var value='${'<>'.encodeAsRaw()}';</g:javascript>'''
        assertOutputEquals('''<script type="text/javascript">\r\nvar value='<>';\r\n</script>\r\n''', template)
    }

    @Test
    void testJavascriptExpressionRaw() {
        def template = '''<g:javascript>var value='${raw('<>')}';</g:javascript>'''
        assertOutputEquals('''<script type="text/javascript">\r\nvar value='<>';\r\n</script>\r\n''', template)
    }

    // GRAILS-10985
    @Test
    void testJavascriptExpressionRawAndEscaped() {
        withConfig("grails.views.default.codec='HTML'") {
            def template = '''<g:javascript>var value='${raw('<>'.intern())}${'<>'.intern()}';</g:javascript>'''
            assertOutputEquals('''<script type="text/javascript">\r\nvar value='<>\\u003c\\u003e';\r\n</script>\r\n''', template)
        }
    }

    @Test
    void testJavascriptExpressionNoneDefaultCodecLegacySettings() {
        withConfig("grails.views.default.codec='none'") {
            def template = '''<g:javascript>var value='${'<>'}';</g:javascript>'''
            assertOutputEquals('''<script type="text/javascript">\r\nvar value='<>';\r\n</script>\r\n''', template)
        }
    }

    @Test
    void testJavascriptExpressionNoneDefaultCodecNewSettings() {
        withConfig('''
grails {
    views {
        gsp {
            codecs {
                expression = 'none'
                scriptlet = 'none'
                taglib = 'none'
                staticparts = 'none'
            }
        }
    }
}
''') {
            def template = '''<g:javascript>var value='${'<>'}';</g:javascript>'''
            assertOutputEquals('''<script type="text/javascript">\r\nvar value='<>';\r\n</script>\r\n''', template)
        }
    }

    @Test
    void testJavascriptExpressionHtmlDefaultCodecNewSettings() {
        withConfig('''
grails {
    views {
        gsp {
            codecs {
                expression = 'html'
                scriptlet = 'html'
                taglib = 'none'
                staticparts = 'none'
            }
        }
    }
}
''') {
            def template = '''<g:javascript>var value='${'<>'}';</g:javascript>'''
            assertOutputEquals('''<script type="text/javascript">\r\nvar value='\\u003c\\u003e';\r\n</script>\r\n''', template)
        }
    }
}
