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

package org.grails.datastore.gorm.validation.jakarta

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import jakarta.validation.Constraint
import jakarta.validation.GroupSequence
import jakarta.validation.OverridesAttribute
import jakarta.validation.Payload
import jakarta.validation.ReportAsSingleViolation
import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

import org.hibernate.validator.constraints.CompositionType
import org.hibernate.validator.constraints.ConstraintComposition
import org.hibernate.validator.constraints.CreditCardNumber
import org.hibernate.validator.constraints.Length
import org.hibernate.validator.constraints.Range
import org.hibernate.validator.constraints.URL
import org.springframework.context.support.StaticMessageSource
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import grails.gorm.validation.ConstrainedProperty
import grails.gorm.validation.DefaultConstrainedProperty
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry

class JakartaConstraintsTranslatorSpec extends Specification {

    @Shared
    Validator validator = Validation.byDefaultProvider().configure().buildValidatorFactory().validator

    ConstraintRegistry registry = new DefaultConstraintRegistry(new StaticMessageSource())
    JakartaConstraintsTranslator translator = translatorFor(TranslatedProduct)

    void "the constraints of the fields, getters and superclasses are found, repeated and composed ones as the provider sees them"() {
        when:
        Map<String, List> descriptors = translator.constraintDescriptors

        then:
        descriptors.keySet() == ['agreed', 'card', 'code', 'contact', 'custom', 'discount', 'inherited', 'launched', 'length', 'name',
                                 'price', 'quantity', 'range', 'rangeValid', 'retired', 'shortCode', 'sku', 'website'] as Set
        descriptors.name*.annotation*.annotationType() as Set == [NotNull, Size] as Set
        descriptors.code*.annotation*.annotationType() == [Pattern, Pattern]
        descriptors.website*.annotation*.annotationType() == [URL]
        descriptors.inherited*.annotation*.annotationType() == [NotBlank]

        and: "a composed constraint carries the constraints composing it, with its attribute overrides applied"
        descriptors.sku*.annotation*.annotationType() == [StrongSku]
        descriptors.sku[0].composingConstraints*.annotation*.annotationType() as Set == [NotBlank, Size] as Set
        descriptors.shortCode*.annotation*.annotationType() == [ShortCode]
        ((Size) descriptors.shortCode[0].composingConstraints.find { it.annotation instanceof Size }.annotation).max() == 7
    }

    void "a constraint of another group than the default one is not validated by validate(), so it is left out"() {
        expect:
        !translator.constraintDescriptors.containsKey('reviewer')
        !translator.nullabilityConstrainedProperties.contains('reviewer')
        !translator.translate([:], registry).containsKey('reviewer')
    }

    void "the groups of the sequence a class redefines the default group with are described"() {
        given:
        JakartaConstraintsTranslator sequenced = translatorFor(SequencedProduct)

        expect:
        sequenced.constraintDescriptors.keySet() == ['name', 'quantity'] as Set
        sequenced.nullabilityConstrainedProperties == ['name'] as Set
        !sequenced.translate([:], registry).name.nullable
        sequenced.translate([:], registry).quantity.min == 1
        !sequenced.translate([:], registry).containsKey('reviewer')
    }

    void "the groups extended by a group of the sequence and the groups of a nested sequence are described, as the provider validates them"() {
        given:
        JakartaConstraintsTranslator inheriting = translatorFor(InheritingSequencedProduct)

        expect: "validate() runs the group extended by ChildChecks, then the group of the nested sequence, then the Default group"
        validator.validate(new InheritingSequencedProduct(retired: 'yes', name: 'n', code: 'ok', quantity: 1))*.propertyPath*.toString() == ['retired']
        validator.validate(new InheritingSequencedProduct(name: 'n', code: 'toolong', quantity: 1))*.propertyPath*.toString() == ['code']
        validator.validate(new InheritingSequencedProduct(name: 'n', code: 'ok', quantity: 0))*.propertyPath*.toString() == ['quantity']

        and: "so the translator describes the same constraints"
        inheriting.constraintDescriptors.keySet() == ['code', 'name', 'quantity', 'retired'] as Set
        inheriting.nullabilityConstrainedProperties == ['name', 'retired'] as Set
        Map<String, ConstrainedProperty> merged = inheriting.translate([:], registry)
        !merged.name.nullable
        merged.retired.nullable
        merged.code.maxSize == 5
        merged.quantity.min == 1
        !merged.containsKey('reviewer')
    }

