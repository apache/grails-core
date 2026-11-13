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
package org.grails.datastore.gorm.mongo

import groovy.transform.CompileStatic

import grails.mongodb.MongoEntity
import org.grails.compiler.gorm.GormEntityTraitProvider
import org.grails.datastore.mapping.reflect.ClassUtils

/**
 * Tells GORM to use the {@link MongoEntity} trait for Mongo entities
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
class MongoEntityTraitProvider implements GormEntityTraitProvider {

    final Class entityTrait = MongoEntity

    final boolean available = ClassUtils.isPresent('com.mongodb.client.MongoClient')

    /**
     * {@code String}, not {@code ObjectId}.
     *
     * <p>A Mongo entity declaring a String id is given a generated {@code ObjectId} in its
     * hexadecimal form, so it reads and binds as an ordinary String - in URLs, in JSON, and as a
     * request parameter. An {@code ObjectId}-typed id is generated the same way and gives up that
     * convenience everywhere else: it serializes as its component fields rather than a hex string, and
     * every caller has to construct one. Neither involves the sequence collection a {@code Long} id
     * needs. An entity wanting the String form in code and a native {@code ObjectId} in storage asks
     * for it with {@code id storedAs: ObjectId}.</p>
     *
     * @since 8.0
     */
    final Class<?> defaultIdentityType = String
}
