package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.mapping.Column
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class NumericColumnConstraintsBinderSpec extends Specification {

    @Subject
    NumericColumnConstraintsBinder binder = new NumericColumnConstraintsBinder()



    void "should use scale and precision from ColumnConfig when provided"() {
        given: "A column config with explicit scale and precision"
        def column = Mock(Column)
        def property = Mock(PersistentProperty)
        def columnConfig = new ColumnConfig(scale: 4, precision: 12)

        when: "The binder is invoked"
        binder.bindNumericColumnConstraints(column, columnConfig, Mock(PropertyConfig))

        then: "The column's scale and precision are set directly from the column config"
        1 * column.setScale(4)
        1 * column.setPrecision(12)
        0 * column.setPrecision(org.hibernate.engine.jdbc.Size.DEFAULT_PRECISION)
    }

    void "should use scale from PropertyConfig when ColumnConfig is not provided"() {
        given: "A property config with a scale constraint"
        def column = Mock(Column)
        def propertyConfig = Mock(PropertyConfig)
        propertyConfig.getScale() >> 3

        when: "The binder is invoked without a column config"
        binder.bindNumericColumnConstraints(column, null, propertyConfig)

        then: "The column's scale is set from the property config"
        1 * column.setScale(3)
    }

    @Unroll
    void "should calculate precision based on min=#minVal, max=#maxVal, and scale=#scale"() {
        given: "A property config with various min/max/scale constraints"
        def column = Mock(Column)
        def propertyConfig = Mock(PropertyConfig)

        propertyConfig.getScale() >> scale
        propertyConfig.getMin() >> minVal
        propertyConfig.getMax() >> maxVal

        when: "The binder is invoked"
        binder.bindNumericColumnConstraints(column, null, propertyConfig)

        then: "The precision is calculated correctly and set on the column"
        1 * column.setPrecision(expectedPrecision)

        and: "the scale is set correctly based on whether it was provided"
        if (scale > -1) {
            1 * column.setScale(scale)
        }
        else {
            0 * column.setScale(_)
        }

        where:
        minVal    | maxVal    | scale || expectedPrecision
        // --- Both min and max are set ---
        10        | 999       | 2     || 5  // max(len(10)+2, len(999)+2) -> max(4, 5) -> 5
        10000     | 999       | 2     || 7  // max(len(10000)+2, len(999)+2) -> max(7, 5) -> 7
        -50.5     | 99.99     | 2     || 4  // countDigits(-50.5) is 2. (2+2)=4. countDigits(99.99) is 2. (2+2)=4. max(4,4)=4
        -999      | -100      | 0     || 3  // max(len(-999), len(-100)) -> max(3, 3) -> 3

        // --- Only one constraint is set ---
        null      | 12.345    | 4     || 19 // max(19, 0, len(12)+4) -> max(19, 6) -> 19
        null      | 987654321 | 0     || 19 // max(19, 0, len(987654321)) -> max(19, 9) -> 19
        -500      | null      | 3     || 19 // max(19, len(-500)+3, 0) -> max(19, 3+3, 0) -> 19

        // --- Non-numeric constraints are ignored ---
        10        | "abc"     | 2     || 19 // max(19, len(10)+2, 0) -> max(19, 4, 0) -> 19
        "abc"     | 999       | 2     || 19 // max(19, 0, len(999)+2) -> max(19, 0, 5) -> 19

//         --- No constraints to determine precision ---
        null      | null      | -1    || 19 // max(19, 0, 0) -> 19
    }

    void "should use default precision and scale when no constraints are provided"() {
        given: "A property config with no relevant constraints"
        def column = Mock(Column)
        def propertyConfig = Mock(PropertyConfig)
        def defaultPrecision = org.hibernate.engine.jdbc.Size.DEFAULT_PRECISION // 19
        def defaultScale = org.hibernate.engine.jdbc.Size.DEFAULT_SCALE // 0

        propertyConfig.getScale() >> -1
        propertyConfig.getMin() >> null
        propertyConfig.getMax() >> null

        when: "The binder is invoked"
        binder.bindNumericColumnConstraints(column, null, propertyConfig)

        then: "The column's precision and scale are set to their defaults"
        1 * column.setPrecision(defaultPrecision)
        // The code sets the default scale only if no other scale is found.
        // The initial value of the local 'scale' variable is the default.
        // The code doesn't explicitly call setScale(DEFAULT_SCALE).
        // This is a subtle point, the test should reflect what the code *does*.
        // The code only calls setScale if a constraint is found.
        0 * column.setScale(_)
    }
}