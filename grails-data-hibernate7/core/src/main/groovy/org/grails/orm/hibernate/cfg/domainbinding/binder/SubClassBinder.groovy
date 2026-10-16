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
import jakarta.annotation.Nonnull
import org.hibernate.mapping.JoinedSubclass
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.SingleTableSubclass
import org.hibernate.mapping.Subclass
import org.hibernate.mapping.UnionSubclass

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterBinder

/** Binder for subclasses. */
@CompileStatic
class SubClassBinder {

    private final SubclassMappingBinder subclassMappingBinder
    private final MultiTenantFilterBinder multiTenantFilterBinder
    private final String dataSourceName

    SubClassBinder(
            SubclassMappingBinder subclassMappingBinder,
            MultiTenantFilterBinder multiTenantFilterBinder,
            String dataSourceName) {
        this.subclassMappingBinder = subclassMappingBinder
        this.multiTenantFilterBinder = multiTenantFilterBinder
        this.dataSourceName = dataSourceName
    }

    /**
     * Binds a sub class.
     *
     * @param sub The sub domain class instance
     * @param parent The parent persistent class instance
     * @return The list of subclasses created
     */
    List<Subclass> bindSubClass(@Nonnull HibernatePersistentEntity sub, PersistentClass parent) {
        Subclass subClass = subclassMappingBinder.createSubclassMapping(sub, parent)
        sub.setPersistentClass(subClass)
        bindMultiTenantFilter(sub, subClass)
        List<Subclass> subclasses = new ArrayList<>()
        subclasses.add(subClass)
        for (HibernatePersistentEntity sub1 : sub.getChildEntities(dataSourceName)) {
            subclasses.addAll(bindSubClass(sub1, subClass))
        }
        return subclasses
    }

    private void bindMultiTenantFilter(HibernatePersistentEntity sub, Subclass subClass) {
        if (subClass instanceof SingleTableSubclass) {
            multiTenantFilterBinder.bind(sub, (SingleTableSubclass) subClass)
        }
        else if (subClass instanceof JoinedSubclass) {
            multiTenantFilterBinder.bind(sub, (JoinedSubclass) subClass)
        }
        else if (subClass instanceof UnionSubclass) {
            multiTenantFilterBinder.bind(sub, (UnionSubclass) subClass)
        }
    }

}
