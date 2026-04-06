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
import spock.lang.Unroll

class FlashMessagesTagLibSpec extends Specification implements TagLibUnitTest<ApplicationTagLib> {

    void "renders nothing when no flash messages are set"() {
        expect:
        applyTemplate('<g:flashMessages />') == ''
    }

    void "renders flash.message as success alert"() {
        given:
        tagLib.flash.message = 'Record saved'

        when:
        def result = applyTemplate('<g:flashMessages />')

        then:
        result.contains('alert alert-success')
        result.contains('bi bi-check-circle me-2')
        result.contains('Record saved')
        result.contains('btn-close')
        result.contains('role="alert"')
    }

    void "renders flash.error as danger alert"() {
        given:
        tagLib.flash.error = 'Something went wrong'

        when:
        def result = applyTemplate('<g:flashMessages />')

        then:
        result.contains('alert alert-danger')
        result.contains('bi bi-exclamation-triangle me-2')
        result.contains('Something went wrong')
    }

    void "renders flash.warning as warning alert"() {
        given:
        tagLib.flash.warning = 'Please review'

        when:
        def result = applyTemplate('<g:flashMessages />')

        then:
        result.contains('alert alert-warning')
        result.contains('bi bi-exclamation-circle me-2')
        result.contains('Please review')
    }

    void "renders all three flash types together"() {
        given:
        tagLib.flash.message = 'Saved'
        tagLib.flash.error = 'Error occurred'
        tagLib.flash.warning = 'Check input'

        when:
        def result = applyTemplate('<g:flashMessages />')

        then:
        result.contains('alert-success')
        result.contains('Saved')
        result.contains('alert-danger')
        result.contains('Error occurred')
        result.contains('alert-warning')
        result.contains('Check input')
    }

    void "sets _flashRendered request attribute when messages are rendered"() {
        given:
        tagLib.flash.message = 'Saved'

        when:
        applyTemplate('<g:flashMessages />')

        then:
        request.getAttribute('_flashRendered') == true
    }

    void "does not set _flashRendered when no messages exist"() {
        when:
        applyTemplate('<g:flashMessages />')

        then:
        request.getAttribute('_flashRendered') == null
    }

    void "skips rendering when _flashRendered is already set"() {
        given:
        tagLib.flash.message = 'Should not appear'
        request.setAttribute('_flashRendered', true)

        when:
        def result = applyTemplate('<g:flashMessages />')

        then:
        result == ''
    }

    void "HTML-encodes flash message content"() {
        given:
        tagLib.flash.message = '<script>alert("xss")</script>'

        when:
        def result = applyTemplate('<g:flashMessages />')

        then:
        !result.contains('<script>')
        result.contains('&lt;script&gt;')
    }

    void "dismissible=false hides the close button"() {
        given:
        tagLib.flash.message = 'No close button'

        when:
        def result = applyTemplate('<g:flashMessages dismissible="false" />')

        then:
        !result.contains('btn-close')
        result.contains('No close button')
    }

    void "custom role attribute"() {
        given:
        tagLib.flash.message = 'Status message'

        when:
        def result = applyTemplate('<g:flashMessages role="status" />')

        then:
        result.contains('role="status"')
        !result.contains('role="alert"')
    }

    void "custom messageClass and messageIcon"() {
        given:
        tagLib.flash.message = 'Custom styled'

        when:
        def result = applyTemplate('<g:flashMessages messageClass="alert alert-info" messageIcon="bi bi-info-circle" />')

        then:
        result.contains('alert alert-info')
        result.contains('bi bi-info-circle')
        !result.contains('alert-success')
    }

    void "custom errorClass and errorIcon"() {
        given:
        tagLib.flash.error = 'Custom error'

        when:
        def result = applyTemplate('<g:flashMessages errorClass="alert alert-dark" errorIcon="bi bi-bug" />')

        then:
        result.contains('alert alert-dark')
        result.contains('bi bi-bug')
        !result.contains('alert-danger')
    }

    void "custom warningClass and warningIcon"() {
        given:
        tagLib.flash.warning = 'Custom warning'

        when:
        def result = applyTemplate('<g:flashMessages warningClass="alert alert-secondary" warningIcon="bi bi-bell" />')

        then:
        result.contains('alert alert-secondary')
        result.contains('bi bi-bell')
        !result.contains('alert-warning')
    }
}
