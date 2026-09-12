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
package grails.gsp.boot

import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.ResourceLoader

import spock.lang.Specification

/**
 * Covers which of the two ways a view can be found the auto-configuration hands the page locator:
 * the views compiled into the application, or the templates it was built from. Rendering from the
 * compiled views end to end is covered by the gsp-spring-boot example.
 */
class GspAutoConfigurationSpec extends Specification {

    private final GspAutoConfiguration.GspTemplateEngineAutoConfiguration configuration =
            new GspAutoConfiguration.GspTemplateEngineAutoConfiguration()

    private final ResourceLoader resourceLoader = new DefaultResourceLoader()

    void 'the compiled views are read for an application served from the class path'() {
        when: 'the templates can only come from the class path, as they do once packaged'
        Map<String, String> views = configuration.resolvePrecompiledViews(resourceLoader, ['classpath:/templates'])

        then: 'every view the registry names is available to the locator, layouts included'
        views == ['/probe.gsp': 'gsp_probe_gsp', '/layouts/probe.gsp': 'gsp_layouts_probe_gsp']
    }

    void 'the compiled views are left unread for an application served from #root'() {
        when: 'a template root on the file system, which holds the templates themselves'
        Map<String, String> views = configuration.resolvePrecompiledViews(resourceLoader, roots)

        then: 'the locator is left to render the templates, so an edit to one takes effect'
        views == null

        where:
        root                | roots
        'a directory'       | ['file:./src/main/resources/templates']
        'a directory first' | ['file:./src/main/resources/templates', 'classpath:/templates']
        'a directory last'  | ['classpath:/templates', 'file:./templates']
    }

    void 'an application with no compiled views renders from its templates'() {
        given: 'a class path without a view registry on it'
        ResourceLoader empty = new DefaultResourceLoader(new ClassLoader(null) {})

        expect:
        configuration.resolvePrecompiledViews(empty, ['classpath:/templates']) == null
    }

}
