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
import org.apache.grails.data.testing.tck.domains.BeanValidatedAttachment
import org.apache.grails.data.testing.tck.domains.BeanValidatedBasket
import org.apache.grails.data.testing.tck.domains.BeanValidatedBasketLine
import org.apache.grails.data.testing.tck.domains.BeanValidatedBulletin
import org.apache.grails.data.testing.tck.domains.BeanValidatedChapter
import org.apache.grails.data.testing.tck.domains.BeanValidatedDossier
import org.apache.grails.data.testing.tck.domains.BeanValidatedInvoice
import org.apache.grails.data.testing.tck.domains.BeanValidatedInvoiceLine
import org.apache.grails.data.testing.tck.domains.BeanValidatedIssue
import org.apache.grails.data.testing.tck.domains.BeanValidatedManuscript
import org.apache.grails.data.testing.tck.domains.BeanValidatedNotice
import org.apache.grails.data.testing.tck.domains.BeanValidatedOrder
import org.apache.grails.data.testing.tck.domains.BeanValidatedPair
import org.apache.grails.data.testing.tck.domains.BeanValidatedPairing
import org.apache.grails.data.testing.tck.domains.BeanValidatedParcel
import org.apache.grails.data.testing.tck.domains.BeanValidatedPost
import org.apache.grails.data.testing.tck.domains.BeanValidatedRecall
import org.apache.grails.data.testing.tck.domains.BeanValidatedReview
import org.apache.grails.data.testing.tck.domains.BeanValidatedSection
import org.apache.grails.data.testing.tck.domains.BeanValidatedShelf
import org.apache.grails.data.testing.tck.domains.BeanValidatedShelfBook
import org.apache.grails.data.testing.tck.domains.BeanValidatedShipment
import org.apache.grails.data.testing.tck.domains.BeanValidatedTeaser
import org.apache.grails.data.testing.tck.support.BeanValidatedOrderDetails

/**
 * A domain class annotated with Bean Validation constraints is validated against its annotations and its
 * constraints block, cascades like any other domain class, and exposes both as constraints.
 */
