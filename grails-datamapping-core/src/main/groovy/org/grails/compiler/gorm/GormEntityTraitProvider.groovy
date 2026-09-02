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
package org.grails.compiler.gorm

/**
 * Provides the implementation to use for the GormEntity trait
 *
 * @author Graeme Rocher
 * @since 5.0
 */
interface GormEntityTraitProvider {

    Class getEntityTrait()

    /**
     * @return Whether this trait provided is available
     */
    boolean isAvailable()

    /**
     * The identity type injected into an entity mapped with this implementation when the entity does
     * not declare an {@code id} of its own, and the build has opted into native identity types with
     * {@code grails { gorm { defaultIdType = 'native' } }}.
     *
     * <p>Defaults to {@code Long}, which is what every entity is given when the build has not opted
     * in. An implementation whose stores generate identifiers of another type overrides this - GORM
     * for MongoDB returns {@code String}, because a Mongo {@code ObjectId} is generated for a String
     * id and handed back as its hexadecimal form, with no sequence collection involved.</p>
     *
     * @return the default identity type, never {@code null}
     * @since 8.0
     */
    default Class<?> getDefaultIdentityType() {
        Long
    }
}
