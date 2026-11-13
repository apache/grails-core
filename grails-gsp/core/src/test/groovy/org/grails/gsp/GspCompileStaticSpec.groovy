/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.gsp

import java.util.concurrent.atomic.AtomicLong

import grails.core.gsp.GrailsTagLibClass
import org.grails.core.gsp.DefaultGrailsTagLibClass
import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import org.grails.taglib.TagLibraryLookup
import spock.lang.Specification


class GspCompileStaticSpec extends Specification {

    GroovyPagesTemplateEngine gpte

    def setup() {
        gpte = new GroovyPagesTemplateEngine()
        gpte.afterPropertiesSet()
        def tagLibraryLookup = new TagLibraryLookup() {
            @Override
            protected void putTagLib(Map<String, Object> tags, String name, GrailsTagLibClass taglib) {
                tags.put(name, taglib.newInstance())
            }
        }
        tagLibraryLookup.registerTagLib(new DefaultGrailsTagLibClass(SampleTagLib))
        gpte.tagLibraryLookup = tagLibraryLookup
    }

    def "should support model fields in both compilation modes"() {
        given:
        def template = """<%@ model="Date date" compileStatic="$compileStatic"%>\${date.time}"""
        def date = new Date(123L)
        when:
        def rendered = renderTemplate(template, [date: date], compileStatic)
        then:
        rendered == '123'
        where:
        compileStatic << [true, false]
    }

    def "specifying model implies compileStatic mode"() {
        given:
        def template = '<%@ model="Date date"%>${date.time}'
        def date = new Date(123L)
        when:
        def rendered = renderTemplate(template, [date: date], true)
        then:
        rendered == '123'
    }

    def "should support typed variables in both compilation modes"() {
        given:
        def template = """<%@ compileStatic="$compileStatic"%><g:def type="Date" var="date" value="\${new Date(123L)}"/>\${date.time}"""
        when:
        def rendered = renderTemplate(template, [:], compileStatic)
        then:
        rendered == '123'
        where:
        compileStatic << [true, false]
    }

    def "should support g:each in both compilation modes"() {
        given:
        def template = """<%@ model="List<Date> dates" compileStatic="$compileStatic"%><g:each var="date" in="\${dates}">\${date.time},</g:each>"""
        def model = [dates: [new Date(123L), new Date(456L), new Date(789L)]]
        when:
        def rendered = renderTemplate(template, model, compileStatic)
        then:
        rendered == '123,456,789,'
        where:
        compileStatic << [true, false]
    }

    def "should support message tag invocation"() {
        given:
        def template = '<%@ compileStatic="true"%>${' + (gDotPrefix ? 'g.' : '') + '''message(code:'World')}'''
        when:
        def rendered = renderTemplate(template, [:], true)
        then:
        rendered == 'Hello World'
        where:
        gDotPrefix << [false, true]
    }

    def "should support message tag invocation inline"() {
        given:
        def template = """<%@ compileStatic="true"%><%

out.print(${gDotPrefix ? 'g.' : ''}message(code:'World'))

%>"""
        when:
        def rendered = renderTemplate(template, [:], true)
        then:
        rendered == 'Hello World'
        where:
        gDotPrefix << [false, true]
    }

    def "should support message tag invocation inline in a closure"() {
        given:
        def template = """<%@ compileStatic="true"%><%

def messageClosure = { code -> ${gDotPrefix ? 'g.' : ''}message(code:code) }
out.print(messageClosure('World'))

%>"""
        when:
        def rendered = renderTemplate(template, [:], true)
        then:
        rendered == 'Hello World'
        where:
        gDotPrefix << [false, true]
    }

    def "should fail compilation when calling invalid property"() {
        given:
        def template = '''<%@ model="Date d1=new Date(123L)"%>${d1.timetypo}'''
        when:
        def t = gpte.createTemplate(template, "template")
        then:
        t.metaInfo.compilationException.message.contains('No such property: timetypo for class: java.util.Date')
    }

    def "should fail compilation when calling invalid method"() {
        given:
        def template = '''<%@ model="Date d1=new Date(123L)"%>${d1.getTimeTypo()}'''
        when:
        def t = gpte.createTemplate(template, "template")
        then:
        t.metaInfo.compilationException.message.contains('Cannot find matching method java.util.Date#getTimeTypo()')
    }

    def "should fail compilation when using invalid property"() {
        given:
        def template = '''<%@ model="Date date"%>${somename}'''
        when:
        def t = gpte.createTemplate(template, "template")
        then:
        t.metaInfo.compilationException.message.contains('The variable [somename] is undeclared.')
    }

