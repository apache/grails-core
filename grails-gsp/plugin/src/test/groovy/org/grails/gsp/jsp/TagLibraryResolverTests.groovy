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

import grails.core.DefaultGrailsApplication
import org.codehaus.groovy.tools.RootLoader
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import org.springframework.mock.web.MockServletContext
import spock.lang.Specification

class TagLibraryResolverTests extends Specification {

    void testResolveTagLibraryFromJar() {
        given:
        def resolver = new TagLibraryResolverImpl()
        resolver.servletContext = new MockServletContext()
        resolver.grailsApplication= new DefaultGrailsApplication()
        resolver.tldScanPatterns = ['classpath*:/META-INF/fmt.tld', 'classpath*:/META-INF/c.tld'] as String[]
        resolver.resourceLoader = new DefaultResourceLoader(this.class.classLoader)

        when:
        def tagLib = resolver.resolveTagLibrary('jakarta.tags.fmt')

        then:
        tagLib

        when:
        def messageTag = tagLib.getTag('message')

        then:
        messageTag

        when:
        // when resolving second time the code will take a different branch
        // because certain locations have been cached. This test tests that
        tagLib = resolver.resolveTagLibrary('jakarta.tags.core')

        then:
        tagLib
        tagLib.getTag('redirect')
    }

    void testResolveTagLibraryFromWebXml() {

        given:
        def resolver = new MockWebXmlTagLibraryResolver()
        resolver.servletContext = new MockServletContext()
        resolver.grailsApplication= new DefaultGrailsApplication()

        when:
        def tagLib = resolver.resolveTagLibrary('http://grails.codehaus.org/tags')

        then:
        tagLib
        tagLib.getTag('javascript')
    }
}

class MockWebXmlTagLibraryResolver extends TagLibraryResolverImpl {

    protected URLClassLoader resolveRootLoader() {
        new RootLoader([] as URL[], Thread.currentThread().getContextClassLoader())
    }

    protected InputStream getTldFromServletContext(String loc) {

        assert '/WEB-INF/tld/grails.tld' == loc

        new ByteArrayResource('''\
            |<?xml version="1.0" encoding="UTF-8"?>
            |<taglib xmlns="https://jakarta.ee/xml/ns/jakartaee"
            |        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            |        xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-jsptaglibrary_3_0.xsd"
            |        version="3.0">
            |   <description>The Grails (Groovy on Rails) custom tag library</description>
            |   <tlib-version>0.2</tlib-version>
            |   <short-name>grails</short-name>
            |   <uri>http://grails.codehaus.org/tags</uri>
            |
            |   <tag>
            |       <description>
        	|           Includes a javascript src file, library or inline script
	 	    |           if the tag has no src or library attributes its assumed to be an inline script
            |       </description>
            |       <name>javascript</name>
            |       <tag-class>JavascriptTagLib</tag-class>
            |       <body-content>JSP</body-content>
            |       <attribute>
            |           <description>A predefined JavaScript or AJAX library to load</description>
            |           <name>library</name>
            |           <required>false</required>
            |           <rtexprvalue>true</rtexprvalue>
            |       </attribute>
            |       <attribute>
            |           <description>A custom (or unknown to Grails) JavaScript source file</description>
            |           <name>src</name>
            |           <required>false</required>
            |           <rtexprvalue>true</rtexprvalue>
            |       </attribute>
            |       <attribute>
            |           <description>Since 0.6 Specifies the full base url to prepend to the library name</description>
            |           <name>base</name>
            |           <required>false</required>
            |           <rtexprvalue>true</rtexprvalue>
            |       </attribute>
            |       <dynamic-attributes>false</dynamic-attributes>
            |    </tag>
            |</taglib>
            |'''.stripMargin().bytes
        ).inputStream

    }

    protected Resource getWebXmlFromServletContext() {
        new ByteArrayResource('''\
            |<?xml version="1.0" encoding="UTF-8"?>
            |<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
            |         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            |         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
            |         version="6.0">
            |    <display-name>/@grails.project.key@</display-name>
            |
	        |    <!-- Grails dispatcher servlet -->
	        |    <servlet>
		    |        <servlet-name>grails</servlet-name>
            |        <servlet-class>org.codehaus.groovy.grails.web.servlet.GrailsDispatcherServlet</servlet-class>
		    |        <load-on-startup>2</load-on-startup>
	        |    </servlet>
            |
            |    <servlet-mapping>
            |        <servlet-name>gsp</servlet-name>
            |        <url-pattern>*.gsp</url-pattern>
            |    </servlet-mapping>
            |
            |    <taglib>
            |       <taglib-uri>http://grails.codehaus.org/tags</taglib-uri>
            |       <taglib-location>/WEB-INF/tld/grails.tld</taglib-location>
            |    </taglib>
            |</web-app>
            |'''.stripMargin().bytes
        )
    }
}
