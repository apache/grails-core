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
package demo

import grails.testing.web.taglib.TagLibUnitTest
import grails.validation.Validateable
import org.springframework.context.i18n.LocaleContextHolder
import spock.lang.Specification

class LocaleTagLibSpec extends Specification implements TagLibUnitTest<SampleTagLib> {

    void 'test customizing messageSource'() {
        given:
        def w = new Widget()
        LocaleContextHolder.setLocale(Locale.US)
        messageSource.addMessage("demo.Widget.title.label", Locale.US, "Title Of Widget")
        messageSource.addMessage("demo.Widget.label", Locale.US, "Widget")

        when:
        w.validate()

        then:
        w.hasErrors()

        when:
        def template = '<g:renderErrors bean="${widget}" />'

        then:
        applyTemplate(template, [widget: w]).contains("<li>Property [Title Of Widget] of class [Widget] cannot be null</li>")
    }
}


class Widget implements Validateable {
    String title
}
