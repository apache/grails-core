/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.lang.reflect.Field

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.unbootable.EmbeddedCollectionHolder
import grails.unbootable.EmbeddedCollectionOwnerA
import grails.unbootable.EmbeddedCollectionOwnerB
import grails.unbootable.EmbeddedCollectionOwnerTwice
import grails.unbootable.NoOwnerLeft
import grails.unbootable.NoOwnerRight
import jakarta.persistence.AssociationOverride
import jakarta.persistence.AssociationOverrides
import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.DiscriminatorType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Convert
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.JoinTable
import jakarta.persistence.MapKeyColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.PrimaryKeyJoinColumn
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.Size
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.Comment
import org.hibernate.annotations.DiscriminatorFormula
import org.hibernate.annotations.DiscriminatorOptions
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.OptimisticLockType
import org.hibernate.annotations.OptimisticLocking
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import org.hibernate.annotations.Formula
import org.hibernate.annotations.IdGeneratorType
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.NumericBooleanConverter
import org.hibernate.type.TrueFalseConverter
import org.hibernate.type.YesNoConverter
import org.hibernate.annotations.NotFound
import org.hibernate.annotations.NotFoundAction
import org.hibernate.annotations.Type
import org.hibernate.annotations.UuidGenerator
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.generator.Assigned
import org.hibernate.generator.Generator
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.id.enhanced.SequenceStyleGenerator
import org.hibernate.id.enhanced.TableGenerator
import org.hibernate.id.uuid.UuidGenerator as HibernateUuidGenerator
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.JoinedSubclass
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.SingleTableSubclass
import org.hibernate.mapping.UnionSubclass
import org.hibernate.mapping.GeneratorCreator
import org.hibernate.boot.registry.BootstrapServiceRegistry
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.boot.registry.StandardServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.dialect.H2Dialect
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Component
import org.hibernate.mapping.IndexedCollection
import org.hibernate.mapping.Property
import org.hibernate.type.CustomType
import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.usertype.ParameterizedType
import org.hibernate.usertype.UserType
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types

import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.grails.orm.hibernate.cfg.IdentityEnumType
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateBasicProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateCustomProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyEntityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsIdentityGenerator
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsIncrementGenerator
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsNativeGenerator
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceStyleGenerator
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceWrapper
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsTableGenerator
import org.grails.orm.hibernate.cfg.domainbinding.util.GeneratorCreationContextWrapper
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.NumericColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.StringColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher

/**
 * Describes what {@link GrailsDomainGenerator} emits for a GORM entity. Expectations are written down only where the
 * domain states them explicitly; defaults are covered by the differential spec, which compares the generator with
 * what the domain binder actually bound.
 */
class GrailsDomainGeneratorSpec extends HibernateGormDatastoreSpec {

    Class<?> generated

    void setupSpec() {
        manager.registerDomainClasses(GenBasic, GenVehicle, GenCar, GenWithEnum, GenWithOwner, GenDerived, GenTyped, GenUnsupportedType, GenIdSequence, GenIdUuid, GenIdAssigned, GenIdTable,
                GenIdIncrement, GenIdIdentity, GenIdNative, GenIdTyped, GenIdTypedMismatch, GenPrimVehicle, GenPrimCar, GenPrimJoinedRoot, GenPrimJoinedChild, GenAnimal, GenDog, GenPuppy, GenCat, GenToy, GenPlushToy, GenGadget,
                GenGizmo, GenCoded, GenCodedChild, GenFormulaRoot, GenFormulaChild, GenAbstractBase, GenConcreteChild, GenNoted, GenNotedChild,
                GenJoinedVehicle, GenJoinedCar, GenJoinedSportsCar, GenJoinedSedan, GenJoinedKeyed, GenJoinedKeyedChild,
                GenFleetVehicle, GenFleetCar, GenFleetSportsCar, GenFleetSedan, GenUnionBase, GenUnionLeaf, GenUnionMiddle, GenUnionBottom,
                GenEmbedOwner, GenEmbedOther, GenEmbedBase, GenEmbedChild, GenEmbedFormulaOwner, GenCollSingle, GenCollKinds, GenCollLazy,
                GenFkTarget, GenFkOwned, GenFkOwner, GenFkCascades, GenFkNodeA, GenFkNodeB, GenFkHasOneOwner, GenFkHasOneDetail, GenOneFace, GenOneNose, GenFkManyOne, GenFkOneSide, GenFkSub, GenFkSubRoot,
                GenOmParent, GenOmChild, GenOmListed, GenOmOrdered, GenOmOrphan, GenOmFetched, GenOmTag, GenOmStep, GenOmKept, GenOmSortedOwner, GenOmMapOwner, GenMapBidiOwner, GenMapBidiChild, GenEmbAssocOwner,
                GenMmStudent, GenMmCourse, GenMmPerson, GenParamsOnly, GenDecimal, GenUnversioned, GenUnversionedRoot, GenUnversionedChild, GenConverted, GenValueTyped,
                GenCollEmbeddedHolder, GenCollEmbeddedSibling, GenCollEmbeddedItem, GenCollEmbeddedEntityHolder, GenMapM2mLeft, GenMapM2mRight)
    }

    List<StandardServiceRegistry> registries = []

    void cleanup() {
        registries.each { StandardServiceRegistryBuilder.destroy(it) }
        registries.clear()
    }

    void setup() {
        generated = generate(GenBasic)
    }

    void "the generated class carries the entity and table annotations"() {
        expect:
        generated.name == GenBasic.name
        !generated.is(GenBasic)
        !generated.classLoader.is(GenBasic.classLoader)
        generated.getAnnotation(jakarta.persistence.Entity).name() == 'GenBasic'
        generated.getAnnotation(Table).name() == 'gen_basic'
    }

    void "the identifier is the only field marked as an id"() {
        expect:
        field('id').isAnnotationPresent(Id)
        generated.declaredFields.findAll { it.isAnnotationPresent(Id) }*.name == ['id']
    }

    void "the generated fields are exactly the persistent properties"() {
        expect:
        generated.declaredFields*.name.toSet() == ['id', 'version', 'name', 'code', 'age', 'price', 'notes', 'tag'].toSet()
    }

    void "a field keeps the Java type of its property"() {
        expect:
        field(property).type == type

        where:
        property | type
        'name'   | String
        'age'    | Integer
        'price'  | BigDecimal
    }

    void "the column annotation reflects the constraints and mapping stated on the domain class"() {
        when:
        Column column = field(property).getAnnotation(Column)

        then:
        column.name() == name
        column.nullable() == nullable
        column.unique() == unique
        column.length() == length
        column.scale() == scale

        where:
        property | name     | nullable | unique | length | scale
        'name'   | 'name'   | false    | true   | 50     | 0
        'code'   | 'code_x' | true     | false  | 255    | 0
        'age'    | 'age'    | true     | false  | 255    | 0
        'price'  | 'price'  | true     | false  | 255    | 2
    }

    void "a decimal column with no scale states the scale Hibernate gives the type, because @Column cannot leave the scale unset next to a precision"() {
        given:
        Class<?> decimals = generate(GenDecimal)

        expect: 'the domain binder sets the precision and no scale, so the database default scale of the type applies'
        decimals.getDeclaredField(property).getAnnotation(Column).precision() == precision
        decimals.getDeclaredField(property).getAnnotation(Column).scale() == scale

        where:
        property | precision | scale
        'amount' | 38        | 2
        'big'    | 38        | 0
        'ratio'  | 0         | 0
        'stated' | 38        | 4
    }

    void "the identifier column is never nullable"() {
        expect:
        !field('id').getAnnotation(Column).nullable()
    }

    void "Bean Validation constraints are copied so Hibernate applies their DDL"() {
        expect:
        field('tag').getAnnotation(Size).max() == 20
    }

    void "an entity of an inheritance hierarchy cannot be generated on its own"() {
        when:
        generate(entityClass)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('inheritance hierarchy')
        e.message.contains('generateAll')

        where:
        entityClass << [GenCar, GenVehicle]
    }

    void "type parameters mapped with no type are not stated, because the built-in type the binder hands them to ignores them"() {
        when:
        Class<?> generated = generate(GenParamsOnly)

        then:
        generated.getDeclaredField('quantity').getAnnotations().every { !(it instanceof Type) && !(it instanceof JdbcTypeCode) }
        generated.getDeclaredField('quantity').getAnnotation(Column).name() == 'quantity'
        getPersistentEntity(GenParamsOnly).persistentClass.getProperty('quantity').value.typeParameters.getProperty('sequence_name') == 'seq'
    }

    void "a property the generator does not support is rejected by name"() {
        when:
        newGenerator().generateAll([unbound(GenUnsupportedType)], getClass().classLoader)

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains('Type [serializable] of property [tag] of [' + GenUnsupportedType.name + ']')
    }

    void "the version is marked as the optimistic lock"() {
        expect:
        field('version').isAnnotationPresent(Version)
        field('id').isAnnotationPresent(Id)
        !field('name').isAnnotationPresent(Version)
    }

    void "the class carries the class-level facets stated in the mapping"() {
        expect:
        generated.isAnnotationPresent(DynamicUpdate)
        generated.getAnnotation(BatchSize).size() == 5
        generated.getAnnotation(Comment).value() == 'basic things'
    }

    void "a root without a version states the NONE optimistic lock style, a versioned root states none"() {
        expect:
        !generated.isAnnotationPresent(OptimisticLocking)
        generate(GenUnversioned).getAnnotation(OptimisticLocking).type() == OptimisticLockType.NONE
        !generate(GenUnversioned).declaredFields*.name.contains('version')
    }

    void "a subclass of an unversioned root leaves the lock style to the root"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = newGenerator().generateAll(
                [GenUnversionedRoot, GenUnversionedChild].collect { getPersistentEntity(it) }, getClass().classLoader)

