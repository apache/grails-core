package grails.gorm.tests

import grails.gorm.annotation.Entity


class JoinCriteriaSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [AclClass, AclObjectIdentity]
    }

    def "check if a criteria join get the expected results"() {
        given:
        def aclc1 = new AclClass(className:'classname1').save(flush:true)
        def aclc2 = new AclClass(className:'classname2').save(flush:true)
        
        def aclObjId1 = new AclObjectIdentity('aclClass':aclc1, objectId:1L).save(flush:true)
        def aclObjId2 = new AclObjectIdentity('aclClass':aclc2, objectId:2L).save(flush:true)
        
        session.clear()

        when:
        def theObjs = AclObjectIdentity.createCriteria().list {
            aclClass { eq('className', 'classname2') }
        }

        then:
        theObjs.size() == 1
        theObjs[0].objectId == 2L
    }
}

@Entity
class AclClass {
    Long id
    Long version
    String className

    @Override
    String toString() {
        "AclClass id $id, className $className"
    }
}

@Entity
class AclObjectIdentity {

    Long id
    Long version

    Long objectId
    AclClass aclClass
    
    static embedded = ['aclClass']

    @Override
    String toString() {
        "AclObjectIdentity id $id, aclClass $aclClass.className, objectId $objectId"
    }
}


