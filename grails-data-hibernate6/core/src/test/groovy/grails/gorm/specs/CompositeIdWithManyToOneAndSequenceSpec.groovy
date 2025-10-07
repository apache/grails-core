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
        manager.addAllDomainClasses([Tooth, ToothDisease])
    }

    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/835')
    void "Test composite id one to many and sequence"() {

        when:"a one to many association is created"
        def tooth = new Tooth()
        def td = new ToothDisease(idColumn: 1, nrVersion: 1)
        tooth.addToToothDiseases(td)
        tooth.save(flush: true, failOnError: true)

        and:"the session is cleared to ensure we are checking persisted state"
        manager.session.clear()

        then:"The object was saved and the association is correct"
        Tooth.count() == 1
        ToothDisease.count() == 1
        def reloadedTooth = Tooth.list().first()
        reloadedTooth.toothDiseases.size() == 1
    }

}


@Entity
class Tooth {
    Integer id
    SortedSet<ToothDisease> toothDiseases

    static hasMany = [toothDiseases: ToothDisease]
    static mappedBy = [toothDiseases: 'tooth']

    static mapping = {
        table name: 'AK_TOOTH'
        id generator: 'sequence', params: [sequence: 'SEQ_AK_TOOTH']
    }
}

@Entity
class ToothDisease implements Serializable, Comparable<ToothDisease> {
    Integer idColumn
    Integer nrVersion

    static belongsTo = [tooth: Tooth]

    static mapping = {
        table name: 'AK_TOOTH_DISEASE'
        idColumn column: 'ID', type: 'integer'
        nrVersion column: 'NR_VERSION', type: 'integer'
        id composite: ['idColumn', 'nrVersion']
        tooth column: 'tooth_id'
    }

    @Override
    int compareTo(ToothDisease other) {
        def idCmp = this.idColumn <=> other.idColumn
        if (idCmp != 0) {
            return idCmp
        }
        return this.nrVersion <=> other.nrVersion
    }

    @Override
    boolean equals(Object o) {
        if (this.is(o)) return true
        if (getClass() != o.getClass()) return false

        ToothDisease that = (ToothDisease) o

        if (idColumn != that.idColumn) return false
        if (nrVersion != that.nrVersion) return false

        return true
    }

    @Override
    int hashCode() {
        int result
        result = idColumn.hashCode()
        result = 31 * result + nrVersion.hashCode()
        return result
    }
}