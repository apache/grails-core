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
package grails.gorm.specs

import org.grails.orm.hibernate.cfg.JoinTable
import org.grails.orm.hibernate.cfg.ColumnConfig

class CompositeKeyJoinTableIntegrationSpec extends HibernateGormDatastoreSpec {

    def "should bind joinTable with composite key mapping"() {
        given:
        def joinTable = new JoinTable(
            keys: [new ColumnConfig(name: 'a_col'), new ColumnConfig(name: 'b_col')],
            column: new ColumnConfig(name: 'c')
        )

        expect:
        joinTable.keys*.name == ['a_col', 'b_col']
        joinTable.getKey().name == 'a_col'
        joinTable.column.name == 'c'
    }

    // Add more integration scenarios as composite key support evolves
}
