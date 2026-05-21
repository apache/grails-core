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
package org.grails.web.taglib

import grails.core.DefaultGrailsApplication
import grails.gsp.TagLib
import org.grails.taglib.NamespacedTagDispatcher
import org.grails.taglib.TagLibraryLookup
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.support.GenericWebApplicationContext
import spock.lang.Issue
import spock.lang.Specification
import spock.util.mop.ConfineMetaClassChanges

/**
 * Created by graemerocher on 13/05/14.
 */
class TagLibraryLookupSpec extends Specification {


    @Issue('GRAILS-11396')
    @ConfineMetaClassChanges([NamespacedTagDispatcher, OneTagLib, TwoTagLib])
    void "Test that TagLibraryLookup correctly registers namespace dispatchers"() {
        given:"A lookup instance"
            def lookup = new TagLibraryLookup()

            def application = new DefaultGrailsApplication([OneTagLib, TwoTagLib] as Class[], TagLibraryLookup.class.classLoader)
            application.initialise()
            def applicationContext = new GenericWebApplicationContext()

            applicationContext.defaultListableBeanFactory.registerSingleton(OneTagLib.name, new OneTagLib(tagLibraryLookup: lookup))
            applicationContext.defaultListableBeanFactory.registerSingleton(TwoTagLib.name, new TwoTagLib(tagLibraryLookup: lookup))
            applicationContext.defaultListableBeanFactory.registerSingleton("gspTagLibraryLookup", lookup)
            // instanceTagLibraryApi(TagLibraryApi, pluginManager)
            applicationContext.refresh()

            lookup.grailsApplication = application
            lookup.applicationContext = applicationContext
            lookup.afterPropertiesSet()

            def context = new MockServletContext()
            context.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext)
            RequestContextHolder.setRequestAttributes(new GrailsWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse(), context, applicationContext))

        when:"We lookup a namespace"
            def result = lookup.lookupNamespaceDispatcher("g").methodMissing("foo", [test:"me"])

        then:"the result is correct"
            result.toString() == "good"


        cleanup:"cleanup request context"
            RequestContextHolder.resetRequestAttributes()

    }
}
@TagLib
class OneTagLib {
    def foo = { attrs ->
        out << two.foo(attrs)
    }
}
@TagLib
class TwoTagLib {
    static namespace = "two"
    def foo = { attrs ->
        out << "good"
    }
}
