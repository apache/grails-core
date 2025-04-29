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
package org.grails.plugins.codecs

import grails.plugins.Plugin
import grails.util.GrailsUtil
import org.grails.plugins.codecs.DefaultCodecLookup
import org.grails.commons.CodecArtefactHandler
import org.grails.encoder.impl.HTML4Codec
import org.grails.plugins.codecs.HTMLCodec
import org.grails.encoder.impl.HTMLJSCodec
import org.grails.encoder.impl.JavaScriptCodec
import org.grails.encoder.impl.RawCodec
import org.grails.plugins.codecs.URLCodec

/**
 * Configures pluggable codecs.
 *
 * @author Jeff Brown
 * @since 0.4
 */
class CodecsGrailsPlugin extends Plugin {
    def version = GrailsUtil.getGrailsVersion()
    def dependsOn = [core: version]
    def watchedResources = "file:./grails-app/utils/**/*Codec.groovy"
    def providedArtefacts = [
            HTMLCodec,
            HTML4Codec,
            JavaScriptCodec,
            HTMLJSCodec,
            URLCodec,
            RawCodec
    ]

}