        then:
        classes.values().findAll { it.isAnnotationPresent(OptimisticLocking) }*.name == [GenUnversionedRoot.name]
    }

    void "the column extras stated in the mapping become Hibernate annotations"() {
        expect:
        field('code').getAnnotation(ColumnDefault).value() == "'none'"
        field('code').getAnnotation(Comment).value() == 'the code'
    }

    void "an enum is stored by name unless the mapping says otherwise"() {
        when:
        Class<?> enumEntity = generate(GenWithEnum)

        then:
        enumEntity.getDeclaredField('kind').getAnnotation(Enumerated).value() == EnumType.STRING
        enumEntity.getDeclaredField('rank').getAnnotation(Enumerated).value() == EnumType.ORDINAL
        enumEntity.getDeclaredField('code').getAnnotation(Type).value() == IdentityEnumType
        enumEntity.getDeclaredField('code').getAnnotation(Type).parameters()*.name() == ['enumClass']
        enumEntity.getDeclaredField('code').getAnnotation(Type).parameters()*.value() == [GenCode.name]
    }

    void "a derived property is a formula with no column"() {
        when:
        Class<?> derived = generate(GenDerived)
        Field fullName = derived.getDeclaredField('fullName')

        then:
        fullName.getAnnotation(Formula).value() == "CONCAT(first_name, ' ', last_name)"
        !fullName.isAnnotationPresent(Column)
        derived.getDeclaredField('firstName').isAnnotationPresent(Column)
    }

    void "a mapped user type becomes @Type with its parameters"() {
        when:
        Class<?> typed = generate(GenTyped)
        Type type = typed.getDeclaredField('shout').getAnnotation(Type)

        then:
        type.value() == GenUpperType
        type.parameters().collectEntries { [(it.name()): it.value()] } == [mode: 'loud', other: 'x']
        !typed.getDeclaredField('shout').isAnnotationPresent(JdbcTypeCode)
    }

    void "a registered type name becomes the JDBC type code it resolves to"() {
        when:
        Class<?> typed = generate(GenTyped)

        then:
        typed.getDeclaredField('body').getAnnotation(JdbcTypeCode).value() == java.sql.Types.LONGVARCHAR
        !typed.getDeclaredField('body').isAnnotationPresent(Type)
    }

    void "a registered type name that converts its value becomes the JPA converter it wraps"() {
        when:
        Class<?> converted = generate(GenConverted)

        then:
        converted.getDeclaredField('yesNo').getAnnotation(Convert).converter() == YesNoConverter
        converted.getDeclaredField('trueFalse').getAnnotation(Convert).converter() == TrueFalseConverter
        converted.getDeclaredField('numeric').getAnnotation(Convert).converter() == NumericBooleanConverter
        !converted.getDeclaredField('yesNo').isAnnotationPresent(Type)
        converted.getDeclaredField('yesNo').getAnnotation(JdbcTypeCode).value() == java.sql.Types.CHAR
        converted.getDeclaredField('numeric').getAnnotation(JdbcTypeCode).value() == java.sql.Types.TINYINT
        !converted.getDeclaredField('plain').isAnnotationPresent(Convert)
    }

    void "Hibernate's own annotation binder resolves a converted type like the domain binder"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenConverted)
        PersistentClass bound = entity(GenConverted).persistentClass
        PersistentClass read = annotationMetadata(classes.values()).getEntityBinding(classes[entity(GenConverted)].name)

        expect:
        ['yesNo', 'trueFalse', 'numeric'].every { String name ->
            BasicValue.Resolution<?> expected = ((BasicValue) bound.getProperty(name).value).resolve()
            BasicValue.Resolution<?> actual = ((BasicValue) read.getProperty(name).value).resolve()
            converterClass(expected) == converterClass(actual) &&
                    expected.jdbcType.defaultSqlTypeCode == actual.jdbcType.defaultSqlTypeCode &&
                    expected.domainJavaType.javaTypeClass == actual.domainJavaType.javaTypeClass
        }
        ((BasicValue) bound.getProperty('yesNo').value).resolve().valueConverter instanceof YesNoConverter
    }

    private static Class<?> converterClass(BasicValue.Resolution<?> resolution) {
        def converter = resolution.valueConverter
        return converter.hasProperty('converterBean') ? converter.converterBean.beanClass : converter.getClass()
    }

    void "a value object of a class GORM does not know, mapped with a UserType class, becomes @Type like a simple property"() {
        given:
        GrailsHibernatePersistentEntity entity = unbound(GenValueTyped)
        HibernatePersistentProperty property = entity.getHibernatePropertyByName('amount')

        when:
        Class<?> valueTyped = generate(GenValueTyped)

        then:
        property instanceof HibernateCustomProperty
        newGenerator().supports(property)
        newGenerator().typeFacets(property).userType() == GenMoneyType
        valueTyped.getDeclaredField('amount').getAnnotation(Type).value() == GenMoneyType
        valueTyped.getDeclaredField('amount').type == GenMoney
        valueTyped.getDeclaredField('amount').isAnnotationPresent(Column)
        !valueTyped.getDeclaredField('name').isAnnotationPresent(Type)
    }

    void "a property without an explicit type carries no type annotation"() {
        when:
        Class<?> typed = generate(GenTyped)

        then:
        !typed.getDeclaredField('plain').isAnnotationPresent(Type)
        !typed.getDeclaredField('plain').isAnnotationPresent(JdbcTypeCode)
    }

    void "an enum with an explicit user type states it with the enum class parameter"() {
        when:
        Class<?> typed = generate(GenTyped)
        Type type = typed.getDeclaredField('kind').getAnnotation(Type)

        then:
        type.value() == GenKindType
        type.parameters().collectEntries { [(it.name()): it.value()] } == [enumClass: GenKind.name]
        !typed.getDeclaredField('kind').isAnnotationPresent(Enumerated)
    }

    void "a type name that is neither a user type nor a registered type for the class is rejected by name"() {
        when:
        generate(GenUnsupportedType)

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains('tag')
        e.message.contains('serializable')
    }

    void "Hibernate's own annotation binder reads the generated types and formulas"() {
        given: "generated classes live in their own class loader, which the registry must be told about"
        Collection<Class<?>> classes = generateGroup(GenTyped, GenDerived).values()
        Class<?> typedClass = classes[0]
        Class<?> derivedClass = classes[1]
        BootstrapServiceRegistry bootstrap = new BootstrapServiceRegistryBuilder()
                .applyClassLoader(typedClass.classLoader)
                .build()
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder(bootstrap)
                .applySetting('hibernate.dialect', H2Dialect.name)
                .applySetting('hibernate.connection.url', 'jdbc:h2:mem:generator-binder;DB_CLOSE_DELAY=-1')
                .build()

        when:
        Metadata metadata = new MetadataSources(registry)
                .addAnnotatedClass(typedClass)
                .addAnnotatedClass(derivedClass)
                .buildMetadata()
        BasicValue shout = (BasicValue) metadata.entityBindings.find { it.jpaEntityName == 'GenTyped' }.getProperty('shout').value
        BasicValue body = (BasicValue) metadata.entityBindings.find { it.jpaEntityName == 'GenTyped' }.getProperty('body').value
        BasicValue kind = (BasicValue) metadata.entityBindings.find { it.jpaEntityName == 'GenTyped' }.getProperty('kind').value
        BasicValue fullName = (BasicValue) metadata.entityBindings.find { it.jpaEntityName == 'GenDerived' }.getProperty('fullName').value

        then:
        ((CustomType) shout.type).userType.getClass() == GenUpperType
        shout.typeParameters.getProperty('mode') == 'loud'
        body.resolve().jdbcType.defaultSqlTypeCode == Types.LONGVARCHAR
        ((CustomType) kind.type).userType.getClass() == GenKindType
        kind.typeParameters.getProperty('enumClass') == GenKind.name
        fullName.selectables*.isFormula() == [true]
        ((org.hibernate.mapping.Formula) fullName.selectables[0]).getFormula() == "CONCAT(first_name, ' ', last_name)"

        cleanup:
        StandardServiceRegistryBuilder.destroy(registry)
    }

    void "the identifier generation is the strategy and the parameters the binder uses"() {
        when:
        IdFacets facets = idFacets(domainClass)

        then:
        facets.strategy().name == strategy
        facets.generatorClass() == generatorClass
        facets.parameters() == parameters

        where:
        domainClass   | strategy          | generatorClass            | parameters
        GenBasic      | 'identity'        | GrailsIdentityGenerator   | [:]
        GenIdNative   | 'native'          | GrailsNativeGenerator     | [:]
        GenIdSequence | 'sequence'        | GrailsSequenceStyleGenerator | [increment_size: '10', sequence_name: 'gen_id_seq']
        GenIdUuid     | 'uuid2'           | HibernateUuidGenerator            | [:]
        GenIdAssigned | 'assigned'        | Assigned                  | [:]
        GenIdTable    | 'table'           | GrailsTableGenerator      | [segment_value: 'gen_table', table_name: 'gen_ids']
        GenIdIncrement | 'increment'      | GrailsIncrementGenerator  | [:]
        GenIdIdentity | 'identity'        | GrailsIdentityGenerator   | [:]
    }

    void "a GORM generator is carried by the marker annotation with its parameters"() {
        when:
        Field id = generate(GenIdSequence).getDeclaredField('id')
        GrailsIdGenerator marker = id.getAnnotation(GrailsIdGenerator)

        then:
        id.isAnnotationPresent(Id)
        marker.strategy() == 'sequence'
        marker.parameters().collectEntries { [(it.name()): it.value()] } == [increment_size: '10', sequence_name: 'gen_id_seq']
        !id.isAnnotationPresent(UuidGenerator)
    }

    void "a strategy without parameters is carried by the marker with none"() {
        expect:
        field('id').getAnnotation(GrailsIdGenerator).strategy() == 'identity'
        field('id').getAnnotation(GrailsIdGenerator).parameters().length == 0
        generate(GenIdNative).getDeclaredField('id').getAnnotation(GrailsIdGenerator).strategy() == 'native'
    }

    void "a uuid identifier is Hibernate's own UuidGenerator, and an assigned one has no generator annotation"() {
        when:
        Field uuid = generate(GenIdUuid).getDeclaredField('id')
        Field assigned = generate(GenIdAssigned).getDeclaredField('id')

        then:
        uuid.isAnnotationPresent(UuidGenerator)
        !uuid.isAnnotationPresent(GrailsIdGenerator)
        !assigned.isAnnotationPresent(UuidGenerator)
        !assigned.isAnnotationPresent(GrailsIdGenerator)
    }

    void "an identifier with a mapped type states the JDBC type of the registered type, and a field of the Java type that type holds"() {
        when:
        Field typed = generate(GenIdTyped).getDeclaredField('id')
        Field mismatch = generate(GenIdTypedMismatch).getDeclaredField('id')

        then: 'the type names the binary form of the UUID: the column is binary(16), not the default of the Java type'
        typed.type == UUID
        typed.getAnnotation(JdbcTypeCode).value() == Types.BINARY

        and: 'a mapping may name a type for a value the identifier property cannot hold (the generator makes it): the field has the type'
        mismatch.type == UUID
        mismatch.getAnnotation(JdbcTypeCode).value() == Types.BINARY
    }

    void "a generated class gets the identifier generator the binder installs, for every strategy"() {
        given:
        Class<?> generatedClass = generate(domainClass)
        Generator oracle = getSessionFactory().mappingMetamodel.getEntityDescriptor(domainClass.name).generator

        when:
        Generator bound = boundGenerator(domainClass, generatedClass)

        then:
        bound.getClass() == oracle.getClass()

        and:
        !(oracle instanceof SequenceStyleGenerator) ||
                (bound.databaseStructure.physicalName == oracle.databaseStructure.physicalName &&
                        bound.optimizer.incrementSize == oracle.optimizer.incrementSize &&
                        bound.optimizer.class == oracle.optimizer.class)
        !(oracle instanceof TableGenerator) ||
                (bound.tableName == oracle.tableName && bound.segmentValue == oracle.segmentValue &&
                        bound.incrementSize == oracle.incrementSize)

        where:
        domainClass << [GenBasic, GenIdNative, GenIdSequence, GenIdUuid, GenIdAssigned, GenIdTable, GenIdIncrement, GenIdIdentity]
    }

    void "a Hibernate IdGeneratorType annotation cannot name GrailsNativeGenerator, which is why a marker carries GORM generators"() {
        given:
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting('hibernate.dialect', H2Dialect.name)
                .applySetting('hibernate.connection.url', 'jdbc:h2:mem:generator-probe;DB_CLOSE_DELAY=-1')
                .build()

        when:
        new MetadataSources(registry).addAnnotatedClass(GenNativeProbeEntity).buildMetadata().buildSessionFactory().close()

        then: "GrailsNativeGenerator inherits AnnotationBasedGenerator<NativeGenerator>, which accepts only Hibernate's own annotation"
        Exception e = thrown()
        e.message.contains('is not assignable to')

        cleanup:
        StandardServiceRegistryBuilder.destroy(registry)
    }

    void "a single-table hierarchy is generated whole, each subclass extending the class generated for its superclass"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateHierarchy(GenAnimal, GenDog, GenPuppy, GenCat)

        then:
        classes[entity(GenAnimal)].superclass == Object
        classes[entity(GenDog)].superclass == classes[entity(GenAnimal)]
        classes[entity(GenPuppy)].superclass == classes[entity(GenDog)]
        classes[entity(GenCat)].superclass == classes[entity(GenAnimal)]
        classes.keySet()*.javaClass == [GenAnimal, GenDog, GenPuppy, GenCat]
    }

    void "the classes of one hierarchy share one class loader that can load each of them by name and see the parent"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateHierarchy(GenAnimal, GenDog, GenPuppy, GenCat)
        ClassLoader loader = classes.values().first().classLoader

        then:
        classes.values().every { it.classLoader.is(loader) }
        !loader.is(getClass().classLoader)
        classes.values().every { Class.forName(it.name, false, loader).is(it) }
        loader.parent.is(getClass().classLoader)

        and: "the loader is not the loader of an unrelated generation"
        !generate(GenBasic).classLoader.is(loader)
    }

    void "the root of a single-table hierarchy states the strategy and the default discriminator"() {
        when:
        Class<?> root = generateHierarchy(GenAnimal, GenDog, GenPuppy, GenCat)[entity(GenAnimal)]
        DiscriminatorColumn column = root.getAnnotation(DiscriminatorColumn)

        then:
        root.getAnnotation(Inheritance).strategy() == InheritanceType.SINGLE_TABLE
        root.getAnnotation(Table).name() == 'gen_animal'
        column.name() == 'class'
        column.discriminatorType() == DiscriminatorType.STRING
        column.length() == 255
        root.getAnnotation(DiscriminatorValue).value() == GenAnimal.name
        root.getAnnotation(DiscriminatorOptions).force()
        root.getAnnotation(DiscriminatorOptions).insert()
        !root.isAnnotationPresent(DiscriminatorFormula)
    }

    void "a single-table subclass has no table, no strategy and no discriminator column, only its value"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateHierarchy(GenAnimal, GenDog, GenPuppy, GenCat)

        expect:
        Class<?> subclass = classes[entity(entityClass)]
        subclass.getAnnotation(DiscriminatorValue).value() == entityClass.name
        subclass.getAnnotation(jakarta.persistence.Entity).name() == entityClass.simpleName
        !subclass.isAnnotationPresent(Table)
        !subclass.isAnnotationPresent(Inheritance)
        !subclass.isAnnotationPresent(DiscriminatorColumn)

        where:
        entityClass << [GenDog, GenPuppy, GenCat]
    }

    void "a subclass declares only its own properties, with no identifier and no version, and its columns are nullable"() {
        when:
        Class<?> dog = generateHierarchy(GenAnimal, GenDog, GenPuppy, GenCat)[entity(GenDog)]

        then:
        dog.declaredFields*.name == ['breed']
        !dog.getDeclaredField('breed').isAnnotationPresent(Id)
        dog.getDeclaredField('breed').getAnnotation(Column).nullable()
        dog.getDeclaredField('breed').getAnnotation(Column).name() == 'breed'
    }

    void "a configured discriminator states its value, column, length and the value of each subclass"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateHierarchy(GenToy, GenPlushToy)
        Class<?> root = classes[entity(GenToy)]
        DiscriminatorColumn column = root.getAnnotation(DiscriminatorColumn)

        then:
        root.getAnnotation(DiscriminatorValue).value() == 'TOY'
        column.name() == 'toy_kind'
        column.length() == 12
        column.discriminatorType() == DiscriminatorType.STRING
        classes[entity(GenPlushToy)].getAnnotation(DiscriminatorValue).value() == 'PLUSH'
    }

    void "a root mapping that sets only the discriminator column keeps the class name as the root value"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateHierarchy(GenGadget, GenGizmo)

        then:
        classes[entity(GenGadget)].getAnnotation(DiscriminatorColumn).name() == 'gadget_kind'
        classes[entity(GenGadget)].getAnnotation(DiscriminatorValue).value() == GenGadget.name
        classes[entity(GenGizmo)].getAnnotation(DiscriminatorValue).value() == GenGizmo.name
    }

    void "a primitive property of a single-table subclass is a boxed field, so Hibernate adds no not-null check for the subclass"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> single = generateHierarchy(GenPrimVehicle, GenPrimCar)
        Map<GrailsHibernatePersistentEntity, Class<?>> joined = generateHierarchy(GenPrimJoinedRoot, GenPrimJoinedChild)

        then: 'a joined root and subclass keep the primitive, which makes the column not null in their own table'
        joined[entity(GenPrimJoinedChild)].getDeclaredField('doors').type == int
        joined[entity(GenPrimJoinedRoot)].getDeclaredField('name').type == String

        and: 'the root and the subclasses of a single-table hierarchy share the table, where such a column must stay nullable'
        single[entity(GenPrimVehicle)].getDeclaredField('wheels').type == Integer
        single[entity(GenPrimCar)].getDeclaredField('doors').type == Integer
        single[entity(GenPrimCar)].getDeclaredField('sporty').type == Boolean
    }

    void "a discriminator type, an insert flag and an explicit value are carried over"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateHierarchy(GenCoded, GenCodedChild)
        Class<?> root = classes[entity(GenCoded)]

        then:
        root.getAnnotation(DiscriminatorColumn).discriminatorType() == DiscriminatorType.INTEGER
        root.getAnnotation(DiscriminatorColumn).name() == 'class'
        root.getAnnotation(DiscriminatorOptions).insert() == false
        root.getAnnotation(DiscriminatorOptions).force()
        root.getAnnotation(DiscriminatorValue).value() == '1'
        classes[entity(GenCodedChild)].getAnnotation(DiscriminatorValue).value() == '2'
    }

    void "a discriminator formula replaces the discriminator column"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateHierarchy(GenFormulaRoot, GenFormulaChild)
        Class<?> root = classes[entity(GenFormulaRoot)]

        then:
        root.getAnnotation(DiscriminatorFormula).value() == "case when kind_code = 1 then 'A' else 'B' end"
        root.getAnnotation(DiscriminatorFormula).discriminatorType() == DiscriminatorType.STRING
        !root.isAnnotationPresent(DiscriminatorColumn)
        root.getAnnotation(DiscriminatorValue).value() == GenFormulaRoot.name
        classes[entity(GenFormulaChild)].getAnnotation(DiscriminatorValue).value() == 'A'
    }

    void "an abstract class in a hierarchy is generated abstract"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateHierarchy(GenAbstractBase, GenConcreteChild, GenNoted, GenNotedChild,
                GenJoinedVehicle, GenJoinedCar, GenJoinedSportsCar, GenJoinedSedan, GenJoinedKeyed, GenJoinedKeyedChild,
                GenFleetVehicle, GenFleetCar, GenFleetSportsCar, GenFleetSedan, GenUnionBase, GenUnionLeaf, GenUnionMiddle, GenUnionBottom)

        then:
        java.lang.reflect.Modifier.isAbstract(classes[entity(GenAbstractBase)].modifiers)
        !java.lang.reflect.Modifier.isAbstract(classes[entity(GenConcreteChild)].modifiers)
        classes[entity(GenConcreteChild)].superclass == classes[entity(GenAbstractBase)]
    }

    void "a single-table subclass states its own class-level facets but never a table or a comment"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateHierarchy(GenNoted, GenNotedChild,
                GenJoinedVehicle, GenJoinedCar, GenJoinedSportsCar, GenJoinedSedan, GenJoinedKeyed, GenJoinedKeyedChild,
                GenFleetVehicle, GenFleetCar, GenFleetSportsCar, GenFleetSedan, GenUnionBase, GenUnionLeaf, GenUnionMiddle, GenUnionBottom)
        Class<?> root = classes[entity(GenNoted)]
        Class<?> child = classes[entity(GenNotedChild)]

        then:
        root.getAnnotation(Comment).value() == 'noted things'
        root.getAnnotation(Table).name() == 'gen_noted'
        child.isAnnotationPresent(org.hibernate.annotations.DynamicInsert)
        child.getAnnotation(BatchSize).size() == 3
        !child.isAnnotationPresent(Comment)
        !child.isAnnotationPresent(Table)
        !root.isAnnotationPresent(org.hibernate.annotations.DynamicInsert)
    }

    void "a joined hierarchy states the strategy on the root and a table and key column on every subclass"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes =
                generateHierarchy(GenJoinedVehicle, GenJoinedCar, GenJoinedSportsCar, GenJoinedSedan)
        Class<?> root = classes[entity(GenJoinedVehicle)]
        Class<?> car = classes[entity(GenJoinedCar)]
        Class<?> sports = classes[entity(GenJoinedSportsCar)]

        then:
        root.getAnnotation(Inheritance).strategy() == InheritanceType.JOINED
        root.getAnnotation(Table).name() == 'gen_joined_vehicle'
        !root.isAnnotationPresent(PrimaryKeyJoinColumn)
        car.getAnnotation(Table).name() == 'gen_joined_car'
        car.getAnnotation(PrimaryKeyJoinColumn).name() == 'id'
        sports.getAnnotation(Table).name() == 'gen_joined_sports_car'
        sports.getAnnotation(PrimaryKeyJoinColumn).name() == 'id'
        !car.isAnnotationPresent(Inheritance)

        and: "a joined hierarchy has no discriminator"
        [root, car, sports].every {
            !it.isAnnotationPresent(DiscriminatorColumn) && !it.isAnnotationPresent(DiscriminatorValue) &&
                    !it.isAnnotationPresent(DiscriminatorFormula)
        }

        and: "the extends chain follows the entity hierarchy and subclasses keep their own columns non-forced"
        sports.superclass == car
        car.superclass == root
        classes[entity(GenJoinedSedan)].superclass == car
        car.getDeclaredField('doors').getAnnotation(Column).nullable() == false
    }

    void "a joined subclass keys its table with the name of the identifier column and keeps an explicit table name"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateHierarchy(GenJoinedKeyed, GenJoinedKeyedChild,
                GenFleetVehicle, GenFleetCar, GenFleetSportsCar, GenFleetSedan, GenUnionBase, GenUnionLeaf, GenUnionMiddle, GenUnionBottom)

        then:
        classes[entity(GenJoinedKeyed)].getAnnotation(Table).name() == 'keyed_roots'
        classes[entity(GenJoinedKeyedChild)].getAnnotation(Table).name() == 'keyed_children'
        classes[entity(GenJoinedKeyedChild)].getAnnotation(PrimaryKeyJoinColumn).name() == 'key_id'
    }

    void "Hibernate's annotation binder reads the generated joined hierarchy as the binder builds it"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes =
                generateHierarchy(GenJoinedVehicle, GenJoinedCar, GenJoinedSportsCar, GenJoinedSedan)

        when:
        Metadata metadata = annotationMetadata(classes.values())
        PersistentClass root = metadata.getEntityBinding(classes[entity(GenJoinedVehicle)].name)
        PersistentClass car = metadata.getEntityBinding(classes[entity(GenJoinedCar)].name)
        PersistentClass sports = metadata.getEntityBinding(classes[entity(GenJoinedSportsCar)].name)
        PersistentClass bound = getPersistentEntity(GenJoinedSportsCar).persistentClass

        then:
        root instanceof RootClass
        car instanceof JoinedSubclass
        sports instanceof JoinedSubclass
        sports.superclass.entityName == car.entityName
        sports.table.name == 'gen_joined_sports_car' && !sports.table.is(car.table)
        ((JoinedSubclass) sports).key.columns*.name == ['id']
        root.discriminator == null

        and: "the binder built the same shape"
        bound instanceof JoinedSubclass
        bound.table.name == sports.table.name
        ((JoinedSubclass) bound).key.columns*.name == ((JoinedSubclass) sports).key.columns*.name
        bound.superclass.entityName == GenJoinedCar.name
    }

    void "a table-per-concrete-class hierarchy states the strategy on the root and a table on every subclass only"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes =
                generateHierarchy(GenFleetVehicle, GenFleetCar, GenFleetSportsCar, GenFleetSedan)
        Class<?> root = classes[entity(GenFleetVehicle)]
        Class<?> car = classes[entity(GenFleetCar)]
        Class<?> sports = classes[entity(GenFleetSportsCar)]

        then:
        root.getAnnotation(Inheritance).strategy() == InheritanceType.TABLE_PER_CLASS
        root.getAnnotation(Table).name() == 'gen_fleet_vehicle'
        car.getAnnotation(Table).name() == 'gen_fleet_car'
        sports.getAnnotation(Table).name() == 'gen_fleet_sports_car'
        !car.isAnnotationPresent(Inheritance)
        [root, car, sports].every {
            !it.isAnnotationPresent(PrimaryKeyJoinColumn) && !it.isAnnotationPresent(DiscriminatorColumn) &&
                    !it.isAnnotationPresent(DiscriminatorValue)
        }
        sports.superclass == car
        classes[entity(GenFleetSedan)].superclass == car
        car.superclass == root
        !java.lang.reflect.Modifier.isAbstract(root.modifiers)
    }

    void "the facets of an abstract table-per-concrete-class root say its table is abstract"() {
        when:
        HierarchyFacets root = newGenerator().hierarchyFacets(entity(GenUnionBase))
        HierarchyFacets middle = newGenerator().hierarchyFacets(entity(GenUnionMiddle))
        HierarchyFacets leaf = newGenerator().hierarchyFacets(entity(GenUnionLeaf))

        then:
        root.strategy() == InheritanceType.TABLE_PER_CLASS
        root.abstractClass() && root.abstractTable()
        middle.abstractClass() && middle.abstractTable()
        !leaf.abstractClass() && !leaf.abstractTable()
        leaf.ownsTable() && leaf.superclass() == GenUnionBase.name
    }

    void "Hibernate's annotation binder reads an abstract table-per-concrete-class root as the binder builds it"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateHierarchy(GenUnionBase, GenUnionLeaf, GenUnionMiddle, GenUnionBottom)

        when:
        Metadata metadata = annotationMetadata(classes.values())
        PersistentClass root = metadata.getEntityBinding(classes[entity(GenUnionBase)].name)
        PersistentClass leaf = metadata.getEntityBinding(classes[entity(GenUnionLeaf)].name)
        PersistentClass bottom = metadata.getEntityBinding(classes[entity(GenUnionBottom)].name)
        PersistentClass boundRoot = getPersistentEntity(GenUnionBase).persistentClass
        PersistentClass boundBottom = getPersistentEntity(GenUnionBottom).persistentClass

        then:
        root instanceof RootClass
        leaf instanceof UnionSubclass
        bottom instanceof UnionSubclass
        bottom.superclass.entityName == classes[entity(GenUnionMiddle)].name
        bottom.table.name == 'gen_union_bottom'
        root.isAbstract() == true
        root.table.isAbstractUnionTable()

        and: "the binder built the same shape"
        boundBottom instanceof UnionSubclass
        boundBottom.table.name == bottom.table.name
        boundRoot.isAbstract() == root.isAbstract()
        boundRoot.table.isAbstractUnionTable() == root.table.isAbstractUnionTable()
        getPersistentEntity(GenUnionMiddle).persistentClass.table.isAbstractUnionTable() ==
                metadata.getEntityBinding(classes[entity(GenUnionMiddle)].name).table.isAbstractUnionTable()
    }

    void "a hierarchy must be generated whole"() {
        when:
        newGenerator().generateAll(entityClasses.collect { entity(it) }, getClass().classLoader)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains(missing)

        where:
        entityClasses          | missing
        [GenDog]               | 'superclass'
        [GenAnimal, GenDog]    | 'subclass [org.grails.orm.hibernate.cfg.domainbinding.jpa.GenCat]'
    }

    void "the facets of a hierarchy are the ones the binder reports"() {
        when:
        HierarchyFacets facets = newGenerator().hierarchyFacets(entity(entityClass))

        then:
        facets.strategy() == strategy
        facets.superclass() == superclass
        facets.ownsTable() == ownsTable
        facets.discriminatorValue() == value
        (facets.discriminator() != null) == hasDiscriminator

        where:
        entityClass | strategy                     | superclass     | ownsTable | value         | hasDiscriminator
        GenAnimal   | InheritanceType.SINGLE_TABLE | null           | true      | GenAnimal.name | true
        GenDog      | InheritanceType.SINGLE_TABLE | GenAnimal.name | false     | GenDog.name   | false
        GenPuppy    | InheritanceType.SINGLE_TABLE | GenDog.name    | false     | GenPuppy.name | false
        GenBasic    | null                         | null           | true      | null          | false
        GenJoinedVehicle | InheritanceType.JOINED  | null           | true      | null          | false
        GenJoinedCar     | InheritanceType.JOINED  | GenJoinedVehicle.name | true | null     | false
        GenFleetVehicle  | InheritanceType.TABLE_PER_CLASS | null   | true      | null          | false
        GenFleetCar      | InheritanceType.TABLE_PER_CLASS | GenFleetVehicle.name | true | null | false
    }

    void "Hibernate's annotation binder reads the generated single-table hierarchy as the binder builds it"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateHierarchy(GenAnimal, GenDog, GenPuppy, GenCat)

        when:
        Metadata metadata = annotationMetadata(classes.values())
        PersistentClass animal = metadata.getEntityBinding(classes[entity(GenAnimal)].name)
        PersistentClass dog = metadata.getEntityBinding(classes[entity(GenDog)].name)
        PersistentClass puppy = metadata.getEntityBinding(classes[entity(GenPuppy)].name)

        then:
        animal instanceof RootClass
        dog instanceof SingleTableSubclass
        puppy instanceof SingleTableSubclass
        puppy.superclass.entityName == dog.entityName
        dog.superclass.entityName == animal.entityName
        puppy.table.is(animal.table)
        animal.table.name == 'gen_animal'
        animal.discriminator.selectables*.text == ['class']
        animal.discriminatorValue == GenAnimal.name
        puppy.discriminatorValue == GenPuppy.name
        puppy.getProperty('weeks').columns*.nullable == [true]

        and: "the binder built the same shape"
        getPersistentEntity(GenPuppy).persistentClass.superclass.entityName == GenDog.name
        getPersistentEntity(GenAnimal).persistentClass.discriminator.selectables*.text == ['class']
    }

    void "an embedded property is an @Embedded field whose type is a generated @Embeddable of the embedded type"() {
        given:
        Class<?> owner = generate(GenEmbedOwner)
        Class<?> address = owner.getDeclaredField('home').type

        expect:
        owner.getDeclaredField('home').isAnnotationPresent(Embedded)
        address.isAnnotationPresent(Embeddable)
        !address.isAnnotationPresent(jakarta.persistence.Entity)
        address.name == 'org.grails.orm.hibernate.generated.org_grails_orm_hibernate_cfg_domainbinding_jpa_GenEmbedAddress_Embeddable'
        address.declaredFields*.name.toSet() == ['street', 'city', 'kind', 'label', 'zip'].toSet()
        address.getDeclaredField('city').getAnnotation(Column).length() == 40
        address.getDeclaredField('zip').isAnnotationPresent(Embedded)
        address.getDeclaredField('zip').type.declaredFields*.name.toSet() == ['code', 'plus'].toSet()
        address.getDeclaredField('kind').getAnnotation(Enumerated).value() == EnumType.STRING
    }

    void "owners that embed the same type share one embeddable class"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateHierarchy(GenEmbedOwner, GenEmbedOther)
        Class<?> owner = classes[entity(GenEmbedOwner)]
        Class<?> other = classes[entity(GenEmbedOther)]

        then:
        owner.getDeclaredField('home').type.is(owner.getDeclaredField('work').type)
        owner.getDeclaredField('home').type.is(other.getDeclaredField('office').type)
        owner.getDeclaredField('home').type.classLoader.is(owner.classLoader)
    }

    void "the owner states every column of an embedded property with an attribute override named by the property path"() {
        given:
        Class<?> owner = generate(GenEmbedOwner)

        when:
        Map<String, Column> home = overrides(owner, 'home')
        Map<String, Column> work = overrides(owner, 'work')

        then: "a column is named from the path of the embedded property"
        home.keySet() == ['street', 'city', 'kind', 'label', 'zip.code', 'zip.plus'].toSet()
        home.street.name() == 'home_street'
        home.city.name() == 'home_city'
        home.kind.name() == 'home_kind'
        home['zip.code'].name() == 'home_zip_code'
        home['zip.plus'].name() == 'home_zip_plus'
        work.city.name() == 'work_city'
        work.street.name() == 'work_street'

        and: "constraints reach the override, a column is only NOT NULL when the property and its embedded property say so"
        home.city.length() == 40
        !home.city.nullable()
        work.city.nullable()
        home.kind.nullable()
        home['zip.code'].nullable()
    }

    void "a column the mapping names keeps that name, whatever the path of the embedded property"() {
        when:
        Map<String, Column> contact = overrides(generate(GenEmbedOther), 'contact')

        then:
        contact.phone.name() == 'phone_no'
    }

    void "an embedded property states the same overrides as the columns the binder bound"() {
        given:
        Class<?> owner = generate(GenEmbedOwner)
        Component bound = (Component) getPersistentEntity(GenEmbedOwner).persistentClass.getProperty(embedded).value
        Map<String, Column> overrides = overrides(owner, embedded)

        expect:
        bound.properties.collectMany { Property p ->
            p.value instanceof Component ? ((Component) p.value).properties.collect { Property q -> [p.name + '.' + q.name, q] } : [[p.name, p]]
        }.findAll { List entry -> !((Property) entry[1]).selectables.any { it.formula } }.every { List entry ->
            org.hibernate.mapping.Column column = (org.hibernate.mapping.Column) ((Property) entry[1]).selectables[0]
            Column override = overrides[(String) entry[0]]
            override.name() == column.name && override.nullable() == column.nullable
        }

        where:
        embedded << ['home', 'work']
    }

    void "an embedded property of a table-per-hierarchy subclass is always nullable"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateHierarchy(GenEmbedBase, GenEmbedChild)
        Map<String, Column> place = overrides(classes[entity(GenEmbedChild)], 'place')

        then:
        place.keySet() == ['code', 'plus'].toSet()
        place.values().every { it.nullable() }
        place.code.name() == 'place_code'
    }

    void "Hibernate's annotation binder reads the embedded columns as the binder builds them"() {
        given:
        Class<?> owner = generate(GenEmbedOwner)
        Class<?> child = generateHierarchy(GenEmbedBase, GenEmbedChild).get(entity(GenEmbedChild))

        when:
        Metadata metadata = annotationMetadata([owner])
        Component home = (Component) metadata.getEntityBinding(owner.name).getProperty('home').value
        Component work = (Component) metadata.getEntityBinding(owner.name).getProperty('work').value
        Component zip = (Component) home.getProperty('zip').value
        Component boundHome = (Component) getPersistentEntity(GenEmbedOwner).persistentClass.getProperty('home').value

        then:
        home.properties*.name.toSet() == ['street', 'city', 'kind', 'label', 'zip'].toSet()
        home.getProperty('street').selectables*.text == ['home_street']
        home.getProperty('city').selectables*.text == ['home_city']
        home.getProperty('city').columns*.nullable == [false]
        home.getProperty('city').columns*.length == [40L]
        zip.getProperty('code').selectables*.text == ['home_zip_code']
        work.getProperty('city').selectables*.text == ['work_city']
        work.getProperty('city').columns*.nullable == [true]
        home.getProperty('label').selectables*.text == ['home_label']

        and: "the binder bound the same columns"
        boundHome.getProperty('city').selectables*.text == ['home_city']
        ((Component) boundHome.getProperty('zip').value).getProperty('code').selectables*.text == ['home_zip_code']
    }

    void "a formula inside an embedded type is ignored by the binder, so the generator states a column for it"() {
        given:
        Class<?> embeddable = generate(GenEmbedFormulaOwner).getDeclaredField('described').type
        Component bound = (Component) getPersistentEntity(GenEmbedFormulaOwner).persistentClass.getProperty('described').value

        expect: "ConfigureDerivedPropertiesConsumer only runs for root and subclass entities, never for an embedded type"
        embeddable.getDeclaredField('full').isAnnotationPresent(Column)
        !embeddable.getDeclaredField('full').isAnnotationPresent(Formula)
        bound.getProperty('full').selectables*.formula == [false]
    }

    void "a derived property of an embedded type is a formula with no column and no override"() {
        given: "the binder never flags a property of an embedded type as derived, so the flag is set by hand"
        GrailsHibernatePersistentEntity type = (GrailsHibernatePersistentEntity) entity(GenEmbedFormulaOwner).getHibernatePropertyByName('described').associatedEntity
        PropertyConfig config = type.getHibernatePropertyByName('full').hibernateMappedForm
        config.derived = true

        when:
        Class<?> owner = generate(GenEmbedFormulaOwner)
        Class<?> embeddable = owner.getDeclaredField('described').type

        then:
        embeddable.getDeclaredField('full').getAnnotation(Formula).value() == "CONCAT(first, ' ', last)"
        !embeddable.getDeclaredField('full').isAnnotationPresent(Column)
        overrides(owner, 'described').keySet() == ['first', 'last'].toSet()
        newGenerator().embeddedLeaves(entity(GenEmbedFormulaOwner).getHibernatePropertyByName('described') as HibernateEmbeddedProperty)
                .find { it.path() == 'full' }.column() == null

        cleanup:
        config.derived = false
    }

    void "an embedded type with a property the generator does not support is rejected by name"() {
        when:
        newGenerator().generateAll([unbound(GenEmbedBadHolder)], getClass().classLoader)

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains('Embedded property [bad] of [' + GenEmbedBadHolder.name + ']')
        e.message.contains('ref')
        !newGenerator().supports(unbound(GenEmbedBadHolder).getHibernatePropertyByName('bad'))
    }

    void "a collection of basic values is an @ElementCollection with its table, key column and element column"() {
        // a basic collection is not lazy unless the mapping says so: that is what the binder binds
        given:
        Class<?> owner = generate(GenCollSingle)
        Field tags = owner.getDeclaredField('tags')

        expect:
        tags.genericType.typeName == 'java.util.Set<java.lang.String>'
        tags.getAnnotation(ElementCollection).fetch() == FetchType.EAGER
        tags.getAnnotation(CollectionTable).name() == 'gen_coll_single_tags'
        tags.getAnnotation(CollectionTable).joinColumns()*.name() == ['gen_coll_single_id']
        tags.getAnnotation(CollectionTable).joinColumns()[0].nullable()
        tags.getAnnotation(CollectionTable).joinColumns()[0].updatable()
        tags.getAnnotation(Column).name() == 'tags_java_lang_string'
        tags.getAnnotation(Column).nullable()
        tags.getAnnotation(Fetch).value() == FetchMode.SELECT
        !tags.isAnnotationPresent(OrderColumn)
        !tags.isAnnotationPresent(MapKeyColumn)
    }

    void "each kind of collection is a field of the exact declared type with the index or key column the binder names"() {
        given:
        Class<?> owner = generate(GenCollKinds)
        Field field = owner.getDeclaredField(property)

        expect:
        field.genericType.typeName == type
        field.isAnnotationPresent(OrderColumn) == indexed
        field.isAnnotationPresent(MapKeyColumn) == keyed
        !indexed || field.getAnnotation(OrderColumn).name() == indexName
        !keyed || field.getAnnotation(MapKeyColumn).name() == indexName

        where:
        property  | type                                                  | indexed | keyed | indexName
        'tags'    | 'java.util.Set<java.lang.String>'                     | false   | false | null
        'scores'  | 'java.util.List<java.lang.Integer>'                   | true    | false | 'position'
        'aliases' | 'java.util.Collection<java.lang.String>'              | false   | false | null
        'labels'  | 'java.util.Set<java.lang.String>'                     | false   | false | null
        'attrs'   | 'java.util.Map<java.lang.String, java.lang.String>'   | false   | true  | 'attr_key'
        'kinds'   | 'java.util.Set<' + GenKind.name + '>'                 | false   | false | null
        'ranks'   | 'java.util.List<' + GenKind.name + '>'                | true    | false | 'ranks_idx'
    }

    void "a join table mapping names the collection table, the key column and the element column"() {
        given:
        Field tags = generate(GenCollKinds).getDeclaredField('tags')

        expect:
        tags.getAnnotation(CollectionTable).name() == 'gen_coll_tag'
        tags.getAnnotation(CollectionTable).joinColumns()[0].name() == 'owner_ref'
        tags.getAnnotation(Column).name() == 'tag_value'
    }

    void "a map has a not-null value column and a key column with the facets of its index column mapping"() {
        given:
        Field attrs = generate(GenCollKinds).getDeclaredField('attrs')

        expect:
        attrs.getAnnotation(CollectionTable).name() == 'gen_coll_kinds_attrs'
        attrs.getAnnotation(Column).name() == 'attr_value'
        !attrs.getAnnotation(Column).nullable()
        attrs.getAnnotation(MapKeyColumn).length() == 40
        attrs.getAnnotation(MapKeyColumn).nullable()
    }

    void "an enum element is stored as the enum style says, in a column named after the enum"() {
        given:
        Class<?> owner = generate(GenCollKinds)

        expect:
        owner.getDeclaredField('kinds').getAnnotation(Enumerated).value() == EnumType.ORDINAL
        owner.getDeclaredField('kinds').getAnnotation(Column).name() == 'kind_code'
        owner.getDeclaredField('ranks').getAnnotation(Enumerated).value() == EnumType.STRING
        owner.getDeclaredField('ranks').getAnnotation(Column).name() == 'gen_kind'
    }

    void "the fetching of a collection is the lazy, fetch, batch size and cache the mapping states"() {
        given:
        Class<?> owner = generate(GenCollKinds)

        expect:
        owner.getDeclaredField('aliases').getAnnotation(ElementCollection).fetch() == FetchType.EAGER
        owner.getDeclaredField('aliases').getAnnotation(Fetch).value() == FetchMode.JOIN
        owner.getDeclaredField('ranks').getAnnotation(ElementCollection).fetch() == FetchType.EAGER
        owner.getDeclaredField('ranks').getAnnotation(Fetch).value() == FetchMode.SELECT
        owner.getDeclaredField('labels').getAnnotation(BatchSize).size() == 5
        owner.getDeclaredField('labels').getAnnotation(Cache).usage() == CacheConcurrencyStrategy.READ_WRITE
        !owner.getDeclaredField('tags').isAnnotationPresent(BatchSize)
        !owner.getDeclaredField('tags').isAnnotationPresent(Cache)
    }

    void "a collection key is updatable however many collections the owner has"() {
        given:
        Class<?> owner = generate(GenCollKinds)

        expect: "the binder and the generator both keep the key updatable, or Hibernate would write no rows"
        owner.declaredFields.findAll { it.isAnnotationPresent(ElementCollection) }.every {
            it.getAnnotation(CollectionTable).joinColumns()[0].updatable() && it.getAnnotation(CollectionTable).joinColumns()[0].insertable()
        }
        ((IndexedCollection) getPersistentEntity(GenCollKinds).persistentClass.getProperty('scores').value).key.updateable
    }

    void "Hibernate's annotation binder reads the generated collections as the binder builds them"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateHierarchy(GenCollKinds, GenCollSingle)
        Class<?> owner = classes[entity(GenCollKinds)]
        Class<?> single = classes[entity(GenCollSingle)]
        org.hibernate.mapping.PersistentClass bound = getPersistentEntity(GenCollKinds).persistentClass

        when:
        Metadata metadata = annotationMetadata(classes.values())
        org.hibernate.mapping.PersistentClass annotated = metadata.getEntityBinding(owner.name)
        org.hibernate.mapping.Collection scores = (org.hibernate.mapping.Collection) annotated.getProperty('scores').value
        org.hibernate.mapping.Collection attrs = (org.hibernate.mapping.Collection) annotated.getProperty('attrs').value
        org.hibernate.mapping.Collection tags = (org.hibernate.mapping.Collection) annotated.getProperty('tags').value
        org.hibernate.mapping.Collection singleTags = (org.hibernate.mapping.Collection) metadata.getEntityBinding(single.name).getProperty('tags').value

        then:
        annotated.getProperty(name).value.getClass() == bound.getProperty(name).value.getClass()
        scores instanceof org.hibernate.mapping.List
        ((IndexedCollection) scores).index.selectables*.text == ['position']
        attrs instanceof org.hibernate.mapping.Map
        ((IndexedCollection) attrs).index.selectables*.text == ['attr_key']
        attrs.element.selectables*.text == ['attr_value']
        tags.collectionTable.name == 'gen_coll_tag'
        tags.key.selectables*.text == ['owner_ref']
        tags.element.selectables*.text == ['tag_value']
        singleTags.collectionTable.name == bound.getProperty('tags').value.collectionTable.name.replace('gen_coll_tag', 'gen_coll_single_tags')
        singleTags.key.selectables*.text == ['gen_coll_single_id']
        singleTags.element.selectables*.text == ['tags_java_lang_string']
        annotated.getProperty('labels').value.batchSize == 5
        annotated.getProperty('labels').value.cacheConcurrencyStrategy == 'read-write'
        annotated.getProperty('aliases').value.fetchMode == org.hibernate.FetchMode.JOIN
        !annotated.getProperty('aliases').value.lazy

        where:
        name << ['tags', 'scores', 'aliases', 'labels', 'attrs', 'kinds', 'ranks']
    }

    void "an explicit lazy true is an extra-lazy collection: the facets say so, and the fetch type follows the binder's lazy facet"() {
        when:
        GrailsHibernatePersistentEntity entity = unbound(GenCollLazy)
        CollectionFacets facets = newGenerator().collectionFacets((HibernateBasicProperty) entity.getHibernatePropertyByName('tags'))
        Class<?> generatedLazy = generate(GenCollLazy)

        then:
        facets.extraLazy()
        generatedLazy.getDeclaredField('tags').getAnnotation(ElementCollection).fetch() == (facets.lazy() ? FetchType.LAZY : FetchType.EAGER)
        !newGenerator().collectionFacets((HibernateBasicProperty) unbound(GenCollSingle).getHibernatePropertyByName('tags')).extraLazy()
    }

    void "a map of enums is rejected by name, because the binder itself cannot export its schema"() {
        given:
        GrailsHibernatePersistentEntity entity = unbound(GenCollEnumMap)
        HibernateBasicProperty property = (HibernateBasicProperty) entity.getHibernatePropertyByName('byName')

        expect:
        !newGenerator().supports(property)
        newGenerator().unsupportedReason(entity, property).contains('a map of enums')
    }

    void "a sorted set is rejected by name, because the binder names it as a custom collection type"() {
        given:
        GrailsHibernatePersistentEntity entity = unbound(GenCollSorted)
        HibernateBasicProperty property = (HibernateBasicProperty) entity.getHibernatePropertyByName('labels')

        expect:
        !newGenerator().supports(property)
        newGenerator().unsupportedReason(entity, property).contains('SortedSet')
    }

    void "a type name mapped on the collection property is rejected by name, because the binder gives it to the element as a class name and cannot boot"() {
        given:
        GrailsHibernatePersistentEntity entity = unbound(GenCollTyped)
        HibernateBasicProperty property = (HibernateBasicProperty) entity.getHibernatePropertyByName('notes')

        expect:
        !newGenerator().supports(property)
        newGenerator().unsupportedReason(entity, property).contains('names neither a class nor serializable')
    }

    void "a collection of embedded objects is rejected by name, because the binder cannot bind one"() {
        given:
        GrailsHibernatePersistentEntity entity = unbound(GenCollEmbeddedItems)
        org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty property = entity.getHibernatePropertyByName('items')

        expect:
        !newGenerator().supports(property)
        newGenerator().unsupportedReason(entity, property).contains('a collection of embedded objects')
    }

    void "a collection has several columns, so it has no single column facets"() {
        when:
        newGenerator().columnFacets(entity(GenCollSingle).getHibernatePropertyByName('tags'))

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('collectionFacets')
    }

    void "a collection of basic values inside an embedded type is an element collection of the embeddable, named after the embedded type"() {
        given:
        GrailsHibernatePersistentEntity holder = unbound(GenCollEmbeddedHolder)
        Class<?> owner = generate(GenCollEmbeddedHolder)
        Class<?> embeddable = owner.getDeclaredField('inner').type
        Field words = embeddable.getDeclaredField('words')

        expect:
        newGenerator().supports(holder.getHibernatePropertyByName('inner'))
        words.isAnnotationPresent(ElementCollection)
        words.getAnnotation(CollectionTable).name() == 'gen_coll_embedded_words'
        words.getAnnotation(CollectionTable).joinColumns()*.name() == ['gen_coll_embedded_id']
        words.getAnnotation(Column).name() == 'words_java_lang_string'
        embeddable.name == 'org.grails.orm.hibernate.generated.org_grails_orm_hibernate_cfg_domainbinding_jpa_GenCollEmbedded_Embeddable'

        and: "the collection has no column of the owner's table, so the owner overrides none for it"
        newGenerator().embeddedLeaves(holder.getHibernatePropertyByName('inner') as HibernateEmbeddedProperty).isEmpty()
        !owner.getDeclaredField('inner').isAnnotationPresent(AttributeOverrides)
    }

    void "a collection of entities inside an embedded type is a to-many of the embeddable, which takes the name of the embedded type"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCollEmbeddedEntityHolder, GenCollEmbeddedItem)
        Class<?> owner = classes[entity(GenCollEmbeddedEntityHolder)]
        Class<?> embeddable = owner.getDeclaredField('inner').type
        Field items = embeddable.getDeclaredField('items')

        expect: "Hibernate retries without end the element check of a collection whose declaring class it resolves to the real embedded class"
        embeddable.name == GenCollEmbeddedEntities.name
        embeddable.isAnnotationPresent(Embeddable)
        items.genericType.typeName == 'java.util.Set<' + GenCollEmbeddedItem.name + '>'
        items.getAnnotation(ManyToMany).fetch() == FetchType.LAZY
        items.getAnnotation(JoinTable).name() == 'gen_coll_embedded_entities_gen_coll_embedded_item'
        items.getAnnotation(JoinTable).joinColumns()*.name() == ['gen_coll_embedded_entities_items_id']
    }

    void "an embedded type that has a collection is rejected when a second embedded property shares it"() {
        when:
        newGenerator().generateAll([unbound(EmbeddedCollectionOwnerA), unbound(EmbeddedCollectionOwnerB)], getClass().classLoader)

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains('The collection [words] of the embedded type [' + EmbeddedCollectionHolder.name + ']')
        e.message.contains('is reachable through 2 embedded properties')
        e.message.contains(EmbeddedCollectionOwnerA.name + '.inner')
        e.message.contains(EmbeddedCollectionOwnerB.name + '.inner')
    }

    void "an owner that embeds the same type with a collection twice is rejected"() {
        when:
        newGenerator().generateAll([unbound(EmbeddedCollectionOwnerTwice)], getClass().classLoader)

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains('is reachable through 2 embedded properties')
        e.message.contains(EmbeddedCollectionOwnerTwice.name + '.home')
        e.message.contains(EmbeddedCollectionOwnerTwice.name + '.work')
    }

    void "owners that embed different types with collections are generated together"() {
        expect:
        generateGroup(GenCollEmbeddedHolder).size() == 1
        newGenerator().generateAll(
                [unbound(GenCollEmbeddedHolder), unbound(GenCollEmbeddedSibling)], getClass().classLoader).size() == 2
    }

    void "a to-one association is a field typed with the class generated for its target, in the same class loader"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenFkOwner, GenFkTarget, GenFkOwned)
        Class<?> owner = classes[entity(GenFkOwner)]
        Class<?> target = classes[entity(GenFkTarget)]

        expect:
        owner.getDeclaredField('plain').type == target
        owner.getDeclaredField('owned').type == classes[entity(GenFkOwned)]
        owner.classLoader == target.classLoader
        owner.getDeclaredField('plain').isAnnotationPresent(ManyToOne)
        !owner.getDeclaredField('plain').isAnnotationPresent(jakarta.persistence.OneToOne)
    }

    void "an entity that refers to another is rejected by name when the target is not part of the call"() {
        when:
        generate(GenFkOwner)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('[' + GenFkOwner.name + ']')
        e.message.contains('[' + GenFkTarget.name + ']')
        e.message.contains('generateAll')
    }

    void "the fetching, optionality, foreign key column and missing-row handling of a to-one association are the ones the binder decides"() {
        given:
        Class<?> owner = generateGroup(GenFkOwner, GenFkTarget, GenFkOwned)[entity(GenFkOwner)]
        Field field = owner.getDeclaredField(property)

        expect:
        field.getAnnotation(ManyToOne).fetch() == fetch
        field.getAnnotation(ManyToOne).optional() == optional
        field.getAnnotation(JoinColumn).name() == column
        field.getAnnotation(JoinColumn).nullable() == nullable
        field.getAnnotation(JoinColumn).unique() == unique
        field.getAnnotation(Fetch).value() == fetchMode
        field.isAnnotationPresent(NotFound) == ignoreNotFound
        !ignoreNotFound || field.getAnnotation(NotFound).action() == NotFoundAction.IGNORE

        where:
        property   | fetch           | optional | column       | nullable | unique | fetchMode         | ignoreNotFound
        'plain'    | FetchType.LAZY  | true     | 'plain_id'   | true     | false  | FetchMode.SELECT  | false
        'required' | FetchType.LAZY  | true     | 'required_id'| true     | false  | FetchMode.SELECT  | false
        'joined'   | FetchType.EAGER | true     | 'joined_id'  | true     | false  | FetchMode.JOIN    | false
        'eager'    | FetchType.EAGER | true     | 'eager_id'   | true     | false  | FetchMode.SELECT  | false
        'missing'  | FetchType.LAZY  | true     | 'missing_id' | true     | false  | FetchMode.SELECT  | true
        'named'    | FetchType.LAZY  | true     | 'target_ref' | true     | true   | FetchMode.SELECT  | false
    }

    void "a self reference is a field of the generated class itself"() {
        given:
        Class<?> owner = generateGroup(GenFkOwner, GenFkTarget, GenFkOwned)[entity(GenFkOwner)]

        expect:
        owner.getDeclaredField('parent').type == owner
    }

    void "the cascade of a to-one association is the binder's, split into what JPA and Hibernate can state"() {
        given:
        Class<?> owner = generateGroup(GenFkCascades, GenFkTarget)[entity(GenFkCascades)]
        Field field = owner.getDeclaredField(property)

        expect:
        field.getAnnotation(ManyToOne).cascade().toList() == jpa
        (field.isAnnotationPresent(Cascade) ? field.getAnnotation(Cascade).value().toList() : []) == hibernate

        where:
        property    | jpa                                                                  | hibernate
        'cascadeAll' | [jakarta.persistence.CascadeType.ALL]                                | []
        'cascadeOrphan' | [jakarta.persistence.CascadeType.ALL]                                | [org.hibernate.annotations.CascadeType.DELETE_ORPHAN]
        'cascadeSaveUpdate' | [jakarta.persistence.CascadeType.PERSIST, jakarta.persistence.CascadeType.MERGE] | []
        'cascadeMerged' | [jakarta.persistence.CascadeType.MERGE]                              | []
        'cascadePersisted' | [jakarta.persistence.CascadeType.PERSIST]                            | []
        'cascadeDeleted' | [jakarta.persistence.CascadeType.REMOVE]                             | []
        'cascadeEvicted' | [jakarta.persistence.CascadeType.DETACH]                             | []
        'cascadeLocked' | []                                                                   | [org.hibernate.annotations.CascadeType.LOCK]
        'cascadeReplicated' | []                                                                   | [org.hibernate.annotations.CascadeType.REPLICATE]
        'cascadeNone' | []                                                                   | []
    }

    void "two entities that refer to each other are generated in one call, whatever the order"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(*order)
        Class<?> a = classes[entity(GenFkNodeA)]
        Class<?> b = classes[entity(GenFkNodeB)]

        then:
        a.getDeclaredField('other').type == b
        b.getDeclaredField('back').type == a
        a.getDeclaredField('other').getAnnotation(JoinColumn).name() == 'other_id'
        b.getDeclaredField('back').getAnnotation(JoinColumn).name() == 'back_id'

        where:
        order << [[GenFkNodeA, GenFkNodeB], [GenFkNodeB, GenFkNodeA]]
    }

    void "a one-to-one with no inverse is bound as a many-to-one, so it is a @ManyToOne with a plain join column"() {
        given:
        GrailsHibernatePersistentEntity entity = entity(GenFkOwner)

        expect:
        entity.getHibernatePropertyByName('plain') instanceof HibernateOneToOneProperty
        !((HibernateOneToOneProperty) entity.getHibernatePropertyByName('plain')).isValidHibernateOneToOne()
        newGenerator().supports(entity.getHibernatePropertyByName('plain'))
    }

    void "a many-to-one with an inverse hasMany is a to-one association with the same facets"() {
        given:
        HibernatePersistentProperty property = entity(GenFkManyOne).getHibernatePropertyByName('one')
        ToOneFacets facets = newGenerator().toOneFacets((HibernateToOneProperty) property)

        expect:
        property instanceof HibernateManyToOneProperty
        facets.target() == GenFkOneSide.name
        facets.lazy()
        facets.fetchMode() == org.hibernate.FetchMode.SELECT
        facets.optional()
        !facets.ignoreNotFound()
        facets.joinColumn().name() == 'one_id'
        facets.joinColumn().nullable()
    }

    void "a to-one association of a subclass in a table-per-hierarchy is always nullable and named like its property"() {
        given:
        ToOneFacets facets = newGenerator().toOneFacets(
                (HibernateToOneProperty) entity(GenFkSub).getHibernatePropertyByName('target'))

        expect:
        facets.joinColumn().name() == 'target_id'
        facets.joinColumn().nullable()
        facets.optional()
    }

    void "the inverse side of a hasOne is a @OneToOne(mappedBy) with no column, and the other side keeps the foreign key"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenFkHasOneOwner, GenFkHasOneDetail)
        Field detail = classes[entity(GenFkHasOneOwner)].getDeclaredField('detail')
        Field owner = classes[entity(GenFkHasOneDetail)].getDeclaredField('owner')

        expect:
        detail.type == classes[entity(GenFkHasOneDetail)]
        detail.getAnnotation(jakarta.persistence.OneToOne).mappedBy() == 'owner'
        detail.getAnnotation(jakarta.persistence.OneToOne).optional()
        detail.getAnnotation(jakarta.persistence.OneToOne).cascade().toList() == [jakarta.persistence.CascadeType.ALL]
        !detail.getAnnotation(jakarta.persistence.OneToOne).orphanRemoval()
        detail.getAnnotation(Fetch).value() == FetchMode.SELECT
        !detail.isAnnotationPresent(JoinColumn)
        !detail.isAnnotationPresent(ManyToOne)
        owner.type == classes[entity(GenFkHasOneOwner)]
        owner.isAnnotationPresent(ManyToOne)
        owner.getAnnotation(JoinColumn).name() == 'owner_id'
        !owner.getAnnotation(JoinColumn).nullable()
        !owner.getAnnotation(ManyToOne).optional()
    }

    void "a one-to-one owned through belongsTo is a foreign key on the owner and a mappedBy @OneToOne on the owned side"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenOneFace, GenOneNose)
        Field nose = classes[entity(GenOneFace)].getDeclaredField('nose')
        Field face = classes[entity(GenOneNose)].getDeclaredField('face')

        expect:
        nose.isAnnotationPresent(ManyToOne)
        nose.getAnnotation(JoinColumn).name() == 'nose_id'
        nose.getAnnotation(ManyToOne).cascade().toList() == [jakarta.persistence.CascadeType.ALL]
        face.getAnnotation(jakarta.persistence.OneToOne).mappedBy() == 'nose'
        face.getAnnotation(jakarta.persistence.OneToOne).cascade().toList() ==
                [jakarta.persistence.CascadeType.PERSIST, jakarta.persistence.CascadeType.MERGE]
        !face.isAnnotationPresent(JoinColumn)
    }

    void "an inverse one-to-one has no column, so it has no column facets"() {
        when:
        newGenerator().columnFacets(entity(GenFkHasOneOwner).getHibernatePropertyByName('detail'))

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('toOneFacets')
    }

    void "the facets of an inverse one-to-one name the property that holds the key and the entity the binder references"() {
        given:
        ToOneFacets facets = newGenerator().toOneFacets(
                (HibernateToOneProperty) entity(GenFkHasOneOwner).getHibernatePropertyByName('detail'))

        expect:
        facets.mappedBy() == 'owner'
        facets.target() == GenFkHasOneDetail.name
        facets.referencedEntity() == GenFkHasOneDetail.name
        facets.joinColumn() == null
        facets.optional()
        !facets.ignoreNotFound()
    }

    void "Hibernate's own annotation binder reads an inverse one-to-one as a OneToOne that the other side's foreign key owns"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenFkHasOneOwner, GenFkHasOneDetail)
        Metadata metadata = annotationMetadata(classes.values())
        PersistentClass ownerClass = metadata.getEntityBinding(classes[entity(GenFkHasOneOwner)].name)
        PersistentClass detailClass = metadata.getEntityBinding(classes[entity(GenFkHasOneDetail)].name)
        Property inverse = ownerClass.getProperty('detail')
        org.hibernate.mapping.OneToOne value = (org.hibernate.mapping.OneToOne) inverse.value
        org.hibernate.mapping.ManyToOne key = (org.hibernate.mapping.ManyToOne) detailClass.getProperty('owner').value

        expect:
        value.referencedEntityName == classes[entity(GenFkHasOneDetail)].name
        value.referencedPropertyName == 'owner'
        !value.constrained
        value.foreignKeyType == org.hibernate.type.ForeignKeyDirection.TO_PARENT
        inverse.cascade == 'all'
        key.selectables*.text == ['owner_id']
        !key.columns[0].nullable
    }

    void "Hibernate's own annotation binder reads a generated to-one association as a ManyToOne to the generated target"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenFkOwner, GenFkTarget, GenFkOwned)
        Metadata metadata = annotationMetadata(classes.values())
        PersistentClass owner = metadata.getEntityBinding(classes[entity(GenFkOwner)].name)
        Property read = owner.getProperty(property)
        org.hibernate.mapping.ManyToOne value = (org.hibernate.mapping.ManyToOne) read.value

        expect:
        value.referencedEntityName == classes[entity(target)].name
        value.selectables*.text == [column]
        value.lazy == lazy
        value.fetchMode == fetchMode
        value.ignoreNotFound == ignoreNotFound
        read.cascade == cascade

        where:
        property   | target       | column       | lazy  | fetchMode                   | ignoreNotFound | cascade
        'plain'    | GenFkTarget  | 'plain_id'   | true  | org.hibernate.FetchMode.SELECT | false       | 'persist,merge'
        'joined'   | GenFkTarget  | 'joined_id'  | false | org.hibernate.FetchMode.JOIN   | false       | 'persist,merge'
        'eager'    | GenFkTarget  | 'eager_id'   | false | org.hibernate.FetchMode.SELECT | false       | 'persist,merge'
        'missing'  | GenFkTarget  | 'missing_id' | false | org.hibernate.FetchMode.SELECT | true        | 'persist,merge'
        'named'    | GenFkTarget  | 'target_ref' | true  | org.hibernate.FetchMode.SELECT | false       | 'persist,merge'
        'parent'   | GenFkOwner   | 'parent_id'  | true  | org.hibernate.FetchMode.SELECT | false       | 'persist,merge'
    }

    void "a to-one association keeps the unique foreign key of its mapping in the annotation-built column"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenFkOwner, GenFkTarget, GenFkOwned)
        PersistentClass owner = annotationMetadata(classes.values()).getEntityBinding(classes[entity(GenFkOwner)].name)

        expect:
        owner.getProperty('named').columns[0].unique
        !owner.getProperty('plain').columns[0].unique
    }

    void "a bidirectional collection is the inverse @OneToMany of the foreign key on the other side, with the binder's cascade"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateOmGroup()
        Field children = classes[entity(GenOmParent)].getDeclaredField('children')

        expect:
        children.genericType.typeName == "java.util.Set<${classes[entity(GenOmChild)].name}>"
        children.getAnnotation(OneToMany).mappedBy() == 'parent'
        children.getAnnotation(OneToMany).fetch() == FetchType.LAZY
        children.getAnnotation(OneToMany).cascade().toList() == [jakarta.persistence.CascadeType.ALL]
        !children.getAnnotation(OneToMany).orphanRemoval()
        children.getAnnotation(Fetch).value() == FetchMode.SELECT
        !children.isAnnotationPresent(JoinColumn)
        !children.isAnnotationPresent(JoinTable)
        !children.isAnnotationPresent(OrderColumn)
        !children.isAnnotationPresent(OrderBy)
    }

    void "the many-to-one on the other side of a bidirectional collection is generated with the foreign key the collection is mapped by"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateOmGroup()
        Field parent = classes[entity(GenOmChild)].getDeclaredField('parent')

        expect:
        parent.type == classes[entity(GenOmParent)]
        parent.getAnnotation(JoinColumn).name() == 'parent_id'
    }

    void "a bidirectional list is owned by the collection: the foreign key is its join column and the index a column of the target table"() {
        given:
        Field listed = generateOmGroup()[entity(GenOmParent)].getDeclaredField('listed')

        expect:
        listed.getAnnotation(OneToMany).mappedBy() == ''
        listed.getAnnotation(JoinColumn).name() == 'parent_id'
        listed.getAnnotation(JoinColumn).nullable()
        listed.getAnnotation(OrderColumn).name() == 'listed_idx'
        listed.getAnnotation(OrderColumn).nullable()
        listed.genericType.typeName.startsWith('java.util.List<')
    }

    void "a unidirectional collection is a @ManyToMany with the join table the binder names"() {
        given:
        Field tags = generateOmGroup()[entity(GenOmParent)].getDeclaredField('tags')

        expect:
        tags.isAnnotationPresent(ManyToMany)
        !tags.isAnnotationPresent(OneToMany)
        tags.getAnnotation(JoinTable).name() == 'gen_om_parent_gen_om_tag'
        tags.getAnnotation(JoinTable).joinColumns()*.name() == ['gen_om_parent_tags_id']
        tags.getAnnotation(JoinTable).inverseJoinColumns()*.name() == ['gen_om_tag_id']
        tags.getAnnotation(ManyToMany).fetch() == FetchType.LAZY
        tags.getAnnotation(ManyToMany).cascade().toList() ==
                [jakarta.persistence.CascadeType.PERSIST, jakarta.persistence.CascadeType.MERGE]
    }

    void "a join table mapping names the table, the key column and the element column"() {
        given:
        Field labels = generateOmGroup()[entity(GenOmParent)].getDeclaredField('labels')

        expect:
        labels.getAnnotation(JoinTable).name() == 'gen_om_label'
        labels.getAnnotation(JoinTable).joinColumns()*.name() == ['owner_ref']
        labels.getAnnotation(JoinTable).inverseJoinColumns()*.name() == ['tag_ref']
    }

    void "a unidirectional list has an order column in the join table"() {
        given:
        Field steps = generateOmGroup()[entity(GenOmParent)].getDeclaredField('steps')

        expect:
        steps.isAnnotationPresent(ManyToMany)
        steps.getAnnotation(OrderColumn).name() == 'position'
        steps.genericType.typeName.startsWith('java.util.List<')
    }

    void "the sort and order of a collection are an @OrderBy"() {
        given:
        Field ordered = generateOmGroup()[entity(GenOmParent)].getDeclaredField('ordered')

        expect:
        ordered.getAnnotation(OrderBy).value() == 'name desc'
    }

    void "the fetch, batch size and cache of a collection of entities are the mapping's"() {
        given:
        Field fetched = generateOmGroup()[entity(GenOmParent)].getDeclaredField('fetched')

        expect:
        fetched.getAnnotation(OneToMany).fetch() == FetchType.EAGER
        fetched.getAnnotation(Fetch).value() == FetchMode.JOIN
        fetched.getAnnotation(BatchSize).size() == 5
        fetched.getAnnotation(Cache).usage() == CacheConcurrencyStrategy.READ_WRITE
    }

    void "orphan removal is stated on a @OneToMany and as Hibernate's orphan cascade on a @ManyToMany"() {
        given:
        Class<?> parent = generateOmGroup()[entity(GenOmParent)]

        expect:
        parent.getDeclaredField('orphans').getAnnotation(OneToMany).orphanRemoval()
        parent.getDeclaredField('orphans').getAnnotation(OneToMany).cascade().toList() == [jakarta.persistence.CascadeType.ALL]
        !parent.getDeclaredField('kept').isAnnotationPresent(OneToMany)
        parent.getDeclaredField('kept').getAnnotation(Cascade).value().toList() == [org.hibernate.annotations.CascadeType.DELETE_ORPHAN]
        parent.getDeclaredField('kept').getAnnotation(ManyToMany).cascade().toList() == [jakarta.persistence.CascadeType.ALL]
    }

    void "a collection of entities is rejected by name when its target is not part of the call"() {
        when:
        newGenerator().generateAll([entity(GenOmParent)], getClass().classLoader)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('[' + GenOmParent.name + ']')
        e.message.contains('generateAll')
    }

    void "a sorted set of entities is a @SortNatural field of the exact declared type"() {
        given:
        Class<?> owner = generateGroup(GenOmSortedOwner, GenOmTag)[entity(GenOmSortedOwner)]
        Field tags = owner.getDeclaredField('tags')

        expect:
        tags.genericType.typeName.startsWith('java.util.SortedSet<')
        tags.isAnnotationPresent(org.hibernate.annotations.SortNatural)
        tags.isAnnotationPresent(ManyToMany)
    }

    void "an explicit lazy true on a collection of entities is an extra-lazy collection in the facets"() {
        given:
        GrailsHibernatePersistentEntity entity = unbound(GenOmLazyOwner)
        HibernateToManyEntityProperty property = (HibernateToManyEntityProperty) entity.getHibernatePropertyByName('tags')

        expect:
        newGenerator().supports(property)
        newGenerator().toManyFacets(property).extraLazy()
    }

    void "Hibernate's own annotation binder reads a bidirectional collection as an inverse one-to-many on the generated target"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateOmGroup()
        PersistentClass parent = annotationMetadata(classes.values()).getEntityBinding(classes[entity(GenOmParent)].name)
        org.hibernate.mapping.Collection value = (org.hibernate.mapping.Collection) parent.getProperty(property).value

        expect:
        value.inverse == inverse
        value.oneToMany
        ((org.hibernate.mapping.OneToMany) value.element).referencedEntityName == classes[entity(target)].name
        value.key.selectables*.text == ['parent_id']

        where:
        property   | target       | inverse
        'children' | GenOmChild   | true
        'ordered'  | GenOmOrdered | true
        'orphans'  | GenOmOrphan  | true
        'listed'   | GenOmListed  | false
    }

    void "Hibernate's own annotation binder reads a list owned through the foreign key with its index in the target table"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateOmGroup()
        PersistentClass parent = annotationMetadata(classes.values()).getEntityBinding(classes[entity(GenOmParent)].name)
        org.hibernate.mapping.List value = (org.hibernate.mapping.List) parent.getProperty('listed').value

        expect:
        value.index.selectables*.text == ['listed_idx']
        value.collectionTable.name == 'gen_om_listed'
    }

    void "Hibernate's own annotation binder reads a unidirectional collection as a join table of the binder's names"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateOmGroup()
        PersistentClass parent = annotationMetadata(classes.values()).getEntityBinding(classes[entity(GenOmParent)].name)
        org.hibernate.mapping.Collection value = (org.hibernate.mapping.Collection) parent.getProperty(property).value

        expect:
        !value.oneToMany
        !value.inverse
        value.collectionTable.name == table
        value.key.selectables*.text == [key]
        value.element.selectables*.text == [element]
        ((org.hibernate.mapping.ToOne) value.element).referencedEntityName == classes[entity(GenOmTag)].name

        where:
        property | table                       | key                      | element
        'tags'   | 'gen_om_parent_gen_om_tag'  | 'gen_om_parent_tags_id'  | 'gen_om_tag_id'
        'labels' | 'gen_om_label'              | 'owner_ref'              | 'tag_ref'
    }

    void "the owning side of a many-to-many is a @ManyToMany with the binder's join table, the other side is mappedBy it"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenMmStudent, GenMmCourse)
        Field courses = classes[entity(GenMmStudent)].getDeclaredField('courses')
        Field students = classes[entity(GenMmCourse)].getDeclaredField('students')

        expect:
        courses.genericType.typeName == "java.util.Set<${classes[entity(GenMmCourse)].name}>"
        courses.getAnnotation(ManyToMany).mappedBy() == ''
        courses.getAnnotation(JoinTable).name() == 'gen_mm_student_courses'
        courses.getAnnotation(JoinTable).joinColumns()*.name() == [keyColumn]
        courses.getAnnotation(JoinTable).inverseJoinColumns()*.name() == [elementColumn]
        !courses.getAnnotation(JoinTable).inverseJoinColumns()[0].nullable()
        students.getAnnotation(ManyToMany).mappedBy() == 'courses'
        !students.isAnnotationPresent(JoinTable)

        where:
        keyColumn         | elementColumn
        'gen_mm_student_id' | 'gen_mm_course_id'
    }

    void "neither side of a many-to-many that has no belongsTo owns it, which the generator rejects by name"() {
        given:
        GrailsHibernatePersistentEntity entity = unbound(NoOwnerLeft, NoOwnerRight)
        HibernatePersistentProperty property = entity.getHibernatePropertyByName('rights')

        expect:
        !newGenerator().supports(property)
        newGenerator().unsupportedReason(entity, property).contains('neither side of the many-to-many owns it')
    }

    void "Hibernate's own annotation binder reads the two sides of a many-to-many as an owning and an inverse collection of one table"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenMmStudent, GenMmCourse)
        Metadata metadata = annotationMetadata(classes.values())
        org.hibernate.mapping.Collection owning = (org.hibernate.mapping.Collection) metadata.getEntityBinding(classes[entity(GenMmStudent)].name).getProperty('courses').value
        org.hibernate.mapping.Collection inverse = (org.hibernate.mapping.Collection) metadata.getEntityBinding(classes[entity(GenMmCourse)].name).getProperty('students').value

        expect:
        !owning.inverse
        inverse.inverse
        owning.collectionTable.is(inverse.collectionTable)
        owning.collectionTable.name == 'gen_mm_student_courses'
        owning.key.selectables*.text == inverse.element.selectables*.text
        owning.element.selectables*.text == inverse.key.selectables*.text
    }

    void "a map of entities is a @ManyToMany join table with a @MapKeyColumn, whatever its direction"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenOmMapOwner, GenOmTag, GenMapBidiOwner, GenMapBidiChild)
        Field field = classes[entity(owner)].getDeclaredField(property)

        expect:
        field.genericType.typeName == "java.util.Map<java.lang.String, ${classes[entity(target)].name}>"
        field.isAnnotationPresent(ManyToMany)
        field.getAnnotation(JoinTable).name() == table
        field.getAnnotation(JoinTable).joinColumns()*.name() == [key]
        field.getAnnotation(JoinTable).inverseJoinColumns()*.name() == [element]
        field.getAnnotation(jakarta.persistence.MapKeyColumn).name() == index

        where:
        owner           | property | target          | table                     | key                      | element                | index
        GenOmMapOwner   | 'byName' | GenOmTag        | 'gen_om_map_owner_by_name' | 'gen_om_map_owner_by_name_id' | 'gen_om_tag_id' | 'by_name_idx'
        GenMapBidiOwner | 'kids'   | GenMapBidiChild | 'gen_map_bidi_owner_kids' | 'kids_id' | 'owner_id'              | 'kids_idx'
    }

    void "a map on a many-to-many is the side that writes the join table, with a map key column, and the set that reads it is mappedBy"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenMapM2mLeft, GenMapM2mRight)
        Field rights = classes[entity(GenMapM2mLeft)].getDeclaredField('rights')
        Field lefts = classes[entity(GenMapM2mRight)].getDeclaredField('lefts')

        expect:
        rights.genericType.typeName == 'java.util.Map<java.lang.String, ' + GenMapM2mRight.name + '>'
        rights.getAnnotation(ManyToMany).mappedBy() == ''
        rights.getAnnotation(JoinTable).name() == 'gen_map_m2m_left_rights'
        rights.getAnnotation(JoinTable).joinColumns()*.name() == ['gen_map_m2m_left_id']
        rights.getAnnotation(JoinTable).inverseJoinColumns()*.name() == ['gen_map_m2m_right_id']
        rights.getAnnotation(MapKeyColumn).name() == 'rights_idx'
        lefts.genericType.typeName == 'java.util.Set<' + GenMapM2mLeft.name + '>'
        lefts.getAnnotation(ManyToMany).mappedBy() == 'rights'
        !lefts.isAnnotationPresent(JoinTable)
        newGenerator().supports(entity(GenMapM2mLeft).getHibernatePropertyByName('rights'))
        newGenerator().supports(entity(GenMapM2mRight).getHibernatePropertyByName('lefts'))
    }

    void "an association inside an embedded type is a field of the embeddable, and each owner states its join column with @AssociationOverride"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenEmbAssocOwner, GenFkTarget)
        Class<?> owner = classes[entity(GenEmbAssocOwner)]
        Class<?> embeddable = owner.getDeclaredField('home').type
        Closure<Map<String, JoinColumn>> overrides = { String property ->
            owner.getDeclaredField(property).getAnnotation(AssociationOverrides).value().collectEntries { AssociationOverride override ->
                [(override.name()): override.joinColumns()[0]]
            }
        }

        expect:
        owner.getDeclaredField('work').type == embeddable
        embeddable.getDeclaredField('city').type == classes[entity(GenFkTarget)]
        embeddable.getDeclaredField('city').isAnnotationPresent(ManyToOne)
        overrides('home')['city'].name() == 'home_city_id'
        overrides('work')['city'].name() == 'work_city_id'
        overrides('home')['city'].nullable()
    }

    void "Hibernate's own annotation binder reads an association inside an embedded type as a many-to-one of the component"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenEmbAssocOwner, GenFkTarget)
        PersistentClass owner = annotationMetadata(classes.values()).getEntityBinding(classes[entity(GenEmbAssocOwner)].name)
        Component home = (Component) owner.getProperty('home').value
        org.hibernate.mapping.ManyToOne city = (org.hibernate.mapping.ManyToOne) home.getProperty('city').value

        expect:
        city.referencedEntityName == classes[entity(GenFkTarget)].name
        city.selectables*.text == ['home_city_id']
    }

    private Map<GrailsHibernatePersistentEntity, Class<?>> generateOmGroup() {
        return generateGroup(GenOmParent, GenOmChild, GenOmListed, GenOmOrdered, GenOmOrphan, GenOmFetched, GenOmTag, GenOmStep, GenOmKept)
    }

    private Map<GrailsHibernatePersistentEntity, Class<?>> generateGroup(Class<?>... domainClasses) {
        return newGenerator().generateAll(domainClasses.collect { entity(it) }, getClass().classLoader)
    }

    private GrailsHibernatePersistentEntity unbound(Class<?> domainClass, Class<?>... companions) {
        // the mapping model alone, so a domain the binder cannot boot can still be described
        return (GrailsHibernatePersistentEntity) new HibernateMappingContext(
                new HibernateConnectionSourceSettings(), (Object) null, ([domainClass] + companions.toList()) as Class[]).getPersistentEntity(domainClass.name)
    }

    private Map<String, Column> overrides(Class<?> owner, String embedded) {
        AttributeOverrides overrides = owner.getDeclaredField(embedded).getAnnotation(AttributeOverrides)
        return overrides.value().collectEntries { AttributeOverride override -> [(override.name()): override.column()] }
    }

    private Metadata annotationMetadata(Collection<Class<?>> classes) {
        BootstrapServiceRegistry bootstrap = new BootstrapServiceRegistryBuilder()
                .applyClassLoader(classes.first().classLoader)
                .build()
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder(bootstrap)
                .applySetting('hibernate.dialect', H2Dialect.name)
                .applySetting('hibernate.connection.url', 'jdbc:h2:mem:generator-hierarchy;DB_CLOSE_DELAY=-1')
                .build()
        registries << registry
        MetadataSources sources = new MetadataSources(registry)
        classes.each { sources.addAnnotatedClass(it) }
        return sources.buildMetadata()
    }

    private Map<GrailsHibernatePersistentEntity, Class<?>> generateHierarchy(Class<?>... domainClasses) {
        return newGenerator().generateAll(domainClasses.collect { entity(it) }, getClass().classLoader)
    }

    private GrailsHibernatePersistentEntity entity(Class<?> domainClass) {
        return getPersistentEntity(domainClass)
    }

    private IdFacets idFacets(Class<?> domainClass) {
        return newGenerator().idFacets(getPersistentEntity(domainClass))
    }

    /**
     * Binds the generated class with Hibernate's own annotation binder, then installs on its identifier the GORM
     * generator the marker names, built by the same code the domain binder uses.
     */
    private Generator boundGenerator(Class<?> domainClass, Class<?> generatedClass) {
        BootstrapServiceRegistry bootstrap = new BootstrapServiceRegistryBuilder().applyClassLoader(generatedClass.classLoader).build()
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder(bootstrap)
                .applySetting('hibernate.dialect', H2Dialect.name)
                .applySetting('hibernate.connection.url', 'jdbc:h2:mem:generator-id;DB_CLOSE_DELAY=-1')
                .build()
        SessionFactoryImplementor sessionFactory = null
        try {
            Metadata metadata = new MetadataSources(registry).addAnnotatedClass(generatedClass).buildMetadata()
            PersistentClass persistentClass = metadata.entityBindings.first()
            GrailsIdGenerator marker = generatedClass.getDeclaredField(persistentClass.identifierProperty.name).getAnnotation(GrailsIdGenerator)
            if (marker != null) {
                GrailsHibernatePersistentEntity entity = getPersistentEntity(domainClass)
                BasicValue identifier = (BasicValue) persistentClass.identifier
                JdbcEnvironment jdbcEnvironment = getSessionFactory().jdbcServices.jdbcEnvironment
                def naming = getGrailsDomainBinder().getNamingStrategy()
                identifier.setCustomIdGeneratorCreator({ GeneratorCreationContext context ->
                    new GrailsSequenceWrapper().getGenerator(
                            marker.strategy(), new GeneratorCreationContextWrapper(context, identifier),
                            (HibernateSimpleIdentity) entity.hibernateIdentity, entity, jdbcEnvironment, naming)
                } as GeneratorCreator)
            }
            sessionFactory = (SessionFactoryImplementor) metadata.buildSessionFactory()
            return sessionFactory.mappingMetamodel.getEntityDescriptor(persistentClass.entityName).generator
        } finally {
            sessionFactory?.close()
            StandardServiceRegistryBuilder.destroy(registry)
        }
    }

    private GrailsDomainGenerator newGenerator() {
        def naming = getGrailsDomainBinder().getNamingStrategy()
        return new GrailsDomainGenerator(
                naming,
                new ColumnNameForPropertyAndPathFetcher(naming, new DefaultColumnNameFetcher(naming), new BackticksRemover()),
                new ColumnConfigToColumnBinder(),
                new StringColumnConstraintsBinder(),
                new NumericColumnConstraintsBinder(new H2Dialect()),
                getSessionFactory().typeConfiguration)
    }

    private Class<?> generate(Class<?> domainClass) {
        GrailsDomainGenerator generator = newGenerator()
        GrailsHibernatePersistentEntity entity = getPersistentEntity(domainClass)
        return generator.generate(entity, getClass().classLoader)
    }

    private Field field(String name) {
        return generated.getDeclaredField(name)
    }
}

