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

import java.sql.Connection

import org.grails.orm.hibernate.HibernateDatastore

/**
 * Reads the foreign keys of a table back from the database, as the column pairs the key is matched by.
 */
class ForeignKeyPairs {

    /**
     * The foreign keys of a table, keyed by the referenced table, each as its column pairs
     * [foreign key column, referenced column] in {@code KEY_SEQ} order, which is the order the
     * database matches the columns of a key by.
     */
    static Map<String, List<List<String>>> of(HibernateDatastore datastore, String table) {
        Map<String, List<List<String>>> keys = [:]
        datastore.sessionFactory.openSession().withCloseable { session ->
            session.doWork { Connection c ->
                c.metaData.getImportedKeys(null, null, table).withCloseable { rs ->
                    List<List> rows = []
                    while (rs.next()) {
                        rows << [rs.getString('FK_NAME'), rs.getInt('KEY_SEQ'),
                                 rs.getString('PKTABLE_NAME').toLowerCase(),
                                 rs.getString('FKCOLUMN_NAME').toLowerCase(), rs.getString('PKCOLUMN_NAME').toLowerCase()]
                    }
                    rows.sort { it[1] }.each { List row ->
                        keys.get(row[0] + ' -> ' + row[2], []) << [row[3], row[4]]
                    }
                }
            }
        }
        keys.collectEntries { String name, List<List<String>> pairs -> [(name.substring(name.indexOf(' -> ') + 4)): pairs] }
    }
}
