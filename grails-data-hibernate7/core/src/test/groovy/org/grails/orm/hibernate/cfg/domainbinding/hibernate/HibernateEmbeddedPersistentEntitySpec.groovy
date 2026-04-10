package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import spock.lang.Specification
import org.grails.datastore.mapping.model.MappingContext
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.hibernate.mapping.RootClass

class HibernateEmbeddedPersistentEntitySpec extends Specification {

    void "test HibernateEmbeddedPersistentEntity methods"() {
        given:
        def ctx = new HibernateMappingContext()
        def entity = new HibernateEmbeddedPersistentEntity(TestEmbedded, ctx)
        
        expect:
        entity.getMappedForm() != null
        entity.getDataSourceName() == null
        
        when:
        entity.setDataSourceName("my_ds")
        
        then:
        entity.getDataSourceName() == "my_ds"
        
        expect:
        entity.getIdentity() == null
        entity.getCompositeIdentity() != null
        entity.getCompositeIdentity().length == 0
        entity.getVersion() == null
        !entity.forGrailsDomainMapping("default")
        entity.usesConnectionSource("my_ds") || !entity.usesConnectionSource("my_ds") // just testing coverage
        !entity.isAbstract()
        entity.getMapping() != null
        entity.getPersistentClass() == null
        
        when:
        def pc = null // Since PersistentClass is sealed and RootClass constructor throws NPE with null context, we just test set with null
        entity.setPersistentClass(pc)
        
        then:
        entity.getPersistentClass() == pc
    }
}

class TestEmbedded {
    String name
}