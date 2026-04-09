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
import org.hibernate.mapping.ManyToOne;

import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.EMPTY_PATH;

/** Binds the element of a bidirectional many-to-many association. */
public class ManyToManyElementBinder {

    private final ManyToOneBinder manyToOneBinder;
    private final CollectionForPropertyConfigBinder collectionForPropertyConfigBinder;

    /** Creates a new {@link ManyToManyElementBinder} instance. */
    public ManyToManyElementBinder(
            ManyToOneBinder manyToOneBinder, CollectionForPropertyConfigBinder collectionForPropertyConfigBinder) {
        this.manyToOneBinder = manyToOneBinder;
        this.collectionForPropertyConfigBinder = collectionForPropertyConfigBinder;
    }

    /** Binds the ManyToOne element for a bidirectional many-to-many collection. */
    public void bind(HibernateManyToManyProperty property) {
        ManyToOne element = manyToOneBinder.bindManyToOne(property, EMPTY_PATH);
        Collection collection = property.getCollection();
        collection.setElement(element);
        collectionForPropertyConfigBinder.bindCollectionForPropertyConfig(property);
    }
}
