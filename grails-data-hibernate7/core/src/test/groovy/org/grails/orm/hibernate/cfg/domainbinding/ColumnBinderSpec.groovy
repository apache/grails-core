package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.hibernate.mapping.Column
import org.hibernate.mapping.Table

import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.IndexBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.NumericColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.StringColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.CreateKeyForProps

class ColumnBinderSpec extends HibernateGormDatastoreSpec {

    def "association ManyToMany without userType uses fetched name and is not nullable"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(
                columnNameFetcher,
                stringBinder,
                numericBinder,
                keyCreator,
                indexBinder
        )

        def entity = createPersistentEntity(CBBook)
        def prop = entity.getPropertyByName("authors")
        def column = new Column()
        def table = new Table()

        // stubs
        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "mtm_fk"

        when:
        binder.bindColumn(prop, null, column, null, null, table)

        then:
        column.getName() == "mtm_fk"
        column.isNullable() == true
        1 * keyCreator.createKeyForProps(prop, null, table, "mtm_fk")
        1 * indexBinder.bindIndex("mtm_fk", column, null, table)
    }

    def "numeric non-association property applies config, numeric constraints, unique and subclass TPH nullable"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(
                columnNameFetcher,
                stringBinder,
                numericBinder,
                keyCreator,
                indexBinder
        )

        def entity = createPersistentEntity(CBNumericSub)
        def prop = entity.getPropertyByName("num")
        def parentProp = Mock(HibernatePersistentProperty)
        def column = new Column("test")
        def table = new Table()
        def cc = new ColumnConfig(comment: "cmt", defaultValue: "def", read: "r", write: "w")

        // stubs
        columnNameFetcher.getColumnNameForPropertyAndPath(prop, "p", cc) >> "num_col"
        parentProp.isNullable() >> true // should make column initially nullable

        when:
        binder.bindColumn(prop, parentProp, column, cc, "p", table)

        then:
        column.getName() == "num_col"
        column.isNullable() == true // due to subclass TPH logic
        column.getComment() == "cmt"
        column.getDefaultValue() == "def"
        column.getCustomRead() == "r"
        column.getCustomWrite() == "w"

        1 * numericBinder.bindNumericColumnConstraints(column, cc, _)
        1 * keyCreator.createKeyForProps(prop, "p", table, "num_col")
        1 * indexBinder.bindIndex("num_col", column, cc, table)
    }

    def "one-to-one inverse non-owning with hasOne keeps existing name and sets nullable=false"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(
                columnNameFetcher,
                stringBinder,
                numericBinder,
                keyCreator,
                indexBinder
        )

        createPersistentEntity(CBOwner)
        def entity = createPersistentEntity(CBPet)
        def prop = entity.getPropertyByName("owner")
        def column = new Column("pre_existing")
        def table = new Table()

        // stubs
        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "fetched_col"

        when:
        binder.bindColumn(prop, null, column, null, null, table)

        then:
        column.getName() == "pre_existing" // should not overwrite existing name
        column.isNullable() == false
        1 * keyCreator.createKeyForProps(prop, null, table, "fetched_col")
        1 * indexBinder.bindIndex("fetched_col", column, null, table)
    }

    def "string property triggers string constraints binder only"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(
                columnNameFetcher,
                stringBinder,
                numericBinder,
                keyCreator,
                indexBinder
        )

        def entity = createPersistentEntity(CBBook)
        def prop = entity.getPropertyByName("title")
        def column = new Column("test")
        def table = new Table()

        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "str_col"

        when:
        binder.bindColumn(prop, null, column, null, null, table)

        then:
        column.getName() == "str_col"
        column.isNullable() == false
        1 * stringBinder.bindStringColumnConstraints(column, _)
        1 * keyCreator.createKeyForProps(prop, null, table, "str_col")
        1 * indexBinder.bindIndex("str_col", column, null, table)
    }

    def "one-to-one inverse non-owning without hasOne sets nullable=true"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(
                columnNameFetcher,
                stringBinder,
                numericBinder,
                keyCreator,
                indexBinder
        )

        createPersistentEntity(CBFace)
        def entity = createPersistentEntity(CBNose)
        def prop = entity.getPropertyByName("face")
        def column = new Column()
        def table = new Table()

        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "one_to_one_fk"

        when:
        binder.bindColumn(prop, null, column, null, null, table)

        then:
        column.getName() == "one_to_one_fk"
        column.isNullable() == true
        1 * keyCreator.createKeyForProps(prop, null, table, "one_to_one_fk")
        1 * indexBinder.bindIndex("one_to_one_fk", column, null, table)
    }

    def "to-one circular association sets nullable=true"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(
                columnNameFetcher,
                stringBinder,
                numericBinder,
                keyCreator,
                indexBinder
        )

        def entity = createPersistentEntity(CBCircular)
        def prop = entity.getPropertyByName("parent")
        def column = new Column()
        def table = new Table()

        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "to_one_fk"

        when:
        binder.bindColumn(prop, null, column, null, null, table)

        then:
        column.getName() == "to_one_fk"
        column.isNullable() == true
        1 * keyCreator.createKeyForProps(prop, null, table, "to_one_fk")
        1 * indexBinder.bindIndex("to_one_fk", column, null, table)
    }

    def "association default nullable falls back to property.isNullable()"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(
                columnNameFetcher,
                stringBinder,
                numericBinder,
                keyCreator,
                indexBinder
        )

        def entity = createPersistentEntity(CBBook)
        def prop = entity.getPropertyByName("authors")
        def column = new Column()
        def table = new Table()

        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "assoc_fk"

        when:
        binder.bindColumn(prop, null, column, null, null, table)

        then:
        column.getName() == "assoc_fk"
        column.isNullable() == true
        1 * keyCreator.createKeyForProps(prop, null, table, "assoc_fk")
        1 * indexBinder.bindIndex("assoc_fk", column, null, table)
    }

    def "non-association nullable computed as property OR parent (prop=true, parent=false)"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(
                columnNameFetcher,
                stringBinder,
                numericBinder,
                keyCreator,
                indexBinder
        )

        def entity = createPersistentEntity(CBNullableEntity)
        def prop = entity.getPropertyByName("nullableProp")
        def parentProp = Mock(HibernatePersistentProperty)
        def column = new Column("test")
        def table = new Table()

        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "na_col"
        parentProp.isNullable() >> false

        when:
        binder.bindColumn(prop, parentProp, column, null, null, table)

        then:
        column.getName() == "na_col"
        column.isNullable() == true
        1 * keyCreator.createKeyForProps(prop, null, table, "na_col")
        1 * indexBinder.bindIndex("na_col", column, null, table)
    }

    def "non-association nullable computed as property OR parent (prop=false, parent=true)"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(
                columnNameFetcher,
                stringBinder,
                numericBinder,
                keyCreator,
                indexBinder
        )

        def entity = createPersistentEntity(CBBook)
        def prop = entity.getPropertyByName("title")
        def parentProp = Mock(HibernatePersistentProperty)
        def column = new Column("test")
        def table = new Table()

        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "na_col2"
        parentProp.isNullable() >> true

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
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(
                columnNameFetcher,
                stringBinder,
                numericBinder,
                keyCreator,
                indexBinder
        )

        def entity = createPersistentEntity(CBBook)
        def prop = entity.getPropertyByName("title")
        def parentProp = Mock(HibernatePersistentProperty)
        def column = new Column("test")
        def table = new Table()

        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "na_col3"
        parentProp.isNullable() >> false

        when:
        binder.bindColumn(prop, parentProp, column, null, null, table)

        then:
        column.getName() == "na_col3"
        column.isNullable() == false
        1 * keyCreator.createKeyForProps(prop, null, table, "na_col3")
        1 * indexBinder.bindIndex("na_col3", column, null, table)
    }

    def "uniqueness handling scenarios"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(
                columnNameFetcher,
                stringBinder,
                numericBinder,
                keyCreator,
                indexBinder
        )

        def entity = createPersistentEntity(CBUniqueEntity)
        def column = new Column("test")
        def table = new Table()

        def propUnique = entity.getPropertyByName("uniqueProp")
        columnNameFetcher.getColumnNameForPropertyAndPath(propUnique, null, null) >> "u_col"

        def propNotUnique = entity.getPropertyByName("notUniqueProp")
        columnNameFetcher.getColumnNameForPropertyAndPath(propNotUnique, null, null) >> "nu_col"

        when:
        binder.bindColumn(propUnique, null, column, null, null, table)

        then:
        column.isUnique()

        when:
        def column2 = new Column("test2")
        binder.bindColumn(propNotUnique, null, column2, null, null, table)

        then:
        !column2.isUnique()
    }

    def "owner not root with tablePerHierarchy=false sets nullable to property.isNullable()"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def stringBinder = Mock(StringColumnConstraintsBinder)
        def numericBinder = Mock(NumericColumnConstraintsBinder)
        def keyCreator = Mock(CreateKeyForProps)
        def indexBinder = Mock(IndexBinder)

        def binder = new ColumnBinder(
                columnNameFetcher,
                stringBinder,
                numericBinder,
                keyCreator,
                indexBinder
        )

        def entity = createPersistentEntity(CBSubNonTph)
        def prop = entity.getPropertyByName("subProp")
        def column = new Column("test")
        def table = new Table()

        columnNameFetcher.getColumnNameForPropertyAndPath(prop, null, null) >> "sub_col"

        when:
        binder.bindColumn(prop, null, column, null, null, table)

        then:
        column.getName() == "sub_col"
        !column.isNullable()
        1 * keyCreator.createKeyForProps(prop, null, table, "sub_col")
        1 * indexBinder.bindIndex("sub_col", column, null, table)
    }
}

