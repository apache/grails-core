package org.grails.gsp

import grails.config.Config
import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.util.GrailsUtil
import org.grails.config.PropertySourcesConfig
import org.grails.core.io.MockStringResourceLoader
import org.grails.gsp.compiler.GroovyPageParser
import org.junit.jupiter.api.Test
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.UrlResource

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

class GroovyPagesTemplateEngineTests {

    @Test
    void testCommentAtEndOfTemplate() {
        System.setProperty("grails.env", "development")
        assert GrailsUtil.isDevelopmentEnv()

        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        // It is important that the template ends with the comment. Whitespace or anything else after
        // the comment will not trigger the problem.  See GRAILS-1737
        def pageSource = "<html><body></body></html><%-- should not be in the output --%>"

        def t = gpte.createTemplate(pageSource, "comment_test")
        def w = t.make()
        w.showSource = true

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        assertTrue(sw.toString().indexOf("should not be in the output") == -1)
    }

    @Test
    void testShowSourceParameter() {
        try {
            System.setProperty("grails.env", "development")
            assert GrailsUtil.isDevelopmentEnv()

            def gpte = new GroovyPagesTemplateEngine()
            gpte.afterPropertiesSet()

            def t = gpte.createTemplate("<%='hello'%>", "hello_test")
            def w = t.make()
            w.showSource = true

            def sw = new StringWriter()
            def pw = new PrintWriter(sw)

            w.writeTo(pw)

            assertTrue(sw.toString().indexOf(GroovyPage.OUT_STATEMENT + ".print('hello')") > -1)

        }
        finally {
            System.setProperty("grails.env", "")
        }
    }

    @Test
    void testEstablishNameForResource() {
        def res = new UrlResource("http://grails.org/some.path/foo.gsp")

        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        assertEquals "some_path_foo_gsp", gpte.establishPageName(res, null)
    }

    @Test
    void testCreateTemplateFromCurrentRequest2() {
        def uri1 = "/another"

        def rl = new MockStringResourceLoader()
        rl.registerMockResource(uri1, "<%='success 2'%>")

        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        gpte.groovyPageLocator.addResourceLoader(rl)

        def t = gpte.createTemplate(uri1)
        def w = t.make()

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        assertEquals "success 2", sw.toString()
    }

    @Test
    void testCreateTemplateFromCurrentRequest1() {
        def uri1 = "/somedir/myview"
        def uri2 = "/another"

        def rl = new MockStringResourceLoader()
        rl.registerMockResource(uri1, "<%='success 1'%>")
        rl.registerMockResource(uri2, "<%='success 2'%>")

        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        gpte.groovyPageLocator.addResourceLoader(rl)

        def t = gpte.createTemplate(uri1)
        def w = t.make()

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        assertEquals "success 1", sw.toString()
    }

    @Test
    void testCreateTemplateFromResource() {
        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        def t = gpte.createTemplate(new ByteArrayResource("<%='hello'%>".bytes))
        def w = t.make()

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        assertEquals "hello", sw.toString()
    }

    @Test
    void testNestingGroovyExpressionInAttribute() {
        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        def src = '''<g:actionSubmit onclick="return confirm('${message}')"/>'''
        def t = gpte.createTemplate(src, "hello_test")

        def w = t.make(message: 'Are You Sure')

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        assertEquals '''<g:actionSubmit onclick="return confirm('Are You Sure')"/>''', sw.toString()
    }

    private GrailsApplication createMockGrailsApplication(Config config = null) {
        if (config == null) {
            config = new PropertySourcesConfig()
            config.put(GroovyPageParser.CONFIG_PROPERTY_GSP_KEEPGENERATED_DIR, System.getProperty("java.io.tmpdir"))
        }
        [getMainContext: { ->  null},  getConfig: { ->  config} , getFlatConfig: { -> config.flatten() } , getArtefacts: { String artefactType -> [] as GrailsClass[] }, getArtefactByLogicalPropertyName: { String type, String logicalName ->  null} ] as GrailsApplication
    }

