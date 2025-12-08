package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.config.Property;
import org.hibernate.mapping.Column;

import java.util.Objects;
import java.util.Optional;

public class StringColumnConstraintsBinder {


    public void bindStringColumnConstraints(Column column, Property mappedForm) {
        Integer number = Optional.ofNullable(mappedForm.getMaxSize())
                .map(Number::intValue)
                .orElse(
                        getMax(mappedForm).orElse(0)
                );
        if (number > 0) {
            column.setLength(number);
        }

    }

    private  Optional<Integer> getMax(Property mappedForm) {
        return Optional.ofNullable(mappedForm.getInList())
                .flatMap(list -> list.stream()
                .map(this::parseInt)
                .filter(Objects::nonNull)
                .reduce(Integer::max));
    }

    private Integer parseInt(String value) {
        try{
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
