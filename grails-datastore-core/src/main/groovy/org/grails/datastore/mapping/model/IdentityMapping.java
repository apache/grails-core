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
package org.grails.datastore.mapping.model;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public interface IdentityMapping extends PropertyMapping {

    /**
     * The identifier property name(s). Usually there is just one identifier
     * name, however in the case of a composite or natural identifier there
     * may be serveral.
     *
     * @return identifier names that make up the key
     */
    String[] getIdentifierName();

    /**
     * @return The type of value generated used
     */
    ValueGenerator getGenerator();

    /**
     * The native storage type for this identifier, which may differ from the declared Java type.
     *
     * <p>When non-{@code null}, the backend is expected to coerce identifier values between
     * the declared type and this type at write, read, and query time. Currently honored by
     * MongoDB GORM to support patterns like "declare {@code String id}, store BSON
     * {@code ObjectId}" without requiring per-call conversion in application code.
     *
     * @return the storage type, or {@code null} to use the declared property type.
     * @since 7.1.1
     */
    default Class<?> getStoredAs() {
        return null;
    }
}
