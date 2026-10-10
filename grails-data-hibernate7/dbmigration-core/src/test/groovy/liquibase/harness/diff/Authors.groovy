package liquibase.harness.diff

import java.sql.Date
import java.sql.Timestamp

import groovy.transform.CompileStatic

@CompileStatic
class Authors {

    int id
    String firstName
    String lastName
    String email
    Date birthdate
    Timestamp added

    Authors() {
    }

    Authors(int id, String firstName, String lastName, String email, Date birthdate, Timestamp added) {
        this.id = id
        this.firstName = firstName
        this.lastName = lastName
        this.email = email
        this.birthdate = birthdate
        this.added = added
    }

    int getId() {
        return id
    }

    void setId(int id) {
        this.id = id
    }

    String getFirstName() {
        return firstName
    }

    void setFirstName(String firstName) {
        this.firstName = firstName
    }

    String getLastName() {
        return lastName
    }

    void setLastName(String lastName) {
        this.lastName = lastName
    }

    String getEmail() {
        return email
    }

    void setEmail(String email) {
        this.email = email
    }

    Date getBirthdate() {
        return birthdate
    }

    void setBirthdate(Date birthdate) {
        this.birthdate = birthdate
    }

    Timestamp getAdded() {
        return added
    }

    void setAdded(Timestamp added) {
        this.added = added
    }

}
