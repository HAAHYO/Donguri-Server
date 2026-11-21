package com.donguri.global.entity

import com.donguri.domain.careaction.entity.CareAction
import com.donguri.domain.careaction.entity.CareActionSendHistory
import com.donguri.domain.careaction.entity.CareActionStatus
import com.donguri.domain.customer.entity.Customer
import com.donguri.domain.customer.entity.CustomerGrade
import com.donguri.domain.employee.entity.Employee
import com.donguri.domain.employee.entity.EmployeeRole
import com.donguri.domain.transaction.entity.FdsStatus
import com.donguri.domain.transaction.entity.Transaction
import jakarta.persistence.EntityManager
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.mapping.Table
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class FlywaySchemaConsistencyTest(
    @Autowired private val em: EntityManager,
) {
    @Test
    fun `마이그레이션의 컬럼, nullable, 길이, 정밀도는 엔티티와 같다`() {
        withMetadata { metadata ->
            tables(metadata).forEach { table ->
                val actual = columns(table.name)
                assertEquals(table.columns.map { it.name }.toSet(), actual.keys, "${table.name} 컬럼")
                table.columns.forEach { column ->
                    val info = actual.getValue(column.name)
                    val label = "${table.name}.${column.name}"
                    assertEquals(column.isNullable, info.nullable, "$label nullable")
                    if (info.type == "CHARACTER VARYING" && column.name != "shap_reasons") {
                        assertEquals(column.length, info.length, "$label 길이")
                    }
                    if (info.type == "NUMERIC" || info.type == "DECIMAL") {
                        assertEquals(column.precision, info.precision, "$label precision")
                        assertEquals(column.scale, info.scale, "$label scale")
                    }
                }
            }
        }
    }

    @Test
    fun `마이그레이션의 단일 컬럼 unique 는 엔티티와 같다`() {
        withMetadata { metadata ->
            tables(metadata).forEach { table ->
                val expected =
                    table.columns
                        .filter { column ->
                            column.isUnique || table.uniqueKeys.values.any { it.columns.singleOrNull() == column }
                        }.map { it.name }
                        .toSet()
                assertEquals(expected, uniqueColumns(table.name), "${table.name} unique")
            }
        }
    }

    @Test
    fun `Enum CHECK 제약의 허용 값은 Enum 상수와 같다`() {
        val expected =
            mapOf(
                ("employee" to "role") to EmployeeRole.entries.map { it.name },
                ("customer" to "grade") to CustomerGrade.entries.map { it.name },
                ("transactions" to "fds_status") to FdsStatus.entries.map { it.name },
                ("care_action" to "status") to CareActionStatus.entries.map { it.name },
            )

        expected.forEach { (target, constants) ->
            val (table, column) = target
            val clause = checkClauses(table).single { it.contains("\"$column\"") }
            val allowed = Regex("'([A-Z_]+)'").findAll(clause).map { it.groupValues[1] }.toSet()
            assertEquals(constants.toSet(), allowed, "$table.$column")
        }
    }

    private fun withMetadata(block: (Metadata) -> Unit) {
        val registry =
            StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.physical_naming_strategy", PhysicalNamingStrategySnakeCaseImpl::class.java.name)
                .build()
        try {
            val sources = MetadataSources(registry)
            ENTITIES.forEach { sources.addAnnotatedClass(it) }
            block(sources.buildMetadata())
        } finally {
            StandardServiceRegistryBuilder.destroy(registry)
        }
    }

    private fun tables(metadata: Metadata): List<Table> = metadata.entityBindings.map { it.table }

    private fun columns(table: String): Map<String, ColumnInfo> =
        em
            .createNativeQuery(
                "select lower(column_name), is_nullable, upper(data_type), character_maximum_length, " +
                    "numeric_precision, numeric_scale from information_schema.columns " +
                    "where lower(table_schema) = 'public' and lower(table_name) = :table",
            ).setParameter("table", table)
            .resultList
            .map { it as Array<*> }
            .associate { row ->
                row[0].toString() to
                    ColumnInfo(
                        nullable = row[1].toString().equals("YES", ignoreCase = true),
                        type = row[2].toString(),
                        length = (row[3] as Number?)?.toLong(),
                        precision = (row[4] as Number?)?.toInt(),
                        scale = (row[5] as Number?)?.toInt(),
                    )
            }

    private fun uniqueColumns(table: String): Set<String> =
        em
            .createNativeQuery(
                "select lower(k.column_name) from information_schema.table_constraints c " +
                    "join information_schema.key_column_usage k on k.constraint_name = c.constraint_name " +
                    "and k.constraint_schema = c.constraint_schema " +
                    "where c.constraint_type = 'UNIQUE' and lower(c.table_schema) = 'public' and lower(c.table_name) = :table",
            ).setParameter("table", table)
            .resultList
            .map { it.toString() }
            .toSet()

    private fun checkClauses(table: String): List<String> =
        em
            .createNativeQuery(
                "select cc.check_clause from information_schema.check_constraints cc " +
                    "join information_schema.table_constraints tc on tc.constraint_name = cc.constraint_name " +
                    "and tc.constraint_schema = cc.constraint_schema " +
                    "where lower(tc.table_schema) = 'public' and lower(tc.table_name) = :table",
            ).setParameter("table", table)
            .resultList
            .map { it.toString() }

    private data class ColumnInfo(
        val nullable: Boolean,
        val type: String,
        val length: Long?,
        val precision: Int?,
        val scale: Int?,
    )

    private companion object {
        val ENTITIES =
            listOf(
                Employee::class.java,
                Customer::class.java,
                Transaction::class.java,
                CareAction::class.java,
                CareActionSendHistory::class.java,
            )
    }
}
