package gorm

class CompanyRelation {
    static belongsTo = [company:Company]
    RelationAddress address
}
