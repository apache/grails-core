package grails.plugin.formfields.mock

import grails.persistence.Entity

@Entity
class Author {
	
	String name
	List<Book> books
	String placeOfBirth

	@Override
	String toString() { name }
	static hasMany = [books: Book]
	
	static constraints = {
		name blank: false
		placeOfBirth nullable: true
	}
}
