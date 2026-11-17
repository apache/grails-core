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
package grails.gorm.tests

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

/**
 * An aggregate on the right of a comparison is taken over the criteria that holds the comparison, not over the
 * class a helper declares for it: a helper typed with a parent class, called with the criteria of a subclass,
 * compares with the aggregate of the subclass.
 */
class WhereAggregateHelperSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(AhAnimal, AhCat)
    }

    static DetachedCriteria<AhAnimal> heavierThanAverage(DetachedCriteria<AhAnimal> criteria) {
        criteria.where { weight > avg(weight) }
    }

    static DetachedCriteria<AhAnimal> lightestOnly(DetachedCriteria<AhAnimal> criteria) {
        criteria.where { weight == min(weight) }
    }

    private void seed() {
        [1, 2, 3].each { new AhAnimal(weight: it).save(failOnError: true) }
        [10, 20, 30].each { new AhCat(weight: it).save(failOnError: true) }
        session.flush()
        session.clear()
    }

    void "the average of a helper typed with the parent class is taken over the criteria passed in"() {
        given: 'the average of every animal is 11, and that of the cats is 20'
        seed()

        expect:
        heavierThanAverage(AhCat.where { }).list()*.weight == [30]
        heavierThanAverage(AhAnimal.where { }).list()*.weight.sort() == [20, 30]
    }

    void "the minimum of a helper typed with the parent class is taken over the criteria passed in"() {
        given: 'the lightest animal weighs 1, and the lightest cat 10'
        seed()

        expect:
        lightestOnly(AhCat.where { }).list()*.weight == [10]
        lightestOnly(AhAnimal.where { }).list()*.weight == [1]
    }
}

@Entity
class AhAnimal {
    Integer weight
}

@Entity
class AhCat extends AhAnimal {
}
