package grails.gorm.tests

import grails.gorm.annotation.Entity
import spock.lang.Ignore

/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author graemerocher
 */
class NativeIdentityGeneratorSpec extends GormDatastoreSpec {

//    @Ignore // currently not working, CREATE returns no results
    void "Test native id generator save and query"() {
        when:"An entity with a native id is persisted"
        def c1 = new Competition(name:"FA Cup")
        def c2 = new Competition(name:"League Cup")
        c1.save(flush:true)
        c2.save(flush:true)
        session.clear()

        then:"The id is generated from the native datastore"
        c1.id == 0L
        c2.id == 1L
        Competition.get(c1.id).id == 0L
        Competition.get(c2.id).id == 1L
    }

    void "Test native id generator save multiple"() {
        when:"An entity with a native id is persisted"
        def c1 = new Competition(name:"FA Cup")
        def c2 = new Competition(name:"League Cup")
        def results = Competition.saveAll(c1, c2)
        session.flush()
        session.clear()

        then:"The id is generated from the native datastore"
        c1.id != null
        c2.id != null
        results == [c1.id, c2.id]
        Competition.get(c1.id).id == c1.id
        Competition.get(c2.id).id == c2.id
    }

    @Override
    List getDomainClasses() {
        [Competition]
    }
}

@Entity
class Competition {
    String name
    static hasMany = [clubs:Club]

    static mapping = {
        id generator:'native'
    }
}
