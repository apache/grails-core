package org.grails.web.pages

import grails.testing.web.taglib.TagLibUnitTest
import grails.util.Environment
import org.grails.gsp.GroovyPagesException
import org.grails.plugins.web.taglib.ApplicationTagLib
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class GroovyPageRenderingTests extends Specification implements TagLibUnitTest<ApplicationTagLib> {

    def testGroovyPageExpressionExceptionInDevelopmentEnvironment() {
        when:
        def template = '${foo.bar.next}'
        String output = applyTemplate(template)
        then:
        thrown GroovyPagesException
    }

    def testGroovyPageExpressionExceptionInOtherEnvironments() {

        when:
        def template = '${foo.bar.next}'
        System.setProperty(Environment.KEY, "production")
        applyTemplate(template)

        then:
        thrown NullPointerException
    }

    def cleanupSpec() {
        System.setProperty(Environment.KEY, "")
    }

    def testForeach() {
        when:
        def template='<g:each in="${toplist}"><g:each var="t" in="${it.sublist}">${t}</g:each></g:each>'
        def result = applyTemplate(template, [toplist: [[sublist:['a','b']],[sublist:['c','d']]]])

        then:
        result == 'abcd'
    }

    def testForeachInTagbody() {
        when:
        def template='<g:set var="p"><g:each in="${toplist}"><g:each var="t" in="${it.sublist}">${t}</g:each></g:each></g:set>${p}'
        def result = applyTemplate(template, [toplist: [[sublist:['a','b']],[sublist:['c','d']]]])

        then:
        result == 'abcd'
    }

    def testForeachIteratingMap() {
        when:
        def template='<g:each var="k,v" in="[a:1,b:2,c:3]">${k}=${v},</g:each>'
        def result = applyTemplate(template, [:])

        then:
        result == 'a=1,b=2,c=3,'
    }

    def testForeachRenaming() {
        when:
        def template='<g:each in="${list}"><g:each in="${list}">.</g:each></g:each>'
        def result=applyTemplate(template, [list: 1..10])

        then:
        result == '.' * 100
    }

    def testForeachGRAILS8089() {
        when:
        def template='''<g:each in="${mockGrailsApplication.domainClasses.findAll{it.clazz=='we' && (it.clazz != 'no')}.sort({a,b->a.fullName.compareTo(b.fullName)})}"><option value="${it.fullName}"><g:message code="content.item.name.${it.fullName}" encodeAs="HTML"/></option></g:each>'''
        def result=applyTemplate(template, [mockGrailsApplication: [domainClasses: [[fullName: 'MyClass2', clazz:'we'], [fullName: 'MyClass1', clazz:'we'], [fullName: 'MyClass3', clazz:'no']] ]])

        then:
        result == '<option value="MyClass1">content.item.name.MyClass1</option><option value="MyClass2">content.item.name.MyClass2</option>'
    }

    def testMultilineAttributeGRAILS8253() {
        when:
        def template='''<html>
<head>
<title>Sample onclick issue page</title>
</head>
<body>
<g:form name="testForm" controller="begin" action="create">
<g:textField name="testField"/>
<g:actionSubmit class="buttons" action="testAction" value="This
is a test action description"
onclick="if (testForm.testField.value=='') { alert('Please enter some text.'); return false; }"
/>
</g:form>
</body>
</html>'''
        def result=applyTemplate(template, [:])

        then:
        result == '''<html>
<head>
<title>Sample onclick issue page</title>
</head>
<body>
<form action="/begin/create" method="post" name="testForm" id="testForm" >
<input type="text" name="testField" value="" id="testField" />
<input type="submit" name="_action_testAction" value="This
is a test action description" class="buttons" onclick="if (testForm.testField.value==&#39;&#39;) { alert(&#39;Please enter some text.&#39;); return false; }" />
</form>
</body>
</html>'''
    }

    def testNestedExpression() {
        when:
        def template='''<g:set var="a" value="hello"/><g:set var="b" value='${[test: "${a} ${a}"]}'/>${b.test}'''
        def result = applyTemplate(template, [:])

        then:
        result == 'hello hello'
    }

    def testGstring() {
        when:
        def template='''<g:set var="a" value="hello"/><g:set var="b" value='${"${a} ${a}"}'/>${b}'''
        def result = applyTemplate(template, [:])

        then:
        result == 'hello hello'
    }

    def testGstring2() {
        when:
        def template='''<g:set var="a" value="hello"/><g:set var="b" value='${a} ${a}'/>${b}'''
        def result = applyTemplate(template, [:])

        then:
        result == 'hello hello'
    }

    def testGstring3() {
        when:
        def template='''<g:set var="a" value="hello"/><g:set var="b" value='${a} hello'/>${b}'''
        def result = applyTemplate(template, [:])

        then:
        result == 'hello hello'
    }

    def testGstring4() {
        when:
        def template='''<g:set var="a" value="hello"/><g:set var="b" value='hello ${a}'/>${b}'''
        def result = applyTemplate(template, [:])

        then:
        result == 'hello hello'
    }

    def testGstring5() {
        when:
        def template='''<g:set var="a" value="hello"/><g:set var="b" value='hello ${a} hello'/>${b}'''
        def result = applyTemplate(template, [:])

        then:
        result == 'hello hello hello'
    }

    def testNotGstring() {
        when:
        def template='''<g:set var="a" value="hello"/><g:set var="b" value="${'hello ${a} hello'}"/>${b}'''
        def result = applyTemplate(template, [:])

        then:
        result == 'hello ${a} hello'
    }

    def testNotGstring2() {
        when:
        def template='''<g:set var="a" value="hello"/><g:set var="b" value='${"hello \\${a} hello"}'/>${b}'''
        def result = applyTemplate(template, [:])

        then:
        result == 'hello ${a} hello'
    }

    def testNotGstring3() {
        when:
        def template='''<g:set var="a" value="hello"/><g:set var="b" value="${a + '${'}"/>${b}'''
        def result = applyTemplate(template, [:])

        then:
        result == 'hello${'
    }

    def testNestedExpressionInMap() {
        when:
        def template='''<g:set var="a" value="hello"/><g:set var="b" value='[test: "${a} ${a}"]'/>${b.test}'''
        def result = applyTemplate(template, [:])

        then:
        result == 'hello hello'
    }
}
