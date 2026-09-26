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
package grails.openapi

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.annotations.media.Schema as SchemaAnnotation

import grails.artefact.Artefact
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.gorm.annotation.Entity
import grails.rest.RestfulController
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.util.WebUtils

import spock.lang.Specification

class PersistentEntitySchemaSpec extends Specification {

    void setup() {
        WebUtils.clearGrailsWebRequest()
    }

    void 'registers a schema for the documented resource and its request body'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)

        then:
        openApi.components.schemas.containsKey('Widget')
    }

    void 'describes an association by the identifier Grails binds and renders'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)

        then: 'a to-one association is a reference by identifier, which a client can send'
        with(openApi.components.schemas['Widget'].properties.crate) {
            $ref == null
            type == 'object'
            properties.keySet() == ['id'] as Set
            properties.id.type == 'integer'
            properties.id.format == 'int64'
            !properties.id.readOnly
            required == ['id']
        }

        and: 'a to-many association is an array of such references'
        with(openApi.components.schemas['Crate'].properties.widgets) {
            type == 'array'
            items.properties.keySet() == ['id'] as Set
        }
    }

    void 'describes an entity in XML as Grails renders it'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)
        def widget = openApi.components.schemas['Widget']

        then: 'an element named for the class, with its identifier as an attribute'
        widget.xml.name == 'widget'
        widget.properties.id.xml.attribute
        !widget.properties.name.xml

        and: 'a to-one association as an element with the identifier as an attribute'
        !widget.properties.crate.xml
        widget.properties.crate.properties.id.xml.attribute

        and: 'a to-many association as an element holding one named for the associated class for each'
        with(openApi.components.schemas['Crate'].properties.widgets) {
            xml.wrapped
            items.xml.name == 'widget'
            items.properties.id.xml.attribute
        }

        and: 'a listing as a list element holding one for each'
        with(openApi.paths['/widgets'].get.responses['200'].content['application/json'].schema) {
            xml.name == 'list'
            xml.wrapped
            items.$ref == '#/components/schemas/Widget'
        }
    }

    void 'describes each of two properties where one is renamed to the name the other is rendered by'() {
        when:
        def openApi = OpenApiFixture.document([CatalogCardController], [CatalogCard]) {
            '/cards'(resources: 'catalogCard')
        }
        def properties = openApi.components.schemas['CatalogCard'].properties

        then: 'the rename holds the name, and the other keeps the one Jackson gives it'
        properties.ISBN.maxLength == 20
        properties.isbn.maxLength == 13
        properties.keySet().containsAll(['id', 'ISBN', 'isbn'])
    }

    void 'describes an identifier named otherwise as Grails renders it'() {
        when:
        def openApi = OpenApiFixture.document([VolumeController, LoanController], [Volume, Loan]) {
            '/volumes'(resources: 'volume')
            '/loans'(resources: 'loan')
        }
        def volume = openApi.components.schemas['Volume'].properties
        def reference = openApi.components.schemas['Loan'].properties.volume

        then: 'under its name in JSON, and as the id attribute in XML'
        volume.code.readOnly
        volume.code.xml.name == 'id'
        volume.code.xml.attribute

        and: 'an association to it by id, which Grails renders and binds whatever the identity is named'
        reference.properties.keySet() == ['id'] as Set
        reference.required == ['id']
        reference.properties.id.xml.attribute
    }

    void 'does not describe an entity reached only through an association'() {
        given:
        def openApi = new OpenAPI()

        when: 'Crate is referenced from Widget but no mapping serves it'
        OpenApiFixture.generator(holder(false), application(), context()).contribute(openApi, null)

        then:
        openApi.components.schemas.containsKey('Widget')
        !openApi.components.schemas.containsKey('Crate')
    }

    void 'omits an unreferenced domain class entirely'() {
        given:
        def openApi = new OpenAPI()
        MappingContext context = new KeyValueMappingContext('test')
        context.addPersistentEntity(Widget)
        context.addPersistentEntity(Crate)
        context.addPersistentEntity(Orphan)
        context.setValidatorRegistry(new DefaultValidatorRegistry(context, new ConnectionSourceSettings()))

        when: 'Orphan has no mapping and nothing references it'
        OpenApiFixture.generator(holder(), application(), context).contribute(openApi, null)

        then:
        !openApi.components.schemas.containsKey('Orphan')
    }

    void 'maps domain property types onto OpenAPI schema types'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)

        then:
        with(openApi.components.schemas['Widget'].properties) {
            id.type == 'integer'
            id.format == 'int64'
            name.type == 'string'
            weight.type == 'number'
            active.type == 'boolean'
        }
    }

    void 'derives required members from the constraints block'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)

        then: 'a property the constraints declare non-nullable is required'
        'name' in openApi.components.schemas['Widget'].required

        and: 'a nullable property is not'
        !('weight' in (openApi.components.schemas['Widget'].required ?: []))

        and: 'the generated version column is never required'
        !('version' in (openApi.components.schemas['Widget'].required ?: []))
    }

    void 'carries validation constraints across to the schema'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)

        then:
        with(openApi.components.schemas['Widget'].properties) {
            name.maxLength == 40
            color.enum == ['red', 'green', null]
            contact.format == 'email'

            and: 'a pattern the whole value must match, as Grails matches it'
            code.pattern == '^(?:[A-Z]{3})$'

            and: 'a value that must not be blank is at least one character'
            name.minLength == 1
        }
    }

    void 'allows null where a property is nullable, in the way each OpenAPI version says so'() {
        when:
        def openApi = OpenApiFixture.generator(holder(), application(), context(),
                ['springdoc.api-docs.version': version]).generate()
        def properties = openApi.components.schemas['Widget'].properties

        then: 'OpenAPI 3.0 reads nullable, and 3.1 a null type; each ignores the other'
        properties.weight.nullable
        properties.weight.types == ['number', 'null'] as Set
        properties.crate.nullable

        and: 'a value a list constrains lists null too'
        properties.color.enum.contains(null)

        and: 'a property that must have a value does not'
        !properties.name.nullable
        !properties.name.types?.contains('null')

        where:
        version << ['openapi_3_0', 'openapi_3_1']
    }

    void 'carries the size of a collection across as its number of items'() {
        when:
        def openApi = OpenApiFixture.document([ManifestController], [Manifest]) {
            '/manifests'(resources: 'manifest')
        }

        then:
        with(openApi.components.schemas['Manifest'].properties.lines) {
            minItems == 1
            maxItems == 5
        }
    }

    void 'describes a collection of values in XML as an element holding one named for the class of each'() {
        when:
        def openApi = OpenApiFixture.document([ManifestController], [Manifest]) {
            '/manifests'(resources: 'manifest')
        }
        def properties = openApi.components.schemas['Manifest'].properties

        then: 'as Grails renders it: <lines><string>..</string></lines>'
        properties.lines.xml.wrapped
        properties.lines.items.xml.name == 'string'

        and: 'a collection GORM maps as values of its own'
        properties.codes.xml.wrapped
        properties.codes.items.xml.name == 'integer'
    }

    void 'marks the server assigned properties readOnly rather than defining a second schema'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)

        then: 'the identifier is present but not for a client to send'
        with(openApi.components.schemas['Widget'].properties) {
            id.readOnly

            and: 'an editable property is not marked'
            !name.readOnly
        }

        and: 'no second schema is defined for request bodies'
        openApi.components.schemas.keySet().every { !it.endsWith('Request') }
    }

    void 'documents a 404 on operations addressed by identifier'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)

        then: 'an instance operation can miss'
        openApi.paths['/widgets/{id}'].get.responses['404']

        and: 'a collection operation cannot'
        openApi.paths['/widgets'].get.responses['404'] == null
    }

    void 'responds with a collection schema for index and a single resource otherwise'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)

        then: 'index returns an array of the resource'
        with(openApi.paths['/widgets'].get.responses['200'].content['application/json'].schema) {
            type == 'array'
            items.$ref == '#/components/schemas/Widget'
        }

        and: 'show returns a single resource'
        openApi.paths['/widgets/{id}'].get.responses['200']
                .content['application/json'].schema.$ref == '#/components/schemas/Widget'
    }

    void 'documents a request body for methods that accept one'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)

        then:
        openApi.paths['/widgets'].post.requestBody
                .content['application/json'].schema.$ref == '#/components/schemas/Widget'

        and: 'a read-only method carries no request body'
        openApi.paths['/widgets'].get.requestBody == null
    }

    void 'describes the resource as a plain type when the application has no mapping context'() {
        given:
        def openApi = new OpenAPI()

        when:
        OpenApiFixture.generator(holder(), application()).contribute(openApi, null)

        then: 'paths and the resource are still documented'
        openApi.paths['/widgets'].get
        openApi.components.schemas['Widget']

        and: 'but without what only GORM knows'
        openApi.components.schemas['Widget'].required == null
        !openApi.components.schemas['Widget'].properties.id?.readOnly
    }

    void 'honors a Schema annotation on the domain class'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)

        then:
        openApi.components.schemas['Widget'].description == 'A widget in the catalog'
    }

    void 'honors a Schema annotation on a property'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)

        then:
        with(openApi.components.schemas['Widget'].properties.name) {
            description == 'The name shown to a customer'
            example == 'Sprocket'
        }
    }

    void 'applies the GORM constraints over the annotated schema'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)

        then: 'the annotation supplies the prose'
        openApi.components.schemas['Widget'].properties.name.description == 'The name shown to a customer'

        and: 'while the constraints block still supplies the validation, which swagger-core cannot see'
        openApi.components.schemas['Widget'].properties.name.maxLength == 40
        'name' in openApi.components.schemas['Widget'].required
    }

    void 'omits the foreign key accessor GORM adds beside an association'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)

        then: 'the association describes the relationship'
        openApi.components.schemas['Widget'].properties.containsKey('crate')

        and: 'so the generated identifier accessor is redundant'
        !openApi.components.schemas['Widget'].properties.containsKey('crateId')
    }

    void 'does not describe the version, which Grails does not render by default'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)

        then:
        !openApi.components.schemas['Widget'].properties.containsKey('version')
    }

    void 'describes the version where Grails is configured to render it, though swagger-core does not surface it'() {
        when:
        def openApi = OpenApiFixture.generator(holder(), application(), context(), [(setting): true]).generate()

        then:
        with(openApi.components.schemas['Widget'].properties.version) {
            it
            readOnly
            xml.attribute
        }
        !('version' in (openApi.components.schemas['Widget'].required ?: []))

        where:
        setting << ['grails.converters.domain.include.version', 'grails.converters.json.domain.include.version']
    }

    void 'describes only what Grails renders of an entity'() {
        when:
        def openApi = OpenApiFixture.document([LedgerLineController], [LedgerLine]) {
            '/lines'(resources: 'ledgerLine')
        }

        then: 'the identifier and the persistent properties, not a transient or a derived getter'
        openApi.components.schemas['LedgerLine'].properties.keySet() == ['id', 'amount', 'memo'] as Set
    }

    void 'describes a renamed property under its name, with its constraints'() {
        when:
        def openApi = OpenApiFixture.document([RenamedBookController], [RenamedBook]) {
            '/renamed'(resources: 'renamedBook')
        }
        def schema = openApi.components.schemas['RenamedBook']

        then: 'a property whose description is renamed is described under the name it gives'
        schema.properties.book_title.maxLength == 50
        schema.required.contains('book_title')
        !schema.properties.containsKey('title')

        and: 'a Jackson rename, which Grails renders and binds the property without, is not'
        schema.properties.containsKey('customerName')
        !schema.properties.containsKey('customer_name')

        and: 'a property whose name Jackson writes another way is described by the name Grails renders it by'
        schema.properties.containsKey('ISBN')
        !schema.properties.containsKey('isbn')

        and: 'a public field, which Grails does not render, is not described'
        !schema.properties.containsKey('shelfNote')
    }

    void 'marks what data binding does not bind as read only'() {
        when:
        def openApi = OpenApiFixture.document([LedgerLineController], [LedgerLine]) {
            '/lines'(resources: 'ledgerLine')
        }

        then: 'a property Grails leaves out of the binding, as it does one constrained bindable: false'
        with(openApi.components.schemas['LedgerLine'].properties) {
            memo.readOnly
            !amount.readOnly
        }
    }

    void 'produces the same schema for a second document'() {
        given: 'one customizer used for two documents, as a singleton bean is'
        def customizer = customizer()
        def first = new OpenAPI()
        def second = new OpenAPI()

        when:
        customizer.contribute(first, null)
        customizer.contribute(second, null)

        then: 'the constraints are applied once, not accumulated on a shared schema object'
        second.components.schemas['Widget'].properties.color.enum.toList() == ['red', 'green', null]
        second.components.schemas['Widget'].required.count { it == 'name' } == 1

        and: 'the two documents agree'
        second.components.schemas['Widget'].properties.color.enum.toList() ==
                first.components.schemas['Widget'].properties.color.enum.toList()
    }

    void 'keeps a zero bound, which Groovy treats as falsy'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().contribute(openApi, null)

        then: 'a min of zero is a real lower bound rather than an absent one'
        openApi.components.schemas['Widget'].properties.weight.minimum == 0G

        and: 'as is a maxSize of zero'
        openApi.components.schemas['Widget'].properties.note.maxLength == 0
    }

    void 'resolves schemas with the converter that matches the document version'() {
        given: 'a 3.1 document, which is what springdoc serves by default'
        def openApi = new OpenAPI(SpecVersion.V31)

        when:
        customizer().contribute(openApi, null)

        then: 'the schemas are still described, through the 3.1 converter'
        openApi.specVersion == SpecVersion.V31
        openApi.components.schemas['Widget'].properties.name.maxLength == 40
        'name' in openApi.components.schemas['Widget'].required
    }

    private static GrailsOpenApiGenerator customizer() {
        OpenApiFixture.generator(holder(), application(), context())
    }

    private static MappingContext context() {
        MappingContext context = new KeyValueMappingContext('test')
        context.addPersistentEntity(Widget)
        context.addPersistentEntity(Crate)
        // Constraints are only available once a validator registry has evaluated them.
        context.setValidatorRegistry(new DefaultValidatorRegistry(context, new ConnectionSourceSettings()))
        context
    }

    private static GrailsApplication application() {
        new DefaultGrailsApplication(WidgetController, CrateController).tap { it.initialise() }
    }

    private static DefaultUrlMappingsHolder holder(boolean crates = true) {
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def evaluator = new DefaultUrlMappingEvaluator(ctx)
        new DefaultUrlMappingsHolder(evaluator.evaluateMappings {
            '/widgets'(resources: 'widget')
            if (crates) {
                '/crates'(resources: 'crate')
            }
        })
    }
}

