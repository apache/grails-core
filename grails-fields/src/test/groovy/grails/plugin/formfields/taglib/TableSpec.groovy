package grails.plugin.formfields.taglib

import grails.converters.XML
import grails.plugin.formfields.FormFieldsTagLib
import grails.plugin.formfields.FormFieldsTemplateService
import grails.plugin.formfields.mock.Address
import grails.plugin.formfields.mock.Employee
import grails.plugin.formfields.mock.Person
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Unroll

import static grails.plugin.formfields.mock.Gender.Female
import static grails.plugin.formfields.mock.Gender.Male

@Unroll
class TableSpec extends AbstractFormFieldsTagLibSpec implements TagLibUnitTest<FormFieldsTagLib> {

	def address = new Address(street: '742 Evergreen Terrace', city: 'Springfield', country: 'USA')
	def bart = new Person(name: "Bart Simpson", gender: Male, address: address)
	def marge = new Person(name: "Marge Simpson", gender: Female, address: address)
	def personList = [bart, marge]
    @Shared def alternativeTable

	def mockFormFieldsTemplateService = Mock(FormFieldsTemplateService)

	def setupSpec() {
		mockDomain(Person)

        //Alternative table template
        alternativeTable = new File('grails-app/views/templates/_fields/_alternativeTable.gsp') << '<table><h1>Alternative Table Template</h1></table>'
    }

	def setup() {
		tagLib.formFieldsTemplateService = mockFormFieldsTemplateService
		mockEmbeddedSitemeshLayout(tagLib)
	}

