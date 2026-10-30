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
package org.grails.datastore.gorm.validation.support

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.gorm.validation.jakarta.GormValidatorAdapter
import org.hibernate.validator.group.GroupSequenceProvider
import org.hibernate.validator.spi.group.DefaultGroupSequenceProvider
import org.springframework.validation.BeanPropertyBindingResult
import spock.lang.Specification

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.GroupSequence
import jakarta.validation.Payload
import jakarta.validation.Valid
import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.groups.ConvertGroup
import jakarta.validation.groups.Default

/**
 * Created by graemerocher on 30/12/2016.
 */
class GormValidatorAdapterSpec extends Specification {


    void "test propagate jakarta.valdiation errors to gorm object"() {

        given:
        def factory = Validation.byDefaultProvider().configure().buildValidatorFactory()

        Validator v = factory.getValidator()
        def adapter = new GormValidatorAdapter(v)


        when:
        def product = new Product(price: "foo")
        adapter.validate(product)

        then:
        adapter.forExecutables()
        product.errors.hasErrors()
        product.errors.getFieldError('price')
    }

    void 'a class level violation of the validated object is a global error'() {
        given:
        def adapter = new GormValidatorAdapter(Validation.buildDefaultValidatorFactory().validator)
        def range = new PriceRange(low: 5, high: 1, currency: 'EUR')
        def errors = new BeanPropertyBindingResult(range, 'range')

        when:
        adapter.validate(range, errors)

        then:
        errors.errorCount == 1
        errors.globalErrors*.code == ['OrderedRange']
    }

    void 'a class level violation under a nested path is an error of the path, as when Bean Validation cascades through @Valid'() {
        given: 'a quote whose range violates its class level constraint and a property annotation'
        def adapter = new GormValidatorAdapter(Validation.buildDefaultValidatorFactory().validator)
        def quote = new Quote(range: new PriceRange(low: 5, high: 1))

        and: 'the errors of Bean Validation cascading from the quote to the range'
        def cascaded = new BeanPropertyBindingResult(quote, 'quote')
        adapter.validate(quote, cascaded)

        when: 'the range is validated under the nested path of the errors of the quote'
        def errors = new BeanPropertyBindingResult(quote, 'quote')
        errors.pushNestedPath('range')
        adapter.validate(quote.range, errors)
        errors.popNestedPath()

        then: 'the class level violation is reported on the path, without the separator'
        errors.globalErrorCount == 0
        errors.fieldErrors*.field.sort() == ['range', 'range.currency']
        errors.getFieldError('range').code == 'OrderedRange'
        errors.getFieldError('range').rejectedValue.is(quote.range)
        errors.getFieldError('range.currency').code == 'NotNull'

        and: 'with the codes, arguments and message of the cascaded error'
        def error = errors.getFieldError('range')
        def expected = cascaded.getFieldError('range')
        error.codes == expected.codes
        error.arguments == expected.arguments
        error.defaultMessage == expected.defaultMessage
    }

    void 'only the violations of the reported groups are reported'() {
        given: 'a validation in ExtendedGroup, which extends BaseGroup, reporting ExtendedGroup alone'
        def adapter = new GormValidatorAdapter(Validation.buildDefaultValidatorFactory().validator)
        def stamp = new Stamp(retired: 'yes', note: '')
        def errors = new BeanPropertyBindingResult(stamp, 'stamp')

        when:
        adapter.validate(stamp, errors, false, [ExtendedGroup] as Class<?>[], [ExtendedGroup] as Set<Class<?>>)

        then: 'the violation of BaseGroup, which the provider validates with ExtendedGroup, is left out'
        errors.fieldErrors*.field == ['note']
        errors.getFieldError('note').code == 'NotBlank'

        when: 'every violation is reported'
        errors = new BeanPropertyBindingResult(stamp, 'stamp')
        adapter.validate(stamp, errors, false, [ExtendedGroup] as Class<?>[], null)

        then:
        errors.fieldErrors*.field.sort() == ['note', 'retired']
    }

