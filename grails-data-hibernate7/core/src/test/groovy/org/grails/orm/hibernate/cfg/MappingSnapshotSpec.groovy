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
package org.grails.orm.hibernate.cfg

import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.hibernate.engine.spi.SessionFactoryImplementor
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Detects drift in what the domain binder produces. Behaviour specs cannot see a renamed column or table
 * because the same binder writes and reads it, so this records the physical schema H2 ends up with, plus the
 * contributor Hibernate tags each entity with (which decides whether Hibernate's schema tooling manages it)
 * and the data source each entity is bound to.
 *
 * <p>The expectation lives in {@code mapping-snapshot.txt}. A deliberate mapping change regenerates it by
 * running this spec with the environment variable {@code MAPPING_SNAPSHOT_UPDATE=true}; review the diff.</p>
 */
class MappingSnapshotSpec extends Specification {

    private static final String SNAPSHOT_RESOURCE = '/org/grails/orm/hibernate/cfg/mapping-snapshot.txt'
    private static final String SNAPSHOT_SOURCE_FILE = 'src/test/resources' + SNAPSHOT_RESOURCE
    private static final String TABLE_PREFIX = 'SNAP_'

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(SnapAuthor, SnapBook, SnapVehicle, SnapCar, SnapTag)

    void "the physical schema and entity tagging match the recorded snapshot"() {
        when:
        String actual = snapshot()

        then:
        if (System.getenv('MAPPING_SNAPSHOT_UPDATE') == 'true') {
            new File(SNAPSHOT_SOURCE_FILE).text = actual
        }
        actual == MappingSnapshotSpec.getResourceAsStream(SNAPSHOT_RESOURCE).text
    }

    private String snapshot() {
        StringBuilder out = new StringBuilder()
        entityLines(out)
        Connection connection = datastore.connectionSources.defaultConnectionSource.dataSource.connection
        try {
            schemaLines(out, connection.metaData)
        } finally {
            connection.close()
        }
        return out.toString()
    }

    private void entityLines(StringBuilder out) {
        SessionFactoryImplementor sessionFactory = (SessionFactoryImplementor) datastore.sessionFactory
        [SnapAuthor, SnapBook, SnapCar, SnapTag, SnapVehicle].each { Class entityClass ->
            def persister = sessionFactory.mappingMetamodel.getEntityDescriptor(entityClass)
            GrailsHibernatePersistentEntity entity =
                    (GrailsHibernatePersistentEntity) datastore.mappingContext.getPersistentEntity(entityClass.name)
            out << "entity ${entityClass.simpleName} contributor=${persister.contributor} " +
                    "dataSource=${entity.dataSourceName}\n"
        }
    }

    private void schemaLines(StringBuilder out, DatabaseMetaData metaData) {
        List<String> tables = rows(metaData.getTables(null, null, '%', ['TABLE'] as String[])) { ResultSet rs ->
            rs.getString('TABLE_NAME')
        }.findAll { it.startsWith(TABLE_PREFIX) }.sort()

        tables.each { String table ->
            out << "table ${table}\n"
            rows(metaData.getColumns(null, null, table, '%')) { ResultSet rs ->
                "  column ${rs.getString('COLUMN_NAME')} ${rs.getString('TYPE_NAME')}" +
                        "(${rs.getInt('COLUMN_SIZE')},${rs.getInt('DECIMAL_DIGITS')}) " +
                        "${rs.getInt('NULLABLE') == DatabaseMetaData.columnNullable ? 'NULL' : 'NOT NULL'}"
            }.sort().each { out << it << '\n' }
            out << "  primaryKey ${rows(metaData.getPrimaryKeys(null, null, table)) { ResultSet rs -> rs.getString('COLUMN_NAME') }.sort()}\n"
            rows(metaData.getImportedKeys(null, null, table)) { ResultSet rs ->
                "  foreignKey ${rs.getString('FKCOLUMN_NAME')} -> ${rs.getString('PKTABLE_NAME')}.${rs.getString('PKCOLUMN_NAME')}"
            }.sort().each { out << it << '\n' }
            rows(metaData.getIndexInfo(null, null, table, false, false)) { ResultSet rs ->
                [rs.getString('INDEX_NAME'), rs.getBoolean('NON_UNIQUE'), rs.getString('COLUMN_NAME')]
            }.groupBy { it[0] }.values().collect { List<List> index ->
                "  index unique=${!index[0][1]} columns=${index.collect { it[2] }.sort()}"
            }.sort().each { out << it << '\n' }
        }
    }

    private static <T> List<T> rows(ResultSet resultSet, Closure<T> mapper) {
        List<T> result = []
        try {
            while (resultSet.next()) {
                result << mapper.call(resultSet)
            }
        } finally {
            resultSet.close()
        }
        return result
    }
}

@Entity
class SnapAuthor {

    String name

    static hasMany = [books: SnapBook]

    static constraints = {
        name blank: false, maxSize: 60
    }
}

@Entity
class SnapBook {

    String title
    Integer pages
    BigDecimal price
    SnapStatus status

    static belongsTo = [author: SnapAuthor]

    static mapping = {
        title column: 'book_title'
    }

    static constraints = {
        pages nullable: true
        price nullable: true, scale: 2
    }
}

enum SnapStatus {
    DRAFT, PUBLISHED
}

@Entity
class SnapVehicle {

    String name
}

@Entity
class SnapCar extends SnapVehicle {

    Integer doors
}

@Entity
class SnapTag {

    String name

    static hasMany = [labels: String]

    static mapping = {
        version false
    }
}
