package grails.gorm.tck

import grails.gorm.annotation.Entity

@Entity
class TestAuthor implements Serializable {

    Long id
    String name

    @Override
    boolean equals(o) {
        if (!(o instanceof TestAuthor)) return false
        if (this.is(o)) return true
        TestAuthor that = (TestAuthor) o
        if (id !=null && that.id !=null) return id == that.id
        return false
    }
}