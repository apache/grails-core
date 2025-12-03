package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.orm.hibernate.cfg.JoinTable
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import spock.lang.Specification
import spock.lang.Unroll

class TableForManyCalculatorSpec extends Specification {

    @Unroll
    def "Test calculateTableForMany for #scenario"() {
        given:
        // 1. Stub all dependencies
        def namingStrategy = Stub(PersistentEntityNamingStrategy)
        def tableNameFetcher = Stub(TableNameFetcher)
        def backticksRemover = Stub(BackticksRemover)
        def shouldBind = Stub(ShouldCollectionBindWithJoinColumn)
        def trimmer = Stub(BackTigsTrimmer)
        def configConverter = Stub(PersistentPropertyToPropertyConfig)

        // 2. Instantiate the calculator with mocks
        def calculator = new TableForManyCalculator(namingStrategy, tableNameFetcher, backticksRemover, shouldBind, trimmer, configConverter)

        // 3. Set up stubs for the property and entities
        def property = Stub(mockClass)
        def ownerEntity = Stub(PersistentEntity)
        def associatedEntity = Stub(PersistentEntity)
        def config = new PropertyConfig()

        if (hasJoinTable) {
            config.setJoinTable(new JoinTable(name: "explicit_join_table"))
        }

        // 4. Define stub behaviors
        property.getType() >> propertyJavaType
        property.getOwner() >> ownerEntity
        property.getName() >> "myProp"
        property.getAssociatedEntity() >> associatedEntity
        property.isOwningSide() >> isOwningSide
        if (mockClass == ManyToMany) {
            ((ManyToMany)property).getInversePropertyName() >> "inverseProp"
        }

        configConverter.toPropertyConfig(property) >> config
        namingStrategy.resolveColumnName("myProp") >> "my_prop_col"
        namingStrategy.resolveColumnName("inverseProp") >> "inverse_prop_col"
        tableNameFetcher.getTableName(ownerEntity) >> "owner_table"
        tableNameFetcher.getTableName(associatedEntity) >> "associated_table"
        shouldBind.apply(property) >> shouldBindWithJoinColumn

        // Make removers and trimmers pass through values for simplicity
        backticksRemover.apply(_) >> { String s -> s }
        trimmer.trimBackTigs(_) >> { String s -> s }

        when:
        def result = calculator.calculateTableForMany(property, "default")

        then:
        result == expectedTableName

        where:
        scenario                      | mockClass    | propertyJavaType | isOwningSide | hasJoinTable | shouldBindWithJoinColumn | expectedTableName
        "a Map property"              | ToMany       | Map              | true         | false        | false                    | "owner_table_my_prop_col"
        "a Basic property"            | Basic        | List             | true         | false        | false                    | "owner_table_my_prop_col"
        "an owning ManyToMany"        | ManyToMany   | Set              | true         | false        | false                    | "owner_table_my_prop_col"
        "an inverse ManyToMany"       | ManyToMany   | Set              | false        | false        | false                    | "associated_table_inverse_prop_col"
        "a ManyToMany with joinTable" | ManyToMany   | Set              | true         | true         | false                    | "explicit_join_table"
        "a ToMany with joinColumn"    | ToMany       | Set              | true         | false        | true                     | "owner_table_associated_table"
        "an owning ToMany"            | ToMany       | Set              | true         | false        | false                    | "owner_table_associated_table"
        "an inverse ToMany"           | ToMany       | Set              | false        | false        | false                    | "associated_table_owner_table"
    }
}
