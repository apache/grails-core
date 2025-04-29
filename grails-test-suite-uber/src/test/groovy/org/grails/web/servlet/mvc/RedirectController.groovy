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
package org.grails.web.servlet.mvc

import grails.artefact.Artefact

@Artefact('Controller')
class RedirectController {

    static defaultAction = 'toAction'

    def index() { redirect action: 'list' }

    def redirectToDefaultAction() {
        redirect(controller:"redirect")
    }

    def testNoController() {
        redirect(action: 'thankyou')
    }

    def redirectTwice() {
        redirect(action:'one')
        redirect(action:'two')
    }

    def responseCommitted() {
        response.outputStream << "write data"
        response.outputStream.flush()
        redirect(action:'one')
    }

    def toAction() {
        redirect(action:'foo')
    }

    def toActionPermanent() {
        redirect(action:'foo', permanent: true)
    }

    def toActionPermanentFalse() {
        redirect(action:'foo', permanent: false)
    }

    def toActionPermanentStringTrue() {
        redirect(action:'foo', permanent: 'true')
    }

    def toActionPermanentStringFalse() {
        redirect(action:'foo', permanent: 'false')
    }

    def toActionWithGstring() {
        def prefix = 'f'
        redirect(action:"${prefix}oo")
    }

    def toActionTemporaryRedirect() {
        redirect(action:'foo', moved: false)
    }

    def toActionPermanentRedirect() {
        redirect(action:'foo', permanent:true, moved:false)
    }

    def toActionMovedTemporary() {
        redirect(action:'foo')
    }

    def toActionMovedPermanent() {
        redirect(action:'foo', permanent:true)
    }

    def toRoot() {
        redirect(controller:'default')
    }

    def toController() {
        redirect(controller:'test')
    }

    def toControllerAndAction() {
        redirect(controller:'test', action:'foo')
    }

    def toControllerAndActionWithFragment() {
        redirect(controller:'test', action:'foo', fragment:"frag")
    }

    def toControllerWithParams() {
        redirect(controller:'test',action:'foo', params:[one:'two', two:'three'])
    }

    def toControllerWithDuplicateParams() {
        redirect(controller:'test',action:'foo', params:[one:['two','three']])
    }

    def toControllerWithDuplicateArrayParams() {
        redirect(controller:'test',action:'foo', params:[one:['two','three'] as String[]])
    }

    def toAbsolute() {
        redirect(url:"http://google.com")
    }
}

