package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.proxy.ProxyHandler

class BuiltinUniqueConstraintWorksWithTargetProxiesConstraintsSpec extends GormDatastoreSpec {

    void "test unique constraint on root instance"() {

        setup:
        ContactDetails contactDetails1 = new ContactDetails(phoneNumber: "+1-202-555-0105").save(failOnError: true)
        ContactDetails contactDetails2 = new ContactDetails(phoneNumber: "+1-202-555-0105")
        session.flush()
        session.clear()

        when: "I try to validate the another object"
        contactDetails2.validate()

        then: "another should have an error on name because it is duplicated"
        contactDetails2.hasErrors()
        contactDetails2.errors.hasFieldErrors("phoneNumber")
        contactDetails2.errors.getFieldError("phoneNumber").codes.contains("unique.phoneNumber")

        cleanup:
        ContactDetails.deleteAll(contactDetails1)
    }

    void "test unique constraint for the associated child object"() {

        setup:
        ContactDetails contactDetails1 = new ContactDetails(phoneNumber: "+1-202-555-0105").save(failOnError: true)
        Patient patient1 = new Patient(contactDetails: contactDetails1).save(failOnError: true)
        session.flush()
        session.clear()

        when:
        Patient patient2 = new Patient(contactDetails: new ContactDetails(phoneNumber: "+1-202-555-0105"))
        patient2.validate()

        then:
        patient2.hasErrors()
        patient2.errors.hasFieldErrors("contactDetails.phoneNumber")
        patient2.errors.getFieldError("contactDetails.phoneNumber").codes.contains("unique.phoneNumber")

        cleanup:
        Patient.deleteAll(patient1)
        ContactDetails.deleteAll(contactDetails1)
    }

    void "test unique constraint on the unmodified association loaded as initialized proxy"() {

        setup:
        final ProxyHandler proxyHandler = session.mappingContext.getProxyHandler()
        ContactDetails contactDetails = new ContactDetails(phoneNumber: "+1-202-555-0105").save(failOnError: true)
        Patient patient = new Patient(contactDetails: contactDetails).save(failOnError: true)
        Long patientId = patient.id
        session.flush()
        session.clear()

        when:
        patient = Patient.get(patientId)
        patient.contactDetails.phoneNumber = "+1-202-555-0105"

        then:
        proxyHandler.isProxy(patient.contactDetails)

        expect:
        patient.validate()

        cleanup:
        Patient.deleteAll(patient)
        ContactDetails.deleteAll(patient.contactDetails)
    }

    @Override
    List getDomainClasses() {
        [ContactDetails, Patient]
    }
}

@Entity
class ContactDetails implements Serializable {

    String phoneNumber

    static constraints = {
        phoneNumber nullable: false, unique: true
    }
}

@Entity
class Patient implements Serializable {

    ContactDetails contactDetails

    static constraints = {
        contactDetails nullable: false
    }

    static mapping = {
        contactDetails lazy: true
    }
}
