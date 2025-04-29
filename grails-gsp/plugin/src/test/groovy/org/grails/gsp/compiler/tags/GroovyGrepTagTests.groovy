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
package org.grails.gsp.compiler.tags

import org.grails.gsp.GroovyPage
import org.grails.gsp.compiler.GroovyPageParser
import org.grails.taglib.GrailsTagException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows

class GroovyGrepTagTests {

    private GroovyGrepTag tag = new GroovyGrepTag();
    private StringWriter sw = new StringWriter();

    @BeforeEach
    protected void setUp() throws Exception {
        Map context = new HashMap();
        context.put(GroovyPage.OUT, new PrintWriter(sw));
        GroovyPageParser parser=new GroovyPageParser("test", "test", "test", new ByteArrayInputStream(new byte[]{}), null);
        context.put(GroovyPageParser.class, parser);
        tag.init(context);
    }


    @Test
    void testDoStartTag() {

        assertThrows(GrailsTagException, {
            tag.doStartTag()
        }, "Should throw exception for required attributes")

        tag.setAttributes('"in"': 'test', '"filter"':'\${~/regex/}')
        tag.doStartTag()

        assertEquals("for( "+tag.getForeachRenamedIt()+" in evaluate('test.grep(~/regex/)', 1, it) { return test.grep(~/regex/) } ) {"+System.getProperty("line.separator")+ "changeItVariable(" + tag.getForeachRenamedIt() + ")" + System.getProperty("line.separator"), sw.toString())
    }

    @Test
    void testWithStatus() {
        tag.setAttributes('"in"': 'test', '"filter"':'\${~/regex/}','"status"':"i",'"var"':"t")
        tag.doStartTag()

        assertEquals("loop:{int i = 0for( t in evaluate('test.grep(~/regex/)', 1, it) { return test.grep(~/regex/) } ) {", sw.toString().replaceAll('[\r\n]', ''))
    }
}
