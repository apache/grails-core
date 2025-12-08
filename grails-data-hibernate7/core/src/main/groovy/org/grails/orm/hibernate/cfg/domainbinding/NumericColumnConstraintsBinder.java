package org.grails.orm.hibernate.cfg.domainbinding;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.hibernate.mapping.Column;

import java.math.BigDecimal;
import java.util.Optional;

public class NumericColumnConstraintsBinder {

    public void bindNumericColumnConstraints(Column column, ColumnConfig cc, PropertyConfig constrainedProperty) {
        int scale = determineScale(cc, constrainedProperty);
        if (scale > -1) {
            column.setScale(scale);
        } else {
            scale = org.hibernate.engine.jdbc.Size.DEFAULT_SCALE; // Ensure scale is non-negative for calculations
        }
        if( cc != null && cc.getPrecision() > -1) {
            column.setPrecision(cc.getPrecision());
        }
        else {
            int minConstraintValueLength = getConstraintValueLength(constrainedProperty.getMin(), scale);
            int maxConstraintValueLength = getConstraintValueLength(constrainedProperty.getMax(), scale);
            int precision = minConstraintValueLength > 0 && maxConstraintValueLength > 0  ?
                    Math.max(minConstraintValueLength, maxConstraintValueLength) :
                    DefaultGroovyMethods.max(new Integer[]{org.hibernate.engine.jdbc.Size.DEFAULT_PRECISION, minConstraintValueLength, maxConstraintValueLength});
            column.setPrecision(precision);
        }
    }

    private int getConstraintValueLength(Comparable min, int scale) {
        return min instanceof Number number ?
                Math.max(countDigits(number), countDigits((number).longValue()) + scale) : 0;
    }

    private int countDigits(Number number) {
        return Optional.ofNullable(number)
                .map(n -> new BigDecimal(n.toString()).precision())
                .orElse(0);
    }

    private int determineScale(ColumnConfig cc, PropertyConfig constrainedProperty) {
        Optional.ofNullable(cc).map(ColumnConfig::getScale).filter(scale -> scale > -1)
                .orElseGet(() -> Optional.ofNullable(constrainedProperty)
                        .map(PropertyConfig::getScale)
                        .filter(scale -> scale > -1)
                        .orElse(-1));
        if (cc != null && cc.getScale() > -1) {
            return cc.getScale();
        }
        if (constrainedProperty != null && constrainedProperty.getScale() > -1) {
            return constrainedProperty.getScale();
        }
        return -1;
    }

}
