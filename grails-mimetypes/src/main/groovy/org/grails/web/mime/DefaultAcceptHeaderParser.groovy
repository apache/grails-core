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
import org.springframework.http.InvalidMediaTypeException
import org.springframework.http.MediaType

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
                LOG.debug("No mime types configured, defaulting to 'text/html'")
            }
            mimeConfig = MimeType.createDefaults()
        }

        if (!header) {
            return mimeConfig
        }

        for (MimeType requestedMimeType in parseRequestedMimeTypes(header)) {
            createMimeTypeAndAddToList(requestedMimeType, mimeConfig, mimes)
        }

        if (!mimes) {
            LOG.debug("No configured mime types found for Accept header: $header")
            return fallbackMimeType ? [fallbackMimeType] as MimeType[] : MimeType.createDefaults()
        }

        // remove duplicate text/xml and application/xml entries
        MimeType textXml = mimes.find { MimeType it -> it.name == 'text/xml' }
        MimeType appXml = mimes.find { MimeType it -> it.name ==  MimeType.XML.name }
        if (textXml && appXml) {
            // take the largest q value, with a type of its own, since a type reads its quality once, in the place of the
            // one the header lists first, which decides between types of equal quality
            BigDecimal quality = [textXml.qualityAsNumber, appXml.qualityAsNumber].max()
            MimeType merged = new MimeType(appXml.name, appXml.extension, appXml.parameters + [q: quality.toString()])
            int place = Math.min(mimes.indexOf(textXml), mimes.indexOf(appXml))
            mimes.remove(textXml)
            mimes.remove(appXml)
            mimes.add(place, merged)
            appXml = merged
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

    /**
     * @param header an {@code Accept} header
     * @return the media types it lists, in the order it lists them, read with Spring's parser, and a token Spring
     *         rejects read leniently, as Grails 8 read it, except that the names of its parameters are read ignoring
     *         case, as Spring reads them
     * @since 9.0
     */
    protected List<MimeType> parseRequestedMimeTypes(String header) {
        List<MimeType> mimeTypes = []

        for (String token in header.split(',')) {
            String candidate = token.trim()
            try {
                if (candidate.split(';', 2)[0].trim() == '*') {
                    // Spring reads a bare *, with parameters or without, as */*; Grails never has, and a configured type
                    // is not a match
                    throw new InvalidMediaTypeException(candidate, 'Not a media type')
                }
                MediaType mediaType = MediaType.parseMediaType(candidate)
                mimeTypes.add(SpringMediaTypeAdapter.toMimeType(mediaType))
            }
            catch (InvalidMediaTypeException ignored) {
                // Read leniently what Spring rejects, as Grails 8 did, such as a parameter with an empty value, an
                // invalid character in a name, an unknown charset, or an out-of-range or non-numeric quality value; the
                // names of its parameters are read in lower case, as Spring reads them
                mimeTypes.add(new MimeType(candidate))
            }
        }

        // In the order of the header, which decides between types of equal quality once they are sorted
        return mimeTypes
    }

    /**
     * The configured type a problem type of RFC 9457 is read as: its format's own type, {@code application/json} or
     * {@code application/xml}, where that is configured, whatever order the configuration lists the types of the format
     * in, and otherwise the first type of the format.
     */
    private static MimeType problemFormat(String name, MimeType[] mimeConfig) {
        MimeType format = name.equalsIgnoreCase('application/problem+json') ? MimeType.JSON :
                name.equalsIgnoreCase('application/problem+xml') ? MimeType.XML : null
        if (format == null) {
            return null
        }
        mimeConfig.find { MimeType mt -> mt.extension == format.extension && mt.name.equalsIgnoreCase(format.name) } ?:
                mimeConfig.find { MimeType mt -> mt.extension == format.extension }
    }

    /**
     * Adds a requested media type to the list, as the configured type it matches, if it matches one. It replaces the
     * method of Grails 8 that took the name and the parameters of the type, which a subclass that overrode it no longer
     * overrides.
     *
     * @param mime a requested media type
     * @param mimeConfig the configured types
     * @param mimes the types the header resolves to
     * @since 9.0
     */
    protected void createMimeTypeAndAddToList(MimeType mime, MimeType[] mimeConfig, List<MimeType> mimes) {
        String name = mime.name
        //First try to find the exact match for the mime type using name and version. If version is not set,  consider
        // version match to be successful.
        // Spring's parser lowercases the requested name, so configured names compare ignoring case.
        def foundMime = mimeConfig.find { MimeType mt ->
            mt.name.equalsIgnoreCase(name) && (!mime.version || mt.version == mime.version)
        }
        //Fallback: Try to find match using the name (if version match is not found).
        foundMime = foundMime ?: mimeConfig.find { MimeType mt -> mt.name.equalsIgnoreCase(name) }
        // The problem types of RFC 9457 are JSON and XML documents: unless they are configured, they are read as the own types
        // of those formats, where configured, whatever order the configuration lists the types of a format in
        foundMime = foundMime ?: problemFormat(name, mimeConfig)
        if (foundMime) {
            mime.name = foundMime.name
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
