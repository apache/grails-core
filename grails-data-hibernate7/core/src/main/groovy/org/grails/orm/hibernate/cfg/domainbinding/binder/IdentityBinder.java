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

import org.hibernate.MappingException;
import org.hibernate.mapping.RootClass;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateCompositeIdentityProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleIdentityProperty;

public class IdentityBinder {

    private final SimpleIdBinder simpleIdBinder;
    private final CompositeIdBinder compositeIdBinder;

    public IdentityBinder(SimpleIdBinder simpleIdBinder, CompositeIdBinder compositeIdBinder) {
        this.simpleIdBinder = simpleIdBinder;
        this.compositeIdBinder = compositeIdBinder;
    }

    public void bindIdentity(@Nonnull HibernatePersistentEntity domainClass, RootClass root) {
        domainClass.setPersistentClass(root);
        var identityProperty = domainClass.getIdentityProperty();
        if (identityProperty instanceof HibernateCompositeIdentityProperty) {
            compositeIdBinder.bindCompositeId(domainClass);
        } else if (identityProperty instanceof HibernateSimpleIdentityProperty) {
            simpleIdBinder.bindSimpleId(domainClass);
        } else {
            throw new MappingException("No identity found for " + domainClass.getName());
        }
    }
}
