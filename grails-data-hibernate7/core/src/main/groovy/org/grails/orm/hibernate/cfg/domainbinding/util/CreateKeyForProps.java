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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.hibernate.MappingException;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;

public class CreateKeyForProps {

  private final ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher;
  private final UniqueKeyForColumnsCreator uniqueKeyForColumnsCreator;

  public CreateKeyForProps(
      ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher) {
    this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher;
    this.uniqueKeyForColumnsCreator = new UniqueKeyForColumnsCreator();
  }

  protected CreateKeyForProps(
      ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
      UniqueKeyForColumnsCreator uniqueKeyForColumnsCreator) {
    this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher;
    this.uniqueKeyForColumnsCreator = uniqueKeyForColumnsCreator;
  }

  public void createKeyForProps(
      HibernatePersistentProperty grailsProp, String path, Table table, String columnName) {
    PropertyConfig mappedForm = grailsProp.getMappedForm();

    if (mappedForm.isUnique() && mappedForm.isUniqueWithinGroup()) {

      List<Column> keyList = new ArrayList<>();
      keyList.add(new Column(columnName));
      List<String> propertyNames = mappedForm.getUniquenessGroup();
      PersistentEntity owner = grailsProp.getOwner();
      for (Iterator<?> i = propertyNames.iterator(); i.hasNext(); ) {
        String propertyName = (String) i.next();
        HibernatePersistentProperty otherProp =
            (HibernatePersistentProperty) owner.getPropertyByName(propertyName);
        if (otherProp == null) {
          throw new MappingException(
              owner.getJavaClass().getName() + " references an unknown property " + propertyName);
        }
        String otherColumnName =
            columnNameForPropertyAndPathFetcher.getColumnNameForPropertyAndPath(
                otherProp, path, null);
        keyList.add(new Column(otherColumnName));
      }

      uniqueKeyForColumnsCreator.createUniqueKeyForColumns(table, keyList);
    }
  }
}
