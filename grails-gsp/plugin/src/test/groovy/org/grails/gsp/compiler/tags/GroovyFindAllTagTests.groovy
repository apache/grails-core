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

import static org.junit.jupiter.api.Assertions.*

/**
 * @author Jeff Brown
 */
class GroovyFindAllTagTests {

    def tag = new GroovyFindAllTag()
    def sw = new StringWriter()

    @BeforeEach
    protected void setUp() {
        Map context = new HashMap();
        context.put(GroovyPage.OUT, new PrintWriter(sw));
        GroovyPageParser parser=new GroovyPageParser("test", "test", "test", new ByteArrayInputStream([] as byte[]), null);
        context.put(GroovyPageParser.class, parser);
        tag.init(context);
    }

    @Test
    void testIsBufferWhiteSpace() {
        assertFalse(tag.isKeepPrecedingWhiteSpace())
    }

    @Test
    void testHasPrecedingContent() {
        assertTrue(tag.isAllowPrecedingContent())
    }

    @Test
    void testDoStartWithNoInAttribute() {
        tag.attributes = ['"expr"': " someExpression "]
        assertThrows(GrailsTagException) {
            tag.doStartTag()
        }
    }

    @Test
    void testDoStartWithNoExprAttribute() {
        tag.attributes = ['"in"': " someExpression "]
        assertThrows(GrailsTagException) {
            tag.doStartTag()
        }
    }

    @Test
    void testDoStartTag() {
        tag.attributes = ['"expr"': " \${it.age > 19}", '"in"': "myObj"]
        tag.doStartTag()

        assertEquals("for( "+tag.getForeachRenamedIt()+" in evaluate('myObj.findAll {it.age > 19}', 1, it) { return myObj.findAll {it.age > 19} } ) {"+System.getProperty("line.separator")+ "changeItVariable(" + tag.getForeachRenamedIt() + ")" + System.getProperty("line.separator"), sw.toString())
    }

    @Test
    void testDoEndTag() {
        tag.doEndTag()
        assertEquals("}${System.properties['line.separator']}".toString(), sw.toString())
    }

    @Test
    void testTagName() {
        assertEquals("findAll", tag.getName())
    }
}
