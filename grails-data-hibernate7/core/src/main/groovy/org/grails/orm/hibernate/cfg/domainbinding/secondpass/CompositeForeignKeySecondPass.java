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

import java.io.Serial;
import java.util.Map;

import org.hibernate.MappingException;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.SimpleValue;

import org.grails.orm.hibernate.cfg.HibernateCompositeIdentity;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;

/**
 * Aligns a foreign key with the composite identifier it references once every persistent class is
 * bound. The binder registers it when the referenced entity, or an entity nested in its identifier,
 * is bound later in the first pass than the entity holding the key, so the sort could not run yet.
 */
@SuppressWarnings("PMD.NonSerializableClass")
public class CompositeForeignKeySecondPass implements org.hibernate.boot.spi.SecondPass, java.io.Serializable {

    @Serial
    private static final long serialVersionUID = 4312279830117492651L;

    private final CompositeIdentifierToManyToOneBinder binder;
    private final HibernatePersistentProperty property;
    private final SimpleValue value;
    private final HibernateCompositeIdentity compositeId;
    private final GrailsHibernatePersistentEntity refDomainClass;

    public CompositeForeignKeySecondPass(
            CompositeIdentifierToManyToOneBinder binder,
            HibernatePersistentProperty property,
            SimpleValue value,
            HibernateCompositeIdentity compositeId,
            GrailsHibernatePersistentEntity refDomainClass) {
        this.binder = binder;
        this.property = property;
        this.value = value;
        this.compositeId = compositeId;
        this.refDomainClass = refDomainClass;
    }

    @Override
    public void doSecondPass(Map<String, PersistentClass> persistentClasses) throws MappingException {
        binder.alignWithReferencedIdentifier(property, value, compositeId, refDomainClass);
    }
}
