/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.grails.gradle.common

import javax.xml.parsers.SAXParserFactory

import spock.lang.Specification
import spock.lang.Unroll

class XmlParserFeatureSpec extends Specification {

    /**
     * Every identifier must be one the parser actually registers.
     *
     * <p>Callers set these inside a catch that tolerates a parser lacking a feature, so an
     * unrecognised identifier disables hardening silently instead of failing. Rewriting the
     * {@code http} scheme to {@code https} is the way that happens in practice. This spec derives
     * the identifiers from {@link XmlParserFeature#values()} rather than restating them, so the
     * same rewrite cannot pass by changing the expectation to match.
     */
    @Unroll
    void 'feature #feature is recognised by the parser'() {
        given:
        SAXParserFactory factory = SAXParserFactory.newInstance()

        when:
        factory.setFeature(feature.featureName, false)

        then:
        noExceptionThrown()

        where:
        feature << XmlParserFeature.values()
    }

    void 'every feature is distinct'() {
        expect:
        XmlParserFeature.values()*.featureName.toUnique().size() == XmlParserFeature.values().length
    }
}
