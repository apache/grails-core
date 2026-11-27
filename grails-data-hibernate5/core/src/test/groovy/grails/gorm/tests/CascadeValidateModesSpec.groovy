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

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Whether an owner validates when its child is invalid, by the mode set on the association's mapping, the kind of
 * association and the state of the child. A new child is changed; a child whose changes are tracked is not.
 */
@Issue('https://github.com/apache/grails-core/issues/14426')
class CascadeValidateModesSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(
            (Settings.SETTING_DB_CREATE): 'create-drop',
            'dataSource.url'             : 'jdbc:h2:mem:cascadeValidateModes;LOCK_TIMEOUT=10000'
    ), CvaDefaultOwner, CvaDefaultPart, CvaDefaultDetail, CvaNoneOwner, CvaNonePart, CvaNoneDetail,
            CvaOwnedOwner, CvaOwnedPart, CvaOwnedDetail, CvaDirtyOwner, CvaDirtyPart, CvaDirtyDetail)

    @Unroll
    void "cascadeValidate '#mode' on #kind with #state invalid child: owner validates is #valid"() {
        when:
        boolean result = ownerType.withNewSession {
            def owner = ownerType.newInstance(name: 'owner')
            def child = kind == 'parts' ? partType.newInstance(label: '') : detailType.newInstance(label: '')
            if (kind == 'parts') {
                owner.addToParts(child)
            } else {
                owner.detail = child
            }
            if (state == 'a tracked') {
                child.trackChanges()
            }
            owner.validate()
        }

        then:
        result == valid

        where:
        mode | kind | state | ownerType | partType | detailType || valid
        'default' | 'parts' | 'a new' | CvaDefaultOwner | CvaDefaultPart | CvaDefaultDetail || false
        'default' | 'parts' | 'a tracked' | CvaDefaultOwner | CvaDefaultPart | CvaDefaultDetail || false
        'default' | 'detail' | 'a new' | CvaDefaultOwner | CvaDefaultPart | CvaDefaultDetail || false
        'default' | 'detail' | 'a tracked' | CvaDefaultOwner | CvaDefaultPart | CvaDefaultDetail || false
        'none' | 'parts' | 'a new' | CvaNoneOwner | CvaNonePart | CvaNoneDetail || true
        'none' | 'parts' | 'a tracked' | CvaNoneOwner | CvaNonePart | CvaNoneDetail || true
        'none' | 'detail' | 'a new' | CvaNoneOwner | CvaNonePart | CvaNoneDetail || true
        'none' | 'detail' | 'a tracked' | CvaNoneOwner | CvaNonePart | CvaNoneDetail || true
        'owned' | 'parts' | 'a new' | CvaOwnedOwner | CvaOwnedPart | CvaOwnedDetail || false
        'owned' | 'parts' | 'a tracked' | CvaOwnedOwner | CvaOwnedPart | CvaOwnedDetail || false
        'owned' | 'detail' | 'a new' | CvaOwnedOwner | CvaOwnedPart | CvaOwnedDetail || false
        'owned' | 'detail' | 'a tracked' | CvaOwnedOwner | CvaOwnedPart | CvaOwnedDetail || false
        'dirty' | 'parts' | 'a new' | CvaDirtyOwner | CvaDirtyPart | CvaDirtyDetail || false
        'dirty' | 'parts' | 'a tracked' | CvaDirtyOwner | CvaDirtyPart | CvaDirtyDetail || true
        'dirty' | 'detail' | 'a new' | CvaDirtyOwner | CvaDirtyPart | CvaDirtyDetail || false
        'dirty' | 'detail' | 'a tracked' | CvaDirtyOwner | CvaDirtyPart | CvaDirtyDetail || true
    }
}

@Entity
class CvaDefaultOwner {
    String name
    static hasOne = [detail: CvaDefaultDetail]
    static hasMany = [parts: CvaDefaultPart]
    static mapping = {
        parts cascadeValidate: 'default'
        detail cascadeValidate: 'default'
    }
}

@Entity
class CvaDefaultPart {
    String label
    static belongsTo = [owner: CvaDefaultOwner]
    static constraints = {
        label blank: false
    }
}

@Entity
class CvaDefaultDetail {
    String label
    static belongsTo = [owner: CvaDefaultOwner]
    static constraints = {
        label blank: false
    }
}

@Entity
class CvaNoneOwner {
    String name
    static hasOne = [detail: CvaNoneDetail]
    static hasMany = [parts: CvaNonePart]
    static mapping = {
        parts cascadeValidate: 'none'
        detail cascadeValidate: 'none'
    }
}

@Entity
class CvaNonePart {
    String label
    static belongsTo = [owner: CvaNoneOwner]
    static constraints = {
        label blank: false
    }
}

@Entity
class CvaNoneDetail {
    String label
    static belongsTo = [owner: CvaNoneOwner]
    static constraints = {
        label blank: false
    }
}

@Entity
class CvaOwnedOwner {
    String name
    static hasOne = [detail: CvaOwnedDetail]
    static hasMany = [parts: CvaOwnedPart]
    static mapping = {
        parts cascadeValidate: 'owned'
        detail cascadeValidate: 'owned'
    }
}

@Entity
class CvaOwnedPart {
    String label
    static belongsTo = [owner: CvaOwnedOwner]
    static constraints = {
        label blank: false
    }
}

@Entity
class CvaOwnedDetail {
    String label
    static belongsTo = [owner: CvaOwnedOwner]
    static constraints = {
        label blank: false
    }
}

@Entity
class CvaDirtyOwner {
    String name
    static hasOne = [detail: CvaDirtyDetail]
    static hasMany = [parts: CvaDirtyPart]
    static mapping = {
        parts cascadeValidate: 'dirty'
        detail cascadeValidate: 'dirty'
    }
}

@Entity
class CvaDirtyPart {
    String label
    static belongsTo = [owner: CvaDirtyOwner]
    static constraints = {
        label blank: false
    }
}

@Entity
class CvaDirtyDetail {
    String label
    static belongsTo = [owner: CvaDirtyOwner]
    static constraints = {
        label blank: false
    }
}
