package org.grails.datastore.gorm.mongo

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec
import grails.mongodb.MongoEntity
import org.bson.Document
import org.bson.types.Decimal128
import spock.lang.Ignore
import spock.lang.IgnoreIf

/**
 * Created by graemerocher on 14/12/16.
 */
class BigDecimalSpec extends GormDatastoreSpec {

    @IgnoreIf( { System.getenv('TRAVIS') } )
    void "test save and retrieve big decimal value"() {
        when:"A big decimal is saved"
        def val = new BigDecimal("1.0")
        new BossMan(salary: val).save(flush:true)
        session.clear()
        BossMan bm = BossMan.first()
        then:""
        bm.salary == val
        BossMan.collection.find().first().salary instanceof Decimal128

    }

    @Override
    List getDomainClasses() {
        [BossMan]
    }
}
@Entity
class BossMan implements MongoEntity<BossMan> {
    BigDecimal salary
}
