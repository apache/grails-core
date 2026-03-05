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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate;

import jakarta.persistence.Entity;
import java.util.Arrays;
import java.util.Optional;

import jakarta.persistence.Entity;

import org.hibernate.mapping.PersistentClass;

import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport;
import org.grails.datastore.mapping.model.AbstractClassMapping;
import org.grails.datastore.mapping.model.AbstractPersistentEntity;
import org.grails.datastore.mapping.model.ClassMapping;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.orm.hibernate.cfg.Mapping;

/**
 * Persistent entity implementation for Hibernate
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public class HibernatePersistentEntity extends AbstractPersistentEntity<Mapping>
        implements GrailsHibernatePersistentEntity {
    private final AbstractClassMapping<Mapping> classMapping;
    private String dataSourceName;
    private PersistentClass persistentClass;

  public HibernatePersistentEntity(Class<?> javaClass, final MappingContext context) {
    super(javaClass, context);

    this.classMapping = new HibernateClassMapping(this, context);
  }

  @Override
  public void setDataSourceName(String dataSourceName) {
    this.dataSourceName = dataSourceName;
  }

  @Override
  public String getDataSourceName() {
    return dataSourceName;
  }


  @Override
  public ClassMapping<Mapping> getMapping() {
    return this.classMapping;
  }

  public Mapping getMappedForm() {
    return Optional.ofNullable(getMapping()).map(ClassMapping::getMappedForm).orElse(null);
  }

  @Override
  public HibernatePersistentProperty getIdentity() {
    return identity instanceof HibernatePersistentProperty ghpp ? ghpp : null;
  }

  @Override
  @SuppressWarnings({"PMD.DataflowAnomalyAnalysis", "PMD.NullAssignment"})
  public HibernatePersistentProperty[] getCompositeIdentity() {
    PersistentProperty<?>[] compositeIdentity = super.getCompositeIdentity();
    if (compositeIdentity == null) {
      return new HibernatePersistentProperty[0];
    }
    return Arrays.stream(compositeIdentity)
        .map(p -> (HibernatePersistentProperty) p)
        .toArray(HibernatePersistentProperty[]::new);
  }

    @Override
    public String getDataSourceName() {
        return dataSourceName;
    }

    @Override
    public ClassMapping<Mapping> getMapping() {
        return this.classMapping;
    }

    public Mapping getMappedForm() {
        return Optional.ofNullable(getMapping())
                .map(ClassMapping::getMappedForm)
                .orElse(null);
    }

  @Override
  public HibernatePersistentProperty getVersion() {
    return (HibernatePersistentProperty )version;
  }
}