    def "should fail compilation when calling method on invalid property"() {
        given:
        def template = '''<%@ model="Date date"%>${somename.somemethod([a: 1])}'''
        when:
        def t = gpte.createTemplate(template, "template")
        then:
        t.metaInfo.compilationException.message.contains('The variable [somename] is undeclared.')
    }

    def "should pass compilation when taglib is defined"() {
        given:
        def template = '''<%@ model="Date date" taglibs="firsttaglib, sometaglib, athirdone"%>${sometaglib.something([a: 1])}'''
        when:
        def t = gpte.createTemplate(template, "template")
        then:
        noExceptionThrown()
    }

    def "an escaped expression is invisible to static compilation"() {
        given:
        def template = '''<%@ compileStatic="true"%>console.log(`Hello \\${undeclaredJsVariable}`);'''
        when:
        def rendered = renderTemplate(template, [:], true)
        then:
        rendered == 'console.log(`Hello ${undeclaredJsVariable}`);'
    }

    def "should support multi-line model declaration"() {
        given:
        def template = '''<%@ model="""
Date d1=new Date(123L)
Date d2=new Date(456L)
Date d3=new Date(789L)
Date d4=new Date(123L)
"""%>${d1.time}-${d2.time}-${d3.time}-${d4.time}'''
        when:
        def rendered = renderTemplate(template, [:], true, true)
        then:
        rendered == '123-456-789-123'
    }

    def "should support multi-line model declaration using single quotes"() {
        given:
        def template = """<%@ model='''
Date d1=new Date(123L)
Date d2=new Date(456L)
Date d3=new Date(789L)
Date d4=new Date(123L)
'''%>\${d1.time}-\${d2.time}-\${d3.time}-\${d4.time}"""
        when:
        def rendered = renderTemplate(template, [:], true, true)
        then:
        rendered == '123-456-789-123'
    }

    def "multiple model fields can be separated with semicolons"() {
        given:
        def template = '''<%@ model="Date d1=new Date(123L); Date d2=new Date(456L); Date d3=new Date(789L); Date d4=new Date(123L)"%>${d1.time}-${d2.time}-${d3.time}-${d4.time}'''
        when:
        def rendered = renderTemplate(template, [:], true, true)
        then:
        rendered == '123-456-789-123'
    }

    def "fields can be added by using alternative syntax"() {
        given:
        def template = '''@{ model="""Date d1=new Date(123L); Date d2=new Date(456L); Date d3=new Date(789L); Date d4=new Date(123L) """}${d1.time}-${d2.time}-${d3.time}-${d4.time}'''
        when:
        def rendered = renderTemplate(template, [:], true, true)
        then:
        rendered == '123-456-789-123'
    }

    def "model fields can be added by multiple declarations"() {
        given:
        def template = '''@{ model="Date d1=new Date(123L); Date d2=new Date(456L)"}@{ model="Date d3=new Date(789L); Date d4=new Date(123L)"}${d1.time}-${d2.time}-${d3.time}-${d4.time}'''
        when:
        def rendered = renderTemplate(template, [:], true, true)
        then:
        rendered == '123-456-789-123'
    }

    def "model field is applied when the model supplies the declared type"() {
        given:
        def template = '''@{ model="Long sampleCount"}${sampleCount}'''
        when:
        def rendered = renderTemplate(template, [sampleCount: 42L], true)
        then:
        rendered == '42'
    }

    def "a Number model field accepts every numeric type a controller may supply"() {
        given:
        def template = '''@{ model="Number sampleCount"}${sampleCount}'''
        when:
        def rendered = renderTemplate(template, [sampleCount: supplied], true)
        then:
        rendered == '42'
        where:
        // scaffolded controllers supply Long via the generated service and Integer
        // via RestfulController.countResources()
        supplied << [42 as Integer, 42L, 42 as Short, 42 as BigInteger]
    }

