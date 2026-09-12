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
package org.grails.forge.web

import grails.web.Controller
import org.grails.forge.api.ForgeApplicationService
import org.grails.forge.api.GrailsForgeConfiguration
import org.grails.forge.api.RequestInfo
import org.grails.forge.application.ApplicationType
import org.grails.forge.options.FeatureFilter
import org.springframework.beans.factory.annotation.Autowired

@Controller
class ForgeApplicationController {

    static allowedMethods = [
            home: 'GET',
            versions: 'GET',
            list: 'GET',
            getType: 'GET',
            features: 'GET',
            defaultFeatures: 'GET'
    ]

    @Autowired
    ForgeApplicationService forgeApplicationService

    @Autowired
    GrailsForgeConfiguration grailsForgeConfiguration

    def home() {
        RequestInfo info = requestInfo()
        String accept = request.getHeader('Accept') ?: ''
        def redirectUri = grailsForgeConfiguration.redirectUri().orElse(null)
        if (accept.contains('text/html') && redirectUri != null) {
            redirect url: redirectUri.toString(), permanent: true
            return
        }
        render text: forgeApplicationService.homeText(info), contentType: 'text/plain'
    }

    def versions() {
        renderJson(forgeApplicationService.getInfo(requestInfo()))
    }

    def list() {
        renderJson(forgeApplicationService.list(requestInfo()))
    }

    def getType() {
        ApplicationType type = requiredType()
        if (type == null) {
            render status: 400
            return
        }
        renderJson(forgeApplicationService.getType(type, requestInfo()))
    }

    def features() {
        ApplicationType type = requiredType()
        if (type == null) {
            render status: 400
            return
        }
        renderJson(forgeApplicationService.features(type, requestInfo(), featureFilter()))
    }

    def defaultFeatures() {
        ApplicationType type = requiredType()
        if (type == null) {
            render status: 400
            return
        }
        renderJson(forgeApplicationService.defaultFeatures(type, requestInfo(), featureFilter()))
    }

    private FeatureFilter featureFilter() {
        ForgeRequestSupport.featureFilter(params)
    }

    private RequestInfo requestInfo() {
        ForgeRequestSupport.info(request, grailsForgeConfiguration)
    }

    private ApplicationType requiredType() {
        ForgeRequestSupport.parseType(params.type)
    }

    private void renderJson(Object body) {
        render text: ForgeRequestSupport.toJson(body), contentType: 'application/json'
    }
}
