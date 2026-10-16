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
package org.grails.commons

import grails.core.ArtefactHandler
import groovy.lang.GroovyClassLoader
import org.grails.core.artefact.DomainClassArtefactHandler
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * @author Marc Palmer
 */
class CodecArtefactHandlerTests {

    @Test
    void testIsCodecClass() {

        ArtefactHandler handler = new CodecArtefactHandler()
        GroovyClassLoader gcl = new GroovyClassLoader()

        Class<?> fullCodecClass = gcl.parseClass('''class FullCodec {
static def encode = { str -> }
static def decode = { str -> }
}
''')
        assertTrue(handler.isArtefact(fullCodecClass), 'class was an encoder/decoder')

        Class<?> decodeOnlyCodecClass = gcl.parseClass('''class DecodeOnlyCodec {
static def decode = { str -> }
}
''')
        assertTrue(handler.isArtefact(decodeOnlyCodecClass), 'class was a decoder')

        Class<?> encodeOnlyCodecClass = gcl.parseClass('''class EncodeOnlyCodec {
static def encode = { str -> }
}
''')
        assertTrue(handler.isArtefact(encodeOnlyCodecClass), 'class was an encoder')

        Class<?> nonCodecClass = gcl.parseClass('''class SomeFoo {
static def encode = { str -> }
}
''')
        assertFalse(handler.isArtefact(nonCodecClass), 'class was not a codec')
    }

    @Test
    void testDomainClassWithNameEndingInCodecIsNotACodec() {
        GroovyClassLoader gcl = new GroovyClassLoader()
        Class<?> c = gcl.parseClass('@grails.persistence.Entity\nclass MySpecialCodec { Long id;Long version;}\n')

        ArtefactHandler domainClassHandler = new DomainClassArtefactHandler()
        assertTrue(domainClassHandler.isArtefact(c))

        ArtefactHandler codecHandler = new CodecArtefactHandler()
        assertFalse(codecHandler.isArtefact(c))
    }
}
