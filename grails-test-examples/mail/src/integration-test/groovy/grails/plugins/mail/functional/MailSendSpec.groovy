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

package grails.plugins.mail.functional

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import testapp1.GreenMailService

@Integration
class MailSendSpec extends ContainerGebSpec {

    GreenMailService greenMailService

    def cleanup() {
        greenMailService.reset()
    }

    void 'controller should be able to send mail'() {
        given:
        def mailDetails = [
            from: 'abc@123.com',
            to: '123@abc.com',
            subject: 'Test subject',
            text: 'Test body'
        ]

        when:
        go "/sendMail?${toParams(mailDetails)}"

        then:
        pageSource.contains('123@abc.com')
        pageSource.contains('abc@123.com')
        pageSource.contains('Test subject')
        pageSource.contains('Test body')
    }

    private static String toParams(Map params) {
        params.collect { k, v ->
            "${URLEncoder.encode(k.toString(), 'UTF-8')}=${URLEncoder.encode(v.toString(), 'UTF-8')}"
        }.join('&')
    }
}
