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
package org.grails.orm.hibernate.cfg.domainbinding.util;

import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceWrapper;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.generator.Generator;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Table;

public class BasicValueIdCreator {

  private final JdbcEnvironment jdbcEnvironment;
  private final GrailsSequenceWrapper grailsSequenceWrapper;

  public BasicValueIdCreator(JdbcEnvironment jdbcEnvironment) {
    this.jdbcEnvironment = jdbcEnvironment;
    this.grailsSequenceWrapper = new GrailsSequenceWrapper();
  }

  protected BasicValueIdCreator(
      JdbcEnvironment jdbcEnvironment, GrailsSequenceWrapper grailsSequenceWrapper) {
    this.jdbcEnvironment = jdbcEnvironment;
    this.grailsSequenceWrapper = grailsSequenceWrapper;
  }

  public BasicValue getBasicValueId(
      MetadataBuildingContext metadataBuildingContext,
      Table table,
      Identity mappedId,
      GrailsHibernatePersistentEntity domainClass,
      boolean useSequence) {
    BasicValue id = new BasicValue(metadataBuildingContext, table);
    // create a BasicValue for the specific entity table (do not reuse the prototype directly
    // because table differs)
    String generatorName = Identity.determineGeneratorName(mappedId, useSequence);
    id.setCustomIdGeneratorCreator(
        context -> createGenerator(mappedId, domainClass, context, generatorName));
    return id;
  }

  private Generator createGenerator(
      Identity mappedId,
      GrailsHibernatePersistentEntity domainClass,
      GeneratorCreationContext context,
      String generatorName) {
    return grailsSequenceWrapper.getGenerator(
        generatorName, context, mappedId, domainClass, jdbcEnvironment);
  }
}
