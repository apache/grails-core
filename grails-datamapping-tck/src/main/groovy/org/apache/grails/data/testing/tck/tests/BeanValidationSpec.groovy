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

package org.apache.grails.data.testing.tck.tests

import spock.lang.IgnoreIf

import grails.gorm.validation.ConstrainedEntity
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.BeanValidatedArticle
import org.apache.grails.data.testing.tck.domains.BeanValidatedInvoice
import org.apache.grails.data.testing.tck.domains.BeanValidatedInvoiceLine
import org.apache.grails.data.testing.tck.domains.BeanValidatedNotice
import org.apache.grails.data.testing.tck.domains.BeanValidatedPair
import org.apache.grails.data.testing.tck.domains.BeanValidatedPairing
import org.apache.grails.data.testing.tck.domains.BeanValidatedParcel
import org.apache.grails.data.testing.tck.domains.BeanValidatedPost
import org.apache.grails.data.testing.tck.domains.BeanValidatedRecall
import org.apache.grails.data.testing.tck.domains.BeanValidatedReview
import org.apache.grails.data.testing.tck.domains.BeanValidatedShipment
import org.apache.grails.data.testing.tck.domains.BeanValidatedTeaser

/**
 * A domain class annotated with Bean Validation constraints is validated against its annotations and its
 * constraints block, cascades like any other domain class, and exposes both as constraints.
 */