@Entity
class GenEmbedOwner {

    String name
    GenEmbedAddress home
    GenEmbedAddress work

    static embedded = ['home', 'work']

    static constraints = {
        home nullable: false
    }
}

@Entity
class GenEmbedOther {

    GenEmbedAddress office
    GenEmbedContact contact

    static embedded = ['office', 'contact']
}

class GenEmbedAddress {

    String street
    String city
    GenKind kind
    String label
    GenEmbedZip zip

    static embedded = ['zip']

    static constraints = {
        city nullable: false, maxSize: 40
    }

}

class GenEmbedContact {

    String phone

    static mapping = {
        phone column: 'phone_no'
    }
}

class GenEmbedZip {

    String code
    String plus
}

@Entity
class GenEmbedBase {

    String name
}

@Entity
class GenEmbedChild extends GenEmbedBase {

    GenEmbedZip place

    static embedded = ['place']

    static constraints = {
        place nullable: false
    }
}

@Entity
class GenEmbedBadHolder {

    GenEmbedBad bad

    static embedded = ['bad']
}

class GenEmbedBad {

    String text
    Set<String> ref

    static hasMany = [ref: String]
    static mapping = {
        ref type: 'text'
    }
}

@Entity
class GenEmbedFormulaOwner {

    GenEmbedFormulaType described