@Entity
class CBBook {
    String title
    static hasMany = [authors: CBAuthor]
    static mapping = {
        authors joinTable: [name: "cb_book_authors", key: "book_id", column: "author_id"]
    }
    static constraints = {
        title nullable: false
        authors nullable: true
    }
}

@Entity
class CBAuthor {
    String name
    static constraints = {
        name nullable: false
    }
}

@Entity
class CBNumericBase {
}

@Entity
class CBNumericSub extends CBNumericBase {
    Integer num
    static constraints = {
        num nullable: false
    }
}

@Entity
class CBOwner {
    static hasOne = [pet: CBPet]
}

@Entity
class CBPet {
    String name
    CBOwner owner
}

@Entity
class CBFace {
    CBNose nose
}

@Entity
class CBNose {
    CBFace face
}

@Entity
class CBCircular {
    CBCircular parent
}

@Entity
class CBNullableEntity {
    String nullableProp
    static constraints = {
        nullableProp nullable: true
    }
}

@Entity
class CBUniqueEntity {
    String uniqueProp
    String notUniqueProp
    static mapping = {
        uniqueProp unique: true
        notUniqueProp unique: false
    }
}

@Entity
class CBBaseNonTph {
    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
class CBSubNonTph extends CBBaseNonTph {
    String subProp
    static constraints = {
        subProp nullable: false
    }
}