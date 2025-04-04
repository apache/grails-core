package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

class UUIDTypeIdentifierSpec extends GormDatastoreSpec {

  void "Test that an id with type of java.util.UUID is correctly generated"() {
    when:"A domain with a UUID is saved"
      def dm = new SimpleUUIDModel(name: "My Doc").save()

    then:"The UUID is correctly generated"
      dm != null
      dm.id != null
      SimpleUUIDModel.count() == 1

    when:"Another entity is saved"
      new SimpleUUIDModel(name: "Another").save()
    then:"There are 2"
      SimpleUUIDModel.count() == 2
  }

  @Override
  List getDomainClasses() {
    [SimpleUUIDModel]
  }
}

@Entity
class SimpleUUIDModel  {

  UUID id
  String name

  static mapping = {
    id generator:'uuid'
  }

  static constraints = {
    name blank: false
  }
}
