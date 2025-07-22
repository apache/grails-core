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

package org.grails.web.pages

import org.grails.gsp.compiler.GrailsLayoutPreprocessor
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

class GrailsLayoutPreprocessorTests {

    @Test
    void testSimpleParse() {
        def gspBody = '''
<html>
        <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>This is the title</title></head>
        <body onload="test();">
            body text
        </body>
</html>
'''
        def preprocessor = new GrailsLayoutPreprocessor()
        def gspBodyExpected = '''
<html>
        <grailsLayout:captureHead>
        <grailsLayout:captureMeta gsp_sm_xmlClosingForEmptyTag="" http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
        <grailsLayout:wrapTitleTag><grailsLayout:captureTitle>This is the title</grailsLayout:captureTitle></grailsLayout:wrapTitleTag></grailsLayout:captureHead>
        <grailsLayout:captureBody onload="test();">
            body text
        </grailsLayout:captureBody>
</html>
'''
        assertEquals(gspBodyExpected, preprocessor.addGspGrailsLayoutCapturing(gspBody))
    }

    @Test
    void testContentParse() {
        def gspBody = '''
<html>
        <head><title>This is the title</title></head>
        <body onload="test();">
            body text
        </body>
        <content tag="nav">
            content test
        </content>
</html>
'''
        def preprocessor = new GrailsLayoutPreprocessor()
        def gspBodyExpected = '''
<html>
        <grailsLayout:captureHead><grailsLayout:wrapTitleTag><grailsLayout:captureTitle>This is the title</grailsLayout:captureTitle></grailsLayout:wrapTitleTag></grailsLayout:captureHead>
        <grailsLayout:captureBody onload="test();">
            body text
        </grailsLayout:captureBody>
        <grailsLayout:captureContent tag="nav">
            content test
        </grailsLayout:captureContent>
</html>
'''
        assertEquals(gspBodyExpected, preprocessor.addGspGrailsLayoutCapturing(gspBody))
    }

    @Test
    void testContentParse2() {
        def gspBody = '''
<html>
        <head><title>This is the title</title></head>
        <body onload="test();">
            body text
        </body>
        <content tag="nav">
            content test
        </content>
        <content tag="nav">
            content test
        </content>
</html>
'''
        def preprocessor = new GrailsLayoutPreprocessor()
        def gspBodyExpected = '''
<html>
        <grailsLayout:captureHead><grailsLayout:wrapTitleTag><grailsLayout:captureTitle>This is the title</grailsLayout:captureTitle></grailsLayout:wrapTitleTag></grailsLayout:captureHead>
        <grailsLayout:captureBody onload="test();">
            body text
        </grailsLayout:captureBody>
        <grailsLayout:captureContent tag="nav">
            content test
        </grailsLayout:captureContent>
        <grailsLayout:captureContent tag="nav">
            content test
        </grailsLayout:captureContent>
</html>
'''
        assertEquals(gspBodyExpected, preprocessor.addGspGrailsLayoutCapturing(gspBody))
    }

    @Test
    void testGrailsLayoutParameterParse() {
        def gspBody = '''
<html>
        <head><title>This is the title</title>
            <parameter name="foo" value="bar" />
        </head>
        <body>
            body text
        </body>
</html>
'''
        def preprocessor = new GrailsLayoutPreprocessor()
        def gspBodyExpected = '''
<html>
        <grailsLayout:captureHead><grailsLayout:wrapTitleTag><grailsLayout:captureTitle>This is the title</grailsLayout:captureTitle></grailsLayout:wrapTitleTag>
            <grailsLayout:parameter name="foo" value="bar" />
        </grailsLayout:captureHead>
        <grailsLayout:captureBody>
            body text
        </grailsLayout:captureBody>
</html>
'''
        assertEquals(gspBodyExpected, preprocessor.addGspGrailsLayoutCapturing(gspBody))
    }

    @Test
    void testOtherParse() {
        def gspBody = '''
<html>
        <head ><titlenot>This is not the title</titlenot></head>
        <body>
            body text
        </body>
</html>
'''
        def preprocessor = new GrailsLayoutPreprocessor()
        def gspBodyExpected = '''
<html>
        <grailsLayout:captureHead ><titlenot>This is not the title</titlenot></grailsLayout:captureHead>
        <grailsLayout:captureBody>
            body text
        </grailsLayout:captureBody>
</html>
'''
        assertEquals(gspBodyExpected, preprocessor.addGspGrailsLayoutCapturing(gspBody))
    }
}