@IgnoreIf({ Boolean.getBoolean('simple.gorm.suite') }) // the simple datastore of the core suite registers no validator
class BeanValidationSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.registerDomainClasses(BeanValidatedInvoice, BeanValidatedInvoiceLine, BeanValidatedShipment, BeanValidatedParcel,
                BeanValidatedReview, BeanValidatedPair, BeanValidatedPairing, BeanValidatedArticle, BeanValidatedPost, BeanValidatedTeaser,
                BeanValidatedNotice, BeanValidatedRecall)
    }

    void 'the annotations and the constraints block are validated, each violation once'() {
        given: 'an instance violating an annotation, a null annotated property and the constraints block'
        def invoice = new BeanValidatedInvoice(code: 'toolong')

        expect:
        !invoice.validate()
        invoice.errors.errorCount == 3
        invoice.errors.getFieldError('reference').code == 'NotNull'
        invoice.errors.getFieldError('code').code == 'Size'
        invoice.errors.getFieldError('note').code == 'nullable'
        invoice.save() == null

        when: 'the instance is valid'
        invoice = new BeanValidatedInvoice(reference: 'INV-1', code: 'ok', note: 'paid')

        then:
        invoice.validate()
        invoice.save(flush: true) != null
        BeanValidatedInvoice.count() == 1
    }

    void 'the constraints of an annotated domain class describe its annotations and its constraints block'() {
        given:
        def entity = BeanValidatedInvoice.gormPersistentEntity
        def validator = entity.mappingContext.getEntityValidator(entity)

        expect:
        validator instanceof ConstrainedEntity
        def constraints = ((ConstrainedEntity) validator).constrainedProperties
        !constraints.reference.nullable
        constraints.code.maxSize == 5
        !constraints.note.nullable
    }

    void 'validation cascades to an annotated association, checking its annotations and its constraints block once'() {
        given: 'a valid invoice with a line violating an annotation and the constraints block'
        def invoice = new BeanValidatedInvoice(reference: 'INV-1', code: 'ok', note: 'paid')
        invoice.addToLines(new BeanValidatedInvoiceLine(label: '', sku: null))

        expect:
        !invoice.validate()
        invoice.errors.errorCount == 2
        invoice.errors.getFieldError('lines[0].label').code == 'NotBlank'
        invoice.errors.getFieldError('lines[0].sku').code == 'nullable'
        invoice.save() == null

        and: 'deepValidate: false leaves the line out'
        invoice.validate(deepValidate: false)
    }

    void 'a domain class without annotations cascades to an annotated association'() {
        given:
        def shipment = new BeanValidatedShipment(carrier: 'post')
        shipment.addToParcels(new BeanValidatedParcel(label: ''))

        expect:
        !shipment.validate()
        shipment.errors.errorCount == 1
        shipment.errors.getFieldError('parcels[0].label').code == 'NotBlank'
    }

    void 'an annotation of another group than the default one leaves the constraints block in force'() {
        given:
        def review = new BeanValidatedReview()
        def entity = BeanValidatedReview.gormPersistentEntity
        def constraints = ((ConstrainedEntity) entity.mappingContext.getEntityValidator(entity)).constrainedProperties

        expect: 'validate() does not run the group, so the block reports the null'
        !review.validate()
        review.errors.errorCount == 1
        review.errors.getFieldError('author').code == 'nullable'
        review.save() == null

        and: 'the constraints say what is enforced'
        !constraints.author.nullable
    }

    void 'an annotation of a group extended by a group of the sequence redefining the default group is validated and described'() {
        given:
        def article = new BeanValidatedArticle(slug: 'toolong')
        def entity = BeanValidatedArticle.gormPersistentEntity
        def constraints = ((ConstrainedEntity) entity.mappingContext.getEntityValidator(entity)).constrainedProperties

        expect: 'validate() runs the extended group, so the annotation alone reports the null'
        !article.validate()
        article.errors.errorCount == 2
        article.errors.getFieldError('title').code == 'NotNull'
        article.errors.getFieldError('slug').code == 'Size'
        article.save() == null

        and: 'the constraints describe the annotations'
        !constraints.title.nullable
        constraints.slug.maxSize == 5

        when: 'the instance is valid'
        article = new BeanValidatedArticle(title: 'Groups', slug: 'ok')

        then:
        article.validate()
        article.save(flush: true) != null
    }

    void 'the sequence a superclass redefines the default group with applies to its annotations, not to the ones of a subclass'() {
        given:
        def recall = new BeanValidatedRecall(tag: 'toolong')
        def entity = BeanValidatedRecall.gormPersistentEntity
        def constraints = ((ConstrainedEntity) entity.mappingContext.getEntityValidator(entity)).constrainedProperties

        expect: 'the annotations of the superclass are validated in its sequence, so they alone report the null'
        !recall.validate()
        recall.errors.errorCount == 3
        recall.errors.getFieldError('headline').code == 'NotNull'
        recall.errors.getFieldError('tag').code == 'Size'

        and: 'the annotation of the subclass is not, so the block reports the null'
        recall.errors.getFieldError('reason').code == 'nullable'
        recall.save() == null

        and: 'the constraints say what is enforced'
        !constraints.headline.nullable
        constraints.tag.maxSize == 5
        !constraints.reason.nullable

        when: 'the instance is valid'
        recall = new BeanValidatedRecall(headline: 'Recall', tag: 'ok', reason: 'Defect')

        then:
        recall.validate()
        recall.save(flush: true) != null
    }

    void 'an association marked @Valid is validated in the group its @ConvertGroup converts the default group to'() {
        given: 'a teaser violating the annotations of both groups and its constraints block'
        def post = new BeanValidatedPost(title: 'Groups')
        post.teaser = new BeanValidatedTeaser(post: post)

        expect: 'the teaser is validated in PublishChecks, so its block reports the null its default group annotation would'
        !post.validate()
        post.errors.errorCount == 2
        post.errors.getFieldError('teaser.label').code == 'NotBlank'
        post.errors.getFieldError('teaser.note').code == 'nullable'
        post.save() == null
    }

    void 'a class level constraint is validated'() {
        given:
        def pair = new BeanValidatedPair(first: 'same', second: 'same')

        expect:
        !pair.validate()
        pair.errors.errorCount == 1
        pair.errors.globalErrors[0].code == 'DistinctLabels'
    }

    void 'a class level constraint of an associated domain class is reported on the association'() {
        given:
        def pairing = new BeanValidatedPairing(name: 'cards')
        pairing.addToPairs(new BeanValidatedPair(first: 'same', second: 'same'))

        expect:
        !pairing.validate()
        pairing.errors.errorCount == 1
        pairing.errors.fieldErrors*.field == ['pairs[0]']
        pairing.errors.getFieldError('pairs[0]').code == 'DistinctLabels'
        pairing.save() == null
    }
}
