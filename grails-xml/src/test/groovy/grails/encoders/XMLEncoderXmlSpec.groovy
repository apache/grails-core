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
package grails.encoders

import grails.converters.XML
import spock.lang.Specification

/**
 * The XML encoder of grails-converters finds the XML converter of this module by its name, which it cannot reference.
 */
class XMLEncoderXmlSpec extends Specification {

    void 'with grails-xml, an object other than text is encoded with the XML converter'() {
        expect:
        new XMLEncoder().encode([title: 'Grails']) instanceof XML
    }

    void 'text is still escaped'() {
        expect:
        new XMLEncoder().encode('<b>&</b>').toString() == '&lt;b&gt;&amp;&lt;/b&gt;'
    }
}
