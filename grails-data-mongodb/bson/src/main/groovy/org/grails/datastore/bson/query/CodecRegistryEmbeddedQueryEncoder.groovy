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

package org.grails.datastore.bson.query

import org.bson.BsonDocument
import org.bson.BsonDocumentWriter
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecRegistry
import org.grails.datastore.bson.codecs.BsonPersistentEntityCodec
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Embedded

/**
 * Default embedded encoder that uses the codec registry
 *
 * @author Graeme Rocher
 * @since 6.0
 */
class CodecRegistryEmbeddedQueryEncoder implements EmbeddedQueryEncoder {

    final CodecRegistry codecRegistry

    CodecRegistryEmbeddedQueryEncoder(CodecRegistry codecRegistry) {
        this.codecRegistry = codecRegistry
    }

    @Override
    Object encode(Embedded embedded, Object instance) {
        PersistentEntity associatedEntity = embedded.associatedEntity
        Codec codec = codecRegistry.get(associatedEntity.javaClass)
        if(codec == null) {
            codec = new BsonPersistentEntityCodec(codecRegistry, associatedEntity)
        }
        final BsonDocument doc = new BsonDocument();
        codec.encode(new BsonDocumentWriter(doc), instance, BsonQuery.ENCODER_CONTEXT);
        return doc;
    }
}
