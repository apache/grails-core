package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.mapping.Column
import org.hibernate.mapping.Table
import spock.lang.Specification

class ColumnBinderSpec extends Specification {

    def "association ManyToMany without userType uses fetched name and is not nullable"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def propToConfig = Mock(PersistentPropertyToPropertyConfig)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def hibernateWrapper = Mock(HibernateEntityWrapper)
        def userTypeFetcher = Mock(UserTypeFetcher)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(

                columnNameFetcher,
                propToConfig,
                stringBinder,
                numericBinder,
                keyCreator,
                hibernateWrapper,
                userTypeFetcher,
                indexBinder
        )

        def prop = Mock(ManyToMany)
        def owner = Mock(PersistentEntity)
        def mappedForm = Mock(PropertyConfig)
        def column = new Column()
        def table = new Table()

        // stubs
        userTypeFetcher.getUserType(prop) >> null
        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "mtm_fk"
        prop.isNullable() >> false
        prop.getOwner() >> owner
        owner.isRoot() >> true // skip subclass nullable logic
        propToConfig.apply(prop) >> mappedForm
        mappedForm.isUnique() >> false
        mappedForm.isUniqueWithinGroup() >> false

        when:
        binder.bindColumn(prop, null, column, null, null, table)

        then:
        column.getName() == "mtm_fk"
        column.isNullable() == false
        0 * stringBinder._
        0 * numericBinder._
        1 * keyCreator.createKeyForProps(prop, null, table, "mtm_fk")
        1 * indexBinder.bindIndex("mtm_fk", column, null, table)
    }

    def "numeric non-association property applies config, numeric constraints, unique and subclass TPH nullable"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def propToConfig = Mock(PersistentPropertyToPropertyConfig)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def hibernateWrapper = Mock(HibernateEntityWrapper)
        def userTypeFetcher = Mock(UserTypeFetcher)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(

                columnNameFetcher,
                propToConfig,
                stringBinder,
                numericBinder,
                keyCreator,
                hibernateWrapper,
                userTypeFetcher,
                indexBinder
        )

        def prop = Mock(PersistentProperty)
        def parentProp = Mock(PersistentProperty)
        def owner = Mock(PersistentEntity)
        def mapping = Mock(Mapping)
        def propertyConfig = Mock(PropertyConfig)
        def column = new Column()
        def table = new Table()
        def cc = new ColumnConfig(comment: "cmt", defaultValue: "def", read: "r", write: "w")

        // stubs
        userTypeFetcher.getUserType(prop) >> null
        columnNameFetcher.getColumnNameForPropertyAndPath(prop, "p", cc) >> "num_col"
        prop.getType() >> Integer
        prop.isNullable() >> false
        parentProp.isNullable() >> true // should make column initially nullable
        prop.getOwner() >> owner
        owner.isRoot() >> false
        hibernateWrapper.getMappedForm(owner) >> mapping
        mapping.getTablePerHierarchy() >> true // forces nullable true for subclass
        propToConfig.apply(prop) >>> [propertyConfig, propertyConfig] // called twice in code
        // numeric constraints applied
        // unique settings
        propertyConfig.isUnique() >> true
        propertyConfig.isUniqueWithinGroup() >> false

        when:
        binder.bindColumn(prop, parentProp, column, cc, "p", table)

        then:
        column.getName() == "num_col"
        column.isNullable() == true // due to subclass TPH logic
        column.getComment() == "cmt"
        column.getDefaultValue() == "def"
        column.getCustomRead() == "r"
        column.getCustomWrite() == "w"

        1 * numericBinder.bindNumericColumnConstraints(column, cc, propertyConfig)
        0 * stringBinder._
        1 * keyCreator.createKeyForProps(prop, "p", table, "num_col")
        1 * indexBinder.bindIndex("num_col", column, cc, table)
    }

    def "one-to-one inverse non-owning with hasOne keeps existing name and sets nullable=false"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def propToConfig = Mock(PersistentPropertyToPropertyConfig)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def hibernateWrapper = Mock(HibernateEntityWrapper)
        def userTypeFetcher = Mock(UserTypeFetcher)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(

                columnNameFetcher,
                propToConfig,
                stringBinder,
                numericBinder,
                keyCreator,
                hibernateWrapper,
                userTypeFetcher,
                indexBinder
        )

        def prop = Mock(org.grails.datastore.mapping.model.types.OneToOne)
        def inverse = Mock(org.grails.datastore.mapping.model.types.Association)
        def owner = Mock(PersistentEntity)
        def mappedForm = Mock(PropertyConfig)
        def column = new Column("pre_existing")
        def table = new Table()

        // stubs
        userTypeFetcher.getUserType(prop) >> null
        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "fetched_col"
        prop.isNullable() >> true
        prop.isBidirectional() >> true
        prop.isOwningSide() >> false
        prop.getInverseSide() >> inverse
        inverse.isHasOne() >> true
        prop.getOwner() >> owner
        owner.isRoot() >> true
        propToConfig.apply(prop) >> mappedForm
        mappedForm.isUnique() >> false
        mappedForm.isUniqueWithinGroup() >> false

        when:
        binder.bindColumn(prop, null, column, null, null, table)

        then:
        column.getName() == "pre_existing" // should not overwrite existing name
        column.isNullable() == false
        1 * keyCreator.createKeyForProps(prop, null, table, "fetched_col")
        1 * indexBinder.bindIndex("fetched_col", column, null, table)
        0 * stringBinder._
        0 * numericBinder._
    }

    def "string property triggers string constraints binder only"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def propToConfig = Mock(PersistentPropertyToPropertyConfig)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def hibernateWrapper = Mock(HibernateEntityWrapper)
        def userTypeFetcher = Mock(UserTypeFetcher)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(

                columnNameFetcher,
                propToConfig,
                stringBinder,
                numericBinder,
                keyCreator,
                hibernateWrapper,
                userTypeFetcher,
                indexBinder
        )

        def prop = Mock(PersistentProperty)
        def owner = Mock(PersistentEntity)
        def propertyConfig = Mock(PropertyConfig)
        def column = new Column()
        def table = new Table()

        userTypeFetcher.getUserType(prop) >> null
        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "str_col"
        prop.getType() >> String
        prop.isNullable() >> true
        prop.getOwner() >> owner
        owner.isRoot() >> true
        propToConfig.apply(prop) >>> [propertyConfig, propertyConfig]
        propertyConfig.isUnique() >> false
        propertyConfig.isUniqueWithinGroup() >> false

        when:
        binder.bindColumn(prop, null, column, null, null, table)

        then:
        column.getName() == "str_col"
        column.isNullable()
        1 * stringBinder.bindStringColumnConstraints(column, propertyConfig)
        0 * numericBinder._
        1 * keyCreator.createKeyForProps(prop, null, table, "str_col")
        1 * indexBinder.bindIndex("str_col", column, null, table)
    }

    def "one-to-one inverse non-owning without hasOne sets nullable=true"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def propToConfig = Mock(PersistentPropertyToPropertyConfig)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def hibernateWrapper = Mock(HibernateEntityWrapper)
        def userTypeFetcher = Mock(UserTypeFetcher)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(

                columnNameFetcher,
                propToConfig,
                stringBinder,
                numericBinder,
                keyCreator,
                hibernateWrapper,
                userTypeFetcher,
                indexBinder
        )

        def prop = Mock(org.grails.datastore.mapping.model.types.OneToOne)
        def inverse = Mock(org.grails.datastore.mapping.model.types.Association)
        def owner = Mock(PersistentEntity)
        def mappedForm = Mock(PropertyConfig)
        def column = new Column() // name is null so binder should set it
        def table = new Table()

        userTypeFetcher.getUserType(prop) >> null
        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "one_to_one_fk"
        prop.isNullable() >> false // but branch should override to true due to !hasOne
        prop.isBidirectional() >> true
        prop.isOwningSide() >> false
        prop.getInverseSide() >> inverse
        inverse.isHasOne() >> false
        prop.getOwner() >> owner
        owner.isRoot() >> true
        propToConfig.apply(prop) >> mappedForm
        mappedForm.isUnique() >> false
        mappedForm.isUniqueWithinGroup() >> false

        when:
        binder.bindColumn(prop, null, column, null, null, table)

        then:
        column.getName() == "one_to_one_fk"
        column.isNullable() == true
        1 * keyCreator.createKeyForProps(prop, null, table, "one_to_one_fk")
        1 * indexBinder.bindIndex("one_to_one_fk", column, null, table)
        0 * stringBinder._
        0 * numericBinder._
    }

    def "to-one circular association sets nullable=true"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def propToConfig = Mock(PersistentPropertyToPropertyConfig)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def hibernateWrapper = Mock(HibernateEntityWrapper)
        def userTypeFetcher = Mock(UserTypeFetcher)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(

                columnNameFetcher,
                propToConfig,
                stringBinder,
                numericBinder,
                keyCreator,
                hibernateWrapper,
                userTypeFetcher,
                indexBinder
        )

        def prop = Mock(org.grails.datastore.mapping.model.types.ToOne)
        def owner = Mock(PersistentEntity)
        def mappedForm = Mock(PropertyConfig)
        def column = new Column()
        def table = new Table()

        userTypeFetcher.getUserType(prop) >> null
        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "to_one_fk"
        prop.isCircular() >> true
        prop.isNullable() >> false
        prop.getOwner() >> owner
        owner.isRoot() >> true
        propToConfig.apply(prop) >> mappedForm
        mappedForm.isUnique() >> false
        mappedForm.isUniqueWithinGroup() >> false

        when:
        binder.bindColumn(prop, null, column, null, null, table)

        then:
        column.getName() == "to_one_fk"
        column.isNullable() == true
        1 * keyCreator.createKeyForProps(prop, null, table, "to_one_fk")
        1 * indexBinder.bindIndex("to_one_fk", column, null, table)
        0 * stringBinder._
        0 * numericBinder._
    }

    def "association default nullable falls back to property.isNullable()"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def propToConfig = Mock(PersistentPropertyToPropertyConfig)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def hibernateWrapper = Mock(HibernateEntityWrapper)
        def userTypeFetcher = Mock(UserTypeFetcher)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(

                columnNameFetcher,
                propToConfig,
                stringBinder,
                numericBinder,
                keyCreator,
                hibernateWrapper,
                userTypeFetcher,
                indexBinder
        )

        def prop = Mock(org.grails.datastore.mapping.model.types.Association)
        def owner = Mock(PersistentEntity)
        def mappedForm = Mock(PropertyConfig)
        def column = new Column()
        def table = new Table()

        userTypeFetcher.getUserType(prop) >> null
        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "assoc_fk"
        prop.isNullable() >> true
        prop.getOwner() >> owner
        owner.isRoot() >> true
        // ensure we don't hit special branches
        // Spock will not have methods isBidirectional, isOwningSide on Association base; not needed since code checks only if OneToOne or ToOne/circular
        propToConfig.apply(prop) >> mappedForm
        mappedForm.isUnique() >> false
        mappedForm.isUniqueWithinGroup() >> false

        when:
        binder.bindColumn(prop, null, column, null, null, table)

        then:
        column.getName() == "assoc_fk"
        column.isNullable() == true
        1 * keyCreator.createKeyForProps(prop, null, table, "assoc_fk")
        1 * indexBinder.bindIndex("assoc_fk", column, null, table)
        0 * stringBinder._
        0 * numericBinder._
    }

    def "non-association nullable computed as property OR parent (prop=true, parent=false)"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def propToConfig = Mock(PersistentPropertyToPropertyConfig)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def hibernateWrapper = Mock(HibernateEntityWrapper)
        def userTypeFetcher = Mock(UserTypeFetcher)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(

                columnNameFetcher,
                propToConfig,
                stringBinder,
                numericBinder,
                keyCreator,
                hibernateWrapper,
                userTypeFetcher,
                indexBinder
        )

        def prop = Mock(PersistentProperty)
        def parentProp = Mock(PersistentProperty)
        def owner = Mock(PersistentEntity)
        def propertyConfig = Mock(PropertyConfig)
        def column = new Column()
        def table = new Table()

        userTypeFetcher.getUserType(prop) >> null
        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "na_col"
        prop.isNullable() >> true
        parentProp.isNullable() >> false
        prop.getOwner() >> owner
        owner.isRoot() >> true
        propToConfig.apply(prop) >>> [propertyConfig, propertyConfig]
        propertyConfig.isUnique() >> false
        propertyConfig.isUniqueWithinGroup() >> false

        when:
        binder.bindColumn(prop, parentProp, column, null, null, table)

        then:
        column.getName() == "na_col"
        column.isNullable() == true
        0 * stringBinder._
        0 * numericBinder._
        1 * keyCreator.createKeyForProps(prop, null, table, "na_col")
        1 * indexBinder.bindIndex("na_col", column, null, table)
    }

    def "non-association nullable computed as property OR parent (prop=false, parent=true)"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def propToConfig = Mock(PersistentPropertyToPropertyConfig)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def hibernateWrapper = Mock(HibernateEntityWrapper)
        def userTypeFetcher = Mock(UserTypeFetcher)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(

                columnNameFetcher,
                propToConfig,
                stringBinder,
                numericBinder,
                keyCreator,
                hibernateWrapper,
                userTypeFetcher,
                indexBinder
        )

        def prop = Mock(PersistentProperty)
        def parentProp = Mock(PersistentProperty)
        def owner = Mock(PersistentEntity)
        def propertyConfig = Mock(PropertyConfig)
        def column = new Column()
        def table = new Table()

        userTypeFetcher.getUserType(prop) >> null
        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "na_col2"
        prop.isNullable() >> false
        parentProp.isNullable() >> true
        prop.getOwner() >> owner
        owner.isRoot() >> true
        propToConfig.apply(prop) >>> [propertyConfig, propertyConfig]
        propertyConfig.isUnique() >> false
        propertyConfig.isUniqueWithinGroup() >> false

        when:
        binder.bindColumn(prop, parentProp, column, null, null, table)

        then:
        column.getName() == "na_col2"
        column.isNullable() == true
        1 * keyCreator.createKeyForProps(prop, null, table, "na_col2")
        1 * indexBinder.bindIndex("na_col2", column, null, table)
    }

    def "non-association nullable computed as property OR parent (both false)"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def propToConfig = Mock(PersistentPropertyToPropertyConfig)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def hibernateWrapper = Mock(HibernateEntityWrapper)
        def userTypeFetcher = Mock(UserTypeFetcher)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(

                columnNameFetcher,
                propToConfig,
                stringBinder,
                numericBinder,
                keyCreator,
                hibernateWrapper,
                userTypeFetcher,
                indexBinder
        )

        def prop = Mock(PersistentProperty)
        def parentProp = Mock(PersistentProperty)
        def owner = Mock(PersistentEntity)
        def propertyConfig = Mock(PropertyConfig)
        def column = new Column()
        def table = new Table()

        userTypeFetcher.getUserType(prop) >> null
        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "na_col3"
        prop.isNullable() >> false
        parentProp.isNullable() >> false
        prop.getOwner() >> owner
        owner.isRoot() >> true
        propToConfig.apply(prop) >>> [propertyConfig, propertyConfig]
        propertyConfig.isUnique() >> false
        propertyConfig.isUniqueWithinGroup() >> false

        when:
        binder.bindColumn(prop, parentProp, column, null, null, table)

        then:
        column.getName() == "na_col3"
        column.isNullable() == false
        1 * keyCreator.createKeyForProps(prop, null, table, "na_col3")
        1 * indexBinder.bindIndex("na_col3", column, null, table)
    }

    def "uniqueness handling scenarios 1"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def propToConfig = Mock(PersistentPropertyToPropertyConfig)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def hibernateWrapper = Mock(HibernateEntityWrapper)
        def userTypeFetcher = Mock(UserTypeFetcher)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(

                columnNameFetcher,
                propToConfig,
                stringBinder,
                numericBinder,
                keyCreator,
                hibernateWrapper,
                userTypeFetcher,
                indexBinder
        )

        def prop = Mock(PersistentProperty)
        def owner = Mock(PersistentEntity)
        def column = new Column()
        def table = new Table()

        userTypeFetcher.getUserType(prop) >> null
        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "u_col"
        prop.getType() >> Object
        prop.isNullable() >> true
        prop.getOwner() >> owner
        owner.isRoot() >> true

        when:
        // Unique true, withinGroup false => unique true
        def pc1 = Mock(PropertyConfig)
        propToConfig.apply(prop) >>> [pc1, pc1]
        pc1.isUnique() >> true
        pc1.isUniqueWithinGroup() >> false
        binder.bindColumn(prop, null, column, null, null, table)

        then:
        column.isUnique()

    }

    def "uniqueness handling scenarios 2"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def propToConfig = Mock(PersistentPropertyToPropertyConfig)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def hibernateWrapper = Mock(HibernateEntityWrapper)
        def userTypeFetcher = Mock(UserTypeFetcher)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(

                columnNameFetcher,
                propToConfig,
                stringBinder,
                numericBinder,
                keyCreator,
                hibernateWrapper,
                userTypeFetcher,
                indexBinder
        )

        def prop = Mock(PersistentProperty)
        def owner = Mock(PersistentEntity)
        def column = new Column()
        def table = new Table()

        userTypeFetcher.getUserType(prop) >> null
        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "u_col"
        prop.getType() >> Object
        prop.isNullable() >> true
        prop.getOwner() >> owner
        owner.isRoot() >> true


        when:
        // Unique true, withinGroup true => unique false
        def column2 = new Column()
        def pc2 = Mock(PropertyConfig)
        propToConfig.apply(prop) >> pc2
        pc2.isUnique() >> true
        pc2.isUniqueWithinGroup() >> true
        binder.bindColumn(prop, null, column2, null, null, table)

        then:
        !column2.isUnique()

    }

    def "uniqueness handling scenarios 3"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def propToConfig = Mock(PersistentPropertyToPropertyConfig)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def hibernateWrapper = Mock(HibernateEntityWrapper)
        def userTypeFetcher = Mock(UserTypeFetcher)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(

                columnNameFetcher,
                propToConfig,
                stringBinder,
                numericBinder,
                keyCreator,
                hibernateWrapper,
                userTypeFetcher,
                indexBinder
        )

        def prop = Mock(PersistentProperty)
        def owner = Mock(PersistentEntity)
        def column = new Column()
        def table = new Table()

        userTypeFetcher.getUserType(prop) >> null
        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "u_col"
        prop.getType() >> Object
        prop.isNullable() >> true
        prop.getOwner() >> owner
        owner.isRoot() >> true


        when:
        // Unique false => unique false
        def column3 = new Column()
        def pc3 = Mock(PropertyConfig)
        propToConfig.apply(prop) >>> [pc3, pc3]
        pc3.isUnique() >> false
        pc3.isUniqueWithinGroup() >> false
        binder.bindColumn(prop, null, column3, null, null, table)

        then:
        !column3.isUnique()
    }

    def "owner not root with tablePerHierarchy=false sets nullable to property.isNullable()"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def propToConfig = Mock(PersistentPropertyToPropertyConfig)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def hibernateWrapper = Mock(HibernateEntityWrapper)
        def userTypeFetcher = Mock(UserTypeFetcher)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(

                columnNameFetcher,
                propToConfig,
                stringBinder,
                numericBinder,
                keyCreator,
                hibernateWrapper,
                userTypeFetcher,
                indexBinder
        )

        def prop = Mock(PersistentProperty)
        def owner = Mock(PersistentEntity)
        def mapping = Mock(Mapping)
        def propertyConfig = Mock(PropertyConfig)
        def column = new Column()
        def table = new Table()

        userTypeFetcher.getUserType(prop) >> null
        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "sub_col"
        prop.getType() >> Object
        prop.isNullable() >> false
        prop.getOwner() >> owner
        owner.isRoot() >> false
        hibernateWrapper.getMappedForm(owner) >> mapping
        mapping.getTablePerHierarchy() >> false
        propToConfig.apply(prop) >>> [propertyConfig, propertyConfig]
        propertyConfig.isUnique() >> false
        propertyConfig.isUniqueWithinGroup() >> false

        when:
        binder.bindColumn(prop, null, column, null, null, table)

        then:
        column.getName() == "sub_col"
        !column.isNullable()
        1 * keyCreator.createKeyForProps(prop, null, table, "sub_col")
        1 * indexBinder.bindIndex("sub_col", column, null, table)
    }
}
