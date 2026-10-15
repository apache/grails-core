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

package org.grails.orm.hibernate.cfg.domainbinding.binder

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.mapping.MappingBuilder
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.PendingFeature
import spock.lang.Shared
import spock.lang.Specification

/**
 * An inverse {@code hasMany} on an entity with a composite identifier must load its elements whatever
 * order the identifier is declared in. The collection key copies the columns of the to-one side, which
 * are already in key order, and then sorts them by the owner identifier's permutation a second time,
 * so the collection is loaded with the identifier values bound to the wrong columns unless the
 * identifier is declared in name order already.
 */
class InverseHasManyCompositeIdentifierOrderSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(
            [
                    'dataSource.url'        : 'jdbc:h2:mem:inverseHasManyCompositeIdOrder;LOCK_TIMEOUT=10000',
                    'dataSource.dbCreate'   : 'create-drop',
                    'dataSource.dialect'    : H2Dialect.name,
                    'hibernate.hbm2ddl.auto': 'create',
            ],
            IhmSortedParent, IhmSortedKid, IhmParent, IhmKid)

    void "an inverse hasMany on a parent whose composite identifier is declared in name order loads its kids"() {
        when:
        IhmSortedParent.withNewTransaction {
            new IhmSortedParent(alpha: 'a', zeta: 'z').addToKids(new IhmSortedKid(name: 'kid')).save(failOnError: true, flush: true)
        }

        then:
        IhmSortedParent.withNewSession {
            IhmSortedParent.findByAlphaAndZeta('a', 'z').kids*.name == ['kid']
        }
    }

    @PendingFeature(reason = 'the collection key re-sorts the copied to-one columns by the identifier permutation, see the follow-up issue')
    void "an inverse hasMany on a parent whose composite identifier is declared out of name order loads its kids"() {
        when:
        IhmParent.withNewTransaction {
            new IhmParent(zeta: 'z', alpha: 'a').addToKids(new IhmKid(name: 'kid')).save(failOnError: true, flush: true)
        }

        then:
        IhmParent.withNewSession {
            IhmParent.findByZetaAndAlpha('z', 'a').kids*.name == ['kid']
        }
    }
}

@Entity
class IhmParent implements Serializable {
    String zeta
    String alpha
    static hasMany = [kids: IhmKid]

    static mapping = MappingBuilder.define {
        composite('zeta', 'alpha')
    }
}

@Entity
class IhmKid {
    String name
    static belongsTo = [parent: IhmParent]
}

@Entity
class IhmSortedParent implements Serializable {
    String alpha
    String zeta
    static hasMany = [kids: IhmSortedKid]

    static mapping = MappingBuilder.define {
        composite('alpha', 'zeta')
    }
}

@Entity
class IhmSortedKid {
    String name
    static belongsTo = [parent: IhmSortedParent]
}
