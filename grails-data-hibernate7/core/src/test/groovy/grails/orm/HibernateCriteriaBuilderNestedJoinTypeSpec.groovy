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
package grails.orm

import org.hibernate.resource.jdbc.spi.StatementInspector
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.DetachedCriteria
import grails.gorm.transactions.Rollback
import org.apache.grails.data.testing.tck.domains.Face
import org.apache.grails.data.testing.tck.domains.Nose
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.domains.Pet
import org.apache.grails.data.testing.tck.domains.PetType
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings

class HibernateCriteriaBuilderNestedJoinTypeSpec extends Specification {

    // The join type an association block takes, as HibernateCriteriaBuilder#convertFromInt reads it
    static final int LEFT_JOIN = 1

    @Shared
    NestedJoinCapture sqlCapture = new NestedJoinCapture()

    @Shared
    @AutoCleanup
    HibernateDatastore hibernateDatastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver(
                    (Settings.SETTING_DB_CREATE): 'create-drop',
                    'hibernate.session_factory.statement_inspector': sqlCapture
            ),
            Person, Pet, PetType, Face, Nose
    )

    @Shared
    PlatformTransactionManager transactionManager = hibernateDatastore.transactionManager

    void setup() {
        sqlCapture.statements.clear()
    }

    @Rollback
    void 'a nested association block keeps the left join it asks for'() {
        given:
        savePets()

        when:
        List<Pet> pets = Pet.createCriteria().list {
            owner(LEFT_JOIN) {
                face(LEFT_JOIN) {
                    or {
                        isNull('id')
                        eq('name', 'Homer')
                    }
                }
            }
            order('name')
        }
        String sql = sqlCapture.statements.last()

        then: 'the pet of an owner without a face and the pet without an owner are still there'
        pets*.name == ['Lucky', 'Rex', 'Whiskers']
        ownerFaceJoins(sql) == ['left']
        !(sql =~ /(?i)(?<!left )join (person|face)/)
    }

    @Rollback
    void 'a criterion in a nested left joined block only filters the rows that have the association'() {
        given:
        savePets()

        expect:
        Pet.createCriteria().list {
            owner(LEFT_JOIN) {
                face(LEFT_JOIN) {
                    or {
                        isNull('id')
                        eq('name', 'Marge')
                    }
                }
            }
            order('name')
        }*.name == ['Rex', 'Whiskers']
    }

    @Rollback
    void 'a nested association block without a join type is still an inner join'() {
        given:
        savePets()

        when:
        List<Pet> pets = Pet.createCriteria().list {
            owner(LEFT_JOIN) {
                face {
                    eq('name', 'Homer')
                }
            }
        }
        String sql = sqlCapture.statements.last()

        then:
        pets*.name == ['Lucky']
        ownerFaceJoins(sql) == ['inner']
    }

    @Rollback
    void 'a projection counts the rows a nested left join keeps'() {
        given:
        savePets()

        expect:
        Pet.createCriteria().get {
            owner(LEFT_JOIN) {
                face(LEFT_JOIN) {
                    or {
                        isNull('id')
                        eq('name', 'Homer')
                    }
                }
            }
            projections {
                rowCount()
            }
        } == 3L
    }

    @Rollback
    void 'the nested block and a block of the queried class for an association of the same name do not mix'() {
        given:
        savePets()

        expect: 'the face of the owner is left joined, the face of the pet inner joined, in either order'
        Pet.createCriteria().list {
            owner(LEFT_JOIN) {
                face(LEFT_JOIN) {
                    or {
                        isNull('id')
                        eq('name', 'Homer')
                    }
                }
            }
            face {
                eq('name', 'Santa')
            }
        }*.name == ['Lucky']
        Pet.createCriteria().list {
            face {
                eq('name', 'Santa')
            }
            owner(LEFT_JOIN) {
                face(LEFT_JOIN) {
                    or {
                        isNull('id')
                        eq('name', 'Homer')
                    }
                }
            }
        }*.name == ['Lucky']
    }

    @Rollback
    void 'a nested block without a recorded join type does not reuse the join of an outer block of the same name'() {
        given:
        savePets()

        expect: 'the face of the owner is joined from the owner, not taken from the face of the pet'
        new DetachedCriteria(Pet).build {
            face {
                eq('name', 'Santa')
            }
            owner {
                face {
                    eq('name', 'Homer')
                }
            }
        }.list()*.name == ['Lucky']
    }

    @Rollback
    void 'an association block of a subquery joins from the root of the subquery'() {
        given:
        savePets()

        expect: 'the subquery selects the age of every pet of Homer, not only for the pets of Homer'
        Pet.createCriteria().list {
            owner(LEFT_JOIN) {
                or {
                    isNull('id')
                    isNotNull('id')
                }
            }
            'in'('age', new DetachedCriteria(Pet).build {
                owner {
                    eq('firstName', 'Homer')
                }
                projections {
                    property('age')
                }
            })
            order('name')
        }*.name == ['Lucky', 'Rex']
    }

    @Rollback
    void 'a criterion on a collection of a nested block still restricts the collection'() {
        given:
        savePets()

        expect:
        Pet.createCriteria().list {
            owner {
                isNotEmpty('pets')
                pets {
                    eq('name', 'Rex')
                }
            }
        }*.name == ['Rex']
    }

    /**
     * Returns, for each join of the face of a person in the SQL, whether it is a left or an inner join.
     */
    private static List<String> ownerFaceJoins(String sql) {
        Set<String> people = sql.findAll(/(?i)join person (\w+)/) { String all, String alias -> alias } as Set
        sql.findAll(/(?i)(left )?join face \w+ on \w+\.id=(\w+)\.face_id/) { String all, String left, String from ->
            from in people ? (left ? 'left' : 'inner') : null
        }.findAll()
    }

    private static void savePets() {
        Person homer = new Person(firstName: 'Homer', lastName: 'Simpson', age: 39, face: face('Homer')).save()
        Person ned = new Person(firstName: 'Ned', lastName: 'Flanders', age: 60).save()
        new Pet(name: 'Lucky', age: 5, owner: homer, face: face('Santa')).save()
        new Pet(name: 'Rex', age: 5, owner: ned).save()
        new Pet(name: 'Whiskers', age: 7).save(flush: true)
    }

    private static Face face(String name) {
        Face face = new Face(name: name)
        face.nose = new Nose(hasFreckles: true, face: face)
        face
    }
}

class NestedJoinCapture implements StatementInspector {

    final List<String> statements = Collections.synchronizedList([])

    @Override
    String inspect(String sql) {
        statements << sql
        sql
    }
}
