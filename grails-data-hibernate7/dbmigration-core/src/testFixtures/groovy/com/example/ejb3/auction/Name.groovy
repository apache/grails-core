package com.example.ejb3.auction

import jakarta.persistence.Embeddable

import groovy.transform.CompileStatic

@Embeddable
@CompileStatic
class Name {

    String firstName
    String lastName
    Character initial

    public Name(String first, Character middle, String last) {
        firstName = first
        initial = middle
        lastName = last
    }

    public String toString() {
        StringBuffer buf = new StringBuffer().append(firstName).append(' ')
        if (initial != null)
            buf.append(initial).append(' ')
        return buf.append(lastName).toString()
    }

}
