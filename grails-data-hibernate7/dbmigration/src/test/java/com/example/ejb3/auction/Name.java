package com.example.ejb3.auction;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Embeddable
public class Name {
    private String firstName;
    private String lastName;
    private Character initial;

    public Name(String first, Character middle, String last) {
        firstName = first;
        initial = middle;
        lastName = last;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer().append(firstName).append(' ');
        if (initial != null)
            buf.append(initial).append(' ');
        return buf.append(lastName).toString();
    }

}
