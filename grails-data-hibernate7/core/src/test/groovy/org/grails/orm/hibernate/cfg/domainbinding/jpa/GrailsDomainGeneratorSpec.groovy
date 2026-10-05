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

import java.lang.reflect.Field

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import jakarta.persistence.Column
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.Size
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.Comment
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.Formula
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.Type
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.BootstrapServiceRegistry
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.boot.registry.StandardServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.dialect.H2Dialect
import org.hibernate.mapping.BasicValue
import org.hibernate.type.CustomType
import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.usertype.ParameterizedType
import org.hibernate.usertype.UserType
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types

import org.grails.orm.hibernate.cfg.IdentityEnumType
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
        manager.registerDomainClasses(GenBasic, GenVehicle, GenCar, GenWithEnum, GenWithOwner, GenDerived, GenTyped, GenUnsupportedType)
    }

    void setup() {
        generated = generate(GenBasic)
    }

    void "the generated class carries the entity and table annotations"() {
        expect:
        generated.name == 'org.grails.orm.hibernate.generated.org_grails_orm_hibernate_cfg_domainbinding_jpa_GenBasic'
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

    void "the identifier column is never nullable"() {
        expect:
        !field('id').getAnnotation(Column).nullable()
    }

    void "Bean Validation constraints are copied so Hibernate applies their DDL"() {
        expect:
        field('tag').getAnnotation(Size).max() == 20
    }

    void "an entity in an inheritance hierarchy is rejected until inheritance is supported"() {
        when:
        generate(GenCar)

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains('inheritance hierarchy')
    }

    void "a property the generator does not support is rejected by name"() {
        when:
        generate(GenWithOwner)

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains('basic')
        e.message.contains('does not support yet')
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
        Class<?> typedClass = generate(GenTyped)
        Class<?> derivedClass = generate(GenDerived)
        BootstrapServiceRegistry bootstrap = new BootstrapServiceRegistryBuilder()
                .applyClassLoader(typedClass.classLoader)
                .applyClassLoader(derivedClass.classLoader)
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

    private Class<?> generate(Class<?> domainClass) {
        def domainBinder = getGrailsDomainBinder()
        def naming = domainBinder.getNamingStrategy()
        def generator = new GrailsDomainGenerator(
                naming,
                new ColumnNameForPropertyAndPathFetcher(naming, new DefaultColumnNameFetcher(naming), new BackticksRemover()),
                new ColumnConfigToColumnBinder(),
                new StringColumnConstraintsBinder(),
                new NumericColumnConstraintsBinder(new H2Dialect()),
                getSessionFactory().typeConfiguration)
        GrailsHibernatePersistentEntity entity = getPersistentEntity(domainClass)
        return generator.generate(entity, getClass().classLoader)
    }

    private Field field(String name) {
        return generated.getDeclaredField(name)
    }
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
class GenVehicle {

    String name
}

@Entity
class GenCar extends GenVehicle {

    Integer doors
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
