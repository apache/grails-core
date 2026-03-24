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

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.SingleTableSubclass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

/**
 * Binds a sub-class using table-per-hierarchy inheritance mapping
 *
 * @since 7.0
 */
public class SingleTableSubclassBinder {

    private static final Logger LOG = LoggerFactory.getLogger(SingleTableSubclassBinder.class);

    private final ClassBinder classBinder;
    private final MetadataBuildingContext metadataBuildingContext;

    public SingleTableSubclassBinder(ClassBinder classBinder, MetadataBuildingContext metadataBuildingContext) {
        this.classBinder = classBinder;
        this.metadataBuildingContext = metadataBuildingContext;
    }

    /**
     * Binds a sub-class using table-per-hierarchy inheritance mapping
     *
     * @param sub      The Grails domain class instance representing the sub-class
     * @param parent   The Hibernate Parent PersistentClass object
     * @return The created SingleTableSubclass
     */
    public SingleTableSubclass bindSubClass(@Nonnull GrailsHibernatePersistentEntity sub, PersistentClass parent) {
        SingleTableSubclass subClass = new SingleTableSubclass(parent, metadataBuildingContext);
        classBinder.bindClass(sub, subClass);
        subClass.setDiscriminatorValue(sub.getDiscriminatorValue());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Mapping subclass: " + subClass.getEntityName() + " -> " +
                    subClass.getTable().getName());
        }
        return subClass;
    }
}
