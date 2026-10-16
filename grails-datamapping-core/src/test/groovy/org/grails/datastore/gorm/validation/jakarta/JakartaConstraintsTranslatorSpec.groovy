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

import java.lang.annotation.Annotation
import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import jakarta.validation.Constraint
import jakarta.validation.Payload
import jakarta.validation.ReportAsSingleViolation
import jakarta.validation.constraints.AssertTrue
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

import org.hibernate.validator.constraints.CreditCardNumber
import org.hibernate.validator.constraints.Length
import org.hibernate.validator.constraints.Range
import org.hibernate.validator.constraints.URL
import org.springframework.context.support.StaticMessageSource
import spock.lang.Specification

import grails.gorm.validation.ConstrainedProperty
import grails.gorm.validation.DefaultConstrainedProperty
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry

class JakartaConstraintsTranslatorSpec extends Specification {

    ConstraintRegistry registry = new DefaultConstraintRegistry(new StaticMessageSource())
    JakartaConstraintsTranslator translator = new JakartaConstraintsTranslator()

    void "the constraint annotations of fields, getters and superclasses are found"() {
        when:
        Map<String, List<Annotation>> annotations = JakartaConstraintsTranslator.findConstraintAnnotations(TranslatedProduct)

        then:
        annotations.keySet() == ['name', 'code', 'sku', 'website', 'inherited', 'launched', 'retired', 'card', 'length', 'range', 'quantity', 'price', 'discount', 'contact', 'agreed', 'custom'] as Set
        annotations.name*.annotationType() == [NotNull, Size]
        annotations.code*.annotationType() == [Pattern, Pattern]
        annotations.website*.annotationType() == [URL]
        annotations.inherited*.annotationType() == [NotBlank]
    }

    void "the properties whose nullability is declared with an annotation are found, composed constraints included"() {
        expect:
        JakartaConstraintsTranslator.findNullabilityConstrainedProperties(TranslatedProduct) ==
                ['name', 'sku', 'inherited', 'retired', 'contact'] as Set
    }

    void "the GORM nullable constraint is relaxed for the properties Bean Validation checks for null"() {
        given:
        DefaultConstrainedProperty name = constrained('name', String, [nullable: false])
        DefaultConstrainedProperty quantity = constrained('quantity', Integer, [nullable: false])
        DefaultConstrainedProperty retired = constrained('retired', String, [nullable: false])
        Map<String, ConstrainedProperty> constraints = [name: name, quantity: quantity, retired: retired]

        when:
        JakartaConstraintsTranslator.delegateNullabilityToBeanValidation(TranslatedProduct, constraints)

        then:
        name.nullable
        retired.nullable
        !quantity.nullable
    }

    void "the annotations are described as constraints on a copy of the GORM constraints"() {
        given:
        DefaultConstrainedProperty name = constrained('name', String, [nullable: true, maxSize: 4])
        name.addMetaConstraint('example', 'Widget')
        Map<String, ConstrainedProperty> original = [name: name]

        when:
        Map<String, ConstrainedProperty> merged = translator.translate(TranslatedProduct, original, registry)

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
        merged.code.matches == '(?i)[a-z]+'
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
    }

    void "a class without annotations gets its constraints back untouched"() {
        given:
        Map<String, ConstrainedProperty> original = [:]

        expect:
        translator.translate(Object, original, registry).is(original)
    }

    void "the flags of a pattern become an embedded flag expression"() {
        expect:
        JakartaConstraintsTranslator.withInlineFlags('[a-z]+', [Pattern.Flag.CASE_INSENSITIVE, Pattern.Flag.COMMENTS] as Pattern.Flag[]) == '(?ix)[a-z]+'
        JakartaConstraintsTranslator.withInlineFlags('[a-z]+', [Pattern.Flag.CANON_EQ] as Pattern.Flag[]) == '[a-z]+'
        JakartaConstraintsTranslator.withInlineFlags('[a-z]+', new Pattern.Flag[0]) == '[a-z]+'
    }

    private DefaultConstrainedProperty constrained(String name, Class type, Map<String, Object> constraints) {
        DefaultConstrainedProperty constrained = new DefaultConstrainedProperty(TranslatedProduct, name, type, registry)
        constraints.each { String constraintName, Object value -> constrained.applyConstraint(constraintName, value) }
        return constrained
    }
}

@SuppressWarnings('unused')
class TranslatedProductBase {
    @NotBlank String inherited
}

@SuppressWarnings('unused')
class TranslatedProduct extends TranslatedProductBase {
    @NotNull @Size(min = 3) String name
    @Pattern(regexp = '[a-z]+', flags = Pattern.Flag.CASE_INSENSITIVE) @Pattern(regexp = '.{3}') String code
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

    @URL
    String getWebsite() { website }
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
