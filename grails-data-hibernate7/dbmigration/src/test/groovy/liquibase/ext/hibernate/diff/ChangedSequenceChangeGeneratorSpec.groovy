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

import liquibase.database.Database
import liquibase.diff.Difference
import liquibase.diff.ObjectDifferences
import liquibase.diff.output.DiffOutputControl
import liquibase.diff.output.changelog.ChangeGeneratorChain
import liquibase.ext.hibernate.database.HibernateDatabase
import liquibase.structure.core.Sequence
import spock.lang.Specification

class ChangedSequenceChangeGeneratorSpec extends Specification {

    ChangedSequenceChangeGenerator generator = new ChangedSequenceChangeGenerator()

    def "getPriority returns correct priority for Sequence and others"() {
        expect:
        generator.getPriority(Sequence, Mock(Database)) == 50 // PRIORITY_ADDITIONAL
        generator.getPriority(String, Mock(Database)) == -1 // PRIORITY_NONE
    }

    def "fixChanged filters ignored fields for HibernateDatabase"() {
        given:
        Sequence sequence = new Sequence(name: "my_seq")
        ObjectDifferences differences = Mock()
        DiffOutputControl control = Mock()
        HibernateDatabase hibernateDatabase = Mock()
        Database otherDatabase = Mock()
        ChangeGeneratorChain chain = Mock()
        
        Difference diff1 = new Difference("name", "old", "new")
        Difference diff2 = new Difference("cacheSize", 10, 20)
        
        when:
        generator.fixChanged(sequence, differences, control, hibernateDatabase, otherDatabase, chain)

        then:
        _ * differences.getDifferences() >> [diff1, diff2]
        1 * differences.removeDifference("cacheSize")
    }

    def "fixChanged ignores name case differences if databases are case-insensitive"() {
        given:
        Sequence sequence = new Sequence(name: "my_seq")
        ObjectDifferences differences = Mock()
        HibernateDatabase hibernateDatabase = Mock()
        Database otherDatabase = Mock()
        
        hibernateDatabase.isCaseSensitive() >> false
        otherDatabase.isCaseSensitive() >> true
        
        Difference diff = new Difference("name", "MY_SEQ", "my_seq")

        when:
        generator.fixChanged(sequence, differences, Mock(DiffOutputControl), hibernateDatabase, otherDatabase, Mock(ChangeGeneratorChain))

        then:
        _ * differences.getDifferences() >> [diff]
        1 * differences.removeDifference("name")
    }

    def "fixChanged ignores startValue/incrementBy differences if values are 1 or 50 vs null"() {
        given:
        Sequence sequence = new Sequence()
        ObjectDifferences differences = Mock()
        HibernateDatabase hibernateDatabase = Mock()
        Database otherDatabase = Mock()
        
        Difference diff1 = new Difference("startValue", "1", null)
        Difference diff2 = new Difference("incrementBy", null, "50")

        when:
        generator.fixChanged(sequence, differences, Mock(DiffOutputControl), hibernateDatabase, otherDatabase, Mock(ChangeGeneratorChain))

        then:
        _ * differences.getDifferences() >> [diff1, diff2]
        1 * differences.removeDifference("startValue")
        1 * differences.removeDifference("incrementBy")
    }

    def "fixChanged does not filter if no HibernateDatabase involved"() {
        given:
        Sequence sequence = new Sequence()
        ObjectDifferences differences = Mock()
        Database db1 = Mock()
        Database db2 = Mock()
        
        db1.getClass() >> Database // Not HibernateDatabase
        db2.getClass() >> Database

        when:
        generator.fixChanged(sequence, differences, Mock(DiffOutputControl), db1, db2, Mock(ChangeGeneratorChain))

        then:
        0 * differences.getDifferences()
    }
}
