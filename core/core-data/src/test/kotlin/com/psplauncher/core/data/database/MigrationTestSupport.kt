package com.psplauncher.core.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry

fun migrationTestHelper(databaseName: String): MigrationTestHelper {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    return MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath(databaseName),
        driver = AndroidSQLiteDriver(),
        databaseClass = PFPDatabase::class,
    )
}

fun <T> SQLiteConnection.rows(sql: String, map: (SQLiteStatement) -> T): List<T> =
    prepare(sql).use { stmt ->
        buildList { while (stmt.step()) add(map(stmt)) }
    }

fun <T> SQLiteConnection.singleRow(sql: String, map: (SQLiteStatement) -> T): T =
    prepare(sql).use { stmt ->
        check(stmt.step()) { "query returned no rows: $sql" }
        map(stmt)
    }

fun SQLiteConnection.count(sql: String): Int = singleRow(sql) { it.getLong(0).toInt() }
