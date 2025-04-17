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

import spock.lang.Specification

class WebXmlTagLibraryReaderTests extends Specification {

    void testWebXmlTagLibraryReader() {
        given:
        def is = new ByteArrayInputStream(testWebXml.getBytes())
        WebXmlTagLibraryReader webXmlReader = new WebXmlTagLibraryReader(is)

        expect:
        webXmlReader.tagLocations
        webXmlReader.tagLocations.size() == 4
        webXmlReader.tagLocations['jakarta.tags.core'] == '/WEB-INF/tld/c.tld'
    }

    def testWebXml = '''\
        |<?xml version="1.0" encoding="UTF-8"?>
        |<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
        |         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        |         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
        |         version="6.0"
        |>
        |    <display-name>/@grails.project.key@</display-name>
        |
        |    <context-param>
        |        <param-name>log4jConfigLocation</param-name>
        |        <param-value>/WEB-INF/classes/log4j.properties</param-value>
        |    </context-param>
        |
        |    <context-param>
        |       <param-name>contextConfigLocation</param-name>
        |        <param-value>/WEB-INF/applicationContext.xml</param-value>
        |    </context-param>
        |
        |    <context-param>
        |        <param-name>webAppRootKey</param-name>
        |        <param-value>@grails.project.key@</param-value>
        |    </context-param>
        |
        |     <filter>
        |         <filter-name>charEncodingFilter</filter-name>
        |         <filter-class>org.springframework.web.filter.DelegatingFilterProxy</filter-class>
        |         <init-param>
        |             <param-name>targetBeanName</param-name>
        |             <param-value>characterEncodingFilter</param-value>
        |         </init-param>
        |         <init-param>
        |             <param-name>targetFilterLifecycle</param-name>
        |             <param-value>true</param-value>
        |         </init-param>
        |    </filter>
        |
        |    <filter-mapping>
        |        <filter-name>charEncodingFilter</filter-name>
        |        <url-pattern>/*</url-pattern>
        |    </filter-mapping>
        |
        |    <servlet>
        |        <servlet-name>log4j</servlet-name>
        |        <servlet-class>org.springframework.web.util.Log4jConfigServlet</servlet-class>
        |        <load-on-startup>1</load-on-startup>
        |    </servlet>
        |
        |    <!-- Context loader servlet for those older servlet engines. -->
        |    <servlet>
        |        <servlet-name>context</servlet-name>
        |        <servlet-class>org.springframework.web.context.ContextLoaderServlet</servlet-class>
        |        <load-on-startup>1</load-on-startup>
        |    </servlet>
        |
        |    <!-- Grails dispatcher servlet -->
        |    <servlet>
        |        <servlet-name>grails</servlet-name>
        |        <servlet-class>org.codehaus.groovy.grails.web.servlet.GrailsDispatcherServlet</servlet-class>
        |        <load-on-startup>2</load-on-startup>
        |    </servlet>
        |
        |    <!-- The Groovy Server Pages servlet -->
        |    <servlet>
        |         <servlet-name>gsp</servlet-name>
        |         <servlet-class>GroovyPagesServlet</servlet-class>
        |    </servlet>
        |
        |    <servlet-mapping>
        |        <servlet-name>gsp</servlet-name>
        |        <url-pattern>*.gsp</url-pattern>
        |    </servlet-mapping>
        |
        |    <welcome-file-list>
        |         <!--
        |         The order of the welcome pages is important.  JBoss deployment will
        |         break if index.gsp is first in the list.
        |         -->
        |         <welcome-file>index.html</welcome-file>
        |         <welcome-file>index.jsp</welcome-file>
        |         <welcome-file>index.gsp</welcome-file>
        |    </welcome-file-list>
        |
        |    <taglib>
        |        <taglib-uri>jakarta.tags.core</taglib-uri>
        |        <taglib-location>/WEB-INF/tld/c.tld</taglib-location>
        |    </taglib>
        |
        |    <taglib>
        |        <taglib-uri>jakarta.tags.fmt</taglib-uri>
        |        <taglib-location>/WEB-INF/tld/fmt.tld</taglib-location>
        |    </taglib>
        |
        |    <taglib>
        |        <taglib-uri>http://www.springframework.org/tags</taglib-uri>
        |        <taglib-location>/WEB-INF/tld/spring.tld</taglib-location>
        |    </taglib>
        |
        |    <taglib>
        |        <taglib-uri>http://grails.codehaus.org/tags</taglib-uri>
        |        <taglib-location>/WEB-INF/tld/grails.tld</taglib-location>
        |    </taglib>
        |</web-app>
        |'''.stripMargin()
}