    def "a model value is converted to the declared type the way a Groovy assignment converts it"() {
        given:
        def template = """@{ model="${declared} sampleCount"}\${sampleCount}"""
        when:
        def rendered = renderTemplate(template, [sampleCount: supplied], true)
        then:
        rendered == expected
        where:
        declared     | supplied                 | expected
        'Integer'    | 42L                      | '42'
        'int'        | 42L                      | '42'
        'Long'       | 42                       | '42'
        'long'       | (42 as Short)            | '42'
        'Integer'    | 42.0G                    | '42'
        'String'     | "${40 + 2}"              | '42'
        'Double'     | 0.1f                     | Double.toString((double) 0.1f)
        'double'     | 1.1f                     | Double.toString((double) 1.1f)
        'Float'      | 0.5d                     | '0.5'
        'float'      | -0.0d                    | '-0.0'
        'Double'     | Float.NaN                | 'NaN'
        'Float'      | Double.POSITIVE_INFINITY | 'Infinity'
        'Double'     | Double.NEGATIVE_INFINITY | '-Infinity'
        'BigDecimal' | 0.5d                     | '0.5'
        'Double'     | 0.5G                     | '0.5'
        'Double'     | 0.1G                     | '0.1'
        'double'     | 19.99G                   | '19.99'
        'Float'      | 19.99G                   | '19.99'
        'BigDecimal' | 0.1d                     | '0.1'
        'BigDecimal' | 19.99d                   | '19.99'
        'BigDecimal' | 0.1f                     | '0.1'
        'BigDecimal' | 0.30000000000000004d     | '0.30000000000000004'
        'Double'     | new BigInteger('1' + '0' * 23) | '1.0E23'
        'Long'       | new AtomicLong(42L)      | '42'
    }

    def "a conversion that would change the value names the field and both types"() {
        given:
        def template = """@{ model="${declared} sampleCount"}\${sampleCount}"""
        when:
        renderTemplate(template, [sampleCount: supplied], true)
        then: 'the value converted, so it is the guard that refused it'
        GroovyPagesException e = thrown()
        e.message.contains("Model field 'sampleCount'")
        e.message.contains(declaredName)
        e.message.contains(supplied.getClass().name)
        e.message.contains('without changing it')
        e.cause == null
        where:
        declared     | supplied                | declaredName
        'Integer'    | 3_000_000_000L          | 'java.lang.Integer'
        'int'        | 3_000_000_000L          | 'int'
        'Integer'    | 42.9G                   | 'java.lang.Integer'
        'Integer'    | 42.5d                   | 'java.lang.Integer'
        'Short'      | 70_000                  | 'java.lang.Short'
        'Float'      | 0.1d                    | 'java.lang.Float'
        'float'      | 1.1d                    | 'float'
        'Double'     | Long.MAX_VALUE          | 'java.lang.Double'
        'Double'     | 123456789012345678L     | 'java.lang.Double'
        'Float'      | 16_777_217              | 'java.lang.Float'
        'Double'     | 9_007_199_254_740_993G  | 'java.lang.Double'
        'Long'       | 1.0E+23G                | 'java.lang.Long'
        'Integer'    | Double.NaN              | 'java.lang.Integer'
    }

    def "a model value that cannot be converted names the field and both types"() {
        given:
        def template = """@{ model="${declared} sampleCount"}\${sampleCount}"""
        when:
        renderTemplate(template, [sampleCount: supplied], true)
        then: 'Groovy refused the conversion, so the page reports it with the cause'
        GroovyPagesException e = thrown()
        e.message.contains("Model field 'sampleCount'")
        e.message.contains(declaredName)
        e.message.contains(supplied.getClass().name)
        e.message.contains('cannot be converted')
        cause.isInstance(e.cause)
        where:
        declared  | supplied                | declaredName       | cause
        'Integer' | new Date()              | 'java.lang.Integer' | GroovyCastException
        'Double'  | new BigDecimal('1E400') | 'java.lang.Double'  | GroovyRuntimeException
        'Double'  | Float.NEGATIVE_INFINITY | 'java.lang.Double'  | GroovyRuntimeException
    }

    def "a non-finite value that cannot be converted names the field and both types"() {
        given:
        def template = """@{ model="${declared} sampleCount"}\${sampleCount}"""
        when:
        renderTemplate(template, [sampleCount: supplied], true)
        then:
        GroovyPagesException e = thrown()
        e.message.contains("Model field 'sampleCount'")
        e.message.contains("java.math.${declared}")
        e.message.contains(supplied.getClass().name)
        e.cause instanceof NumberFormatException
        where:
        [declared, supplied] << [['BigDecimal', 'BigInteger'],
                                [Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY]].combinations()
    }

    def renderTemplate(templateSource, model, expectedCompileStaticMode, printSource = false) {
        def t = gpte.createTemplate(templateSource, "template${templateSource.hashCode()}")
        assert t.metaInfo.compilationException == null
        def w = t.make(model)
        if(printSource) {
            def sourceWriter = new StringWriter()
            w.writeGroovySourceToResponse(w.metaInfo, sourceWriter)
            println(sourceWriter.toString())
        }
        assert w.metaInfo.compileStaticMode == expectedCompileStaticMode
        def sw = new StringWriter()
        def pw = new PrintWriter(sw, true)
        w.writeTo(pw)
        sw.toString()
    }
}

class SampleTagLib {
    static returnObjectForTags = ['message']

    Closure message = { attrs ->
        "Hello ${attrs.code}"
    }
}
