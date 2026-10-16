package com.example.pojo.auction

import groovy.transform.CompileStatic

@CompileStatic
class Name {

    String firstName
    String lastName
    Character initial

    Name(String first, Character middle, String last) {
        firstName = first
        initial = middle
        lastName = last
    }

    String toString() {
        StringBuffer buf = new StringBuffer().append(firstName).append(' ')
        if (initial != null)
            buf.append(initial).append(' ')
        return buf.append(lastName).toString()
    }

}
