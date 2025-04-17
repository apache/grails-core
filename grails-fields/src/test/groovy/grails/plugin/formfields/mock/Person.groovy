package grails.plugin.formfields.mock

import grails.gorm.annotation.AutoTimestamp
import grails.persistence.Entity

@Entity
class Cyborg extends HomoSapiens {
	@AutoTimestamp(AutoTimestamp.EventType.CREATED) Date created
	@AutoTimestamp Date modified
}

@Entity
class Person extends HomoSapiens {}

/*
 	Workaround for problem with domain inheritance in Groovy 4.
 	(Superclasses cannot be Gorm entities).
*/
class HomoSapiens {
    Salutation salutation
	String name
	String password
	Gender gender
	Date dateOfBirth
	Address address
	Map emails = [:]
	boolean minor
	Date lastUpdated
	String excludedProperty
	String displayFalseProperty
	Boolean grailsDeveloper
	Byte[] picture
	byte[] anotherPicture
	String biography

    transient String transientText = "transient text"

	static hasMany = [emails: String]
	static embedded = ['address']

	static constraints = {
        salutation nullable: true
		name blank: false
		dateOfBirth nullable: true
		address nullable: true
		excludedProperty nullable: true
		displayFalseProperty nullable: true, display: false
		grailsDeveloper nullable: true
		picture nullable: true
		anotherPicture nullable: true
		password password: true
		biography nullable: true, widget: 'textarea'
	}

	static scaffold = [exclude: ['excludedProperty']]
    static transients = ['transientText']
	def onLoad = {
		println "loaded"
	}

	@Override
	String toString() {
		name
	}
}
