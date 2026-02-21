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

import jakarta.annotation.Nonnull;
import java.io.Serial;
import java.util.Map;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Collection;

public class MapSecondPass implements org.hibernate.boot.spi.SecondPass, GrailsSecondPass {
  @Serial private static final long serialVersionUID = -3244991685626409031L;

  private final MapSecondPassBinder mapSecondPassBinder;
  protected final HibernateToManyProperty property;
  protected final @Nonnull InFlightMetadataCollector mappings;
  protected final Collection collection;

  public MapSecondPass(
      MapSecondPassBinder mapSecondPassBinder,
      HibernateToManyProperty property,
      @Nonnull InFlightMetadataCollector mappings,
      @Nonnull Collection coll) {
    this.mapSecondPassBinder = mapSecondPassBinder;
    this.property = property;
    this.mappings = mappings;
    this.collection = coll;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public void doSecondPass(Map persistentClasses) throws MappingException {
    mapSecondPassBinder.bindMapSecondPass(
        property, mappings, persistentClasses, (org.hibernate.mapping.Map) collection);
    createCollectionKeys(collection);
  }
}