    static embedded = ['described']
}

class GenEmbedFormulaType {

    String first
    String last
    String full

    static mapping = {
        full formula: "CONCAT(first, ' ', last)"
    }
}

@Entity
class GenCollSingle {

    Set<String> tags

    static hasMany = [tags: String]
}

@Entity
class GenCollKinds {

    String name
    Set<String> tags
    List<Integer> scores
    Collection<String> aliases
    Set<String> labels
    Map<String, String> attrs
    Set<GenKind> kinds
    List<GenKind> ranks

    static hasMany = [tags: String, scores: Integer, aliases: String, labels: String, attrs: String, kinds: GenKind, ranks: GenKind]

    static mapping = {
        tags joinTable: [name: 'gen_coll_tag', key: 'owner_ref', column: 'tag_value']
        scores indexColumn: [name: 'position']
        aliases fetch: 'join'
        labels batchSize: 5, cache: 'read-write'
        attrs joinTable: [column: 'attr_value'], indexColumn: [name: 'attr_key', length: 40]
        kinds enumType: 'ordinal', joinTable: [column: 'kind_code']
        ranks lazy: false
    }
}

@Entity
class GenCollLazy {

    Set<String> tags

    static hasMany = [tags: String]

    static mapping = {
        tags lazy: true
    }
}

