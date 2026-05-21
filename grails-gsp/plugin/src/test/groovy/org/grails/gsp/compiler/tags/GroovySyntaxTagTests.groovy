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
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author graemerocher
 */
class GroovySyntaxTagTests {

    private tag = new MyGroovySyntaxTag()

    /**
     * Test method for {@link GroovySyntaxTag#init(java.util.Map)}.
     */
    @Test
    void testInit() {
        Map ctx = [:]
        ctx.put(GroovyPage.OUT, new PrintWriter(new StringWriter()))
        tag.init(ctx)
        Assertions.assertEquals(tag.out,ctx.get(GroovyPage.OUT))
    }

    /**
     * Test method for {@link GroovySyntaxTag#setAttributes(java.util.Map)}.
     */
    @Test
    void testSetAttributes() {
        Map attrs = [:]
        attrs.put("\"test1\"","value1")
        attrs.put("\"test2\"","value2")

        tag.setAttributes(attrs)

        Assertions.assertNotNull(tag.attributes)
        Assertions.assertFalse(tag.attributes.isEmpty())
        Assertions.assertEquals(2, tag.attributes.size())
        Assertions.assertTrue(tag.attributes.containsKey("test1"))
        Assertions.assertTrue(tag.attributes.containsKey("test2"))
    }

    /**
     * Test method for {@link GroovySyntaxTag#calculateExpression(java.lang.String)}.
     */
    @Test
    void testCalculateExpression() {
        Assertions.assertEquals("test", tag.calculateExpression(" test "))
        Assertions.assertEquals("test",tag.calculateExpression(" \" test\" "))
        Assertions.assertEquals("test.method()", tag.calculateExpression(' ${ test.method() } '))
    }
}

class MyGroovySyntaxTag extends GroovySyntaxTag {
    boolean isAllowPrecedingContent() { false }

    boolean isKeepPrecedingWhiteSpace() { false }

    void doEndTag() {}

    void doStartTag() {}

    String getName() { null }
}
