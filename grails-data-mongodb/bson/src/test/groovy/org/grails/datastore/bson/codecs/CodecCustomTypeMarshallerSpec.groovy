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
package org.grails.datastore.bson.codecs

import org.bson.Document
import org.bson.codecs.Codec
import spock.lang.Specification

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentProperty

class CodecCustomTypeMarshallerSpec extends Specification {

    Codec codec = Mock(Codec)
    MappingContext mappingContext = Stub(MappingContext)
    CodecCustomTypeMarshaller marshaller = new CodecCustomTypeMarshaller(codec, mappingContext)

    void "supports the exact mapping context it was constructed with"() {
        expect:
        marshaller.supports(mappingContext)

        and:
        !marshaller.supports(Stub(MappingContext))
    }

    void "never supports a Datastore directly"() {
        expect:
        !marshaller.supports(Stub(Datastore))
    }

    void "reports the codec's encoder class as its target type"() {
        given:
        1 * codec.encoderClass >> String

        expect:
        marshaller.targetType == String
    }

    void "write is unsupported, callers must use the codec directly"() {
        when:
        marshaller.write(Stub(PersistentProperty), new Document(), new Document())

        then:
        thrown(UnsupportedOperationException)
    }

    void "query is unsupported, callers must use the codec directly"() {
        when:
        marshaller.query(Stub(PersistentProperty), null, new Document())

        then:
        thrown(UnsupportedOperationException)
    }

    void "read is unsupported, callers must use the codec directly"() {
        when:
        marshaller.read(Stub(PersistentProperty), new Document())

        then:
        thrown(UnsupportedOperationException)
    }
}
