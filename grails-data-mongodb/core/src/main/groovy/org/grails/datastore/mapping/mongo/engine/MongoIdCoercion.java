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
package org.grails.datastore.mapping.mongo.engine;

import org.springframework.core.convert.ConversionService;

import org.grails.datastore.mapping.model.ClassMapping;
import org.grails.datastore.mapping.model.IdentityMapping;
import org.grails.datastore.mapping.model.PersistentEntity;

/**
 * Centralizes coercion of an identifier value to the {@code storedAs} type declared
 * on a {@link PersistentEntity}'s id mapping.
 *
 * <p>Used at every boundary where in-memory ids cross into BSON: point lookups
 * ({@code MongoCodecEntityPersister#retrieveEntity}), batch lookups
 * ({@code MongoCodecEntityPersister#retrieveAllEntities}), update/delete filters
 * ({@code MongoCodecSession}), and query handlers ({@code MongoQuery}'s
 * {@code IdEquals} and {@code In}). Keeping the rules in one place prevents the
 * four call sites from diverging — the null-return fallback for converter
 * rejection (e.g. non-hex String → ObjectId) is the kind of subtlety that breaks
 * silently when duplicated.
 *
 * @since 7.1.1
 */
public final class MongoIdCoercion {

    private MongoIdCoercion() {
    }

    /**
     * Read the {@code storedAs} class from the entity's id mapping, or {@code null}
     * if the mapping doesn't declare one (or the mapping implementation predates
     * {@link IdentityMapping#getStoredAs()}).
     */
    public static Class<?> resolveStoredAs(PersistentEntity entity) {
        if (entity == null) return null;
        try {
            ClassMapping<?> mapping = entity.getMapping();
            if (mapping == null) return null;
            IdentityMapping identifier = mapping.getIdentifier();
            return identifier != null ? identifier.getStoredAs() : null;
        }
        catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Coerce {@code key} to the entity's {@code storedAs} type so query/update/delete
     * filters target BSON values that match what the encoder actually wrote on disk.
     *
     * <p>Returns the original {@code key} when:
     * <ul>
     *   <li>{@code key} is {@code null}, or already an instance of the storedAs type,</li>
     *   <li>the entity declares no {@code storedAs},</li>
     *   <li>the converter rejects the value by returning {@code null} (e.g. a non-hex
     *       natural-key String against {@code storedAs: ObjectId}) — kept symmetric with
     *       {@code IdentityEncoder}'s non-hex fallback so filters target the BSON String
     *       the encoder wrote rather than {@code {_id: null}},</li>
     *   <li>the converter throws.</li>
     * </ul>
     */
    public static Object coerceIdToStoredType(Object key, PersistentEntity entity) {
        if (key == null) return null;
        Class<?> storedAs = resolveStoredAs(entity);
        if (storedAs == null || storedAs.isInstance(key)) return key;
        try {
            ConversionService cs = entity.getMappingContext().getConversionService();
            Object converted = cs.convert(key, storedAs);
            return converted != null ? converted : key;
        }
        catch (Exception ignored) {
            return key;
        }
    }
}