@Entity
class GenCollSorted {

    SortedSet<String> labels

    static hasMany = [labels: String]
}

@Entity
class GenCollTyped {

    Set<String> notes

    static hasMany = [notes: String]

    static mapping = {
        notes type: 'text'
    }
}

@Entity
class GenCollEnumMap {

    Map<String, GenKind> byName

    static hasMany = [byName: GenKind]
}

@Entity
class GenCollEmbeddedItems {

    static hasMany = [items: GenCollItem]
    static embedded = ['items']
}

class GenCollItem {

    String label
}

@Entity
class GenCollEmbeddedHolder {

    GenCollEmbedded inner

    static embedded = ['inner']
}

class GenCollEmbedded {

    Set<String> words

    static hasMany = [words: String]
}

@Entity
class GenCollEmbeddedSibling {

    GenCollOther inner

    static embedded = ['inner']
}

class GenCollOther {

    Set<String> tags

    static hasMany = [tags: String]
}

@Entity
class GenCollEmbeddedItem {

    String name
}

@Entity
class GenCollEmbeddedEntityHolder {

    GenCollEmbeddedEntities inner

    static embedded = ['inner']
}

class GenCollEmbeddedEntities {

    Set<GenCollEmbeddedItem> items

    static hasMany = [items: GenCollEmbeddedItem]
}

