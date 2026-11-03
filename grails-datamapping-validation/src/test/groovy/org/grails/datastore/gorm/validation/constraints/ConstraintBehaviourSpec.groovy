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
package org.grails.datastore.gorm.validation.constraints

import org.apache.commons.validator.routines.UrlValidator
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.StaticMessageSource
import org.springframework.validation.FieldError
import spock.lang.Specification
import spock.lang.Unroll

import grails.gorm.validation.ConstrainedProperty
import org.grails.datastore.mapping.validation.ValidationErrors

class ConstraintBehaviourSpec extends Specification {

    static ValidationErrors validate(AbstractConstraint constraint, CbPerson target, Object value) {
        ValidationErrors errors = new ValidationErrors(target, CbPerson.name)
        constraint.validate(target, value, errors)
        return errors
    }

    static FieldError rejected(AbstractConstraint constraint, CbPerson target, Object value) {
        ValidationErrors errors = validate(constraint, target, value)
        errors.errorCount == 1 ? errors.fieldErrors[0] : null
    }

    void "nullable constraints veto further validation on null values"() {
        given:
        NullableConstraint required = new NullableConstraint(CbPerson, 'name', false, null)
        NullableConstraint optional = new NullableConstraint(CbPerson, 'name', true, null)
        CbPerson person = new CbPerson()

        expect:
        required.name == 'nullable'
        !required.nullable
        optional.nullable
        required.parameter == false
        required.supports(String)
        !required.supports(int)
        !required.supports(null)
        required.validateWithVetoing(person, null, new ValidationErrors(person))
        !optional.validateWithVetoing(person, null, new ValidationErrors(person))
        !required.validateWithVetoing(person, 'x', new ValidationErrors(person))
        rejected(required, person, null).codes.contains('cbPerson.name.nullable')
        rejected(required, person, null).codes.contains(CbPerson.name + '.name.nullable.error')
        rejected(required, person, null).defaultMessage == ConstrainedProperty.DEFAULT_NULL_MESSAGE
        rejected(required, person, null).arguments == ['name', CbPerson] as Object[]
        validate(optional, person, null).errorCount == 0
        validate(required, person, '').errorCount == 0

        when:
        new NullableConstraint(CbPerson, 'name', 'yes', null)

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Parameter for constraint [nullable] of property [name] of class [class ' + CbPerson.name + '] must be a boolean value'
    }

    void "blank constraints validate through the public api and veto empty strings"() {
        given:
        BlankConstraint notBlank = new BlankConstraint(CbPerson, 'name', false, null)
        CbPerson person = new CbPerson(name: '')

        expect:
        notBlank.name == 'blank'
        !notBlank.blank
        notBlank.parameter == false
        notBlank.supports(String)
        !notBlank.supports(Integer)
        rejected(notBlank, person, '').codes.contains('cbPerson.name.blank')
        rejected(notBlank, person, '   ') != null
        validate(notBlank, person, 'ok').errorCount == 0
        validate(notBlank, person, null).errorCount == 0
        notBlank.validateWithVetoing(person, '', new ValidationErrors(person))
        !notBlank.validateWithVetoing(person, null, new ValidationErrors(person))
        !new BlankConstraint(CbPerson, 'name', true, null).validateWithVetoing(person, '', new ValidationErrors(person))

        when:
        new BlankConstraint(CbPerson, 'name', 1, null)

        then:
        thrown(IllegalArgumentException)
    }

    void "blank values are skipped by ordinary constraints"() {
        given:
        EmailConstraint email = new EmailConstraint(CbPerson, 'email', true, null)
        CbPerson person = new CbPerson()

        expect:
        validate(email, person, '').errorCount == 0
        validate(email, person, null).errorCount == 0
        validate(email, person, 'not-an-email').errorCount == 1
    }

