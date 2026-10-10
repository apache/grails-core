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
package org.grails.orm.hibernate.cfg.domainbinding.util

import groovy.transform.CompileStatic
import org.hibernate.mapping.Column
import org.hibernate.mapping.Table
import org.hibernate.mapping.UniqueKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CompileStatic
class UniqueKeyForColumnsCreator {

    private static final Logger LOG = LoggerFactory.getLogger(UniqueKeyForColumnsCreator)
    private final UniqueNameGenerator uniqueNameGenerator

    UniqueKeyForColumnsCreator() {
        uniqueNameGenerator = new UniqueNameGenerator()
    }

    protected UniqueKeyForColumnsCreator(UniqueNameGenerator uniqueNameGenerator) {
        this.uniqueNameGenerator = uniqueNameGenerator
    }

    void createUniqueKeyForColumns(Table table, List<Column> columns) {
        Collections.reverse(columns)

        UniqueKey uk = new UniqueKey(table)
        for (Column column : columns) {
            uk.addColumn(column)
        }

        if (LOG.debugEnabled) {
            LOG.debug('create unique key for {} columns = {}', table.name, columns)
        }
        uniqueNameGenerator.setGeneratedUniqueName(uk)
        table.addUniqueKey(uk)
    }

}
