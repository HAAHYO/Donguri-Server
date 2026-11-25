package com.safeflow.domain.transaction.entity

import com.safeflow.domain.customer.entity.Customer
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TransactionMysqlSchemaTest {
    @Test
    fun `MySQL 에서 shapReasons 컬럼은 longtext 로 생성된다`() {
        val registry =
            StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build()
        try {
            val metadata =
                MetadataSources(registry)
                    .addAnnotatedClass(Customer::class.java)
                    .addAnnotatedClass(Transaction::class.java)
                    .buildMetadata()
            val column =
                metadata
                    .getEntityBinding(Transaction::class.java.name)
                    .table
                    .columns
                    .single { it.name.equals("shapReasons", ignoreCase = true) }

            assertEquals("longtext", column.getSqlType(metadata).lowercase())
        } finally {
            StandardServiceRegistryBuilder.destroy(registry)
        }
    }
}