@Artefact('Controller')
class WidgetController extends RestfulController<Widget> {
    WidgetController() { super(Widget) }
}

@Artefact('Controller')
class CrateController extends RestfulController<Crate> {
    CrateController() { super(Crate) }
}

@SchemaAnnotation(description = 'A widget in the catalog')
@Entity
class Widget {
    @SchemaAnnotation(description = 'The name shown to a customer', example = 'Sprocket')
    String name
    Double weight
    Boolean active
    String color
    String code
    String note
    String contact
    Crate crate

    static constraints = {
        name blank: false, nullable: false, maxSize: 40
        weight nullable: true, min: 0.0d
        active nullable: true
        color nullable: true, inList: ['red', 'green']
        code nullable: true, matches: '[A-Z]{3}'
        note nullable: true, maxSize: 0
        contact nullable: true, email: true
        crate nullable: true
    }
}

@Entity
class Crate {
    String label
    static hasMany = [widgets: Widget]
}

@Entity
class CatalogCard {
    String ISBN

    @SchemaAnnotation(name = 'ISBN')
    String legacyCode

    static constraints = {
        ISBN maxSize: 13
        legacyCode maxSize: 20
    }
}

@Artefact('Controller')
class CatalogCardController extends RestfulController<CatalogCard> {
    CatalogCardController() { super(CatalogCard) }
}