    @Unroll
    void "#type constraints accept #good and reject #bad"() {
        given:
        AbstractConstraint constraint = type.newInstance(CbPerson, 'name', parameter, null)
        CbPerson person = new CbPerson()

        expect:
        constraint.name == name
        validate(constraint, person, good).errorCount == 0
        rejected(constraint, person, bad).codes.contains('cbPerson.name.' + code)
        rejected(constraint, person, bad).codes.contains(code)
        rejected(constraint, person, bad).defaultMessage == ConstrainedProperty.DEFAULT_MESSAGES[messageCode]

        where:
        type                 | parameter                    | name         | good                   | bad                | code                 | messageCode
        EmailConstraint      | true                         | 'email'      | 'a@b.com'              | 'nope'             | 'email.invalid'      | 'default.invalid.email.message'
        CreditCardConstraint | true                         | 'creditCard' | '4111111111111111'     | '1234'             | 'creditCard.invalid' | 'default.invalid.creditCard.message'
        UrlConstraint        | true                         | 'url'        | 'https://grails.org'   | 'nope'             | 'url.invalid'        | 'default.invalid.url.message'
        UrlConstraint        | 'localhost'                  | 'url'        | 'http://localhost/x'   | 'http://nope.local' | 'url.invalid'       | 'default.invalid.url.message'
        UrlConstraint        | ['intra', 'net']             | 'url'        | 'http://intra/x'       | 'http://nope.local' | 'url.invalid'       | 'default.invalid.url.message'
        MatchesConstraint    | '[a-z]+'                     | 'matches'    | 'abc'                  | 'ABC'              | 'matches.invalid'    | 'default.doesnt.match.message'
        InListConstraint     | ['a', 'b']                   | 'inList'     | 'a'                    | 'c'                | 'not.inList'         | 'default.not.inlist.message'
        NotEqualConstraint   | 'taken'                      | 'notEqual'   | 'free'                 | 'taken'            | 'notEqual'           | 'default.not.equal.message'
        MinSizeConstraint    | 2                            | 'minSize'    | 'ab'                   | 'a'                | 'minSize.notmet'     | 'default.invalid.min.size.message'
        MaxSizeConstraint    | 2                            | 'maxSize'    | 'ab'                   | 'abc'              | 'maxSize.exceeded'   | 'default.invalid.max.size.message'
        SizeConstraint       | 2..3                         | 'size'       | 'ab'                   | 'a'                | 'size.toosmall'      | 'default.invalid.size.message'
        SizeConstraint       | 2..3                         | 'size'       | 'abc'                  | 'abcd'             | 'size.toobig'        | 'default.invalid.size.message'
    }

    void "email and credit card constraints do nothing when switched off"() {
        given:
        CbPerson person = new CbPerson()

        expect:
        validate(new EmailConstraint(CbPerson, 'name', false, null), person, 'nope').errorCount == 0
        validate(new CreditCardConstraint(CbPerson, 'name', false, null), person, 'nope').errorCount == 0
        new EmailConstraint(CbPerson, 'name', true, null).supports(String)
        !new EmailConstraint(CbPerson, 'name', true, null).supports(Integer)
        new CreditCardConstraint(CbPerson, 'name', true, null).supports(String)
        new MatchesConstraint(CbPerson, 'name', 'x', null).supports(String)
        new MatchesConstraint(CbPerson, 'name', 'x', null).regex == 'x'
        new InListConstraint(CbPerson, 'name', ['x'], null).supports(Object)
        new InListConstraint(CbPerson, 'name', ['x'], null).list == ['x']
        new NotEqualConstraint(CbPerson, 'name', 'x', null).supports(Object)
        new NotEqualConstraint(CbPerson, 'name', 'x', null).notEqualTo == 'x'
        new UrlConstraint(CbPerson, 'name', true, null).supports(String)
        new UrlConstraint(CbPerson, 'name', true, null).parameter instanceof UrlValidator
        new UrlConstraint(CbPerson, 'name', new UrlValidator(), null).parameter instanceof UrlValidator
    }

