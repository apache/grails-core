package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.bson.Document
import org.bson.types.ObjectId

/**
 * @author Graeme Rocher
 */
class BasicArraySpec extends GormDatastoreSpec{

    void "Test that arrays are saved correctly"() {
        when:"An entity with an array is saved"
            Data data = new Data(str: "foo", strArray: ["foo", "bar"] as String[]).save(flush:true, validate:false)
            session.clear()
            data = Data.findByStr("foo")

        then:"The array is saved correct"
            data.str == "foo"
            data.strArray[0] == "foo"
            data.strArray[1] == 'bar'
    }

    void "Test that arrays of convertible properties are saved correctly"() {
        when:"An entity with an array is saved"
            Data data = new Data(str: "bar", locArray: [Locale.US, Locale.CANADA_FRENCH] as Locale[]).save(flush:true,validate:false)
            session.clear()
            data = Data.findByStr("bar")

        then:"The array is saved correct"
            data.str == "bar"
            data.locArray[0] == Locale.US
            data.locArray[1] == Locale.CANADA_FRENCH
    }

    void "Test that byte arrays are saved as binary"() {
        when:"An entity with an array is saved"
            Data data = new Data(str: "baz", byteArray: 'hello'.bytes).save(flush:true,validate:false)
            session.clear()
            data = Data.findByStr("baz")
            Document dbo = data.dbo

        then:"The array is saved correct"
            data.str == "baz"
            data.byteArray == 'hello'.bytes
            dbo.byteArray.data == 'hello'.bytes
    }

    @Override
    List getDomainClasses() {
        [Data]
    }
}

@Entity
class Data {

    ObjectId id
    String str
    String[] strArray
    Locale[] locArray
    byte[] byteArray

    @Override
    String toString() {
        "Data{id=$id, str='$str', strArray=${(strArray == null ? null : Arrays.asList(strArray))}, locArray=${(locArray == null ? null : Arrays.asList(locArray))}}"
    }
}
