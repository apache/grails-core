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

package org.apache.grails.views.gsp.layout

import com.opensymphony.module.sitemesh.RequestConstants
import org.apache.grails.web.layout.GSPGrailsLayoutPage
import org.apache.grails.web.layout.EmbeddedGrailsLayoutView
import BeforeEach

class FullGrailsLayoutLifeCycleWithNoPreprocessingTests extends FullGrailsLayoutLifeCycleTests {

    @BeforeEach
    @Override
    protected void setUp() throws Exception {
        super.setUp()
        def config = new ConfigSlurper().parse('''
grails.views.gsp.layout.preprocess=false
''')
        buildMockRequest(config)
        def page = new GSPGrailsLayoutPage()
        request.setAttribute(RequestConstants.PAGE, page)
        request.setAttribute(EmbeddedGrailsLayoutView.GSP_GRAILS_LAYOUT_PAGE, page)
    }

    @Override
    void testMultipleLevelsOfLayouts() {
        // no-op
    }
}

