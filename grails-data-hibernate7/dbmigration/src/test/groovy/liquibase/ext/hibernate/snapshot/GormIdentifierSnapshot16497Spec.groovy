/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package liquibase.ext.hibernate.snapshot

import grails.gorm.annotation.Entity
import liquibase.snapshot.SnapshotGeneratorFactory
import liquibase.structure.core.Column
import liquibase.structure.core.Schema
import liquibase.structure.core.Table

class GormIdentifierSnapshot16497Spec extends HibernateSnapshotIntegrationSpec {

    @Override
    List<Class> getEntityClasses() {
        [NativeIdentifier16497, SequenceIdentifier16497, IdentityIdentifier16497]
    }

    def 'native identifier snapshots through the GORM chain without a sequence default'() {
        when:
        Column column = snapshotId('native_identifier_16497')

        then:
        noExceptionThrown()
        column.autoIncrement
        !column.defaultValue?.toString()?.contains('nextval(')
    }

    def 'sequence identifier snapshots through the GORM chain with its nextval default'() {
        when:
        Column column = snapshotId('sequence_identifier_16497')

        then:
        noExceptionThrown()
        column.defaultValue.toString().contains('nextval(')
        column.defaultValue.toString().contains('gorm_sequence_16497')
        !column.autoIncrement
    }

    def 'identity identifier snapshots through the GORM chain as auto increment'() {
        when:
        Column column = snapshotId('identity_identifier_16497')

        then:
        noExceptionThrown()
        column.autoIncrement
    }

    private Column snapshotId(String tableName) {
        Table table = new Table().setName(tableName)
        table.setSchema(new Schema())
        Column example = new Column().setName('id').setRelation(table)
        SnapshotGeneratorFactory.instance.createSnapshot(example, database)
    }
}

@Entity
class NativeIdentifier16497 {
    Long id

    static mapping = {
        table 'native_identifier_16497'
    }
}

@Entity
class SequenceIdentifier16497 {
    Long id

    static mapping = {
        table 'sequence_identifier_16497'
        id generator: 'sequence', params: [sequence_name: 'gorm_sequence_16497']
    }
}

@Entity
class IdentityIdentifier16497 {
    Long id

    static mapping = {
        table 'identity_identifier_16497'
        id generator: 'identity'
    }
}