    def cleanupSpec() {
        alternativeTable.delete()
    }

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/231')
	void "table tag renders columns set by '<f:table collection=\"collection\" maxProperties=\"#maxProperties\"/>'"() {
		when:
		def table = XML.parse(applyTemplate('<f:table collection="collection" maxProperties="' + maxProperties + '"/>', [collection: personList]))
		def renderedTableColumns = table.thead.tr.th.a.collect { it.text().trim() }

		then:
		expectedTableColumns.containsAll(renderedTableColumns)
		renderedTableColumns.containsAll(expectedTableColumns)

		where:
		maxProperties | expectedTableColumns
		1             | ['Salutation']
		2             | ['Salutation', 'Name']
		3             | ['Salutation', 'Name', 'Date Of Birth']
		6             | ['Salutation', 'Name', 'Date Of Birth', 'Address', 'Grails Developer', 'Picture']
		7             | ['Salutation', 'Name', 'Date Of Birth', 'Address', 'Grails Developer', 'Picture', 'Another Picture']
		8             | ['Salutation', 'Name', 'Date Of Birth', 'Address', 'Grails Developer', 'Picture', 'Another Picture', 'Password']
		-4            | ['Salutation', 'Name', 'Date Of Birth', 'Address', 'Grails Developer', 'Picture', 'Another Picture', 'Password']
		-9            | ['Salutation', 'Name', 'Date Of Birth']
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/231')
	void "table tag renders all columns '<f:table collection=\"collection\" maxProperties=\"#maxProperties\"/>'"() {
		when:
		def table = XML.parse(applyTemplate('<f:table collection="collection" maxProperties="' + maxProperties + '"/>', [collection: personList]))
		def renderedTableColumns = table.thead.tr.th.a.collect { it.text().trim() }

		then:
		expectedTableColumns.containsAll(renderedTableColumns)
		renderedTableColumns.containsAll(expectedTableColumns)

		where:
		maxProperties | expectedTableColumns
		0             | ['Salutation', 'Name', 'Date Of Birth', 'Address', 'Grails Developer', 'Picture', 'Another Picture', 'Password', 'Biography', 'Minor', 'Gender', 'Emails']
		1000          | ['Salutation', 'Name', 'Date Of Birth', 'Address', 'Grails Developer', 'Picture', 'Another Picture', 'Password', 'Biography', 'Minor', 'Gender', 'Emails']
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/231')
	void "table tag renders all columns when no maxProperties attribute is set"() {
		when:
		def table = XML.parse(applyTemplate('<f:table collection="collection"/>', [collection: personList]))
		def renderedTableColumns = table.thead.tr.th.a.collect { it.text().trim() }
		def expectedTableColumns = ['Salutation', 'Name', 'Date Of Birth', 'Address', 'Grails Developer', 'Picture', 'Another Picture']

		then:
		expectedTableColumns.containsAll(renderedTableColumns)
		renderedTableColumns.containsAll(expectedTableColumns)
	}

	@Issue('https://github.com/grails3-plugins/fields/issues/3')
	void "table tag allows to specify the domain class"() {
		given:
		def mixedPersonList = [new Employee(name: "Homer Simpson", salary: 1)] + personList

		when:
		def output = applyTemplate('<f:table collection="collection" maxProperties="7" domainClass="grails.plugin.formfields.mock.Person"/>', [collection: mixedPersonList])
		def table = XML.parse(output)

		then:
		table.thead.tr.th.a.collect {
			it.text().trim()
		} == ['Salutation', 'Name', 'Date Of Birth', 'Address', 'Grails Developer', "Picture", "Another Picture"]
		table.tbody.tr.collect { it.td[1].text() } == ['Homer Simpson', 'Bart Simpson', 'Marge Simpson']
	}

	@Issue('https://github.com/grails3-plugins/fields/issues/3')
	void "table tag allows to specify the properties to be shown"() {
		when:
		def output = applyTemplate('<f:table collection="collection" properties="[\'gender\', \'name\']"/>', [collection: personList])
		def table = XML.parse(output)

		then:
		table.thead.tr.th.a.collect { it.text().trim() } == ['Gender', 'Name']
		table.tbody.tr.collect { it.td[0].text() } == ['Male', 'Female']
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/231')
	void "table tag renders columns for properties until maxProperties is reached, ordered by the domain class constraints"() {
		when:
		def output = applyTemplate('<f:table collection="collection" maxProperties="5"/>', [collection: personList])
		def table = XML.parse(output)

		then:
		table.thead.tr.th.a.collect { it.text().trim() } == ['Salutation', 'Name', 'Date Of Birth', 'Address', 'Grails Developer']
		table.tbody.tr.collect { it.td[1].text() } == ['Bart Simpson', 'Marge Simpson']
	}

	void "table tag allows to specify the order"() {
		when:
		def output = applyTemplate('<f:table collection="collection" order="name,gender"/>', [collection: personList])
		def table = XML.parse(output)

		then:
		table.thead.tr.th.a.collect { it.text().trim() } == ['Name', 'Gender']
		table.tbody.tr.collect { it.td[0].text() } == ['Bart Simpson', 'Marge Simpson']
		table.tbody.tr.collect { it.td[1].text() } == ['Male', 'Female']
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/257')
	void "table tag allows to specify the except"() {
		when:
		def output = applyTemplate('<f:table collection="collection" except="${except}"  maxProperties="0"/>', [collection: personList, except: except])
		def table = XML.parse(output)

		then: "The first 7 headers should be (limited by maxProperties not being set)"
		table.thead.tr.th.a.collect { it.text().trim() }.sort() == ['Address', 'Biography', 'Date Of Birth', 'Emails', 'Gender', 'Id', 'Name']

		where:
		except << [
			'salutation, grailsDeveloper, picture, anotherPicture, password, lastUpdated, minor',
			'salutation,grailsDeveloper,picture,anotherPicture,password,lastUpdated,minor',
			['salutation', 'grailsDeveloper', 'picture', 'anotherPicture', 'password', 'minor', 'lastUpdated']
		]
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/257')
	void "table tag allows to specify the except as empty will render id and lastUpdated"() {
		when:
		def output = applyTemplate('<f:table collection="collection" except="${except}" maxProperties="0"/>', [collection: personList, except: except])
		def table = XML.parse(output)

		then:
		table.thead.tr.th.a.collect { it.text().trim() }.sort() == [
			'Id', 'Salutation', 'Name', 'Date Of Birth', 'Address', 'Grails Developer', 'Picture',
			'Another Picture', 'Password', 'Biography', 'Emails', 'Gender', 'Last Updated', 'Minor'
		].sort()

		where:
		except << ['', [], null]
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/264')
	void "table tag renders transient columns set by '<f:table collection=\"collection\" properties=\"['transient']\"/>'"() {
		given:
		List expectedTableColumns = ['Name', 'Transient Text']

		when:
		def table = XML.parse(applyTemplate('<f:table collection="collection" properties="${properties}"/>', [collection: personList, properties: ['name', 'transientText']]))
		def renderedTableColumns = table.thead.tr.th.a.collect { it.text().trim() }

		then:
		expectedTableColumns.containsAll(renderedTableColumns)
		renderedTableColumns.containsAll(expectedTableColumns)
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/269')
	void "table tag renders reference columns set by '<f:table collection=\"collection\" properties=\"['transient']\"/>'"() {
		given:
		List expectedTableColumns = ['Name', 'Street']

		when:
		def table = XML.parse(applyTemplate('<f:table collection="collection" properties="${properties}"/>', [collection: personList, properties: ['name', 'address.street']]))
		def renderedTableColumns = table.thead.tr.th.a.collect { it.text().trim() }

		then:
		expectedTableColumns.containsAll(renderedTableColumns)
		renderedTableColumns.containsAll(expectedTableColumns)
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/325')
	void "table tag renders transient columns when using the order attribute '<f:table collection=\"collection\" order=\"['transient']\"/>'"(String order, List expectedTableColumns) {
		when:
		def table = XML.parse(applyTemplate(
			'<f:table collection="collection" order="${order}"/>',
			[
				collection: personList,
				order: order
			]
		))
		def renderedTableColumns = table.thead.tr.th.a.collect { it.text().trim() }

		then:
		renderedTableColumns == expectedTableColumns

		where:
		order                 | expectedTableColumns
		'transientText, name' | ['Transient Text', 'Name']
		'name, transientText' | ['Name', 'Transient Text']
	}

	void "table tag displays embedded properties by default with toString"() {
		when:
		def output = applyTemplate('<f:table collection="collection" properties="[\'address\']"/>', [collection: personList])
		def table = XML.parse(output)

		then:
		table.thead.tr.th.a.collect { it.text().trim() } == ['Address']
		table.tbody.tr.collect { it.td[0].text() } == [address.toString()] * 2
	}

	void "table tag uses custom display template when displayStyle is specified"() {
		given:
		views["/_fields/display/_custom.gsp"] = 'Custom: ${value}'
		mockFormFieldsTemplateService.findTemplate(_, 'displayWidget-custom', _, null) >> [path: '/_fields/display/custom']

		when:

		def output = applyTemplate('<f:table collection="collection" properties="[\'address\']" displayStyle="custom"/>', [collection: personList])
		def table = XML.parse(output)

		then:
		table.thead.tr.th.a.collect { it.text().trim() } == ['Address']
		table.tbody.tr.collect { it.td[0].text() } == ["Custom: $address"] * 2
	}

	void "table tag uses custom display template from theme when displayStyle and theme is specified"() {
		given:
		views["/_fields/_themes/test/display/_custom.gsp"] = 'Theme: ${value}'
		mockFormFieldsTemplateService.findTemplate(_, 'displayWidget-custom', _, "test") >> [path: '/_fields/_themes/test/display/custom']

		when:

		def output = applyTemplate('<f:table collection="collection" properties="[\'address\']" displayStyle="custom" theme="test"/>', [collection: personList])
		def table = XML.parse(output)

		then:
		table.thead.tr.th.a.collect { it.text().trim() } == ['Address']
		table.tbody.tr.collect { it.td[0].text() } == ["Theme: $address"] * 2
	}

	void "table renders full embedded beans when displayStyle= 'default'"() {
		when:

		def output = applyTemplate('<f:table collection="collection" properties="[\'address\']" displayStyle="default"/>', [collection: [bart]])
		def table = XML.parse(output)
		println output

		then:
		table.thead.tr.th.a.collect { it.text().trim() } == ['Address']
		table.tbody.tr.td.a.ol.li.collect { it.div.text() } == [address.city, address.country, address.street]
	}

	void "table renders different template when template is set"() {

		when:
		def output = applyTemplate('<f:table collection="collection" template="alternativeTable"/>', [collection: personList])
		def table = XML.parse(output)

        then:
        table.h1 == 'Alternative Table Template'
	}

    void "table will render the default template if parameter is empty"() {

        when:
		def columns = ['Salutation', 'Name', 'Date Of Birth', 'Address', 'Grails Developer', 'Picture', 'Another Picture']
        def output = applyTemplate('<f:table collection="collection" template=""/>', [collection: personList])
        def table = XML.parse(output)

		then:
		table.thead.tr.th.collect { it.text().trim() } == columns
    }

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/286')
	void "table should pass extra properties to template"() {
		given:
		views["/templates/_fields/_table.gsp"] = '<table>${foo}</table>'

		expect:
		applyTemplate('<f:table collection="personList" foo="bar"/>', [personList: personList]) == '<table>bar</table>'
	}
}
