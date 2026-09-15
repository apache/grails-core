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
package org.grails.gsp

import groovy.text.Template
import groovy.transform.CompileStatic

import org.grails.taglib.encoder.OutputContextLookup
import org.grails.taglib.encoder.OutputContextLookupHelper

/**
 * Knows how to make in instance of GroovyPageWritable.
 *
 * @author Graeme Rocher
 * @since 0.5
 */
@CompileStatic
class GroovyPageTemplate implements Template, Cloneable {

    private final OutputContextLookup outputContextLookup
    private GroovyPageMetaInfo metaInfo
    private boolean allowSettingContentType = false

    GroovyPageTemplate(GroovyPageMetaInfo metaInfo) {
        this(metaInfo, OutputContextLookupHelper.getOutputContextLookup())
    }

    GroovyPageTemplate(GroovyPageMetaInfo metaInfo, OutputContextLookup outputContextLookup) {
        this.metaInfo = metaInfo
        this.outputContextLookup = outputContextLookup
    }

    Writable make() {
        return new GroovyPageWritable(metaInfo, outputContextLookup, allowSettingContentType)
    }

    @SuppressWarnings('rawtypes')
    GroovyPageWritable make(Map binding) {
        GroovyPageWritable gptw = new GroovyPageWritable(metaInfo, outputContextLookup, allowSettingContentType)
        gptw.setBinding(binding)
        return gptw
    }

    GroovyPageMetaInfo getMetaInfo() {
        return metaInfo
    }

    boolean isAllowSettingContentType() {
        return allowSettingContentType
    }

    void setAllowSettingContentType(boolean allowSettingContentType) {
        this.allowSettingContentType = allowSettingContentType
    }

    @Override
    Object clone() {
        GroovyPageTemplate cloned = new GroovyPageTemplate(metaInfo)
        cloned.setAllowSettingContentType(allowSettingContentType)
        return cloned
    }

}