@Entity
class GenBasic {

    String name
    String code
    Integer age
    BigDecimal price
    String notes

    @Size(max = 20)
    String tag

    static constraints = {
        name maxSize: 50, unique: true, nullable: false
        age nullable: true
        price scale: 2, nullable: true
        notes nullable: true
    }

    static mapping = {
        dynamicUpdate true
        batchSize 5
        comment 'basic things'
        code column: 'code_x', defaultValue: "'none'", comment: 'the code'
    }
}

@Entity
class GenDecimal {

    BigDecimal amount
    BigInteger big
    Double ratio
    BigDecimal stated

    static constraints = {
        stated scale: 4
    }
}

@Entity
class GenVehicle {

    String name
}

@Entity
class GenCar extends GenVehicle {

    Integer doors
}

@Entity
class GenPrimVehicle {

    String name
    int wheels
}

@Entity
class GenPrimCar extends GenPrimVehicle {

    int doors
    boolean sporty
}

@Entity
class GenPrimJoinedRoot {

    String name
    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
class GenPrimJoinedChild extends GenPrimJoinedRoot {

    int doors
}

@Entity
class GenAnimal {

    String name
}

@Entity
class GenDog extends GenAnimal {

    String breed

    static constraints = {
        breed nullable: false
    }
}

@Entity
class GenPuppy extends GenDog {

