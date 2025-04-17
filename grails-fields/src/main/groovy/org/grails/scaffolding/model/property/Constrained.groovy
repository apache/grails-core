package org.grails.scaffolding.model.property


class Constrained {

    grails.gorm.validation.Constrained constrained1

    Constrained(grails.gorm.validation.Constrained constrained1) {
        this.constrained1 = constrained1
    }

    Object callMethod(String name, Object arguments) {
        if (this.constrained1 != null) {
            this.constrained1.invokeMethod(name, arguments)
        } else {
            null
        }
    }

    boolean hasAppliedConstraint(String name) {
        if (this.constrained1 != null) {
            this.constrained1.hasAppliedConstraint(name)
        } else {
            false
        }
    }

    int getOrder() {
        if (this.constrained1 != null) {
            this.constrained1.order
        } else {
            0
        }
    }

    boolean isNullable() {
        if (this.constrained1 != null) {
            this.constrained1.nullable
        } else {
            false
        }
    }

    boolean isBlank() {
        if (this.constrained1 != null) {
            this.constrained1.blank
        } else {
            false
        }
    }

    boolean isDisplay() {
        if (this.constrained1 != null) {
            this.constrained1.display
        } else {
            true
        }
    }

    boolean isEditable() {
        if (this.constrained1 != null) {
            this.constrained1.editable
        } else {
            true
        }
    }
    
    List getInList() {
        if (this.constrained1 != null) {
            this.constrained1.inList
        } else {
            null
        }
    }

    Range getRange() {
        if (this.constrained1 != null) {
            this.constrained1.range
        } else {
            null
        }
    }

    Integer getScale() {
        if (this.constrained1 != null) {
            this.constrained1.scale
        } else {
            null
        }
    }

    Comparable getMin() {
        if (this.constrained1 != null) {
            this.constrained1.min
        } else {
            null
        }
    }

    Comparable getMax() {
        if (this.constrained1 != null) {
            this.constrained1.max
        } else {
            null
        }
    }

    Range getSize() {
        if (this.constrained1 != null) {
            this.constrained1.size
        } else {
            null
        }
    }

    Integer getMaxSize() {
        if (this.constrained1 != null) {
            this.constrained1.maxSize
        } else {
            null
        }
    }

    String getWidget() {
        if (this.constrained1 != null) {
            this.constrained1.widget
        } else {
            null
        }
    }

    boolean isPassword() {
        if (this.constrained1 != null) {
            this.constrained1.password
        } else {
            false
        }
    }

    boolean isEmail() {
        if (this.constrained1 != null) {
            this.constrained1.email
        } else {
            false
        }
    }

    boolean isCreditCard() {
        if (this.constrained1 != null) {
            this.constrained1.creditCard
        } else {
            false
        }
    }

    boolean isUrl() {
        if (this.constrained1 != null) {
            this.constrained1.url
        } else {
            false
        }
    }

    String getMatches() {
        if (this.constrained1 != null) {
            this.constrained1.matches
        } else {
            null
        }
    }

    Object getNotEqual() {
        if (this.constrained1 != null) {
            this.constrained1.notEqual
        } else {
            null
        }
    }

    Integer getMinSize() {
        if (this.constrained1 != null) {
            this.constrained1.minSize
        } else {
            null
        }
    }

    String getFormat() {
        if (this.constrained1 != null) {
            this.constrained1.format
        } else {
            null
        }
    }

    void applyConstraint(String constraintName, Object constrainingValue) {
        if (this.constrained1 != null) {
            this.constrained1.applyConstraint(constraintName, constrainingValue)
        } else {
            null
        }
    }

    Class getOwner() {
        if (this.constrained1 != null) {
            this.constrained1.owner
        } else {
            null
        }
    }

    boolean isNull() {
        this.constrained1 == null 
    }
}
