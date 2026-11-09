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
package org.grails.web.mapping.domainlink

import grails.artefact.Artefact
import grails.rest.RestfulController

class Person {
    Long id
    String name
}

class Widget {
    Long id
    String name
}

class Gadget {
    Long id
    String name
}

class Note {
    Long id
    String body
}

/**
 * A generic base class that is not a REST controller, as a report or export controller might extend. A
 * controller parameterised on a domain class through it does not serve that class.
 */
abstract class ReportBase<T> {
}

/**
 * A non-generic intermediate that binds the type argument, so a controller extending it declares no
 * generics of its own. Exercises resolution through more than one level of the hierarchy.
 */
abstract class WidgetControllerBase extends RestfulController<Widget> {
    WidgetControllerBase() {
        super(Widget)
    }
}

/**
 * The only controller for {@code Person}, and named for the plural resource rather than the domain
 * class, which is the case a {@code resource} link cannot resolve by name alone.
 */
@Artefact('Controller')
class PeopleController extends RestfulController<Person> {
    PeopleController() {
        super(Person)
    }
}

/**
 * The only controller for {@code Widget}, reaching {@code RestfulController} through {@link WidgetControllerBase}.
 */
@Artefact('Controller')
class WidgetsController extends WidgetControllerBase {
}

/**
 * One of two controllers serving {@code Gadget}, making the domain class ambiguous.
 */
@Artefact('Controller')
class GadgetsController extends RestfulController<Gadget> {
    GadgetsController() {
        super(Gadget)
    }
}

/**
 * The second controller serving {@code Gadget}.
 */
@Artefact('Controller')
class AdminGadgetsController extends RestfulController<Gadget> {
    AdminGadgetsController() {
        super(Gadget)
    }
}

/**
 * A controller that serves no domain class, so resolution falls back to the domain class name.
 */
@Artefact('Controller')
class NoteController {
    def index() {}
    def show() {}
}

class Chapter {
    Long id
    String title
}

/**
 * A plain controller named after {@code Chapter}, alongside {@link ChapterApiController} which also serves
 * the domain class. The naming convention has to win, or an application that already relies on it has its
 * links silently retargeted.
 */
@Artefact('Controller')
class ChapterController {
    def index() {}
    def show() {}
}

@Artefact('Controller')
class ChapterApiController extends RestfulController<Chapter> {
    ChapterApiController() {
        super(Chapter)
    }
}

class Tag {
    Long id
    String label
}

/**
 * Stands in for a generic trait a controller implements for another purpose than serving the domain class,
 * such as auditing it. Groovy traits compile to an interface.
 */
interface Audited<T> {
}

@Artefact('Controller')
class TagsController implements Audited<Tag> {
    def index() {}
    def show() {}
}

class Chronicle {
    Long id
    String name
}

/**
 * A base parameterised on both a parent and a child resource, serving the child, so the domain class has to
 * be the one {@code RestfulController} is parameterised on rather than any type argument.
 */
abstract class NestedResourceControllerBase<P, T> extends RestfulController<T> {
    NestedResourceControllerBase(Class<T> resource) {
        super(resource)
    }
}

@Artefact('Controller')
class ChroniclesController extends NestedResourceControllerBase<Person, Chronicle> {
    ChroniclesController() {
        super(Chronicle)
    }
}

class Assessment {
    Long id
}

/**
 * The root controller named after {@code Assessment}, which the naming convention resolves to.
 */
@Artefact('Controller')
class AssessmentController {
    def index() {}
    def show() {}
}

/**
 * A second controller serving {@code Assessment}, in the {@code manage} namespace, so a link rendered
 * while it or another {@code manage} controller handles the request should stay in {@code manage}.
 */
@Artefact('Controller')
class ManageAssessmentController extends RestfulController<Assessment> {
    static namespace = 'manage'

    ManageAssessmentController() {
        super(Assessment)
    }
}

/**
 * A {@code manage} controller serving no domain class, to render links from inside the namespace.
 */
@Artefact('Controller')
class ManageDashboardController {
    static namespace = 'manage'
    def index() {}
}

/**
 * A root controller serving no domain class, to render links from outside any namespace.
 */
@Artefact('Controller')
class HomeController {
    def index() {}
}

class Ballot {
    Long id
}

@Artefact('Controller')
class BallotController {
    def index() {}
    def show() {}
}

/**
 * One of two {@code manage} controllers serving {@code Ballot}, making the namespace ambiguous for it.
 */
@Artefact('Controller')
class ManageBallotController extends RestfulController<Ballot> {
    static namespace = 'manage'

    ManageBallotController() {
        super(Ballot)
    }
}

@Artefact('Controller')
class AuditBallotController extends RestfulController<Ballot> {
    static namespace = 'manage'

    AuditBallotController() {
        super(Ballot)
    }
}

class Invoice {
    Long id
}

/**
 * Named after {@code Invoice}, but in the {@code admin} namespace only.
 */
@Artefact('Controller')
class InvoiceController {
    static namespace = 'admin'
    def index() {}
    def show() {}
}

/**
 * Serves {@code Invoice} in the default namespace, which is nearer than {@link InvoiceController} to a link
 * rendered outside the {@code admin} namespace.
 */
@Artefact('Controller')
class InvoicesController extends RestfulController<Invoice> {
    InvoicesController() {
        super(Invoice)
    }
}

/**
 * An {@code admin} controller serving no domain class, to render links from inside that namespace.
 */
@Artefact('Controller')
class AdminDashboardController {
    static namespace = 'admin'
    def index() {}
}

class Manuscript {
    Long id
}

/**
 * The controller serving {@code Manuscript}, in the default namespace.
 */
@Artefact('Controller')
class ManuscriptController extends RestfulController<Manuscript> {
    ManuscriptController() {
        super(Manuscript)
    }
}

/**
 * Reports on {@code Manuscript} from the {@code admin} namespace through a generic base class that is not a
 * REST controller. It has a {@code show} action of its own, for a report, so the action a link targets does
 * not tell it apart from the controller serving the domain class.
 */
@Artefact('Controller')
class ManuscriptReportController extends ReportBase<Manuscript> {
    static namespace = 'admin'
    def index() {}
    def show() {}
    def export() {}
}

class Folio {
    Long id
}

/**
 * Named after {@code Folio}, but defines no {@code show} action, so it should not be sent the links to one,
 * even from its own pages.
 */
@Artefact('Controller')
class FolioController {
    def index() {}
}

/**
 * Serves {@code Folio} under another name, with actions of its own besides those it inherits.
 */
@Artefact('Controller')
class FoliosController extends RestfulController<Folio> {
    FoliosController() {
        super(Folio)
    }

    def export() {}
    def exportAll() {}
}

class Pamphlet {
    Long id
}

class TourGuide {
    Long id
}

/**
 * Serves {@code TourGuide} in the default namespace, under a name of more than one word.
 */
@Artefact('Controller')
class CityGuidesController extends RestfulController<TourGuide> {
    CityGuidesController() {
        super(TourGuide)
    }
}

/**
 * One of two {@code backOffice} controllers serving {@code TourGuide}, neither named after it, so only the
 * controller handling the request settles which of them a link rendered in {@code backOffice} targets.
 */
@Artefact('Controller')
class TourDeskController extends RestfulController<TourGuide> {
    static namespace = 'backOffice'

    TourDeskController() {
        super(TourGuide)
    }
}

@Artefact('Controller')
class GuideLedgerController extends RestfulController<TourGuide> {
    static namespace = 'backOffice'

    GuideLedgerController() {
        super(TourGuide)
    }
}