    Integer weeks
}

@Entity
class GenCat extends GenAnimal {

    Boolean indoor
}

@Entity
class GenToy {

    String name

    static mapping = {
        discriminator value: 'TOY', column: [name: 'toy_kind', length: 12]
    }
}

@Entity
class GenPlushToy extends GenToy {

    static mapping = {
        discriminator 'PLUSH'
    }
}

@Entity
class GenGadget {

    String name

    static mapping = {
        discriminator column: 'gadget_kind'
    }
}

@Entity
class GenGizmo extends GenGadget {
}

@Entity
class GenCoded {

    String name

    static mapping = {
        discriminator value: '1', type: 'integer', insert: false
    }
}

@Entity
class GenCodedChild extends GenCoded {

    static mapping = {
        discriminator '2'
    }
}

@Entity
class GenFormulaRoot {

    Integer kindCode

    static mapping = {
        discriminator formula: "case when kind_code = 1 then 'A' else 'B' end"
    }
}

@Entity
class GenFormulaChild extends GenFormulaRoot {

    static mapping = {
        discriminator 'A'
    }
}

@Entity
class GenNoted {

    String name

    static mapping = {
        comment 'noted things'
    }
}

@Entity
class GenNotedChild extends GenNoted {

    static mapping = {
        dynamicInsert true
        batchSize 3
    }
}

@Entity
class GenJoinedVehicle {

