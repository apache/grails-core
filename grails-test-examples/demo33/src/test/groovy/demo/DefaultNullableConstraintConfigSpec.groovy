package demo

import grails.testing.gorm.DomainUnitTest
import spock.lang.Ignore
import spock.lang.Specification

@Ignore('Cannot invoke "org.grails.orm.hibernate.HibernateGormEnhancer.registerEntity(org.grails.datastore.mapping.model.PersistentEntity)" because "this.this$0.gormEnhancer" is null')
class DefaultNullableConstraintConfigSpec extends Specification implements DomainUnitTest<Person> {

    Closure doWithConfig() {{ c ->
        c.grails.gorm.default.constraints = {
            '*'(nullable: true)
        }
    }}

    void 'test default nullable'() {
        expect:
        new Person().validate()
    }
}