    @Test
    void testParsingNestedCurlyBraces() {
        // GRAILS-7915
        def gpte = new GroovyPagesTemplateEngine()
        gpte.grailsApplication = createMockGrailsApplication()
        gpte.afterPropertiesSet()

        def src = '${people.collect {it.firstName}}'
        def t = gpte.createTemplate(src, "hello_test")

        def people = [[firstName: 'Peter', lastName: 'Gabriel'], [firstName: 'Phil', lastName: 'Collins']]
        def w = t.make(people: people)

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        assertEquals "[Peter, Phil]", sw.toString()

        src = '''
        <g:if env="production">
        </g:if>

        <script type="text/javascript">
        try {
        <g:if test="${title == 'Selling England By The Pound'}">
        ${bandName}
        </g:if>
        } catch( err ) {}
        </script>
'''

        gpte.createTemplate(src, "hello_test2")
        t = gpte.createTemplate(src, "hello_test2")
        w = t.make(bandName: 'Genesis', title: 'Selling England By The Pound')

        sw = new StringWriter()
        pw = new PrintWriter(sw)

        w.writeTo(pw)

        def expected = '''
        

        <script type="text/javascript">
        try {
        
        Genesis
        
        } catch( err ) {}
        </script>
'''
        assertEquals expected, sw.toString()
    }

    @Test
    void testParsingParensInNestedCurlyBraces() {
        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        def src = '${people.collect {it.firstName.toUpperCase()}}'
        def t = gpte.createTemplate(src, "hello_test")

        def people = [[firstName: 'Peter', lastName: 'Gabriel'], [firstName: 'Phil', lastName: 'Collins']]
        def w = t.make(people: people)

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        assertEquals "[PETER, PHIL]", sw.toString()
    }

    @Test
    void testParsingBracketsInNestedCurlyBraces() {
        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        def src = '${people.collect {it.lastName[0]}}'
        def t = gpte.createTemplate(src, "hello_test")

        def people = [[firstName: 'Peter', lastName: 'Gabriel'], [firstName: 'Phil', lastName: 'Collins']]
        def w = t.make(people: people)

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        assertEquals "[G, C]", sw.toString()
    }

    @Test
    void testParsingIfs() {
        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        def src = '''<g:if test="${var=='1' || var=='2'}">hello</g:if>'''

        def t = gpte.createTemplate(src, "if_test")

        def w = t.make(var: '1')

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        assertEquals "hello", sw.toString()
    }

    @Test
    void testParsingMultilineQuotes() {
        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        def src = '''${{var=='1'}()?"""start.
This is a multi-line string. } 
end.""":''}
<g:if test="${var=='1' || var=='2'}">hello</g:if>
'''

        def t = gpte.createTemplate(src, "test_multiline_str")

        def w = t.make(var: '1')

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        def result='''start.
This is a multi-line string. } 
end.
hello
'''

        assertEquals result, sw.toString()
    }

    @Test
    void testParsingMultilineQuotes2() {
        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        def src = '''${{var=='1'}()?\'\'\'start.
This is a multi-line string. }
end.\'\'\':''}
<g:if test="${var=='1' || var=='2'}">hello</g:if>
'''

        def t = gpte.createTemplate(src, "test_multiline_str")

        def w = t.make(var: '1')

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        def result='''start.
This is a multi-line string. }
end.
hello
'''

        assertEquals result, sw.toString()
    }

    @Test
    void testGscript() {
        def gpte = new GroovyPagesTemplateEngine()
        gpte.grailsApplication = createMockGrailsApplication()
        gpte.afterPropertiesSet()

        def src = '''@{page defaultCodec="HTML"}%{if(var=='1') { out.print('hello') } else { out.print('not_ok') } }%'''

        def t = gpte.createTemplate(src, "gscript_test")

        def w = t.make(var: '1')

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        assertEquals "hello", sw.toString()
    }