    @Unroll
    void "#type rejects parameter #parameter"() {
        when:
        type.newInstance(CbPerson, property, parameter, null)

        then:
        IllegalArgumentException e = thrown()
        e.message == message

        where:
        type               | property | parameter | message
        EmailConstraint    | 'name'   | 'x'       | 'Parameter for constraint [email] of property [name] of class [class ' + CbPerson.name + '] must be a boolean value'
        CreditCardConstraint | 'name' | 'x'       | 'Parameter for constraint [creditCard] of property [name] of class [class ' + CbPerson.name + '] must be a boolean value'
        UrlConstraint      | 'name'   | 1         | 'Parameter for constraint [url] of property [name] of class [class ' + CbPerson.name + '] must be a boolean, string, or list value'
        MatchesConstraint  | 'name'   | 1         | 'Parameter for constraint [matches] of property [name] of class [class ' + CbPerson.name + '] must be of type [CharSequence]'
        InListConstraint   | 'name'   | 'a'       | 'Parameter for constraint [inList] of property [name] of class [class ' + CbPerson.name + '] must implement the interface [java.util.List]'
        NotEqualConstraint | 'name'   | null      | 'Parameter for constraint [notEqual] of property [name] of class [class ' + CbPerson.name + '] cannot be null'
        NotEqualConstraint | 'age'    | 'x'       | 'Parameter for constraint [notEqual] of property [age] of class [class ' + CbPerson.name + '] must be the same type as property: [java.lang.Integer]'
        MinConstraint      | 'age'    | null      | 'Parameter for constraint [min] of property [age] of class [class ' + CbPerson.name + '] cannot be null'
        MinConstraint      | 'age'    | new Object() | 'Parameter for constraint [min] of property [age] of class [class ' + CbPerson.name + '] must implement the interface [java.lang.Comparable]'
        MinConstraint      | 'age'    | 'x'       | 'Parameter for constraint [min] of property [age] of class [class ' + CbPerson.name + '] must be the same type as property: [java.lang.Integer]'
        MaxConstraint      | 'age'    | null      | 'Parameter for constraint [max] of property [age] of class [class ' + CbPerson.name + '] cannot be null'
        MaxConstraint      | 'age'    | new Object() | 'Parameter for constraint [max] of property [age] of class [class ' + CbPerson.name + '] must implement the interface [java.lang.Comparable]'
        MaxConstraint      | 'age'    | 'x'       | 'Parameter for constraint [max] of property [age] of class [class ' + CbPerson.name + '] must be the same type as property: [java.lang.Integer]'
        MinSizeConstraint  | 'name'   | 'x'       | 'Parameter for constraint [minSize] of property [name] of class [class ' + CbPerson.name + '] must be a of type [java.lang.Number]'
        MaxSizeConstraint  | 'name'   | 'x'       | 'Parameter for constraint [maxSize] of property [name] of class [class ' + CbPerson.name + '] must be a of type [java.lang.Number]'
        SizeConstraint     | 'name'   | 3         | 'Parameter for constraint [size] of property [name] of class [class ' + CbPerson.name + '] must be a of type [groovy.lang.IntRange]'
        RangeConstraint    | 'age'    | 3         | 'Parameter for constraint [range] of property [age] of class [class ' + CbPerson.name + '] must be a of type [groovy.lang.Range]'
        ScaleConstraint    | 'price'  | 'x'       | 'Parameter for constraint [scale] of property [price] of class [class ' + CbPerson.name + '] must be a of type [java.lang.Integer]'
        ScaleConstraint    | 'price'  | -1        | 'Parameter for constraint [scale] of property [price] of class [class ' + CbPerson.name + '] must have a nonnegative value'
        ValidatorConstraint | 'name'  | 'x'       | 'Parameter for constraint [validator] of property [name] of class [class ' + CbPerson.name + '] must be a Closure'
        ValidatorConstraint | 'name'  | { a, b, c, d -> true } | 'Parameter for constraint [validator] of property [name] of class [class ' + CbPerson.name + '] must be a Closure taking no more than 3 parameters (value, [object, [errors]])'
    }

    void "min and max constraints compare comparables"() {
        given:
        MinConstraint min = new MinConstraint(CbPerson, 'age', 18, null)
        MaxConstraint max = new MaxConstraint(CbPerson, 'age', 65, null)
        CbPerson person = new CbPerson()

        expect:
        min.minValue == 18
        max.maxValue == 65
        min.supports(Integer)
        min.supports(int)
        min.supports(String)
        !min.supports(Object)
        max.supports(Long)
        validate(min, person, 18).errorCount == 0
        validate(max, person, 65).errorCount == 0
        rejected(min, person, 17).codes.contains('cbPerson.age.min.notmet')
        rejected(min, person, 17).arguments == ['age', CbPerson, 17, 18] as Object[]
        rejected(max, person, 66).codes.contains('cbPerson.age.max.exceeded')
        rejected(max, person, 66).arguments == ['age', CbPerson, 66, 65] as Object[]
    }

    void "size constraints measure strings, collections and arrays"() {
        given:
        MinSizeConstraint minSize = new MinSizeConstraint(CbPerson, 'tags', 2, null)
        MaxSizeConstraint maxSize = new MaxSizeConstraint(CbPerson, 'tags', 2, null)
        SizeConstraint size = new SizeConstraint(CbPerson, 'tags', 1..2, null)
        CbPerson person = new CbPerson()

        expect:
        minSize.minSize == 2
        maxSize.maxSize == 2
        size.range == 1..2
        [minSize, maxSize, size].every { it.supports(String) && it.supports(List) && it.supports(String[]) && !it.supports(Integer) }
        validate(minSize, person, ['a', 'b']).errorCount == 0
        validate(minSize, person, ['a'] as String[]).errorCount == 1
        validate(maxSize, person, ['a', 'b', 'c'] as String[]).errorCount == 1
        validate(maxSize, person, ['a', 'b']).errorCount == 0
        validate(size, person, ['a', 'b']).errorCount == 0
        validate(size, person, [] as String[]).errorCount == 1
        rejected(size, person, ['a', 'b', 'c']).arguments == ['tags', CbPerson, ['a', 'b', 'c'], 1, 2] as Object[]
    }

