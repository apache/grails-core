package grails.gorm.hibernate.mapping

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.grails.orm.hibernate.GormSpec
import org.grails.orm.hibernate.cfg.HibernateMappingBuilder
import org.hibernate.boot.Metadata
import org.hibernate.engine.OptimisticLockStyle
import org.hibernate.mapping.PersistentClass

class HibernateOptimisticLockingStyleMappingSpec extends GormDatastoreSpec {

    void testEvaluateHibernateOptimisticLockStyleIsDefined() {
        setup:
        Metadata hibernateMetadata = setupClass.hibernateDatastore.getMetadata()

        when: 'Find out Hibernate PersistentClass representations for our domains'
        PersistentClass forVersioned = hibernateMetadata.getEntityBinding(HibernateOptLockingStyleVersioned.name)
        PersistentClass forNotVersioned = hibernateMetadata.getEntityBinding(HibernateOptLockingStyleNotVersioned.name)

        then:
        forVersioned.optimisticLockStyle == OptimisticLockStyle.VERSION
        forNotVersioned.optimisticLockStyle == OptimisticLockStyle.NONE
    }

    @Override
    List getDomainClasses() {
        [HibernateOptLockingStyleVersioned, HibernateOptLockingStyleNotVersioned]
    }

}


@Entity
class HibernateOptLockingStyleVersioned implements Serializable {
    Long id
    Long version

    String name
}

@Entity
class HibernateOptLockingStyleNotVersioned implements Serializable {
    Long id
    Long version

    String name

    static mapping = {
        version false
    }
}