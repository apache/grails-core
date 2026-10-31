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

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec

class SubqueryAssociationCorrelationSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(SubqueryCorrPerson, SubqueryCorrPet, SubqueryCorrAddress, SubqueryCorrCountry)
    }

    def setup() {
        def homer = new SubqueryCorrPerson(firstName: "Homer").save(failOnError: true)
        def ned = new SubqueryCorrPerson(firstName: "Ned").save(failOnError: true)

        // Homer owns "Santa's Little Helper" and "Bart"
        new SubqueryCorrPet(name: "Santa's Little Helper", owner: homer).save(failOnError: true)
        new SubqueryCorrPet(name: "Bart", owner: homer).save(failOnError: true)

        // Ned owns a pet named "Homer" and a pet named "Bart"
        new SubqueryCorrPet(name: "Homer", owner: ned).save(failOnError: true)
        new SubqueryCorrPet(name: "Bart", owner: ned).save(failOnError: true, flush: true)
    }

    void "subquery projecting association property should not correlate with outer association block"() {
        when:
        // Ned owns "Homer" and "Bart".
        // The subquery projects owner.firstName for pets named "Santa's Little Helper" (owned by Homer).
        // Subquery projects ["Homer"].
        // Ned's pet "Homer" matches ["Homer"].
        // If the subquery incorrectly correlates with the outer `owner` block (where owner is Ned),
        // the subquery would project outer Ned's firstName ("Ned") instead of joining owner from its own root,
        // resulting in 0 matches because Ned has no pet named "Ned".
        def results = SubqueryCorrPet.createCriteria().list {
            owner {
                eq('firstName', 'Ned')
            }
            'in'('name', new DetachedCriteria(SubqueryCorrPet).build {
                eq('name', "Santa's Little Helper")
                projections {
                    property('owner.firstName')
                }
            })
        }

        then:
        results.size() == 1
        results[0].name == "Homer"
        results[0].owner.firstName == "Ned"
    }

    void "subquery filtering on association property should not correlate with outer association block"() {
        when:
        // Outer query restricts to owner Ned (Ned's pets are ["Bart", "Homer"]).
        // Subquery filters for pets whose owner is Homer (firstName == 'Homer') and projects 'name' (["Santa's Little Helper", "Bart"]).
        // Ned's pet "Bart" is in ["Santa's Little Helper", "Bart"].
        // If the subquery incorrectly correlates with the outer owner (Ned),
        // the subquery checks if outer owner (Ned) has firstName == 'Homer', which is false,
        // returning empty subquery results and 0 outer pets.
        def results = SubqueryCorrPet.createCriteria().list {
            owner {
                eq('firstName', 'Ned')
            }
            'in'('name', new DetachedCriteria(SubqueryCorrPet).build {
                eq('owner.firstName', 'Homer')
                projections {
                    property('name')
                }
            })
        }

        then:
        results.size() == 1
        results[0].name == "Bart"
        results[0].owner.firstName == "Ned"
    }

    void "subquery with its own association block should not correlate with outer association block"() {
        when:
        // Outer query restricts to owner Ned.
        // Subquery has its own association block restricting to Homer and projects 'name'.
        // If the inner association block incorrectly reuses the outer join (Ned),
        // inner predicate (Homer) fails against outer join (Ned), yielding empty subquery and 0 results.
        def results = SubqueryCorrPet.createCriteria().list {
            owner {
                eq('firstName', 'Ned')
            }
            'in'('name', new DetachedCriteria(SubqueryCorrPet).build {
                owner {
                    eq('firstName', 'Homer')
                }
                projections {
                    property('name')
                }
            })
        }

        then:
        results.size() == 1
        results[0].name == "Bart"
        results[0].owner.firstName == "Ned"
    }

    void "subquery projecting countDistinct on association property should not correlate with outer association block"() {
        when:
        // Outer query restricts to owner Ned.
        // Inner subquery counts distinct owners across all pets in DB (2: Homer and Ned).
        // If the subquery incorrectly uses the outer owner join (which is filtered to Ned),
        // the subquery countDistinct('owner') would return 1 instead of 2.
        def results = SubqueryCorrPet.createCriteria().list {
            owner {
                eq('firstName', 'Ned')
            }
            gt(new DetachedCriteria(SubqueryCorrPet).build {
                projections {
                    countDistinct('owner')
                }
            }, 1L)
        }

        then:
        results.size() == 2
        results*.name.sort() == ["Bart", "Homer"]
        results*.owner.firstName.unique() == ["Ned"]
    }

    void "correlated subquery using explicit outer alias does not match when property differs"() {
        when:
        // In the fixture, no pet's name equals their own owner's firstName:
        // - Ned's pets are "Bart" ("Bart" != "Ned") and "Homer" ("Homer" != "Ned")
        // - Homer's pets are "Santa's Little Helper" and "Bart" (neither == "Homer")
        // The subquery correlates with outer alias 'o' (the outer pet's owner):
        // subqueryPet.owner.id == o.id, projecting subqueryPet.owner.firstName.
        // If correlated correctly, for each outer pet, the subquery returns their own owner's firstName.
        // Outer query checks: pet.name in (owner.firstName), which is false for all pets.
        // If 'o' was incorrectly materialized on the subquery root, 'o' is subqueryPet.owner,
        // so subqueryPet.owner.id == subqueryPet.owner.id is a tautology, and the subquery projects
        // all owners' firstNames: ["Homer", "Ned"].
        // Ned's pet named "Homer" would then incorrectly match!
        def results = SubqueryCorrPet.createCriteria().list {
            createAlias('owner', 'o')
            'in'('name', new DetachedCriteria(SubqueryCorrPet).build {
                eqProperty('owner.id', 'o.id')
                projections {
                    property('owner.firstName')
                }
            })
        }

        then:
        results.isEmpty()
    }

    void "correlated subquery using explicit outer alias correlates with outer row"() {
        when:
        // Query for pets that have a pet named "Santa's Little Helper" under the same owner:
        def results = SubqueryCorrPet.createCriteria().list {
            createAlias('owner', 'o')
            'in'('name', new DetachedCriteria(SubqueryCorrPet).build {
                eqProperty('owner.id', 'o.id')
                eq('name', "Santa's Little Helper")
                projections {
                    property('name')
                }
            })
        }

        then:
        // Only Homer owns "Santa's Little Helper".
        // Homer's pets are "Santa's Little Helper" and "Bart".
        // The outer query checks pet.name in ("Santa's Little Helper") for Homer's pets.
        // For Ned's pets, the subquery returns empty, so no match.
        results.size() == 1
        results[0].name == "Santa's Little Helper"
        results[0].owner.firstName == "Homer"
    }

    void "ordinary association block with nested createAlias joins association on block entity not root"() {
        given:
        def addr = new SubqueryCorrAddress(city: "Springfield").save(failOnError: true)
        def person = new SubqueryCorrPerson(firstName: "Marge", address: addr).save(failOnError: true)
        new SubqueryCorrPet(name: "Snowball", owner: person).save(failOnError: true, flush: true)

        when:
        def results = SubqueryCorrPet.createCriteria().list {
            owner {
                createAlias('address', 'a')
                eq('a.city', 'Springfield')
            }
        }

        then:
        results.size() == 1
        results[0].name == "Snowball"
        results[0].owner.firstName == "Marge"
    }

    void "correlated subquery through a nested association alias preserves the outer join"() {
        given:
        def addr1 = new SubqueryCorrAddress(city: "Springfield").save(failOnError: true)
        def addr2 = new SubqueryCorrAddress(city: "Shelbyville").save(failOnError: true)
        def person1 = new SubqueryCorrPerson(firstName: "Homer", address: addr1).save(failOnError: true)
        def person2 = new SubqueryCorrPerson(firstName: "Ned", address: addr2).save(failOnError: true)

        new SubqueryCorrPet(name: "Santa's Little Helper", owner: person1).save(failOnError: true)
        new SubqueryCorrPet(name: "Homer", owner: person2).save(failOnError: true, flush: true)

        when:
        def results = SubqueryCorrPet.createCriteria().list {
            createAlias('owner', 'o')
            createAlias('owner.address', 'a')
            'in'('name', new DetachedCriteria(SubqueryCorrPet).build {
                eqProperty('owner.id', 'o.id')
                eq('name', "Santa's Little Helper")
                projections {
                    property('name')
                }
            })
            eq('a.city', 'Springfield')
        }

        then:
        results.size() == 1
        results[0].name == "Santa's Little Helper"
        results[0].owner.firstName == "Homer"
    }

    void "sibling association blocks querying the same association name retain independent joins"() {
        given:
        def usa = new SubqueryCorrCountry(name: "USA").save(failOnError: true)
        def canada = new SubqueryCorrCountry(name: "Canada").save(failOnError: true)

        def homeAddr1 = new SubqueryCorrAddress(city: "Springfield", country: usa).save(failOnError: true)
        def workAddr1 = new SubqueryCorrAddress(city: "Vancouver", country: canada).save(failOnError: true)
        def person1 = new SubqueryCorrPerson(firstName: "Homer", homeAddress: homeAddr1, workAddress: workAddr1).save(failOnError: true)

        def homeAddr2 = new SubqueryCorrAddress(city: "Capital City", country: usa).save(failOnError: true)
        def workAddr2 = new SubqueryCorrAddress(city: "Shelbyville", country: usa).save(failOnError: true)
        def person2 = new SubqueryCorrPerson(firstName: "Ned", homeAddress: homeAddr2, workAddress: workAddr2).save(failOnError: true)

        def mexico = new SubqueryCorrCountry(name: "Mexico").save(failOnError: true)
        def homeAddr3 = new SubqueryCorrAddress(city: "Cancun", country: mexico).save(failOnError: true)
        def workAddr3 = new SubqueryCorrAddress(city: "Toronto", country: canada).save(failOnError: true)
        def person3 = new SubqueryCorrPerson(firstName: "Carlos", homeAddress: homeAddr3, workAddress: workAddr3).save(failOnError: true, flush: true)

        when:
        // person1 has homeAddress.country = USA and workAddress.country = Canada
        // person2 has homeAddress.country = USA and workAddress.country = USA
        // If sibling blocks share the 'country' join, both restrictions hit homeAddress.country,
        // which cannot be both USA and Canada simultaneously, returning 0 rows.
        def results = SubqueryCorrPerson.createCriteria().list {
            homeAddress {
                country {
                    eq('name', 'USA')
                }
            }
            workAddress {
                country {
                    eq('name', 'Canada')
                }
            }
        }

        then:
        results.size() == 1
        results[0].firstName == "Homer"
    }

    void "sibling association blocks querying the same association name with dotted property path retain independent joins"() {
        given:
        def usa = new SubqueryCorrCountry(name: "USA").save(failOnError: true)
        def canada = new SubqueryCorrCountry(name: "Canada").save(failOnError: true)

        def homeAddr1 = new SubqueryCorrAddress(city: "Springfield", country: usa).save(failOnError: true)
        def workAddr1 = new SubqueryCorrAddress(city: "Vancouver", country: canada).save(failOnError: true)
        def person1 = new SubqueryCorrPerson(firstName: "Homer", homeAddress: homeAddr1, workAddress: workAddr1).save(failOnError: true)

        def homeAddr2 = new SubqueryCorrAddress(city: "Capital City", country: usa).save(failOnError: true)
        def workAddr2 = new SubqueryCorrAddress(city: "Shelbyville", country: usa).save(failOnError: true)
        def person2 = new SubqueryCorrPerson(firstName: "Ned", homeAddress: homeAddr2, workAddress: workAddr2).save(failOnError: true)

        def mexico = new SubqueryCorrCountry(name: "Mexico").save(failOnError: true)
        def homeAddr3 = new SubqueryCorrAddress(city: "Cancun", country: mexico).save(failOnError: true)
        def workAddr3 = new SubqueryCorrAddress(city: "Toronto", country: canada).save(failOnError: true)
        def person3 = new SubqueryCorrPerson(firstName: "Carlos", homeAddress: homeAddr3, workAddress: workAddr3).save(failOnError: true, flush: true)

        when:
        // person1 has homeAddress.country = USA and workAddress.country = Canada
        // person2 has homeAddress.country = USA and workAddress.country = USA
        // When written as eq('country.name', ...), each block must constrain its own association's country.
        def results = SubqueryCorrPerson.createCriteria().list {
            homeAddress {
                eq('country.name', 'USA')
            }
            workAddress {
                eq('country.name', 'Canada')
            }
        }

        then:
        results.size() == 1
        results[0].firstName == "Homer"
    }

    void "subquery following outer implicit join does not correlate through outer implicit join"() {
        given:
        def usa = new SubqueryCorrCountry(name: "USA").save(failOnError: true)
        def canada = new SubqueryCorrCountry(name: "Canada").save(failOnError: true)

        // Homer has homeAddress in USA, and another person "Bart" has homeAddress in Canada
        def homeAddr1 = new SubqueryCorrAddress(city: "Springfield", country: usa).save(failOnError: true)
        def person1 = new SubqueryCorrPerson(firstName: "Homer", homeAddress: homeAddr1).save(failOnError: true)

        def homeAddr2 = new SubqueryCorrAddress(city: "Vancouver", country: canada).save(failOnError: true)
        def person2 = new SubqueryCorrPerson(firstName: "Bart", homeAddress: homeAddr2).save(failOnError: true)

        // Person3 also has firstName "Homer" but homeAddress in Canada
        def homeAddr3 = new SubqueryCorrAddress(city: "Toronto", country: canada).save(failOnError: true)
        def person3 = new SubqueryCorrPerson(firstName: "HomerCanada", homeAddress: homeAddr3).save(failOnError: true, flush: true)

        when:
        // Outer query: persons whose homeAddress is in USA, whose firstName is in (subquery projecting firstName where homeAddress is in Canada)
        // Subquery should return ["Bart", "HomerCanada"].
        // Wait, if outer query matches persons with firstName in ("Bart", "HomerCanada"), but outer person has homeAddress in USA...
        // None of the outer persons with homeAddress in USA has firstName "Bart" or "HomerCanada".
        // Let's create person4 with firstName "Bart" and homeAddress in USA!
        def homeAddr4 = new SubqueryCorrAddress(city: "Shelbyville", country: usa).save(failOnError: true)
        def person4 = new SubqueryCorrPerson(firstName: "Bart", homeAddress: homeAddr4).save(failOnError: true, flush: true)

        def results = SubqueryCorrPerson.createCriteria().list {
            homeAddress {
                eq('country.name', 'USA')
            }
            'in'('firstName', new DetachedCriteria(SubqueryCorrPerson).build {
                eq('homeAddress.city', 'Vancouver')
                projections {
                    property('firstName')
                }
            })
        }

        then:
        results.size() == 1
        results[0].firstName == "Bart"
    }

    void "comparison through an inherited join in nested association blocks rejects mismatched rows"() {
        given:
        // matchingPerson: firstName = "Springfield", address.city = "Springfield" (matches!)
        def matchingAddr = new SubqueryCorrAddress(city: "Springfield").save(failOnError: true)
        def matchingPerson = new SubqueryCorrPerson(firstName: "Springfield", address: matchingAddr).save(failOnError: true)
        new SubqueryCorrPet(name: "MatchPet", owner: matchingPerson).save(failOnError: true)

        // mismatchPerson: firstName = "Ned", address.city = "Shelbyville" (mismatch: "Shelbyville" != "Ned")
        def mismatchAddr = new SubqueryCorrAddress(city: "Shelbyville").save(failOnError: true)
        def mismatchPerson = new SubqueryCorrPerson(firstName: "Ned", address: mismatchAddr).save(failOnError: true)
        new SubqueryCorrPet(name: "MismatchPet", owner: mismatchPerson).save(failOnError: true, flush: true)

        when:
        // owner { address { eqProperty('city', 'owner.firstName') } }
        // Should only match MatchPet whose owner's firstName equals address.city ("Springfield").
        // If the comparison through the inherited join is dropped, MismatchPet would also be returned.
        def results = SubqueryCorrPet.createCriteria().list {
            owner {
                address {
                    eqProperty('city', 'owner.firstName')
                }
            }
        }

        then:
        results.size() == 1
        results[0].name == "MatchPet"
        results[0].owner.firstName == "Springfield"

        when: "asserting a complete mismatch returns no rows"
        def noResults = SubqueryCorrPet.createCriteria().list {
            owner {
                address {
                    eqProperty('city', 'owner.firstName')
                }
                eq('firstName', 'Ned') // Ned's address city is "Shelbyville" != "Ned"
            }
        }

        then:
        noResults.isEmpty()
    }
}

@Entity
class SubqueryCorrCountry {
    String name

    static constraints = {
        name nullable: false
    }
}

@Entity
class SubqueryCorrAddress {
    String city
    SubqueryCorrCountry country

    static constraints = {
        city nullable: false
        country nullable: true
    }
}

@Entity
class SubqueryCorrPerson {
    String firstName
    SubqueryCorrAddress address
    SubqueryCorrAddress homeAddress
    SubqueryCorrAddress workAddress

    static constraints = {
        firstName nullable: false
        address nullable: true
        homeAddress nullable: true
        workAddress nullable: true
    }
}

@Entity
class SubqueryCorrPet {
    String name
    SubqueryCorrPerson owner

    static constraints = {
        name nullable: false
        owner nullable: false
    }
}
