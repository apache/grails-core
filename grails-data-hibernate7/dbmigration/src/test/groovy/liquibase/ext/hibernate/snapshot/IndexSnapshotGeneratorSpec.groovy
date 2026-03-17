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
package liquibase.ext.hibernate.snapshot

import grails.gorm.annotation.Entity
import liquibase.structure.core.Index as LiquibaseIndex
import liquibase.structure.core.Table as LiquibaseTable

class IndexSnapshotGeneratorSpec extends HibernateSnapshotIntegrationSpec {

    IndexSnapshotGenerator generator = new IndexSnapshotGenerator()

    @Override
    List<Class> getEntityClasses() {
        return [IndexedEntity]
    }

    def "addTo adds indexes to table"() {
        given:
        LiquibaseTable table = new LiquibaseTable(name: "indexed_entity")
        snapshot.getSnapshotControl().shouldInclude(LiquibaseIndex) >> true

        when:
        generator.addTo(table, snapshot)

        then:
        table.getIndexes().any { it.name.equalsIgnoreCase("idx_code") }
    }
}

@Entity
class IndexedEntity {
    String code

    static mapping = {
        table 'indexed_entity'
        code index: 'idx_code'
    }
}
