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

import jakarta.annotation.Nonnull;

import org.hibernate.mapping.RootClass;

import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentity;

public class IdentityBinder {

    private final SimpleIdBinder simpleIdBinder;
    private final CompositeIdBinder compositeIdBinder;

    public IdentityBinder(SimpleIdBinder simpleIdBinder, CompositeIdBinder compositeIdBinder) {
        this.simpleIdBinder = simpleIdBinder;
        this.compositeIdBinder = compositeIdBinder;
    }

    public void bindIdentity(@Nonnull GrailsHibernatePersistentEntity domainClass, RootClass root) {

        var id = domainClass.getHibernateIdentity();
        if (id instanceof CompositeIdentity || (id == null && domainClass.getCompositeIdentity() != null)) {
            compositeIdBinder.bindCompositeId(domainClass, root, (CompositeIdentity) id);
        } else {
            Identity identity = id instanceof Identity ? (Identity) id : null;
            if (identity != null && identity.getName() == null) {
                identity.setName(root.getEntityName());
            }
            simpleIdBinder.bindSimpleId(domainClass, root, identity, root.getTable());
        }
    }
}
