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

import org.junit.jupiter.api.Test

import static org.grails.web.pages.ParseSpec.parseCode
import static org.grails.web.pages.ParseSpec.trimAndRemoveCR
import static org.grails.web.pages.ParseSpec.makeImports
import static org.grails.web.pages.ParseSpec.GSP_FOOTER
import static org.junit.jupiter.api.Assertions.assertEquals

class GroovyEachParseTests {

    @Test
    void testEachOutput() {
        def output = parseCode("myTest", """
<g:each var="t" in="${'blah'}">
</g:each>
""")

        assertEquals(trimAndRemoveCR(makeImports() + """\n
class myTest extends org.grails.gsp.GroovyPage {
public String getGroovyPageFileName() { "myTest" }
public Object run() {
Writer out = getOut()
Writer expressionOut = getExpressionOut()

printHtmlPart(0)
for( t in evaluate('"blah"', 2, it) { return "blah" } ) {
printHtmlPart(0)
}
printHtmlPart(0)
}""" + GSP_FOOTER
        ), trimAndRemoveCR(output.toString()))
        assertEquals("\n", output.htmlParts[0])
    }

    @Test
    void testEachOutputNoLineBreaks() {
        def output = parseCode("myTest", """
<g:each var="t" in="${'blah'}"></g:each>""")

        assertEquals(trimAndRemoveCR(makeImports() + """\n
class myTest extends org.grails.gsp.GroovyPage {
public String getGroovyPageFileName() { "myTest" }
public Object run() {
Writer out = getOut()
Writer expressionOut = getExpressionOut()

printHtmlPart(0)
for( t in evaluate('"blah"', 1, it) { return "blah" } ) {
}
}""" + GSP_FOOTER
        ), trimAndRemoveCR(output.toString()))
        assertEquals("\n", output.htmlParts[0])
    }

    @Test
    void testEachOutVarAndIndex() {
        def output = parseCode("myTest2", """
<g:each var="t" status="i" in="${'blah'}">
</g:each>
""")

        assertEquals(trimAndRemoveCR(makeImports() + """\n
class myTest2 extends org.grails.gsp.GroovyPage {
public String getGroovyPageFileName() { "myTest2" }
public Object run() {
Writer out = getOut()
Writer expressionOut = getExpressionOut()

printHtmlPart(0)
loop:{
int i = 0
for( t in evaluate('"blah"', 2, it) { return "blah" } ) {
printHtmlPart(0)
i++
}
}
printHtmlPart(0)
}""" + GSP_FOOTER
        ), trimAndRemoveCR(output.toString()))
        assertEquals("\n", output.htmlParts[0])
    }

}