    void "a @Null of a group extended by a group of the sequence relaxes the nullable constraint of the block"() {
        given:
        DefaultConstrainedProperty retired = constrained('retired', String, [nullable: false])
        DefaultConstrainedProperty reviewer = constrained('reviewer', String, [nullable: false])

        when:
        translatorFor(InheritingSequencedProduct).delegateNullabilityToBeanValidation([retired: retired, reviewer: reviewer])

        then:
        retired.nullable
        !reviewer.nullable
    }

    void "the sequence a superclass redefines the default group with applies to its constraints, not to the ones of a subclass"() {
        given:
        JakartaConstraintsTranslator inheriting = translatorFor(SequenceInheritingProduct)

        expect: "the provider validates the sequence of the superclass for its constraints only"
        validator.validate(new SequenceInheritingProduct(name: 'n', retired: 'yes', reviewer: 'r', code: 'toolong'))*.propertyPath*.toString() == ['retired']
        validator.validate(new SequenceInheritingProduct(name: 'n', code: 'toolong', label: 'long')).empty

        and: "so the translator describes the same constraints"
        inheriting.constraintDescriptors.keySet() == ['name', 'retired'] as Set
        inheriting.nullabilityConstrainedProperties == ['name', 'retired'] as Set
        Map<String, ConstrainedProperty> merged = inheriting.translate([:], registry)
        !merged.name.nullable
        merged.retired.nullable
        !merged.containsKey('code')
        !merged.containsKey('label')
        !merged.containsKey('reviewer')
    }

    void "the sequence a subclass redefines the default group with applies to its constraints and to the ones of its superclasses"() {
        given:
        JakartaConstraintsTranslator redefining = translatorFor(SequenceRedefiningProduct)

        expect: "the provider validates the sequence of the subclass, not the one of the superclass"
        validator.validate(new SequenceRedefiningProduct(retired: 'yes', code: 'toolong', label: 'long'))*.propertyPath*.toString() as Set == ['code', 'label'] as Set

        and: "so the translator describes the same constraints"
        redefining.constraintDescriptors.keySet() == ['code', 'label'] as Set
        redefining.nullabilityConstrainedProperties.empty
        Map<String, ConstrainedProperty> merged = redefining.translate([:], registry)
        merged.code.maxSize == 5
        merged.label.maxSize == 3
        !merged.containsKey('name')
        !merged.containsKey('retired')
    }

    void "the properties whose nullability a constraint of given groups declares are found, and their nullable constraint relaxed"() {
        given:
        DefaultConstrainedProperty name = constrained('name', String, [nullable: false])
        DefaultConstrainedProperty reviewer = constrained('reviewer', String, [nullable: false])

        expect:
        translator.findNullabilityConstrainedProperties(OnReview) == ['reviewer'] as Set
        translator.findNullabilityConstrainedProperties() == translator.nullabilityConstrainedProperties
        translator.findNullabilityConstrainedProperties(jakarta.validation.groups.Default) == translator.nullabilityConstrainedProperties

        when:
        translator.delegateNullabilityToBeanValidation([name: name, reviewer: reviewer], OnReview)

        then: "a @NotNull of the default group leaves the block in force when the default group is not validated"
        reviewer.nullable
        !name.nullable
    }

    @Unroll
    void "a decimal bound on #propertyName is described as the nearest whole number it admits"() {
        given:
        Map<String, ConstrainedProperty> merged = translatorFor(RoundedProduct).translate([:], registry)

        expect:
        merged[propertyName].min == min
        merged[propertyName].max == max

        and: "the provider admits the bounds and nothing beyond them"
        validator.validate(new RoundedProduct((propertyName): min)).empty
        validator.validate(new RoundedProduct((propertyName): max)).empty
        !validator.validate(new RoundedProduct((propertyName): min - 1)).empty
        !validator.validate(new RoundedProduct((propertyName): max + 1)).empty

        where:
        propertyName | min | max
        'inclusive'  | 2   | 4
        'exclusive'  | 2   | 4
        'negative'   | -1L | -1L
        'whole'      | 2L  | 4L
    }

