package grails.gorm.tests


import org.grails.datastore.mapping.proxy.ProxyHandler

class ProxyInitializationSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Patient, ContactDetails]
    }

    void "test if proxy is initialized"() {

        setup:
        final ProxyHandler proxyHandler = session.mappingContext.getProxyHandler()
        ContactDetails contactDetails = new ContactDetails(phoneNumber: "+1-202-555-0178").save(failOnError: true)
        Long patientId = new Patient(contactDetails: contactDetails).save(failOnError: true).id
        session.flush()
        session.clear()

        when:
        Patient patient = Patient.get(patientId)

        then:
        proxyHandler.isProxy(patient.contactDetails)

        when:
        patient.contactDetails.phoneNumber = "+1-202-555-0178"

        then:
        proxyHandler.isInitialized(patient.contactDetails)

        cleanup:
        Patient.deleteAll(patient)
        ContactDetails.deleteAll(patient.contactDetails)
    }
}
