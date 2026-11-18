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
package welcomepagejetty

import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.servlet.ServletRegistrationBean
import spock.lang.Specification

/**
 * The welcome page's Servlet Filters panel, rendered by a running Jetty from the page compiled statically.
 *
 * <p>No portable API exposes the order a container chains its filters in, so off Tomcat the panel lists
 * Spring Boot's enabled registrations in the order it registers them, then the container's other filters,
 * and numbers none of them. The filters registered in {@code resources.groovy} are the cases it has to get
 * right.</p>
 */
@Integration
class WelcomePageFiltersJettySpec extends Specification {

    @Autowired
    @Qualifier('dispatcherServletRegistration')
    ServletRegistrationBean dispatcherServletRegistration

    void 'off Tomcat the servlet filters panel lists the filters without claiming an execution order'() {
        when:
        String page = new URL("http://localhost:${serverPort}/").text
        List<Map> rows = filterRows(page)
        List<String> names = rows*.name

        then: 'nothing is numbered or grouped, and the description says the order is not the execution order'
        rows
        rows.every { it.position == null && it.group == null }
        page.contains('welcome.filters.unordered')
        !page.contains('welcome.filters.description')

        and: "Spring Boot's registrations in the order it registers them, then the container's other filters"
        names.containsAll(['urlFilter', 'servletMappedFilter', 'containerFilter'])
        names.indexOf('urlFilter') < names.indexOf('servletMappedFilter')
        names.indexOf('servletMappedFilter') < names.indexOf('containerFilter')

        and: 'a filter mapped through a servlet registration shows the servlet name rather than /*'
        rows.find { it.name == 'servletMappedFilter' }.mappings == [dispatcherServletRegistration.servletName]

        and: 'a registration that leaves out REQUEST is badged with the dispatcher types it is for, and one for REQUEST is not'
        rows.find { it.name == 'errorOnlyFilter' }.dispatchers == ['ERROR']
        rows.find { it.name == 'urlFilter' }.dispatchers == []

        and: 'a disabled registration, which the container never sees, is not listed'
        !names.contains('disabledFilter')
    }

    /**
     * The rows of the Servlet Filters panel: the group heading each sits under, its position, its
     * registration name, its mappings and the dispatcher types it is badged with.
     */
    static List<Map> filterRows(String page) {
        int start = page.indexOf('id="filters-list"')
        String panel = page.substring(start, page.indexOf('id="filters-empty"', start))
        List<Map> rows = []
        String group = null
        (panel =~ /(?s)text-uppercase[^>]*>\s*(.*?)\s*<\/div>|<li(.*?)<\/li>/).each { List<String> match ->
            if (match[1] != null) {
                group = match[1].replaceAll(/\s*\(\d+\)$/, '')
                return
            }
            String title = (match[2] =~ /title="([^"]*)"/)[0][1]
            def position = match[2] =~ /tabular-nums;">(\d+)<\/span>/
            rows << [group   : group,
                     position: position ? position[0][1] as int : null,
                     name    : title.substring(0, title.lastIndexOf(' (')),
                     mappings: (match[2] =~ /<code class="me-1">(.*?)<\/code>/).collect { it[1] },
                     dispatchers: (match[2] =~ /<span class="badge[^"]*">([A-Z]+)<\/span>/).collect { it[1] }]
        }
        rows
    }
}
