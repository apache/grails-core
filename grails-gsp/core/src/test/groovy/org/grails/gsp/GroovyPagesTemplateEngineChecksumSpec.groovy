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

import spock.lang.Specification

import org.springframework.core.io.ByteArrayResource

import org.grails.gsp.compiler.GroovyPageParser

/**
 * A page compiled at runtime records a checksum of its source, so that it is checked for staleness the same
 * way a precompiled page is.
 *
 * The checksum must be taken over the bytes as stored, not over the decoded or decorated source the parse
 * path works with, because the runtime re-reads the resource raw when comparing.
 */
class GroovyPagesTemplateEngineChecksumSpec extends Specification {

    private static final byte[] PAGE_SOURCE = '<html><body>${greeting}</body></html>'.bytes

    private GroovyPagesTemplateEngine engineForRuntimeCompilation() {
        new GroovyPagesTemplateEngine().tap { afterPropertiesSet() }
    }

    void 'a page compiled at runtime records a checksum of its stored bytes'() {
        given:
        GroovyPagesTemplateEngine engine = engineForRuntimeCompilation()

        when:
        GroovyPageTemplate template = engine.createTemplate(new ByteArrayResource(PAGE_SOURCE)) as GroovyPageTemplate

        then: 'the checksum is over the raw bytes, so it matches one taken by re-reading the resource'
        template.metaInfo.sourceChecksum == GroovyPageParser.checksumOf(PAGE_SOURCE)
    }
}
