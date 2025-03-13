package grails.gorm.tests.compositeid

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.mapping.MappingBuilder
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

@Rollback
class CompositeIdCriteria extends Specification {

  @Shared
  @AutoCleanup
  HibernateDatastore datastore = new HibernateDatastore(CompositeIdToMany, CompositeIdSimple, Author, Book)

  @Issue("https://github.com/grails/gorm-hibernate5/issues/234")
  def "test that composite to-many properties can be queried using JPA"() {
    Author _author = new Author(name:"Author").save()
    Book _book = new Book(title:"Book").save()
    CompositeIdToMany compositeIdToMany = new CompositeIdToMany(author:_author, book:_book).save(failOnError:true, flush:true)

    def criteriaBuilder = datastore.sessionFactory.criteriaBuilder
    def criteriaQuery = criteriaBuilder.createQuery()
    def root = criteriaQuery.from(CompositeIdToMany)
    criteriaQuery.select(root)
    criteriaQuery.where(criteriaBuilder.equal(root.get("author"), _author))
    def query = datastore.sessionFactory.currentSession.createQuery(criteriaQuery)

    expect:
    query.list() == [compositeIdToMany]
  }

  def "test that composite can be queried using JPA"() {
    CompositeIdSimple compositeIdSimple = new CompositeIdSimple(name:"name", age:2l).save(failOnError:true, flush:true)

    def criteriaBuilder = datastore.sessionFactory.criteriaBuilder
    def criteriaQuery = criteriaBuilder.createQuery()
    def root = criteriaQuery.from(CompositeIdSimple)
    criteriaQuery.select(root)
    criteriaQuery.where(criteriaBuilder.equal(root.get("name"), "name"))
    def query = datastore.sessionFactory.currentSession.createQuery(criteriaQuery)

    expect:
    query.list() == [compositeIdSimple]
  }

  @Issue("https://github.com/grails/grails-data-mapping/issues/1351")
  def "test that composite to-many can be used in criteria"() {
    Author _author = new Author(name:"Author").save()
    Book _book = new Book(title:"Book").save()
    CompositeIdToMany compositeIdToMany = new CompositeIdToMany(author:_author, book:_book).save(failOnError:true, flush:true)

    expect:
    CompositeIdToMany.createCriteria().list {
      author {
        eq('id', _author.id)
      }
    } == [compositeIdToMany]
  }
}

@Entity
class Author {
  String name
}

@Entity
class Book {
  String title
}

@Entity
class CompositeIdToMany implements Serializable {
  Author author
  Book book

  static mapping = MappingBuilder.define {
    composite("author", "book")
  }
}

@Entity
class CompositeIdSimple implements Serializable {
  String name
  Long age

  static mapping = MappingBuilder.define {
    composite("name", "age")
  }
}


