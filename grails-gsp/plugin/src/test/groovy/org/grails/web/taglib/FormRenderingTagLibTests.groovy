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

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnJre
import org.junit.jupiter.api.condition.EnabledOnJre
import org.junit.jupiter.api.condition.JRE

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue


class FormRenderingTagLibTests extends AbstractGrailsTagTests {

    // TODO: #14200 Java 21 has different date requirements and needs fixed
    @Test
    @EnabledOnJre(JRE.JAVA_17)
    void testTimeZoneSelect() {
        def template = '<g:timeZoneSelect name="foo" locale="en_US"/>'

        def engine = appCtx.groovyPagesTemplateEngine

        assert engine
        def t = engine.createTemplate(template, "test_"+ System.currentTimeMillis())

        def w = t.make()

        def sw = new StringWriter()
        def out = new PrintWriter(sw)
        webRequest.out = out
        w.writeTo(out)

        def output = sw.toString()
        println output

        assertTrue output.startsWith('<select name="foo" id="foo" >')
        assertTrue output.contains('<option value="Pacific/Galapagos" >GALT, Galapagos Time -6:0.0 [Pacific/Galapagos]</option>')
        assertTrue (output.contains('<option value="US/Central" >CDT, Central Daylight Time -6:0.0 [US/Central]</option>') || output.contains('<option value="US/Central" >CST, Central Standard Time -6:0.0 [US/Central]</option>'))
        assertTrue output.endsWith('</select>')
    }

     void assertOutputEquals(expected, template, params = [:]) {
        def engine = appCtx.groovyPagesTemplateEngine

        assert engine
        def t = engine.createTemplate(template, "test_"+ System.currentTimeMillis())

        def w = t.make(params)

        def sw = new StringWriter()
        def out = new PrintWriter(sw)
        webRequest.out = out
        w.writeTo(out)

        assertEquals expected, sw.toString()
    }
}