    void "the properties whose nullability is declared with a constraint of the default group are found, composed constraints included"() {
        expect:
        translator.nullabilityConstrainedProperties == ['contact', 'inherited', 'name', 'retired', 'shortCode', 'sku'] as Set
    }

    void "the GORM nullable constraint is relaxed for the properties Bean Validation checks for null"() {
        given:
        DefaultConstrainedProperty name = constrained('name', String, [nullable: false])
        DefaultConstrainedProperty quantity = constrained('quantity', Integer, [nullable: false])
        DefaultConstrainedProperty retired = constrained('retired', String, [nullable: false])
        DefaultConstrainedProperty reviewer = constrained('reviewer', String, [nullable: false])
        Map<String, ConstrainedProperty> constraints = [name: name, quantity: quantity, retired: retired, reviewer: reviewer]

        when:
        translator.delegateNullabilityToBeanValidation(constraints)

        then:
        name.nullable
        retired.nullable
        !quantity.nullable

        and: "a @NotNull of another group leaves the block in force"
        !reviewer.nullable
    }

    void "the constraints are described on a copy of the GORM constraints"() {
        given:
        DefaultConstrainedProperty name = constrained('name', String, [nullable: true, maxSize: 4])
        name.addMetaConstraint('example', 'Widget')
        Map<String, ConstrainedProperty> original = [name: name]

        when:
        Map<String, ConstrainedProperty> merged = translator.translate(original, registry)

        then: "the original constraints are untouched"
        !merged.is(original)
        !merged.name.is(name)
        name.nullable
        name.minSize == null
        original.size() == 1

        and: "the annotations tighten the copy"
        !merged.name.nullable
        merged.name.minSize == 3
        merged.name.maxSize == 4
        ((DefaultConstrainedProperty) merged.name).getMetaConstraintValue('example') == 'Widget'

        and: "properties without GORM constraints are described too"
        merged.code.matches in ['(?i)[a-z]+', '.{3}']
        !merged.sku.nullable
        !merged.sku.blank
        merged.sku.minSize == 8
        merged.website.url
        merged.card.creditCard
        merged.length.minSize == 2
        merged.length.maxSize == 3
        merged.range.min == 1
        merged.range.max == 5
        merged.quantity.min == 1
        merged.quantity.max == 100
        merged.price.min == 1.5G
        merged.discount.min == null
        merged.contact.email
        merged.contact.matches == '.+@example\\.com'
        merged.agreed.inList == [true]
        merged.retired.nullable
        !merged.inherited.nullable

        and: "annotations without an equivalent and custom constraints are left out"
        merged.launched.appliedConstraints.empty
        merged.custom.appliedConstraints.empty

        and: "a getter without a backing field is validated but not described"
        !merged.containsKey('rangeValid')
    }

    void "a getter without a backing field is described when the constraints block constrains it"() {
        given:
        DefaultConstrainedProperty rangeValid = constrained('rangeValid', Boolean, [nullable: true])

        when:
        Map<String, ConstrainedProperty> merged = translator.translate([rangeValid: rangeValid], registry)

        then:
        merged.rangeValid.inList == [true]
    }

    void "a composed constraint overriding an attribute of a constraint composing it is described as enforced"() {
        when:
        Map<String, ConstrainedProperty> merged = translator.translate([:], registry)

        then: "@ShortCode(max = 7) overrides Size.max, whose declared default is 10"
        merged.sku.minSize == 8
        merged.shortCode.maxSize == 7
        !merged.shortCode.nullable
    }

