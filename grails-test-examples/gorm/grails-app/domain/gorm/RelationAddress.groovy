package gorm

class RelationAddress {
    CompanyRelation relation

    static belongsTo = [relation:CompanyRelation]

}