    @Test
    void testGRAILS8218() {
        def gpte = new GroovyPagesTemplateEngine()

        gpte.afterPropertiesSet()

        def src = '''<g:if test='[pwd:"${actionName}-xx"]'>ok</g:if>'''

        def t = gpte.createTemplate(src, "testGRAILS8218")

        def w = t.make(actionName: 'hello')

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        assertEquals "ok", sw.toString()
    }

    @Test
    void testGRAILS8199() {
        def gpte = new GroovyPagesTemplateEngine()

        gpte.afterPropertiesSet()

        def src = '''<div id='${map["${id}_postfix"]}'/>'''

        def t = gpte.createTemplate(src, "testGRAILS8199")

        def w = t.make(id: 'id', map:[id_postfix:'hello'])

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        assertEquals "<div id='hello'/>", sw.toString()
    }

    @Test
    void testParsingQuotes() {
        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        def src = '''<g:if test="${var=="1" || var=="2" || var=='}' || var=="{" || var=='"' || var=="\\"" || var=='' || var=="" }">hello</g:if>'''

        def t = gpte.createTemplate(src, "if_test")

        def w = t.make(var: '1')
        def sw = new StringWriter()
        def pw = new PrintWriter(sw)
        w.writeTo(pw)

        assertEquals "hello", sw.toString()

        w = t.make(var: '"')
        sw = new StringWriter()
        pw = new PrintWriter(sw)
        w.writeTo(pw)
        assertEquals "hello", sw.toString()
    }

    @Test
    void testCreateTemplateWithBinding() {
        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        def t = gpte.createTemplate('Hello ${foo}', "hello_test")
        def w = t.make(foo:"World")

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        assertEquals "Hello World", sw.toString()
    }

    @Test
    void testInlineScriptWithValidUnmatchedBrackets() {
        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        def t = gpte.createTemplate('''
<% if(true) { %>
Hello ${foo}
<% } %>
<% if(false) { %>
never
<% } else { %>
  
<% } %>
''', "hello_test")
        def w = t.make(foo:"World")

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        assertEquals "Hello World", sw.toString().trim()
    }

    @Test
    void testInlineScriptWithValidUnmatchedBracketsGspSyntax() {
        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        def t = gpte.createTemplate('''
%{ if(true) { }%
Hello ${foo}
%{ } }%
%{ if(false) { }%
never
%{ } else { }%
  
%{ } }%
''', "hello_test")
        def w = t.make(foo:"World")

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        assertEquals "Hello World", sw.toString().trim()
    }

    @Test
    void testCreateTemplateFromText() {
        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        def t = gpte.createTemplate("<%='hello'%>", "hello_test")
        def w = t.make()

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)

        assertEquals "hello", sw.toString()
    }

    @Test
    void testForEachInProductionMode() {
        System.setProperty("grails.env", "production")

        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        def t = gpte.createTemplate("<g:each var='num' in='\${1..5}'>\${num} </g:each>", "foreach_test")
        def w = t.make()

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)

        w.writeTo(pw)
        System.setProperty("grails.env", "development")

        assertEquals "1 2 3 4 5 ", sw.toString()
    }

    @Test
    void testGetUriWithinGrailsViews() {
        def gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()

        assertEquals "/WEB-INF/grails-app/views/myview.gsp", gpte.getUriWithinGrailsViews("/myview")
        assertEquals "/WEB-INF/grails-app/views/myview.gsp", gpte.getUriWithinGrailsViews("myview")
        assertEquals "/WEB-INF/grails-app/views/mydir/myview.gsp", gpte.getUriWithinGrailsViews("mydir/myview")
        assertEquals "/WEB-INF/grails-app/views/mydir/myview.gsp", gpte.getUriWithinGrailsViews("/mydir/myview")
    }

}
