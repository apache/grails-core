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

import java.util.Optional;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
public class ColumnNameForPropertyAndPathFetcher {

  private final PersistentEntityNamingStrategy namingStrategy;
  private final DefaultColumnNameFetcher defaultColumnNameFetcher;
  private final BackticksRemover backticksRemover;

  public ColumnNameForPropertyAndPathFetcher(
      PersistentEntityNamingStrategy namingStrategy,
      DefaultColumnNameFetcher defaultColumnNameFetcher,
      BackticksRemover backticksRemover) {
    this.namingStrategy = namingStrategy;
    this.defaultColumnNameFetcher = defaultColumnNameFetcher;
    this.backticksRemover = backticksRemover;
  }

  private static final String UNDERSCORE = "_";

  public String getColumnNameForPropertyAndPath(
          HibernatePersistentProperty grailsProp, String path, ColumnConfig cc) {
    return Optional.ofNullable(grailsProp.getColumnName(cc))
        .orElseGet(
            () -> {
              String suffix = defaultColumnNameFetcher.getDefaultColumnName(grailsProp);
              return Optional.ofNullable(path)
                  .filter(GrailsHibernateUtil::isNotEmpty)
                  .map(
                      p ->
                          backticksRemover.apply(namingStrategy.resolveColumnName(p))
                              + UNDERSCORE
                              + backticksRemover.apply(suffix))
                  .orElse(suffix);
            });
  }
}
