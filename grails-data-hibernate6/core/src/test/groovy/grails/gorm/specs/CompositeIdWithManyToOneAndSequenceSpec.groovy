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

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import jakarta.annotation.Nonnull

//import org.jetbrains.annotations.NotNull
import spock.lang.Issue

/**
 * Created by graemerocher on 26/01/2017.
 */
class CompositeIdWithManyToOneAndSequenceSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.domainClasses.addAll([Tooth, ToothDisease])
    }

    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/835')
    void "Test composite id many to one and sequence"() {

        when: "a many to one association is created"
        ToothDisease td = new ToothDisease(nrVersion: 1).save()

        def tooth = new Tooth()
        tooth.toothDisease << td
        tooth.save(flush: true)

        then: "The object was saved"
        Tooth.count() == 1
        Tooth.list().first().toothDisease != null
    }

}


@Entity
class Tooth {
    Integer id
    SortedSet<ToothDisease> toothDisease
    static mapping = {
        table name: 'AK_TOOTH'
        id generator: 'sequence', params: [sequence: 'SEQ_AK_TOOTH']
        toothDisease {
            column name: 'FK_AK_TOOTH_ID'
            column name: 'FK_AK_TOOTH_NR_VERSION'
        }
    }
}

@Entity
class ToothDisease implements Serializable, Comparable<ToothDisease> {
    Integer idColumn
    Integer nrVersion
    static mapping = {
        table name: 'AK_TOOTH_DISEASE'
        idColumn column: 'ID', generator: 'sequence', params: [sequence: 'SEQ_AK_TOOTH_DISEASE']
        nrVersion column: 'NR_VERSION'
        id composite: ['idColumn', 'nrVersion']
    }

    @Override
    int compareTo(@Nonnull ToothDisease o) {
        return idColumn <=> ((ToothDisease) o).idColumn
    }
}