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

import static org.grails.orm.hibernate.cfg.GrailsHibernateUtil.unqualify;

import jakarta.annotation.Nonnull;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.PersistentClass;

/** The class binder class. */
public class ClassBinder {

  /**
   * Binds the specified persistant class to the runtime model based on the properties defined in
   * the domain class
   *
   * @param persistentEntity The Grails domain class
   * @param persistentClass The persistant class
   * @param collector Existing collector
   */
  public void bindClass(
      @Nonnull GrailsHibernatePersistentEntity persistentEntity,
      PersistentClass persistentClass,
      @Nonnull InFlightMetadataCollector collector) {
    persistentClass.setLazy(true);
    var entityName = persistentEntity.getName();
    persistentClass.setEntityName(entityName);
    persistentClass.setJpaEntityName(entityName);
    persistentClass.setProxyInterfaceName(entityName);
    persistentClass.setClassName(entityName);
    persistentClass.setDynamicInsert(false);
    persistentClass.setDynamicUpdate(false);
    persistentClass.setSelectBeforeUpdate(false);

    boolean autoImport;
    Mapping mappedForm = persistentEntity.getMappedForm();
    if (mappedForm != null) {
      autoImport = mappedForm.isAutoImport();
    } else {
      autoImport =
          collector.getMetadataBuildingOptions().getMappingDefaults().isAutoImportEnabled();
    }
    if (autoImport) {
      String unqualified = unqualify(entityName);
      persistentClass.setJpaEntityName(unqualified);
      collector.addImport(unqualified, entityName);
    }
  }
}
