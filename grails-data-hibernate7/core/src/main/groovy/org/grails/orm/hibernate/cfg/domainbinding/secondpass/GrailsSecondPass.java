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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import org.hibernate.mapping.Collection;

/**
 * Mixin of the second passes that created the keys of their collection.
 *
 * @deprecated since 8.0.1: the second passes still implement it, so that code which casts to it keeps compiling, but
 *     they no longer call it. The keys of a collection are created by
 *     {@code CollectionSecondPassBinder#createCollectionKeys}, which also gives a set that shares its join table the
 *     uniqueness of its association; the default method here creates the keys and nothing else. Kept for one release
 *     for code that implements or calls it.
 */
@Deprecated
public interface GrailsSecondPass {

    default void createCollectionKeys(Collection collection) {
        collection.createAllKeys();
    }
}