    void 'the reported groups of a bean the provider cascades to are converted along the path of the violation'() {
        given: 'a stamp validated in ExtendedGroup reporting it alone, whose details convert ExtendedGroup to DetailGroup'
        def adapter = new GormValidatorAdapter(Validation.buildDefaultValidatorFactory().validator)
        def stamp = new Stamp(note: 'ok', details: new StampDetails(summary: '', draft: 'yes'))
        def errors = new BeanPropertyBindingResult(stamp, 'stamp')

        expect: 'the provider finds a violation of DetailGroup through ExtendedGroup and one of BaseGroup through BaseGroup'
        adapter.validate(stamp, [ExtendedGroup] as Class<?>[])*.propertyPath*.toString().sort() == ['details.draft', 'details.summary']

        when:
        adapter.validate(stamp, errors, false, [ExtendedGroup] as Class<?>[], [ExtendedGroup] as Set<Class<?>>)

        then: 'the violation of DetailGroup is reported, and the one of BaseGroup, which is not reported, is not'
        errors.fieldErrors*.field == ['details.summary']
        errors.getFieldError('details.summary').code == 'NotBlank'

        when: 'BaseGroup is reported too'
        errors = new BeanPropertyBindingResult(stamp, 'stamp')
        adapter.validate(stamp, errors, false, [ExtendedGroup] as Class<?>[], [ExtendedGroup, BaseGroup] as Set<Class<?>>)

        then:
        errors.fieldErrors*.field.sort() == ['details.draft', 'details.summary']
    }

    void 'the reported groups follow the class of a bean the provider cascades to, not the declared type of the property'() {
        given: 'a stamp validated in ExtendedGroup reporting it alone, whose attachment, declared as an Object, converts ExtendedGroup to DetailGroup for its details'
        def adapter = new GormValidatorAdapter(Validation.buildDefaultValidatorFactory().validator)
        def stamp = new Stamp(note: 'ok', attachment: new StampAttachment(details: new StampDetails(summary: '', draft: 'yes')))
        def errors = new BeanPropertyBindingResult(stamp, 'stamp')

        expect: 'the provider finds a violation of DetailGroup through ExtendedGroup and one of BaseGroup through BaseGroup'
        adapter.validate(stamp, [ExtendedGroup] as Class<?>[])*.propertyPath*.toString().sort() == ['attachment.details.draft', 'attachment.details.summary']

        when:
        adapter.validate(stamp, errors, false, [ExtendedGroup] as Class<?>[], [ExtendedGroup] as Set<Class<?>>)

        then: 'the violation of DetailGroup, converted by the class of the attachment, is reported, and the one of BaseGroup is not'
        errors.fieldErrors*.field == ['attachment.details.summary']
        errors.getFieldError('attachment.details.summary').code == 'NotBlank'
    }

    void 'the default group of a bean the provider cascades to stands for the sequence its group sequence provider chooses for it'() {
        given: 'a holder validated in the default group reporting it alone, whose entry is validated in EntryChecks when it is checked'
        def adapter = new GormValidatorAdapter(Validation.buildDefaultValidatorFactory().validator)
        def holder = new ProvidedHolder(entry: new ProvidedEntry(checked: true, code: null))
        def errors = new BeanPropertyBindingResult(holder, 'holder')

        expect: 'the provider validates the entry in EntryChecks'
        adapter.validate(holder)*.propertyPath*.toString() == ['entry.code']

        when:
        adapter.validate(holder, errors, false, [Default] as Class<?>[], [Default] as Set<Class<?>>)

        then: 'the violation of the group the provider of the entry chose for it is reported'
        errors.fieldErrors*.field == ['entry.code']
        errors.getFieldError('entry.code').code == 'NotNull'

        when: 'the entry is not checked'
        holder.entry.checked = false
        errors = new BeanPropertyBindingResult(holder, 'holder')
        adapter.validate(holder, errors, false, [Default] as Class<?>[], [Default] as Set<Class<?>>)

        then: 'its provider leaves EntryChecks out'
        !errors.hasErrors()
    }

    void 'a validation started by a constraint validator leaves the cascade flag of the enclosing validation in place'() {
        given: 'a receipt whose issuer a constraint validates with the adapter, cascading'
        def adapter = new GormValidatorAdapter(Validation.buildDefaultValidatorFactory().validator)
        ValidIssuerValidator.adapter = adapter
        ValidIssuerValidator.cascadeFlags.clear()
        def receipt = new Receipt(issuer: new Issuer(code: 'ok'))
        def errors = new BeanPropertyBindingResult(receipt, 'receipt')

        when: 'the receipt is validated without cascading'
        adapter.validate(receipt, errors, false)

        then: 'the flag of the validation of the receipt is the same before and after the one of the issuer'
        !errors.hasErrors()
        ValidIssuerValidator.cascadeFlags == [false, false]
        GormValidatorAdapter.CASCADE_VALIDATION.get() == null

        when: 'the issuer is invalid'
        receipt.issuer.code = ''
        errors = new BeanPropertyBindingResult(receipt, 'receipt')
        adapter.validate(receipt, errors, false)

        then:
        errors.fieldErrors*.field == ['issuer']
        errors.getFieldError('issuer').code == 'ValidIssuer'

        cleanup:
        ValidIssuerValidator.adapter = null
    }

