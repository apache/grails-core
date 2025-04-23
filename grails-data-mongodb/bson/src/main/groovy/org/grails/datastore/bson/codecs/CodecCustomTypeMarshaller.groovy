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

import groovy.transform.CompileStatic
import org.bson.BsonDocument
import org.bson.BsonDocumentWriter
import org.bson.BsonWriter
import org.bson.Document
import org.bson.codecs.Codec
import org.bson.codecs.EncoderContext
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.internal.MappingUtils
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.query.Query

/**
 * Custom type handler for types that have codecs
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class CodecCustomTypeMarshaller implements CustomTypeMarshaller<Document, Document, Document> {
    final Codec codec
    final MappingContext mappingContext

    CodecCustomTypeMarshaller(Codec codec, MappingContext mappingContext) {
        this.codec = codec
        this.mappingContext = mappingContext
    }

    @Override
    boolean supports(MappingContext context) {
        return context == mappingContext
    }

    @Override
    boolean supports(Datastore datastore) {
        return false
    }

    @Override
    Class getTargetType() {
        return codec.encoderClass
    }

    @Override
    Object write(PersistentProperty property, Document value, Document nativeTarget) {
        throw new UnsupportedOperationException("Use the codec directly");
    }

    @Override
    Document query(PersistentProperty property, Query.PropertyCriterion criterion, Document nativeQuery) {
        throw new UnsupportedOperationException("Use the codec directly");
    }

    @Override
    Document read(PersistentProperty property, Document source) {
        throw new UnsupportedOperationException("Use the codec directly");
    }

}
