package grails.plugin.formfields.mock

class Address {
	String street
	String city
	String country

	@Override
	String toString() { "$street, $city - $country" }

	static constraints = {
		street blank: false, order: 1
		city blank: false, order: 2
		country inList: ["USA", "UK", "Canada"], order: 3
	}
}
