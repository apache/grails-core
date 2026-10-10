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
package welcomepage

import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.servlet.ServletRegistrationBean
import spock.lang.Specification

/**
 * The welcome page's Servlet Filters panel, rendered by a running Tomcat from the page compiled statically.
 *
 * <p>Tomcat builds a request's chain from its filter maps in two passes, the maps whose URL patterns match
 * and then the maps whose servlet names match, adding a filter once. The panel lists one row per map in
 * that order. The filters registered in {@code resources.groovy} are the cases it has to get right.</p>
 */
@Integration
class WelcomePageFiltersSpec extends Specification {

    @Autowired
    @Qualifier('dispatcherServletRegistration')
    ServletRegistrationBean dispatcherServletRegistration

    void 'the servlet filters panel lists every filter mapping in the order Tomcat applies them'() {
        when:
        String page = new URL("http://localhost:${serverPort}/").text
        List<Map> rows = filterRows(page)
        List<Map> byUrl = rows.findAll { it.group == 'welcome.filters.byUrl' }
        List<Map> byServlet = rows.findAll { it.group == 'welcome.filters.byServlet' }

        then: 'every mapping is numbered in the order Tomcat walks them, URL patterns first'
        byUrl && byServlet
        rows == byUrl + byServlet
        rows*.position == (1..rows.size()).toList()
        page.contains('welcome.filters.description')
        !page.contains('welcome.filters.unordered')

        and: 'a filter mapped by servlet name ahead of another filter runs after it when its URL mapping comes later'
        byUrl*.name.containsAll(['urlFilter', 'servletAndUrlFilter'])
        byUrl*.name.indexOf('urlFilter') < byUrl*.name.indexOf('servletAndUrlFilter')
        byServlet.find { it.name == 'servletAndUrlFilter' }.mappings == [dispatcherServletRegistration.servletName]

        and: 'a match-all URL mapping keeps its *'
        byUrl.find { it.name == 'matchAllFilter' }.mappings == ['*']

        and: 'a filter added straight to the container is listed with its own mapping'
        byUrl.find { it.name == 'containerFilter' }.mappings == ['/container/*']

        and: 'a map that leaves out REQUEST is badged with the dispatcher types it is for, and one for REQUEST is not'
        byUrl.find { it.name == 'errorOnlyFilter' }.dispatchers == ['ERROR']
        byUrl.find { it.name == 'urlFilter' }.dispatchers == []

        and: 'a disabled registration never reaches the container'
        !rows.any { it.name == 'disabledFilter' }
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