    void 'the default group of a bean stands for the groups of the sequence redefining it'() {
        given: 'a holder validated in the default group reporting it alone, whose entry redefines the default group with a sequence'
        def adapter = new GormValidatorAdapter(Validation.buildDefaultValidatorFactory().validator)
        def holder = new SequencedHolder(entry: new SequencedEntry(code: null))
        def errors = new BeanPropertyBindingResult(holder, 'holder')

        when:
        adapter.validate(holder, errors, false, [Default] as Class<?>[], [Default] as Set<Class<?>>)

        then: 'the violation of the group of the sequence of the entry is reported'
        errors.fieldErrors*.field == ['entry.code']
        errors.getFieldError('entry.code').code == 'NotNull'

        when: 'the entry is validated on its own'
        errors = new BeanPropertyBindingResult(holder.entry, 'entry')
        adapter.validate(holder.entry, errors, false, [Default] as Class<?>[], [Default] as Set<Class<?>>)

        then:
        errors.fieldErrors*.field == ['code']
    }
}

interface BaseGroup {}

interface ExtendedGroup extends BaseGroup {}

interface DetailGroup {}

class Stamp {

    @Null(groups = BaseGroup)
    String retired

    @NotBlank(groups = ExtendedGroup)
    String note

    @Valid
    @ConvertGroup(from = ExtendedGroup, to = DetailGroup)
    StampDetails details

    @Valid
    Object attachment
}

class StampAttachment {

    @Valid
    @ConvertGroup(from = ExtendedGroup, to = DetailGroup)
    StampDetails details
}

class StampDetails {

    @NotBlank(groups = DetailGroup)
    String summary

    @Null(groups = BaseGroup)
    String draft
}

interface EntryChecks {}

class SequencedHolder {

    @Valid
    SequencedEntry entry
}

@GroupSequence([EntryChecks, SequencedEntry])
class SequencedEntry {

    @NotNull(groups = EntryChecks)
    String code
}

class ProvidedHolder {

    @Valid
    ProvidedEntry entry
}

@GroupSequenceProvider(ProvidedEntrySequenceProvider)
class ProvidedEntry {

    boolean checked

    @NotNull(groups = EntryChecks)
    String code
}

class ProvidedEntrySequenceProvider implements DefaultGroupSequenceProvider<ProvidedEntry> {

    @Override
    List<Class<?>> getValidationGroups(Class<?> klass, ProvidedEntry entry) {
        List<Class<?>> sequence = [ProvidedEntry]
        if (entry?.checked) {
            sequence.add(EntryChecks)
        }
        return sequence
    }
}

@Constraint(validatedBy = [ValidIssuerValidator])
@Target([ElementType.FIELD])
@Retention(RetentionPolicy.RUNTIME)
@interface ValidIssuer {

    String message() default 'the issuer is not valid'

    Class<?>[] groups() default []

    Class<? extends Payload>[] payload() default []
}

/**
 * Validates the issuer with the adapter, a validation of its own inside the one of the receipt, recording the cascade
 * flag of the enclosing validation before and after.
 */
class ValidIssuerValidator implements ConstraintValidator<ValidIssuer, Issuer> {

    static GormValidatorAdapter adapter
    static final List<Boolean> cascadeFlags = []

    @Override
    boolean isValid(Issuer issuer, ConstraintValidatorContext context) {
        if (issuer == null) {
            return true
        }
        cascadeFlags.add(GormValidatorAdapter.CASCADE_VALIDATION.get())
        def errors = new BeanPropertyBindingResult(issuer, 'issuer')
        adapter.validate(issuer, errors, true)
        cascadeFlags.add(GormValidatorAdapter.CASCADE_VALIDATION.get())
        return !errors.hasErrors()
    }
}

class Receipt {

    @ValidIssuer
    Issuer issuer
}

class Issuer {

    @NotBlank
    String code
}

class Product implements GormValidateable {
    @Digits(integer = 6, fraction = 2)
    String price
}

@Constraint(validatedBy = [OrderedRangeValidator])
@Target([ElementType.TYPE])
@Retention(RetentionPolicy.RUNTIME)
@interface OrderedRange {

    String message() default 'the low price must not exceed the high price'

    Class<?>[] groups() default []

    Class<? extends Payload>[] payload() default []
}

class OrderedRangeValidator implements ConstraintValidator<OrderedRange, PriceRange> {

    @Override
    boolean isValid(PriceRange range, ConstraintValidatorContext context) {
        range == null || range.low == null || range.high == null || range.low <= range.high
    }
}

@OrderedRange
class PriceRange {

    Integer low

    Integer high

    @NotNull
    String currency
}

class Quote {

    @Valid
    PriceRange range
}
