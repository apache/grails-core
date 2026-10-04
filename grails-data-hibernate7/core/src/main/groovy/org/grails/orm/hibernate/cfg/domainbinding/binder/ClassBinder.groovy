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
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.PersistentClass

import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

import static org.grails.orm.hibernate.cfg.GrailsHibernateUtil.unqualify

/**
 * Binds a Grails domain class to the Hibernate persistent class model.
 *
 * @since 8.0
 */
@CompileStatic
class ClassBinder {

    private final InFlightMetadataCollector collector

    ClassBinder(@Nonnull InFlightMetadataCollector collector) {
        this.collector = collector
    }

    /**
     * Binds the specified persistent class to the runtime model based on the properties defined in
     * the domain class
     *
     * @param persistentEntity The Grails domain class
     * @param persistentClass The persistent class
     */
    void bindClass(@Nonnull GrailsHibernatePersistentEntity persistentEntity, PersistentClass persistentClass) {
        persistentClass.lazy = true
        String entityName = persistentEntity.name
        persistentClass.entityName = entityName
        persistentClass.jpaEntityName = entityName
        persistentClass.proxyInterfaceName = entityName
        persistentClass.className = entityName
        persistentClass.setAbstract(persistentEntity.isAbstract())

        Mapping mappedForm = persistentEntity.hibernateMappedForm
        boolean autoImport
        if (mappedForm != null) {
            autoImport = mappedForm.autoImport
            persistentClass.dynamicInsert = mappedForm.dynamicInsert
            persistentClass.dynamicUpdate = mappedForm.dynamicUpdate
            persistentClass.batchSize = mappedForm.batchSize != null ? mappedForm.batchSize : 0
        } else {
            autoImport =
                    collector.metadataBuildingOptions.mappingDefaults.isAutoImportEnabled()
            persistentClass.dynamicInsert = false
            persistentClass.dynamicUpdate = false
            persistentClass.batchSize = 0
        }
        persistentClass.selectBeforeUpdate = false
        persistentEntity.persistentClass = persistentClass

        if (autoImport) {
            String unqualified = unqualify(entityName)
            persistentClass.jpaEntityName = unqualified
            collector.addImport(unqualified, entityName)
        }
    }

}
