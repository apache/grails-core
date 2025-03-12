package grails.gorm.tests

import grails.persistence.Entity

class ConstraintsSpec extends GormDatastoreSpec {

    void "Test constraints with static default values"() {
        given: "A Test class with static constraint values"
        def ce = new ConstrainedEntity(num: 1000, str: "ABC")

        when: "saved is called"
        ce.save()

        then:
        !ce.hasErrors()
    }

    @Override
    List getDomainClasses() {
        [ConstrainedEntity]
    }
}

@SuppressWarnings(["ClashingTraitMethods", "UnnecessaryQualifiedReference"])
@Entity
class ConstrainedEntity implements Serializable {

    static final int MAX_VALUE = 1000
    static final List<String> ALLOWABLE_VALUES = ['ABC','DEF','GHI']

    Long id
    Integer num
    String str

    static constraints = {

        num(maxSize: ConstrainedEntity.MAX_VALUE)
        str validator: { val, obj ->
            if (val != null && !ConstrainedEntity.ALLOWABLE_VALUES.contains(val)) {
                return ['not.valid']
            }
        }
    }
}
