package com.donguri.employee

import com.donguri.global.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "employee")
class Employee(
    @Column(nullable = false, unique = true, length = 50)
    val username: String,
    /** `{bcrypt}...` 형식의 해시. 평문을 넣지 않는다 */
    @Column(nullable = false)
    var password: String,
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    var role: EmployeeRole,
    @Column(nullable = false, length = 50)
    var department: String,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
