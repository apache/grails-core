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

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.JPA_DEFAULT_DISCRIMINATOR_TYPE;

import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.SimpleValue;

public class DefaultDiscriminatorBinder {

  private static final String STRING_TYPE = "string";

  private final SimpleValueColumnBinder simpleValueColumnBinder;

  public DefaultDiscriminatorBinder(SimpleValueColumnBinder simpleValueColumnBinder) {
    this.simpleValueColumnBinder = simpleValueColumnBinder;
  }

  /**
   * Binds a discriminator with default configuration (no explicit config)
   *
   * @param entity The root class entity
   * @param discriminator The discriminator value to configure
   */
  public void bindDefaultDiscriminator(RootClass entity, SimpleValue discriminator) {
    // Use class name as discriminator value
    entity.setDiscriminatorValue(entity.getClassName());

    // Bind with default column configuration
    simpleValueColumnBinder.bindSimpleValue(
        discriminator, STRING_TYPE, JPA_DEFAULT_DISCRIMINATOR_TYPE, false);
  }
}