@Entity
class Volume {
    String code
    String title

    static mapping = {
        id name: 'code', generator: 'assigned'
    }
}

@Artefact('Controller')
class VolumeController extends RestfulController<Volume> {
    VolumeController() { super(Volume) }
}

@Entity
class Loan {
    Volume volume
}

@Artefact('Controller')
class LoanController extends RestfulController<Loan> {
    LoanController() { super(Loan) }
}

@Entity
class Orphan {
    String note
}

@Entity
class LedgerLine {

    // Grails generates the properties a domain class binds, in the list data binding reads by
    // default and the one it reads when it binds only what is declared bindable; declared here as
    // it would generate them for a domain class in grails-app/domain.
    public static final List $defaultDatabindingWhiteList = ['amount']
    public static final List $legacyDatabindingWhiteList = ['amount']

    BigDecimal amount
    String memo
    String scratch

    static transients = ['scratch']

    String getDisplay() {
        "${amount} ${memo}"
    }

    static constraints = {
        memo nullable: true, bindable: false
        scratch nullable: true
    }
}

@Artefact('Controller')
class LedgerLineController extends RestfulController<LedgerLine> {
    LedgerLineController() { super(LedgerLine) }
}

@Entity
class Manifest {
    List<String> lines

    static hasMany = [codes: Integer]

    static constraints = {
        lines size: 1..5
    }
}

@Artefact('Controller')
class ManifestController extends RestfulController<Manifest> {
    ManifestController() { super(Manifest) }
}

@Entity
class RenamedBook {

    @SchemaAnnotation(name = 'book_title')
    String title

    @JsonProperty('customer_name')
    String customerName

    String ISBN

    public String shelfNote

    static constraints = {
        title nullable: false, maxSize: 50
        ISBN nullable: true
    }
}

@Artefact('Controller')
class RenamedBookController extends RestfulController<RenamedBook> {
    RenamedBookController() { super(RenamedBook) }
}
