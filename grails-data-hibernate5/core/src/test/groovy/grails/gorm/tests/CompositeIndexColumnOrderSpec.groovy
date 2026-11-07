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
package grails.gorm.tests

import grails.gorm.annotation.Entity

import spock.lang.Issue

@Issue('https://github.com/apache/grails-core/issues/14424')
class CompositeIndexColumnOrderSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(OrderedIndexNft)
    }

    void "properties naming the same index give a composite index in declaration order"() {
        when:
        List<String> columns = []
        datastore.dataSource.connection.withCloseable { conn ->
            [ 'ordered_index_nft', 'ORDERED_INDEX_NFT' ].each { String table ->
                conn.metaData.getIndexInfo(null, null, table, false, false).withCloseable { rs ->
                    List<List> rows = []
                    while (rs.next()) {
                        if (rs.getString('INDEX_NAME')?.equalsIgnoreCase('idx_ordered_nft')) {
                            rows << [rs.getInt('ORDINAL_POSITION'), rs.getString('COLUMN_NAME').toLowerCase()]
                        }
                    }
                    if (rows) {
                        columns = rows.sort { it[0] }.collect { (String) it[1] }
                    }
                }
            }
        }

        then:
        columns == ['token', 'address']
    }
}

@Entity
class OrderedIndexNft {
    String token
    String address

    static mapping = {
        token index: 'idx_ordered_nft'
        address index: 'idx_ordered_nft'
    }
}
