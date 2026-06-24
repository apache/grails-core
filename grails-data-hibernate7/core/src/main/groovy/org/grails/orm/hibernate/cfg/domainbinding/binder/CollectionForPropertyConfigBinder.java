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
package org.grails.orm.hibernate.cfg.domainbinding.binder;

import java.util.Optional;

import org.jspecify.annotations.NonNull;

import org.hibernate.mapping.Collection;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

/**
 * Binds collection-level lazy and extra-lazy settings from the property config.
 *
 * @since 8.0
 */
public class CollectionForPropertyConfigBinder {

    /** Bind collection for property config. */
    public void bindCollectionForPropertyConfig(@NonNull HibernateToManyProperty property) {
        Collection collection = property.getCollection();
        collection.setLazy(property.isLazy());
        Optional.ofNullable(property.getLazy()).ifPresent(collection::setExtraLazy);
    }
}