    String name

    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
class GenJoinedCar extends GenJoinedVehicle {

    Integer doors

    static constraints = {
        doors nullable: false
    }
}

@Entity
class GenJoinedSportsCar extends GenJoinedCar {

    Integer topSpeed
}

@Entity
class GenJoinedSedan extends GenJoinedCar {

    Boolean limousine
}

@Entity
class GenJoinedKeyed {

    String name

    static mapping = {
        table 'keyed_roots'
        tablePerHierarchy false
        id column: 'key_id'
    }
}

@Entity
class GenJoinedKeyedChild extends GenJoinedKeyed {

    String extra

    static mapping = {
        table 'keyed_children'
    }
}

@Entity
class GenFleetVehicle {

    String name

    static mapping = {
        tablePerHierarchy false
        tablePerConcreteClass true
        id generator: 'table'
    }
}

@Entity
class GenFleetCar extends GenFleetVehicle {

    Integer doors
}

@Entity
class GenFleetSportsCar extends GenFleetCar {

    Integer topSpeed
}

@Entity
class GenFleetSedan extends GenFleetCar {

    Boolean limousine
}

@Entity
abstract class GenUnionBase {

    String title

    static mapping = {
        tablePerConcreteClass true
        id generator: 'table'
    }
}

@Entity
class GenUnionLeaf extends GenUnionBase {

    String leafValue
}

@Entity
abstract class GenUnionMiddle extends GenUnionBase {

    String middleValue
}

@Entity
class GenUnionBottom extends GenUnionMiddle {

    String bottomValue
}

@Entity
abstract class GenAbstractBase {

    String title
}

@Entity
class GenConcreteChild extends GenAbstractBase {

    String extra
}

enum GenKind {
    SMALL, LARGE
}

enum GenCode {
    FIRST('a'), SECOND('b')

    final String id

    GenCode(String id) {
        this.id = id
    }
}

@Entity
class GenWithEnum {

    GenKind kind
    GenKind rank
    GenCode code

    static mapping = {
        rank enumType: 'ordinal'
        code enumType: 'identity'
    }
}

@Entity
class GenWithOwner {

    GenBasic basic
}

@Entity
class GenDerived {

    String firstName
    String lastName
    String fullName

    static mapping = {
        fullName formula: "CONCAT(first_name, ' ', last_name)"
    }
}

@Entity
class GenTyped {

    String body
    String shout
    String plain
    GenKind kind

    static mapping = {
        body type: 'text'
        shout type: GenUpperType, params: [mode: 'loud', other: 'x']
        kind type: GenKindType
    }
}

@Entity
class GenValueTyped {

    String name
    GenMoney amount

    static mapping = {
        amount type: GenMoneyType
    }
}

class GenMoney implements Serializable {

    final long cents

    GenMoney(long cents) {
        this.cents = cents
    }
}

class GenMoneyType implements UserType<GenMoney> {

    @Override
    int getSqlType() {
        return Types.BIGINT
    }

    @Override
    Class<GenMoney> returnedClass() {
        return GenMoney
    }

    @Override
    boolean equals(GenMoney x, GenMoney y) {
        return x?.cents == y?.cents
    }

    @Override
    int hashCode(GenMoney x) {
        return Long.hashCode(x.cents)
    }

    @Override
    GenMoney nullSafeGet(ResultSet rs, int position, WrapperOptions options) throws SQLException {
        long cents = rs.getLong(position)
        return rs.wasNull() ? null : new GenMoney(cents)
    }

    @Override
    void nullSafeSet(PreparedStatement st, GenMoney value, int index, WrapperOptions options) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.BIGINT)
        } else {
            st.setLong(index, value.cents)
        }
    }

    @Override
    GenMoney deepCopy(GenMoney value) {
        return value
    }

    @Override
    boolean isMutable() {
        return false
    }

    @Override
    Serializable disassemble(GenMoney value) {
        return value
    }

    @Override
    GenMoney assemble(Serializable cached, Object owner) {
        return (GenMoney) cached
    }
}

@Entity
class GenConverted {

    Boolean yesNo
    Boolean trueFalse
    Boolean numeric
    Boolean plain

    static mapping = {
        yesNo type: 'yes_no'
        trueFalse type: 'true_false'
        numeric type: 'numeric_boolean'
    }
}

@Entity
class GenParamsOnly {

    Integer quantity

    static mapping = {
        quantity params: [sequence_name: 'seq']
    }
}

@Entity
class GenUnsupportedType {

    String tag

    static mapping = {
        tag type: 'serializable'
    }
}

class GenUpperType implements UserType<String>, ParameterizedType {

    @Override
    void setParameterValues(Properties parameters) {
    }

    @Override
    int getSqlType() {
        return Types.VARCHAR
    }

    @Override
    Class<String> returnedClass() {
        return String
    }

    @Override
    boolean equals(String x, String y) {
        return x == y
    }

    @Override
    int hashCode(String x) {
        return x.hashCode()
    }

    @Override
    String nullSafeGet(ResultSet rs, int position, WrapperOptions options) throws SQLException {
        return rs.getString(position)
    }

    @Override
    void nullSafeSet(PreparedStatement st, String value, int index, WrapperOptions options) throws SQLException {
        st.setString(index, value?.toUpperCase())
    }

    @Override
    String deepCopy(String value) {
        return value
    }

    @Override
    boolean isMutable() {
        return false
    }

    @Override
    Serializable disassemble(String value) {
        return value
    }

    @Override
    String assemble(Serializable cached, Object owner) {
        return (String) cached
    }
}

class GenKindType implements UserType<GenKind>, ParameterizedType {

    @Override
    void setParameterValues(Properties parameters) {
    }

    @Override
    int getSqlType() {
        return Types.VARCHAR
    }

    @Override
    Class<GenKind> returnedClass() {
        return GenKind
    }

    @Override
    boolean equals(GenKind x, GenKind y) {
        return x == y
    }

    @Override
    int hashCode(GenKind x) {
        return x.hashCode()
    }

    @Override
    GenKind nullSafeGet(ResultSet rs, int position, WrapperOptions options) throws SQLException {
        String name = rs.getString(position)
        return name == null ? null : GenKind.valueOf(name)
    }

    @Override
    void nullSafeSet(PreparedStatement st, GenKind value, int index, WrapperOptions options) throws SQLException {
        st.setString(index, value?.name())
    }

    @Override
    GenKind deepCopy(GenKind value) {
        return value
    }

    @Override
    boolean isMutable() {
        return false
    }

    @Override
    Serializable disassemble(GenKind value) {
        return value
    }

    @Override
    GenKind assemble(Serializable cached, Object owner) {
        return (GenKind) cached
    }
}

@Entity
class GenIdSequence {

    String name

    static mapping = {
        id generator: 'sequence', params: [sequence_name: 'gen_id_seq', increment_size: '10']
    }
}

@Entity
class GenIdTyped {

    UUID id
    String name

    static mapping = {
        id generator: 'uuid2', type: 'uuid-binary'
    }
}

@Entity
class GenIdTypedMismatch {

    Long id
    String name

    static mapping = {
        id generator: 'assigned', type: 'uuid-binary'
    }
}

@Entity
class GenIdUuid {

    String id
    String name

    static mapping = {
        id generator: 'uuid2'
    }
}

@Entity
class GenIdAssigned {

    String id
    String name

    static mapping = {
        id generator: 'assigned'
    }
}

@Entity
class GenIdTable {

    String name

    static mapping = {
        id generator: 'table', params: [table_name: 'gen_ids', segment_value: 'gen_table']
    }
}

@Entity
class GenIdIncrement {

    String name

    static mapping = {
        id generator: 'increment'
    }
}

@Entity
class GenIdIdentity {

    String name

    static mapping = {
        id generator: 'identity'
    }
}

@Entity
class GenIdNative {

    String name

    static mapping = {
        id generator: 'native'
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@IdGeneratorType(GrailsNativeGenerator)
@interface GenNativeProbe {
}

@jakarta.persistence.Entity
class GenNativeProbeEntity {

    @Id
    @GenNativeProbe
    Long id
}

@Entity
class GenFkTarget {

    String name
}

@Entity
class GenFkOwned {

    String name

    static belongsTo = [GenFkOwner]
}

@Entity
class GenFkOwner {

    String title
    GenFkTarget plain
    GenFkTarget required
    GenFkTarget joined
    GenFkTarget eager
    GenFkTarget missing
    GenFkTarget named
    GenFkOwned owned
    GenFkOwner parent

    static constraints = {
        required nullable: false
    }

    static mapping = {
        joined fetch: 'join'
        eager lazy: false
        missing ignoreNotFound: true
        named column: 'target_ref', unique: true
    }
}

@Entity
class GenFkCascades {

    GenFkTarget cascadeAll
    GenFkTarget cascadeOrphan
    GenFkTarget cascadeSaveUpdate
    GenFkTarget cascadeMerged
    GenFkTarget cascadePersisted
    GenFkTarget cascadeDeleted
    GenFkTarget cascadeEvicted
    GenFkTarget cascadeLocked
    GenFkTarget cascadeReplicated
    GenFkTarget cascadeNone

    static mapping = {
        cascadeAll cascade: 'all'
        cascadeOrphan cascade: 'all-delete-orphan'
        cascadeSaveUpdate cascade: 'save-update'
        cascadeMerged cascade: 'merge'
        cascadePersisted cascade: 'persist'
        cascadeDeleted cascade: 'delete'
        cascadeEvicted cascade: 'evict'
        cascadeLocked cascade: 'lock'
        cascadeReplicated cascade: 'replicate'
        cascadeNone cascade: 'none'
    }
}

@Entity
class GenFkNodeA {

    GenFkNodeB other
}

@Entity
class GenFkNodeB {

    GenFkNodeA back
}

@Entity
class GenFkHasOneOwner {

    static hasOne = [detail: GenFkHasOneDetail]
}

@Entity
class GenFkHasOneDetail {

    GenFkHasOneOwner owner
}

@Entity
class GenOneFace {

    GenOneNose nose
}

@Entity
class GenOneNose {

    static belongsTo = [face: GenOneFace]
}

@Entity
class GenFkManyOne {

    GenFkOneSide one
}

@Entity
class GenFkOneSide {

    static hasMany = [many: GenFkManyOne]
}

@Entity
class GenFkSubRoot {

    String name
}

@Entity
class GenFkSub extends GenFkSubRoot {

    GenFkTarget target
}

@Entity
class GenFkComposite implements Serializable {

    String first
    String last

    static mapping = {
        id composite: ['first', 'last']
    }
}

@Entity
class GenFkCompositeRef {

    GenFkComposite target
}

@Entity
class GenOmParent {

    String name
    Set<GenOmChild> children
    List<GenOmListed> listed
    Set<GenOmOrdered> ordered
    Set<GenOmOrphan> orphans
    Set<GenOmFetched> fetched
    Set<GenOmTag> tags
    Set<GenOmTag> labels
    List<GenOmStep> steps
    Set<GenOmKept> kept

    static hasMany = [children: GenOmChild, listed: GenOmListed, ordered: GenOmOrdered, orphans: GenOmOrphan, fetched: GenOmFetched,
                      tags: GenOmTag, labels: GenOmTag, steps: GenOmStep, kept: GenOmKept]

    static mapping = {
        ordered sort: 'name', order: 'desc'
        orphans cascade: 'all-delete-orphan'
        fetched fetch: 'join', batchSize: 5, cache: 'read-write'
        labels joinTable: [name: 'gen_om_label', key: 'owner_ref', column: 'tag_ref']
        steps indexColumn: [name: 'position']
        kept cascade: 'all-delete-orphan'
    }
}

@Entity
class GenOmChild {

    String name
    GenOmParent parent

    static belongsTo = [parent: GenOmParent]
}

@Entity
class GenOmListed {

    String name
    GenOmParent parent
}

@Entity
class GenOmOrdered {

    String name
    GenOmParent parent
}

@Entity
class GenOmOrphan {

    String name
    GenOmParent parent
}

@Entity
class GenOmFetched {

    String name
    GenOmParent parent
}

@Entity
class GenOmTag {

    String label
}

@Entity
class GenOmStep {

    String label
}

@Entity
class GenOmKept {

    String label
}

@Entity
class GenOmMapOwner {

    Map<String, GenOmTag> byName

    static hasMany = [byName: GenOmTag]
}

@Entity
class GenOmLazyOwner {

    Set<GenOmTag> tags

    static hasMany = [tags: GenOmTag]

    static mapping = {
        tags lazy: true
    }
}

@Entity
class GenOmSortedOwner {

    SortedSet<GenOmTag> tags

    static hasMany = [tags: GenOmTag]
}

@Entity
class GenMmStudent {

    String name
    Set<GenMmCourse> courses

    static hasMany = [courses: GenMmCourse]
}

@Entity
class GenMmCourse {

    String title
    Set<GenMmStudent> students

    static hasMany = [students: GenMmStudent]
    static belongsTo = [GenMmStudent]
}

@Entity
class GenMmPerson {

    String name
    Set<GenMmPerson> friends

    static hasMany = [friends: GenMmPerson]
}

@Entity
class GenMmSelf {

    String name
    Set<GenMmSelf> followers
    Set<GenMmSelf> following

    static hasMany = [followers: GenMmSelf, following: GenMmSelf]
    static mappedBy = [followers: 'following', following: 'followers']
    static belongsTo = [GenMmSelf]
}

@Entity
class GenMapBidiOwner {

    Map<String, GenMapBidiChild> kids

    static hasMany = [kids: GenMapBidiChild]
}

@Entity
class GenMapBidiChild {

    GenMapBidiOwner owner
}

@Entity
class GenEmbAssocOwner {

    String name
    GenEmbAssocPlace home
    GenEmbAssocPlace work

    static embedded = ['home', 'work']
}

class GenEmbAssocPlace {

    String street
    GenFkTarget city
}

@Entity
class GenUnversioned {

    String name

    static mapping = {
        version false
    }
}

@Entity
class GenUnversionedRoot {

    String name

    static mapping = {
        version false
    }
}

@Entity
class GenUnversionedChild extends GenUnversionedRoot {

    String extra
}

@Entity
class GenMapM2mLeft {

    String name
    Map<String, GenMapM2mRight> rights

    static hasMany = [rights: GenMapM2mRight]
}

@Entity
class GenMapM2mRight {

    String name
    Set<GenMapM2mLeft> lefts

    static hasMany = [lefts: GenMapM2mLeft]
}
