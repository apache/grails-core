package gorm

class Company {
    CompanyRelation relation

    static constraints = {
        relation nullable:true
    }
}
