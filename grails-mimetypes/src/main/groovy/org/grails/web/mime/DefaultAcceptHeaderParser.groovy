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
package org.grails.web.mime

import groovy.transform.CompileStatic

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import grails.web.mime.AcceptHeaderParser
import grails.web.mime.MimeType

/**
 * Parsed the HTTP accept header into a a list of MimeType instances in the order of priority.
 * Priority is dictated by the order of the mime entries and the associated q parameter.
 * The higher the q parameter the higher the priority.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class DefaultAcceptHeaderParser implements AcceptHeaderParser {

    static final Log LOG = LogFactory.getLog(DefaultAcceptHeaderParser)

    MimeType[] configuredMimeTypes

    DefaultAcceptHeaderParser() {}

    DefaultAcceptHeaderParser(MimeType[] configuredMimeTypes) {
        this.configuredMimeTypes = configuredMimeTypes
    }

    MimeType[] parse(String header, MimeType fallbackMimeType = null) {
        List<MimeType> mimes = []
        MimeType[] mimeConfig = configuredMimeTypes
        if (!mimeConfig) {
            if (LOG.isDebugEnabled()) {
                LOG.debug "No mime types configured, defaulting to 'text/html'"
            }
            mimeConfig = MimeType.createDefaults()
        }

        if (!header) {
            return mimeConfig
        }

        String[] tokens = header.split(',')
        for (String t in tokens) {
            if (t.indexOf(';') > -1) {
                List tokenWithArgs = t.split(';').toList()
                Map<String, String> params = [:]
                final paramsList = tokenWithArgs.size() > 1 ? tokenWithArgs[1..-1] : []
                paramsList.each{ it ->
                    String theString = it as String
                    def i = theString.indexOf('=')
                    if (i > -1) {
                        params[theString[0..i-1].trim()] = theString[i+1..-1].trim()
                    }
                }
                if (params) {
                    createMimeTypeAndAddToList(tokenWithArgs[0].trim(),mimeConfig, mimes, params)
                }
                else {
                    createMimeTypeAndAddToList(tokenWithArgs[0].trim(),mimeConfig, mimes)
                }
            }
            else {
                createMimeTypeAndAddToList(t.trim(),mimeConfig, mimes)
            }
        }

        if (!mimes) {
            LOG.debug "No configured mime types found for Accept header: $header"
            return fallbackMimeType ? [fallbackMimeType] as MimeType[] : MimeType.createDefaults()
        }

        // remove duplicate text/xml and application/xml entries
        MimeType textXml = mimes.find { MimeType it -> it.name == 'text/xml' }
        MimeType appXml = mimes.find { MimeType it -> it.name ==  MimeType.XML.name }
        if (textXml && appXml) {
            // take the largest q value
            appXml.parameters.q = [textXml.qualityAsNumber, appXml.qualityAsNumber].max()

            mimes.remove(textXml)
        }
        else if (textXml) {
            textXml.name = MimeType.XML.name
        }

        if (appXml) {
            // prioritise more specific XML types like xhtml+xml if they are of equal quality
            def specificTypes = mimes.findAll { MimeType it -> it.name ==~ /\S+?\+xml$/ }
            def appXmlIndex = mimes.indexOf(appXml)
            def appXmlQuality = appXml.qualityAsNumber
            for (mime in specificTypes) {
                if (mime.qualityAsNumber < appXmlQuality) continue

                def mimeIndex = mimes.indexOf(mime)
                if (mimeIndex > appXmlIndex) {
                    mimes.remove(mime)
                    mimes.add(appXmlIndex, mime)
                }
            }
        }
        mimes.sort(true, new QualityComparator())
        mimes as MimeType[]
    }


    protected void createMimeTypeAndAddToList(String name, MimeType[] mimeConfig, List<MimeType> mimes, Map<String,String> params = null) {
        def mime = params ? new MimeType(name, params) : new MimeType(name)
        //First try to find the exact match for the mime type using name and version. If version is not set,  consider
        // version match to be successful.
        def foundMime = mimeConfig.find { MimeType mt ->
            mt.name == name && (!mime.version || mt.version == mime.version)
        }
        //Fallback: Try to find match using the name (if version match is not found).
        foundMime = foundMime?: mimeConfig.find { MimeType mt -> mt.name == name }
        if (foundMime) {
            mime.extension = foundMime.extension
            mimes << mime
        }
    }
}

@CompileStatic
class QualityComparator implements Comparator<MimeType> {

    int compare(MimeType t, MimeType t1) {
        BigDecimal left = t.qualityAsNumber
        BigDecimal right = t1.qualityAsNumber
        if (left > right) return -1
        if (left < right) return 1
        return 0
    }
}