    void "the constraints composing an OR or ALL_FALSE composition do not each apply, so they are not described"() {
        given:
        JakartaConstraintsTranslator composed = translatorFor(ComposedProduct)
        Map<String, ConstrainedProperty> merged = composed.translate([:], registry)

        expect: "the provider accepts a short and a long code, and rejects one in between"
        validator.validate(new ComposedProduct(code: 'ab', tag: 'long enough')).empty
        validator.validate(new ComposedProduct(code: 'abcdefghijkl', tag: 'long enough')).empty
        validator.validate(new ComposedProduct(code: 'abcde', tag: 'long enough'))*.propertyPath*.toString() == ['code']

        and: "so neither bound is described, as the schema would otherwise reject a code the provider accepts"
        merged.code.minSize == null
        merged.code.maxSize == null

        and: "a null and a long note are accepted, so the @Null composing the note's constraint does not decide its nullability"
        validator.validate(new ComposedProduct(code: 'ab', tag: 'long enough')).empty
        validator.validate(new ComposedProduct(code: 'ab', tag: 'long enough', note: 'long enough')).empty
        validator.validate(new ComposedProduct(code: 'ab', tag: 'long enough', note: 'ab'))*.propertyPath*.toString() == ['note']
        !composed.nullabilityConstrainedProperties.contains('note')
        merged.note.minSize == null

        and: "an ALL_FALSE composition rejects what its constraint accepts, so the bound is not described either"
        validator.validate(new ComposedProduct(code: 'ab', tag: 'abc'))*.propertyPath*.toString() == ['tag']
        merged.tag.maxSize == null
    }

    void "a class without constraints gets its constraints back untouched"() {
        given:
        Map<String, ConstrainedProperty> original = [:]

        expect:
        translatorFor(Object).translate(original, registry).is(original)
    }

    void "the flags of a pattern become an embedded flag expression"() {
        expect:
        JakartaConstraintsTranslator.withInlineFlags('[a-z]+', [Pattern.Flag.CASE_INSENSITIVE, Pattern.Flag.COMMENTS] as Pattern.Flag[]) == '(?ix)[a-z]+'
        JakartaConstraintsTranslator.withInlineFlags('[a-z]+', [Pattern.Flag.CANON_EQ] as Pattern.Flag[]) == '[a-z]+'
        JakartaConstraintsTranslator.withInlineFlags('[a-z]+', new Pattern.Flag[0]) == '[a-z]+'
    }

    private JakartaConstraintsTranslator translatorFor(Class<?> clazz) {
        new JakartaConstraintsTranslator(validator, clazz)
    }

    private DefaultConstrainedProperty constrained(String name, Class type, Map<String, Object> constraints) {
        DefaultConstrainedProperty constrained = new DefaultConstrainedProperty(TranslatedProduct, name, type, registry)
        constraints.each { String constraintName, Object value -> constrained.applyConstraint(constraintName, value) }
        return constrained
    }
}

interface OnReview {}

interface Basics {}

@SuppressWarnings('unused')
class TranslatedProductBase {
    @NotBlank String inherited
}

@SuppressWarnings('unused')
class TranslatedProduct extends TranslatedProductBase {
    @NotNull @Size(min = 3) String name
    @Pattern(regexp = '[a-z]+', flags = Pattern.Flag.CASE_INSENSITIVE) @Pattern(regexp = '.{3}') String code
    @ShortCode(max = 7) String shortCode
    @StrongSku String sku
    String website
    @Past Date launched
    @Null String retired
    @CreditCardNumber String card
    @Length(min = 2, max = 3) String length
    @Range(min = 1L, max = 5L) Integer range
    @Min(1L) @Max(100L) Integer quantity
    @DecimalMin('1.5') BigDecimal price
    @Positive BigDecimal discount
    @NotNull @Email(regexp = '.+@example\\.com') String contact
    @AssertTrue Boolean agreed
    @Mysterious String custom
    @NotNull(groups = OnReview) String reviewer

    @URL
    String getWebsite() { website }

    @AssertTrue
    boolean isRangeValid() { true }
}

@SuppressWarnings('unused')
@GroupSequence([Basics, SequencedProduct])
class SequencedProduct {
    @NotNull(groups = Basics) String name
    @Min(value = 1L) Integer quantity
    @NotNull(groups = OnReview) String reviewer
}

interface ParentChecks {}

interface ChildChecks extends ParentChecks {}

interface ShippingChecks {}

