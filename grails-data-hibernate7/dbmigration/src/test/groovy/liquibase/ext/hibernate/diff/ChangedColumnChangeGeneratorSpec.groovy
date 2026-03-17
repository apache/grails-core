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
package liquibase.ext.hibernate.diff

import liquibase.change.Change
import liquibase.database.Database
import liquibase.diff.Difference
import liquibase.diff.ObjectDifferences
import liquibase.diff.output.DiffOutputControl
import liquibase.ext.hibernate.database.HibernateDatabase
import liquibase.statement.DatabaseFunction
import liquibase.structure.core.Column
import liquibase.structure.core.DataType
import liquibase.structure.core.Table
import spock.lang.Specification

class ChangedColumnChangeGeneratorSpec extends Specification {

    ChangedColumnChangeGenerator generator = new ChangedColumnChangeGenerator()

    def "getPriority returns correct priority for Column and others"() {
        expect:
        generator.getPriority(Column, Mock(Database)) == 50 // PRIORITY_ADDITIONAL
        generator.getPriority(DataType, Mock(Database)) == -1 // PRIORITY_NONE
    }

    def "handleTypeDifferences ignores size for TIMESTAMP and TIME for HibernateDatabase"() {
        given:
        Column column = new Column()
        column.setType(new DataType(typeName))
        ObjectDifferences differences = Mock()
        DiffOutputControl control = Mock()
        List<Change> changes = []
        HibernateDatabase hibernateDatabase = Mock()

        when:
        generator.handleTypeDifferences(column, differences, control, changes, hibernateDatabase, hibernateDatabase)

        then:
        0 * differences.getDifference("type")

        where:
        typeName << ["TIMESTAMP", "TIME", "timestamp", "time"]
    }

    def "handleTypeDifferences handles size changes for other types"() {
        given:
        Column column = new Column()
        column.setName("myCol")
        column.setRelation(new Table(name: "myTable"))
        column.setType(new DataType("VARCHAR"))
        ObjectDifferences differences = Mock()
        DiffOutputControl control = Mock()
        List<Change> changes = []
        HibernateDatabase hibernateDatabase = Mock()
        
        Difference diff = new Difference("type", new DataType("VARCHAR(10)"), new DataType("VARCHAR(20)"))
        diff.referenceValue.setColumnSize(10)
        diff.comparedValue.setColumnSize(20)

        when:
        generator.handleTypeDifferences(column, differences, control, changes, hibernateDatabase, hibernateDatabase)

        then:
        _ * differences.getDifference("type") >> diff
        1 * differences.getDifferences() >> [diff]
        0 * differences.removeDifference("type")
    }

    def "handleTypeDifferences removes difference if size is same"() {
        given:
        Column column = new Column()
        column.setName("myCol")
        column.setRelation(new Table(name: "myTable"))
        column.setType(new DataType("VARCHAR"))
        ObjectDifferences differences = Mock()
        DiffOutputControl control = Mock()
        List<Change> changes = []
        HibernateDatabase hibernateDatabase = Mock()
        
        Difference diff = new Difference("type", new DataType("VARCHAR(10)"), new DataType("VARCHAR(10)"))
        diff.referenceValue.setColumnSize(10)
        diff.comparedValue.setColumnSize(10)

        when:
        generator.handleTypeDifferences(column, differences, control, changes, hibernateDatabase, hibernateDatabase)

        then:
        _ * differences.getDifference("type") >> diff
        1 * differences.getDifferences() >> [diff]
        1 * differences.removeDifference("type")
    }

    def "handleDefaultValueDifferences ignores null to DatabaseFunction changes for HibernateDatabase"() {
        given:
        Column column = new Column()
        ObjectDifferences differences = Mock()
        DiffOutputControl control = Mock()
        List<Change> changes = []
        HibernateDatabase hibernateDatabase = Mock()
        
        Difference diff = new Difference("defaultValue", null, new DatabaseFunction("now()"))

        when:
        generator.handleDefaultValueDifferences(column, differences, control, changes, hibernateDatabase, hibernateDatabase)

        then:
        1 * differences.getDifference("defaultValue") >> diff
        0 * differences.getDifferences()
    }
}
