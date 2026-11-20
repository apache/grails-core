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
package liquibase.ext.hibernate.snapshot.extension

import grails.gorm.annotation.Entity
import liquibase.ext.hibernate.snapshot.HibernateSnapshotIntegrationSpec
import liquibase.structure.core.Table
import org.hibernate.id.enhanced.TableGenerator

class TableGeneratorSnapshotGeneratorSpec extends HibernateSnapshotIntegrationSpec {

    TableGeneratorSnapshotGenerator generator = new TableGeneratorSnapshotGenerator()

    @Override
    List<Class> getEntityClasses() {
        return [TableGeneratorEntity]
    }

    def "snapshot returns table with generator details"() {
        given:
        def persister = datastore.sessionFactory.getMappingMetamodel().getEntityDescriptor(TableGeneratorEntity.name)
        def tableGenerator = persister.getGenerator() as TableGenerator

        when:
        Table table = generator.snapshot(tableGenerator)

        then:
        table.name == tableGenerator.getTableName()
        table.getColumn(tableGenerator.getSegmentColumnName()) != null
        table.getColumn(tableGenerator.getValueColumnName()) != null
        table.primaryKey != null
    }
}

@Entity
class TableGeneratorEntity {
    Long id

    static mapping = {
        id generator: 'table'
    }
}
