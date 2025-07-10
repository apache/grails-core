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

package org.grails.plugins.web

import org.grails.plugins.web.taglib.RenderGrailsLayoutTagLib
import org.grails.plugins.web.taglib.GrailsLayoutTagLib

import grails.plugins.Plugin
import grails.util.GrailsUtil
import groovy.util.logging.Slf4j

/**
 * Plugin responsible for Grails Layout specific configuration.
 */
class GrailsLayoutGrailsPlugin extends Plugin {
    def grailsVersion = '7.0.0-SNAPSHOT > *'
    def dependsOn = [core: GrailsUtil.getGrailsVersion(), i18n: GrailsUtil.getGrailsVersion()]
    def observe = ['controllers']
    def loadBefore = ['groovyPages']
    def loadAfter = ['webLayout']

    def providedArtefacts = [
            RenderGrailsLayoutTagLib,
            GrailsLayoutTagLib
    ]
}
