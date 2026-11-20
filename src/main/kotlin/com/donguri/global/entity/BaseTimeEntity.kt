package com.donguri.global.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

/**
 * 생성/수정 시각을 Hibernate 가 자동으로 기록한다. 직접 대입하지 않는다.
 */
@MappedSuperclass
abstract class BaseTimeEntity {
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime
        protected set

    @UpdateTimestamp
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
        protected set
}