    void "range constraints normalise numbers and strings"() {
        given:
        RangeConstraint range = new RangeConstraint(CbPerson, 'age', 10..20, null)
        RangeConstraint letters = new RangeConstraint(CbPerson, 'name', 'b'..'d', null)
        CbPerson person = new CbPerson()

        expect:
        range.range == 10..20
        range.supports(Integer)
        range.supports(String)
        !range.supports(Object)
        validate(range, person, 15).errorCount == 0
        validate(range, person, 15L).errorCount == 0
        validate(range, person, '15').errorCount == 0
        rejected(range, person, 9).codes.contains('cbPerson.age.range.toosmall')
        rejected(range, person, 21L).codes.contains('cbPerson.age.range.toobig')
        rejected(range, person, '21').codes.contains('cbPerson.age.range.toobig')
        validate(letters, person, 'c').errorCount == 0
        rejected(letters, person, 'a').codes.contains('cbPerson.name.range.toosmall')
        rejected(letters, person, 'z').codes.contains('cbPerson.name.range.toobig')

        when: 'a non numeric string is rejected as invalid before the comparison fails'
        validate(range, person, 'abc')

        then:
        thrown(ClassCastException)
    }

    void "scale constraints round the value on the target bean"() {
        given:
        ScaleConstraint scale = new ScaleConstraint(CbPerson, 'price', 2, null)
        ScaleConstraint ratioScale = new ScaleConstraint(CbPerson, 'ratio', 1, null)
        ScaleConstraint factorScale = new ScaleConstraint(CbPerson, 'factor', 1, null)
        CbPerson person = new CbPerson(price: 1.23456G, ratio: 1.25d, factor: 1.25f)

        expect:
        scale.scale == 2
        scale.name == 'scale'
        scale.supports(BigDecimal)
        scale.supports(Float)
        scale.supports(double)
        !scale.supports(Integer)

        when:
        validate(scale, person, person.price)
        validate(ratioScale, person, person.ratio)
        validate(factorScale, person, person.factor)

        then:
        person.price == 1.23G
        person.ratio == 1.3d
        person.factor == 1.3f

        when:
        person.price = 1.2G
        validate(scale, person, person.price)

        then:
        person.price == 1.2G

        when:
        validate(scale, person, 12)

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Unsupported type detected in constraint [scale] of property [price] of class [class ' + CbPerson.name + ']'
    }

    void "validator constraints interpret the closure result"() {
        given:
        CbPerson person = new CbPerson(name: 'target')

        expect:
        new ValidatorConstraint(CbPerson, 'name', { it }, null).supports(Object)
        new ValidatorConstraint(CbPerson, 'name', { it }, null).name == 'validator'
        validate(new ValidatorConstraint(CbPerson, 'name', { true }, null), person, 'x').errorCount == 0
        validate(new ValidatorConstraint(CbPerson, 'name', { null }, null), person, 'x').errorCount == 0
        validate(new ValidatorConstraint(CbPerson, 'name', { false }, null), person, null).errorCount == 1
        validate(new ValidatorConstraint(CbPerson, 'name', { false }, null), person, '').errorCount == 1
        rejected(new ValidatorConstraint(CbPerson, 'name', { false }, null), person, 'x').codes.contains('cbPerson.name.validator.invalid')
        rejected(new ValidatorConstraint(CbPerson, 'name', { 'custom.code' }, null), person, 'x').codes.contains('cbPerson.name.custom.code')
        rejected(new ValidatorConstraint(CbPerson, 'name', { ['list.code', 'extra'] }, null), person, 'x').arguments == ['name', CbPerson, 'x', 'extra'] as Object[]
        rejected(new ValidatorConstraint(CbPerson, 'name', { ['array.code'] as Object[] }, null), person, 'x').codes.contains('cbPerson.name.array.code')
        rejected(new ValidatorConstraint(CbPerson, 'name', { val, obj -> obj.is(person) && val == 'x' ? false : true }, null), person, 'x') != null
        rejected(new ValidatorConstraint(CbPerson, 'name', { val, obj -> propertyName == 'name' ? 'delegate.seen' : 'nope' }, null), person, 'x').codes.contains('cbPerson.name.delegate.seen')

        when:
        ValidationErrors errors = new ValidationErrors(person, CbPerson.name)
        new ValidatorConstraint(CbPerson, 'name', { val, obj, errs -> errs.rejectValue('name', 'manual'); false }, null).validate(person, 'x', errors)

        then:
        errors.errorCount == 1
        errors.fieldErrors[0].code == 'manual'

        when:
        validate(new ValidatorConstraint(CbPerson, 'name', { [1] }, null), person, 'x')

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Return value from validation closure [validator] of property [name] of class [class ' + CbPerson.name + '] is returning a list but the first element must be a string containing the error message code'

        when:
        validate(new ValidatorConstraint(CbPerson, 'name', { 42 }, null), person, 'x')

        then:
        e = thrown()
        e.message == 'Return value from validation closure [validator] of property [name] of class [class ' + CbPerson.name + '] must be a boolean, a string, an array or a collection'
    }