@GroupSequence([ShippingChecks])
interface ShippingSequence {}

@SuppressWarnings('unused')
@GroupSequence([ChildChecks, ShippingSequence, InheritingSequencedProduct])
class InheritingSequencedProduct {
    @Null(groups = ParentChecks) String retired
    @NotNull(groups = ParentChecks) String name
    @Size(max = 5, groups = ShippingChecks) String code
    @Min(1L) Integer quantity
    @NotNull(groups = OnReview) String reviewer
}

@SuppressWarnings('unused')
@GroupSequence([SequencedProductBase, ChildChecks])
class SequencedProductBase {
    @Null(groups = ParentChecks) String retired
    @NotNull(groups = ChildChecks) String name
    @Size(max = 3, groups = ShippingChecks) String label
}

@SuppressWarnings('unused')
class SequenceInheritingProduct extends SequencedProductBase {
    @NotNull(groups = ParentChecks) String reviewer
    @Size(max = 5, groups = ShippingChecks) String code
}

@SuppressWarnings('unused')
@GroupSequence([SequenceRedefiningProduct, ShippingChecks])
class SequenceRedefiningProduct extends SequencedProductBase {
    @NotNull(groups = OnReview) String reviewer
    @Size(max = 5, groups = ShippingChecks) String code
}

@SuppressWarnings('unused')
class RoundedProduct {
    @DecimalMin('1.5') @DecimalMax('4.5') Integer inclusive
    @DecimalMin(value = '1.5', inclusive = false) @DecimalMax(value = '4.5', inclusive = false) Integer exclusive
    @DecimalMin('-1.5') @DecimalMax('-0.5') Long negative
    @DecimalMin('2') @DecimalMax('4.0') Long whole
}

@SuppressWarnings('unused')
class ComposedProduct {
    @ShortOrLong String code
    @NotShort String tag
    @NullOrLong String note
}

@ConstraintComposition(CompositionType.OR)
@Size(max = 3)
@Size(min = 10)
@ReportAsSingleViolation
@Constraint(validatedBy = [])
@Documented
@Target([ElementType.FIELD, ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@interface ShortOrLong {
    String message() default 'must be short or long'
    Class<?>[] groups() default []
    Class<? extends Payload>[] payload() default []
}

@ConstraintComposition(CompositionType.OR)
@Null
@Size(min = 10)
@ReportAsSingleViolation
@Constraint(validatedBy = [])
@Documented
@Target([ElementType.FIELD, ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@interface NullOrLong {
    String message() default 'must be null or long'
    Class<?>[] groups() default []
    Class<? extends Payload>[] payload() default []
}

@ConstraintComposition(CompositionType.ALL_FALSE)
@Size(max = 3)
@ReportAsSingleViolation
@Constraint(validatedBy = [])
@Documented
@Target([ElementType.FIELD, ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@interface NotShort {
    String message() default 'must not be short'
    Class<?>[] groups() default []
    Class<? extends Payload>[] payload() default []
}

@NotBlank
@Size(min = 8)
@ReportAsSingleViolation
@Constraint(validatedBy = [])
@Documented
@Target([ElementType.FIELD, ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@interface StrongSku {
    String message() default 'must be a strong sku'
    Class<?>[] groups() default []
    Class<? extends Payload>[] payload() default []
}

@NotNull
@Size(max = 10)
@Constraint(validatedBy = [])
@Documented
@Target([ElementType.FIELD, ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@interface ShortCode {
    String message() default 'must be a short code'
    Class<?>[] groups() default []
    Class<? extends Payload>[] payload() default []
    @OverridesAttribute(constraint = Size, name = 'max') int max() default 10
}

@Constraint(validatedBy = [MysteriousValidator])
@Documented
@Target([ElementType.FIELD, ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@interface Mysterious {
    String message() default 'mysterious'
    Class<?>[] groups() default []
    Class<? extends Payload>[] payload() default []
}

class MysteriousValidator implements jakarta.validation.ConstraintValidator<Mysterious, String> {
    @Override
    boolean isValid(String value, jakarta.validation.ConstraintValidatorContext context) {
        return value == null || value == 'mysterious'
    }
}
