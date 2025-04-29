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

import grails.util.GrailsUtil
import org.junit.jupiter.api.Test

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class PluginTagLibTests extends AbstractGrailsTagTests {

    @Test
    void testPluginpath() {
        def template = '<plugin:path name="controllers" />'
        def grailsVersion = GrailsUtil.getGrailsVersion()
        assertOutputEquals("/plugins/controllers-$grailsVersion", template)
    }

    @Test
    void testPluginTagLib() {
        def template = '<plugin:isAvailable name="core">printme</plugin:isAvailable>'
        assertOutputEquals "printme", template

        template = '<plugin:isNotAvailable name="core">printme</plugin:isNotAvailable>'
        assertOutputEquals "", template

        template = '<plugin:isNotAvailable name="hibernate">printme</plugin:isNotAvailable>'
        assertOutputEquals "printme", template
    }
}
