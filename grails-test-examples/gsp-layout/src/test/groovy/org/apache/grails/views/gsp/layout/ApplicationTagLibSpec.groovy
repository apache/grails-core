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

import grails.testing.web.taglib.TagLibUnitTest
import org.grails.plugins.web.taglib.ApplicationTagLib
import spock.lang.Specification

class ApplicationTagLibSpec extends Specification implements TagLibUnitTest<ApplicationTagLib> {
    void 'multiline attribute - GRAILS-8253'() {
        when:
        def template='''<html>
<head>
<title>Sample onclick issue page</title>
</head>
<body>
<g:form name="testForm" controller="begin" action="create">
<g:textField name="testField"/>
<g:actionSubmit class="buttons" action="testAction" value="This
is a test action description"
onclick="if (testForm.testField.value=='') { alert('Please enter some text.'); return false; }"
/>
</g:form>
</body>
</html>'''
        def result= applyTemplate(template, [:])

        then:
        result == '''<html>
<head>
<title>Sample onclick issue page</title>
</head>
<body>
<form action="/begin/create" method="post" name="testForm" id="testForm" >
<input type="text" name="testField" value="" id="testField" />
<input type="submit" name="_action_testAction" value="This
is a test action description" class="buttons" onclick="if (testForm.testField.value==&#39;&#39;) { alert(&#39;Please enter some text.&#39;); return false; }" />
</form>
</body>
</html>'''
    }
}