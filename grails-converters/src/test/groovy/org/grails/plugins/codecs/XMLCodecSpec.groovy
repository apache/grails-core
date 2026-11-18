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

import org.springframework.util.ClassUtils

import org.grails.plugins.converters.ConvertersGrailsPlugin
import spock.lang.Specification

/**
 * The XML codec escapes without the grails-xml module, which this module's tests do not include.
 */
class XMLCodecSpec extends Specification {

    void 'the converters plugin provides the XML codec, so an application without grails-xml can escape XML'() {
        expect:
        new ConvertersGrailsPlugin().providedArtefacts.contains(XMLCodec)
    }

    void 'text is escaped for XML'() {
        expect:
        new XMLCodec().encoder.encode('<a href="x">Tom & Jerry\'s</a>').toString() ==
                '&lt;a href=&quot;x&quot;&gt;Tom &amp; Jerry&#39;s&lt;/a&gt;'
        new XMLCodec().encoder.encode(1).toString() == '1'
        new XMLCodec().encoder.encode(null) == null
    }

    void 'without grails-xml another object is escaped as its string'() {
        given:
        def value = new Object() {
            @Override
            String toString() { '<b>bold</b>' }
        }

        expect:
        !ClassUtils.isPresent('grails.converters.XML', XMLCodecSpec.classLoader)
        new XMLCodec().encoder.encode(value).toString() == '&lt;b&gt;bold&lt;/b&gt;'
    }

    void 'escaped text is unescaped'() {
        expect:
        new XMLCodec().decoder.decode('&lt;a&gt;Tom &amp; Jerry&lt;/a&gt;') == '<a>Tom & Jerry</a>'
    }
}