    void "rejected values resolve class and property labels through the message source"() {
        given:
        StaticMessageSource messageSource = new StaticMessageSource()
        messageSource.addMessage('cbPerson.label', LocaleContextHolder.locale, 'Person')
        messageSource.addMessage('cbPerson.name.label', LocaleContextHolder.locale, 'Full name')
        messageSource.addMessage('default.invalid.email.message', LocaleContextHolder.locale, 'Bad email')
        EmailConstraint constraint = new EmailConstraint(CbPerson, 'name', true, messageSource)
        CbPerson person = new CbPerson(name: 'nope')

        when:
        FieldError error = rejected(constraint, person, 'nope')

        then:
        error.arguments == ['Full name', 'Person', 'nope'] as Object[]
        error.defaultMessage == 'Bad email'
        error.rejectedValue == 'nope'
        error.codes[0] == CbPerson.name + '.name.email.error.' + CbPerson.name + '.name'
        error.codes.contains('email.invalid')

        when:
        messageSource.addMessage(CbPerson.name + '.label', LocaleContextHolder.locale, 'Qualified')
        messageSource.addMessage(CbPerson.name + '.name.label', LocaleContextHolder.locale, 'Qualified name')
        error = rejected(constraint, person, 'nope')

        then:
        error.arguments == ['Qualified name', 'Qualified', 'nope'] as Object[]
    }

    void "constraints fall back to bundled messages when the message source fails"() {
        given:
        MessageSource failing = Stub(MessageSource) {
            getMessage(_, _, _) >> { throw new IllegalStateException('no messages') }
        }
        NullableConstraint constraint = new NullableConstraint(CbPerson, 'name', false, failing)
        CbPerson person = new CbPerson()

        expect:
        rejected(constraint, person, null).defaultMessage == ConstrainedProperty.DEFAULT_NULL_MESSAGE
        constraint.toString().contains('false')
        constraint.valid
        constraint.propertyName == 'name'
    }

    void "the backwards compatible reject methods and nested property values are supported"() {
        given:
        NullableConstraint constraint = new NullableConstraint(CbPerson, 'address.city', false, null)
        CbPerson person = new CbPerson(address: new CbAddress(city: 'Cork'))
        ValidationErrors errors = new ValidationErrors(person, CbPerson.name)

        when:
        constraint.rejectValue(person, errors, 'legacy.args', ['a'] as Object[], 'Legacy with args')
        constraint.rejectValue(person, errors, 'default.null.message', ['x', 'y'] as Object[])

        then:
        errors.errorCount == 2
        errors.fieldErrors[0].codes.contains('cbPerson.address.city.legacy.args')
        errors.fieldErrors[0].defaultMessage == 'Legacy with args'
        errors.fieldErrors[0].rejectedValue == 'Cork'
        errors.fieldErrors[0].arguments == ['a'] as Object[]
        errors.fieldErrors[1].defaultMessage == ConstrainedProperty.DEFAULT_NULL_MESSAGE
        errors.fieldErrors[1].codes.contains(CbPerson.name + '.address.city.nullable.error')

        when: 'the argument-less legacy overload cannot be used because it passes null arguments'
        constraint.rejectValue(person, errors, 'legacy.code', 'Legacy message')

        then:
        thrown(NullPointerException)

        when:
        NullableConstraint invalid = new NullableConstraint(CbPerson, '', false, null)
        invalid.validate(person, null, errors)

        then:
        thrown(IllegalArgumentException)
    }

}

class CbPerson {

    String name
    String email
    Integer age
    List<String> tags
    BigDecimal price
    Double ratio
    Float factor
    CbAddress address

}

class CbAddress {

    String city

}
