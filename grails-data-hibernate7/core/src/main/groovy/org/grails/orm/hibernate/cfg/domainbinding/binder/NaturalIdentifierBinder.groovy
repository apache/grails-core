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
package org.grails.orm.hibernate.cfg.domainbinding.binder

import groovy.transform.CompileStatic
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.UniqueKey

import org.grails.orm.hibernate.cfg.NaturalId
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePropertyIdentity
import org.grails.orm.hibernate.cfg.domainbinding.util.UniqueNameGenerator

@CompileStatic
class NaturalIdentifierBinder {

    private final UniqueNameGenerator uniqueNameGenerator

    NaturalIdentifierBinder(UniqueNameGenerator uniqueNameGenerator) {
        this.uniqueNameGenerator = uniqueNameGenerator
    }

    NaturalIdentifierBinder() {
        this(new UniqueNameGenerator())
    }

    void bindNaturalIdentifier(
            GrailsHibernatePersistentEntity persistentEntity, PersistentClass persistentClass) {
        HibernatePropertyIdentity identity = persistentEntity.hibernateMappedForm.identity
        if (identity == null) {
            return
        }
        NaturalId natural = identity.natural
        if (natural == null) {
            return
        }
        Optional<UniqueKey> uniqueKey = natural.createUniqueKey(persistentClass)
        if (uniqueKey.isPresent()) {
            UniqueKey uk = uniqueKey.get()
            uniqueNameGenerator.setGeneratedUniqueName(uk)
            persistentClass.table.addUniqueKey(uk)
        }
    }

}
