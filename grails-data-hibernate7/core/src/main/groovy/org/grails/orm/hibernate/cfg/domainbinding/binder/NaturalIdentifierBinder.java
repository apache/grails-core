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

import org.hibernate.mapping.PersistentClass;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentity;
import org.grails.orm.hibernate.cfg.domainbinding.util.UniqueNameGenerator;

public class NaturalIdentifierBinder {

    private final UniqueNameGenerator uniqueNameGenerator;

    public NaturalIdentifierBinder(UniqueNameGenerator uniqueNameGenerator) {
        this.uniqueNameGenerator = uniqueNameGenerator;
    }

    public NaturalIdentifierBinder() {
        this(new UniqueNameGenerator());
    }

    public void bindNaturalIdentifier(
            GrailsHibernatePersistentEntity persistentEntity, PersistentClass persistentClass) {
        Optional.ofNullable(persistentEntity.getMappedForm().getIdentity())
                .map(HibernateIdentity::getNatural)
                .flatMap(naturalId -> naturalId.createUniqueKey(persistentClass))
                .ifPresent(uk -> {
                    uniqueNameGenerator.setGeneratedUniqueName(uk);
                    persistentClass.getTable().addUniqueKey(uk);
                });
    }
}
