package grails.gorm.tests.inheritance

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec

class SubclassToOneProxySpec extends GormDatastoreSpec {

    void "the hasOne is a proxy and unwraps"() {
        given:
        SubclassProxy dog = new SubclassProxy().save()
        new HasOneProxy(superclassProxy: dog).save()
        session.flush()
        session.clear()
        HasOneProxy owner = HasOneProxy.first()

        expect:
        session.mappingContext.proxyFactory.isProxy(owner.@superclassProxy)
    }

    @Override
    List getDomainClasses() {
        [SuperclassProxy, SubclassProxy, HasOneProxy]
    }
}

@Entity
class SuperclassProxy {
}

@Entity
class SubclassProxy extends SuperclassProxy {
}

@Entity
class HasOneProxy {
    SuperclassProxy superclassProxy
}
