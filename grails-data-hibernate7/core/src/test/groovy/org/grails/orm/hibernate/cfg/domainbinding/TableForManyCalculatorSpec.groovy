package org.grails.orm.hibernate.cfg.domainbinding


import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.HibernateToManyProperty
import org.grails.orm.hibernate.cfg.JoinTable
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import spock.lang.Specification
import spock.lang.Unroll

class TableForManyCalculatorSpec extends Specification {

    @Unroll
    def "Test calculateTableForMany for #scenario"() {
        given:
        def namingStrategy = Stub(PersistentEntityNamingStrategy)
        def tableNameFetcher = Stub(TableNameFetcher)
        def backticksRemover = Stub(BackticksRemover)
        def shouldBind = Stub(ShouldCollectionBindWithJoinColumn)
        def trimmer = Stub(BackTigsTrimmer)

        def calculator = new TableForManyCalculator(namingStrategy, tableNameFetcher, backticksRemover, shouldBind, trimmer)

        GrailsHibernatePersistentEntity ownerEntity = Stub(GrailsHibernatePersistentEntity)
        GrailsHibernatePersistentEntity associatedEntity = Stub(GrailsHibernatePersistentEntity)
        def config = new PropertyConfig()
        if (hasJoinTable) {
            config.setJoinTable(new JoinTable(name: "explicit_join_table"))
        }

        HibernateToManyProperty property = Mock(mockClass, additionalInterfaces: [HibernateToManyProperty])
        property.getType() >> propertyJavaType
        property.getOwner() >> ownerEntity
        property.getName() >> "myProp"
        property.getAssociatedEntity() >> associatedEntity
        property.isOwningSide() >> isOwningSide
        property.getMappedForm() >> config

        if (property instanceof ManyToMany) {
            ((ManyToMany)property).getInversePropertyName() >> "inverseProp"
        }

        ownerEntity.getMappedForm() >> new Mapping()
        namingStrategy.resolveColumnName("myProp") >> "my_prop_col"
        namingStrategy.resolveColumnName("inverseProp") >> "inverse_prop_col"
        tableNameFetcher.getTableName(ownerEntity) >> "owner_table"
        tableNameFetcher.getTableName(associatedEntity) >> "associated_table"
        shouldBind.apply(_) >> shouldBindWithJoinColumn

        backticksRemover.apply(_) >> { String s -> s }
        trimmer.trimBackTigs(_) >> { String s -> s }

        when:
        def result = calculator.calculateTableForMany(property)

        then:
        result == expectedTableName

        where:
        scenario                      | mockClass        | propertyJavaType | isOwningSide | hasJoinTable | shouldBindWithJoinColumn | expectedTableName
        "a Map property"              | ToMany.class     | Map              | true         | false        | false                    | "owner_table_my_prop_col"
        "a Basic property"            | Basic.class      | List             | true         | false        | false                    | "owner_table_my_prop_col"
        "an owning ManyToMany"        | ManyToMany.class | Set              | true         | false        | false                    | "owner_table_my_prop_col"
        "an inverse ManyToMany"       | ManyToMany.class | Set              | false        | false        | false                    | "associated_table_inverse_prop_col"
        "a ManyToMany with joinTable" | ManyToMany.class | Set              | true         | true         | false                    | "explicit_join_table"
        "a ToMany with joinColumn"    | ToMany.class     | Set              | true         | false        | true                     | "owner_table_associated_table"
        "an owning ToMany"            | ToMany.class     | Set              | true         | false        | false                    | "owner_table_associated_table"
        "an inverse ToMany"           | ToMany.class     | Set              | false        | false        | false                    | "associated_table_owner_table"
    }
}