@IgnoreIf({ Boolean.getBoolean('simple.gorm.suite') }) // the simple datastore of the core suite registers no validator
class BeanValidationSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.registerDomainClasses(BeanValidatedInvoice, BeanValidatedInvoiceLine, BeanValidatedShipment, BeanValidatedParcel,
                BeanValidatedReview, BeanValidatedPair, BeanValidatedPairing, BeanValidatedArticle, BeanValidatedPost, BeanValidatedTeaser,
                BeanValidatedNotice, BeanValidatedRecall, BeanValidatedDossier, BeanValidatedAttachment, BeanValidatedIssue,
                BeanValidatedSection, BeanValidatedBasket, BeanValidatedBasketLine, BeanValidatedOrder, BeanValidatedShelf,
                BeanValidatedShelfBook, BeanValidatedBulletin, BeanValidatedManuscript, BeanValidatedChapter)
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

    void 'an instance associated twice, each association converting the default group to another group, is validated in both'() {
        given: 'an attachment valid for publishing but not for archiving, as the cover and the appendix of a dossier'
        def attachment = new BeanValidatedAttachment(label: 'Cover', tag: '')
        def dossier = new BeanValidatedDossier(title: 'Groups', cover: attachment, appendix: attachment)

        expect: 'the appendix reports the ArchiveChecks violation, and the cover none'
        !dossier.validate()
        dossier.errors.errorCount == 1
        dossier.errors.getFieldError('appendix.tag').code == 'NotBlank'
        dossier.save() == null

        when: 'the attachment is valid for both'
        attachment.tag = 'kept'

        then:
        dossier.validate()
        dossier.save(flush: true) != null
    }

    void 'a group sequence an association converts to is expanded before the conversions of the associated instance apply'() {
        given: 'a section validated in a sequence of PublishChecks, whose attachment converts PublishChecks to ArchiveChecks'
        def attachment = new BeanValidatedAttachment(label: 'Cover', tag: '')
        def issue = new BeanValidatedIssue(title: 'Sequence', section: new BeanValidatedSection(heading: 'Head', attachment: attachment))

        expect: 'the attachment is validated in ArchiveChecks'
        !issue.validate()
        issue.errors.errorCount == 1
        issue.errors.getFieldError('section.attachment.tag').code == 'NotBlank'
        issue.save() == null

        when: 'the attachment is valid for archiving'
        attachment.tag = 'kept'

        then:
        issue.validate()
        issue.save(flush: true) != null
    }

    void 'two associated instances that compare equal are both validated'() {
        given: 'a basket with two lines of the same name, which compare equal'
        def basket = new BeanValidatedBasket(name: 'basket')
        basket.addToLines(new BeanValidatedBasketLine(name: 'same', code: 'A1'))
        basket.addToLines(new BeanValidatedBasketLine(name: 'same', code: null))

        expect:
        basket.lines[0] == basket.lines[1]
        !basket.lines[0].is(basket.lines[1])

        and: 'the second line is validated as well as the first'
        !basket.validate()
        basket.errors.errorCount == 1
        basket.errors.getFieldError('lines[1].code').code == 'nullable'
        basket.save() == null
    }

    void 'a property marked @Valid that is not a persistent association is validated by Bean Validation'() {
        given: 'an order whose transient details violate an annotation'
        def order = new BeanValidatedOrder(reference: 'ORD-1', details: new BeanValidatedOrderDetails(note: ''))

        expect:
        !order.validate()
        order.errors.errorCount == 1
        order.errors.getFieldError('details.note').code == 'NotBlank'
        order.save() == null

        and: 'deepValidate: false leaves the associations out, not the details, which are part of the instance'
        !order.validate(deepValidate: false)
        order.errors.getFieldError('details.note').code == 'NotBlank'

        when: 'the details are valid'
        order.details.note = 'rush'

        then:
        order.validate()
        order.save(flush: true) != null
    }

    void 'an association marked @Valid of a domain class without constraint annotations is validated in the group its @ConvertGroup converts to'() {
        given: 'a shelf whose book violates an annotation of each group'
        def shelf = new BeanValidatedShelf(name: 'shelf', book: new BeanValidatedShelfBook(title: '', isbn: null))

        expect: 'the book is validated in PublishChecks, not in its default group'
        !shelf.validate()
        shelf.errors.errorCount == 1
        shelf.errors.getFieldError('book.title').code == 'NotBlank'
        shelf.save() == null

        when: 'the book is valid for publishing, and for the default group Hibernate validates on its own when it persists'
        shelf.book.title = 'Groups'
        shelf.book.isbn = '1234567890123'

        then:
        shelf.validate()
        shelf.save(flush: true) != null
    }

    void 'a subclass declaring no annotation of its own is validated with the annotations it inherits'() {
        given:
        def bulletin = new BeanValidatedBulletin(tag: 'toolong')
        def entity = BeanValidatedBulletin.gormPersistentEntity
        def constraints = ((ConstrainedEntity) entity.mappingContext.getEntityValidator(entity)).constrainedProperties

        expect: 'the annotations of the superclass are validated in its sequence, so they alone report the null and the length'
        !bulletin.validate()
        bulletin.errors.errorCount == 2
        bulletin.errors.getFieldError('headline').code == 'NotNull'
        bulletin.errors.getFieldError('tag').code == 'Size'
        bulletin.save() == null

        and: 'the constraints describe the inherited annotations'
        !constraints.headline.nullable
        constraints.tag.maxSize == 5

        when: 'the instance is valid'
        bulletin = new BeanValidatedBulletin(headline: 'Bulletin', tag: 'ok')

        then:
        bulletin.validate()
        bulletin.save(flush: true) != null
    }

    void 'a group sequence an association converts to stops at the first group with a violation of the associated instance or of its own associations'() {
        given: 'a chapter valid for publishing but not for archiving, whose attachment converts PublishChecks to ArchiveChecks and violates it'
        def attachment = new BeanValidatedAttachment(label: 'Cover', tag: '')
        def manuscript = new BeanValidatedManuscript(title: 'Sequence',
                chapter: new BeanValidatedChapter(heading: 'Head', tag: '', attachment: attachment))

        expect: 'the violation of the attachment, found in PublishChecks, stops the sequence before ArchiveChecks'
        !manuscript.validate()
        manuscript.errors.errorCount == 1
        manuscript.errors.getFieldError('chapter.attachment.tag').code == 'NotBlank'
        manuscript.save() == null

        when: 'the attachment is valid'
        attachment.tag = 'kept'

        then: 'the sequence goes on to ArchiveChecks'
        !manuscript.validate()
        manuscript.errors.errorCount == 1
        manuscript.errors.getFieldError('chapter.tag').code == 'NotBlank'

        when: 'the chapter is valid for archiving'
        manuscript.chapter.tag = 'kept'

        then:
        manuscript.validate()
        manuscript.save(flush: true) != null
    }
}
