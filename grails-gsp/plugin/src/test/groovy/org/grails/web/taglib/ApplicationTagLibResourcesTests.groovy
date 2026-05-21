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

import grails.testing.spock.OnceBefore
import grails.testing.web.taglib.TagLibUnitTest
import org.grails.plugins.web.taglib.ApplicationTagLib
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Specification

class ApplicationTagLibResourcesTests extends Specification implements TagLibUnitTest<ApplicationTagLib> {

    @OnceBefore
    void onInitMockBeans() {
        ConfigurableApplicationContext applicationContext = grailsApplication.parentContext
        applicationContext.beanFactory.registerSingleton('grailsResourceProcessor', [something:'value'])
    }

    private def replaceMetaClass(Object o) {
        def old = o.metaClass

        // Create a new EMC for the class and attach it.
        def emc = new ExpandoMetaClass(o.class, true, true)
        emc.initialize()
        o.metaClass = emc

        return old
    }

    def testResourceTagDirOnlyWithResourcesHooks() {
        when:
        request.contextPath = '/test'
        def template = '${resource(dir:"jquery")}'

        def taglib = tagLib
        taglib.hasResourceProcessor = true

        def oldMC = replaceMetaClass(taglib)

        // Dummy r.resource impl
        def mockRes = [
            resource: { attrs -> "WRONG"}
        ]
        taglib.metaClass.getR = { -> mockRes }
        String output = applyTemplate(template)

        then:
        output == '/test/static/jquery'

        cleanup:
        taglib.metaClass = oldMC
    }

    def testResourceTagDirAndFileWithResourcesHooks() {
        when:
        request.contextPath = '/test'
        def template = '${resource(dir:"jquery", file:"jqtest.js")}'

        def taglib = tagLib
        taglib.hasResourceProcessor = true
        def oldMC = replaceMetaClass(taglib)

        // Dummy r.resource impl
        def mockRes = [
            resource: { attrs -> "RESOURCES:${attrs.dir}/${attrs.file}" }
        ]
        taglib.metaClass.getR = { -> mockRes }
        String output = applyTemplate(template)

        then:
        output == 'RESOURCES:jquery/jqtest.js'

        cleanup:
        taglib.metaClass = oldMC
    }
}
