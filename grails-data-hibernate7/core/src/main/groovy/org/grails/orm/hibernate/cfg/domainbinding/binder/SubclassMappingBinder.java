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

import org.checkerframework.checker.nullness.qual.NonNull;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Subclass;

import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity;

public class SubclassMappingBinder {

    private final JoinedSubClassBinder joinedSubClassBinder;
    private final UnionSubclassBinder unionSubclassBinder;
    private final SingleTableSubclassBinder singleTableSubclassBinder;
    private final ClassPropertiesBinder classPropertiesBinder;

    public SubclassMappingBinder(
            JoinedSubClassBinder joinedSubClassBinder,
            UnionSubclassBinder unionSubclassBinder,
            SingleTableSubclassBinder singleTableSubclassBinder,
            ClassPropertiesBinder classPropertiesBinder) {
        this.joinedSubClassBinder = joinedSubClassBinder;
        this.unionSubclassBinder = unionSubclassBinder;
        this.singleTableSubclassBinder = singleTableSubclassBinder;
        this.classPropertiesBinder = classPropertiesBinder;
    }

    public @NonNull Subclass createSubclassMapping(HibernatePersistentEntity subEntity, PersistentClass parent) {
        Subclass subClass;
        subEntity.configureDerivedProperties();
        Mapping m = subEntity.getHibernateMappedForm();
        if (subEntity.isJoinedSubclass()) {
            subClass = joinedSubClassBinder.bindJoinedSubClass(subEntity, parent);
        } else if (subEntity.isUnionSubclass()) {
            subClass = unionSubclassBinder.bindUnionSubclass(subEntity, parent);
        } else {
            subClass = singleTableSubclassBinder.bindSubClass(subEntity, parent);
        }

        subClass.setBatchSize(Optional.ofNullable(m.getBatchSize()).orElse(-1));
        subClass.setDynamicUpdate(m.getDynamicUpdate());
        subClass.setDynamicInsert(m.getDynamicInsert());
        subClass.setCached(parent.isCached());
        subClass.setAbstract(subEntity.isAbstract());
        subClass.setEntityName(subEntity.getName());
        subClass.setJpaEntityName(GrailsHibernateUtil.unqualify(subEntity.getName()));
        classPropertiesBinder.bindClassProperties(subEntity);
        return subClass;
    }
}
