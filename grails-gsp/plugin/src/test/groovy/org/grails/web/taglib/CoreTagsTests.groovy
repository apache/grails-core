package org.grails.web.taglib

import grails.util.Environment
import org.grails.taglib.GrailsTagException
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

/**
 * Tests some of the core tags when rendering inside GSP.
 *
 * @author Graeme Rocher
 * @since 0.6
 */
class CoreTagsTests extends AbstractGrailsTagTests {

    @Override
    protected void onSetUp() {
        System.setProperty(Environment.KEY, "development")
    }

    @Override
    protected void onDestroy() {
        System.setProperty(Environment.KEY, "")
    }

    @Test
    void testUnlessWithTestCondition() {
        def template = '<g:unless test="${cond}">body text</g:unless>'
        assertOutputEquals 'body text', template, [cond: false], {it.toString().trim()}
        assertOutputEquals '', template, [cond: true], {it.toString().trim()}
    }

    @Test
    void testUnlessWithEnvCondition() {
        def template = '<g:unless env="production">body text</g:unless>'
        assertOutputEquals 'body text', template, [:], {it.toString().trim()}
        template = '<g:unless env="development">body text</g:unless>'
        assertOutputEquals '', template, [:], {it.toString().trim()}
    }

    @Test
    void testUnlessWithEnvAndTestConditions() {
        def template = '<g:unless env="production" test="${cond}">body text</g:unless>'
        assertOutputEquals 'body text', template, [cond: false], {it.toString().trim()}
        assertOutputEquals 'body text', template, [cond: true], {it.toString().trim()}

        template = '<g:unless env="development" test="${cond}">body text</g:unless>'
        assertOutputEquals 'body text', template, [cond: false], {it.toString().trim()}
        assertOutputEquals '', template, [cond: true], {it.toString().trim()}
    }

    @Test
    void testIfElse() {

        def template = '''
<g:if test="${foo}">foo</g:if>
<g:else>bar</g:else>
'''

        assertOutputEquals("foo", template, [foo:true], { it.toString().trim() })
        assertOutputEquals("bar", template, [foo:false], { it.toString().trim() })
    }

   void testIfElseWithSpace() {
       def template = '''
<g:if test="${foo}">
foo
</g:if>

<g:else>
bar
</g:else>
   '''

       assertOutputEquals("foo", template, [foo:true]) { it.toString().trim() }
       assertOutputEquals("bar", template, [foo:false]) { it.toString().trim() }
   }

    @Test
    void testIfWithEnv() {
        def template = '''
<g:if env="testing" test="${foo}">foo</g:if>
'''
        assertOutputEquals("", template, [foo:true], { it.toString().trim() })

        // Here we assume "development" is the env during tests
        def template2 = '''
<g:if env="development" test="${foo}">foo</g:if>
'''
        assertOutputEquals("foo", template2, [foo:true], { it.toString().trim() })
    }

    @Test
    void testIfWithEnvAndWithoutTestAttribute() {
        def template = '''<g:if env="development">foo</g:if>'''
        assertOutputEquals("foo", template)
    }

    @Test
    void testIfWithoutEnvAndTestAttributes() {
        assertThrows(GrailsTagException, {
            applyTemplate("<g:if>foo</g:if>")
        })
    }

    @Test
    void testElseIf() {
        def template = '''
<g:if test="${foo}">foo</g:if>
<g:elseif env="development">bar</g:elseif>
'''
        assertOutputEquals("bar", template, [foo:false], { it.toString().trim() })

        template = '''
<g:if test="${foo}">foo</g:if>
<g:elseif test="${!foo}">bar</g:elseif>
'''
        assertOutputEquals("bar", template, [foo:false], { it.toString().trim() })

        template = '''
<g:if test="${foo}">foo</g:if>
<g:elseif test="${foo}" env="development">bar</g:elseif>
'''
        assertOutputEquals("", template, [foo:false], { it.toString().trim() })

        template = '''
<g:if test="${foo}">foo</g:if>
<g:elseif test="${!foo}" env="development">bar</g:elseif>
'''
        assertOutputEquals("bar", template, [foo:false], { it.toString().trim() })
    }
}
