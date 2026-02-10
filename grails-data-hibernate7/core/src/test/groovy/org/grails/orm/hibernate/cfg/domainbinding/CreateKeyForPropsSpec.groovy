package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.hibernate.MappingException
import org.hibernate.mapping.Table
import spock.lang.Specification

class CreateKeyForPropsSpec extends Specification {

    def "creates unique key when property is unique within group"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)

        def owner = Mock(PersistentEntity)
        def grailsProp = Mock(GrailsHibernatePersistentProperty) {
            getOwner() >> owner
        }

        def mappedForm = Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            isUnique() >> true
            isUniqueWithinGroup() >> true
            getUniquenessGroup() >> ["p1", "p2"]
        }
        grailsProp.getMappedForm() >> mappedForm

        def otherProp1 = Mock(GrailsHibernatePersistentProperty)
        def otherProp2 = Mock(GrailsHibernatePersistentProperty)
        owner.getPropertyByName("p1") >> otherProp1
        owner.getPropertyByName("p2") >> otherProp2

        String path = "some_path"
        def table = new Table("t")
        String baseColumnName = "base_col"

        columnNameFetcher.getColumnNameForPropertyAndPath(otherProp1, path, null) >> "col1"
        columnNameFetcher.getColumnNameForPropertyAndPath(otherProp2, path, null) >> "col2"

        when:
        subject.createKeyForProps(grailsProp, path, table, baseColumnName)

        then:
        1 * grailsProp.getMappedForm() >> mappedForm
        1 * grailsProp.getOwner() >> owner
        1 * mappedForm.isUnique() >> true
        1 * mappedForm.isUniqueWithinGroup() >> true
        1 * mappedForm.getUniquenessGroup() >> ["p1", "p2"]
        1 * owner.getPropertyByName("p1") >> otherProp1
        1 * owner.getPropertyByName("p2") >> otherProp2
        1 * columnNameFetcher.getColumnNameForPropertyAndPath(otherProp1, path, null)
        1 * columnNameFetcher.getColumnNameForPropertyAndPath(otherProp2, path, null)
        1 * uniqueKeyCreator.createUniqueKeyForColumns(table, _ as List)
    }

    def "does nothing when property is not unique within group"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)

        def owner = Mock(PersistentEntity)
        def grailsProp = Mock(GrailsHibernatePersistentProperty) { getOwner() >> owner }

        def mappedForm = Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            isUnique() >> false
            isUniqueWithinGroup() >> true
            getUniquenessGroup() >> ["p1"]
        }
        grailsProp.getMappedForm() >> mappedForm

        when:
        subject.createKeyForProps(grailsProp, null, new Table("t"), "base")

        then:
        1 * grailsProp.getMappedForm() >> mappedForm
        0 * grailsProp.getOwner() >> owner
        1 * mappedForm.isUnique() >> false
        0 * uniqueKeyCreator._
        0 * columnNameFetcher._
    }

    def "throws when uniqueness group references unknown property"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)

        def owner = Mock(PersistentEntity)
        def grailsProp = Mock(GrailsHibernatePersistentProperty) { getOwner() >> owner }
        owner.getJavaClass() >> CreateKeyForPropsSpec

        def mappedForm = Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            isUnique() >> true
            isUniqueWithinGroup() >> true
            getUniquenessGroup() >> ["missingProp"]
        }
        grailsProp.getMappedForm() >> mappedForm

        owner.getPropertyByName("missingProp") >> null

        when:
        subject.createKeyForProps(grailsProp, null, new Table("t"), "base")

        then:
        thrown(MappingException)
        1 * grailsProp.getMappedForm() >> mappedForm
        1 * grailsProp.getOwner() >> owner
        1 * mappedForm.isUnique() >> true
        1 * mappedForm.isUniqueWithinGroup() >> true
        1 * mappedForm.getUniquenessGroup() >> ["missingProp"]
        1 * owner.getJavaClass() >> CreateKeyForPropsSpec
        1 * owner.getPropertyByName("missingProp")
        0 * uniqueKeyCreator._
        0 * columnNameFetcher._
    }
}
