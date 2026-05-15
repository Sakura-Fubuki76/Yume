package com.sakurafubuki.yume.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

class MediaDatabaseMigrationTest {

    @Test
    fun migrate5To6_preserves_existing_tables_and_adds_webdav_video_metadata() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)

        val helper = createV5Helper(TEST_DB)
        val database = helper.writableDatabase
        database.apply {
            execSQL(
                """
                INSERT INTO webdav_servers (id, name, url, username, base_path, created_at)
                VALUES (1, 'demo', 'https://dav.example.com', 'user', '/', 123)
                """.trimIndent(),
            )
        }

        MediaDatabase.MIGRATION_5_6.migrate(database)

        database.apply {
            query("SELECT COUNT(*) FROM webdav_servers WHERE id = 1").use { cursor ->
                assert(cursor.moveToFirst())
                assert(cursor.getInt(0) == 1)
            }

            query("PRAGMA table_info(`webdav_video_metadata`)").use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                }
                assert(columns.contains("server_id"))
                assert(columns.contains("href"))
                assert(columns.contains("duration_ms"))
                assert(columns.contains("thumbnail_path"))
                assert(columns.contains("updated_at"))
            }

            execSQL(
                """
                INSERT INTO webdav_video_metadata (server_id, href, duration_ms, thumbnail_path, updated_at)
                VALUES (1, 'https://dav.example.com/movie.mp4', 7000, '/tmp/movie.jpg', 456)
                """.trimIndent(),
            )

            query("SELECT duration_ms, thumbnail_path FROM webdav_video_metadata WHERE server_id = 1").use { cursor ->
                assert(cursor.moveToFirst())
                assert(cursor.getLong(0) == 7000L)
                assert(cursor.getString(1) == "/tmp/movie.jpg")
            }
        }

        database.close()
        helper.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migrate7To8_preserves_existing_folder_metadata_and_adds_counts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)

        val helper = createV7Helper(TEST_DB)
        val database = helper.writableDatabase
        database.execSQL(
            """
            INSERT INTO webdav_folder_metadata (server_id, folder_path, total_duration_ms, total_size, updated_at)
            VALUES (1, '/movies', 120000, 4096, 456)
            """.trimIndent(),
        )

        MediaDatabase.MIGRATION_7_8.migrate(database)

        database.query("PRAGMA table_info(`webdav_folder_metadata`)").use { cursor ->
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            assert(columns.contains("media_count"))
            assert(columns.contains("folder_count"))
        }

        database.query(
            """
            SELECT total_duration_ms, total_size, media_count, folder_count
            FROM webdav_folder_metadata
            WHERE server_id = 1 AND folder_path = '/movies'
            """.trimIndent(),
        ).use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getLong(0) == 120000L)
            assert(cursor.getLong(1) == 4096L)
            assert(cursor.getInt(2) == 0)
            assert(cursor.getInt(3) == 0)
        }

        database.execSQL(
            """
            INSERT INTO webdav_folder_metadata (server_id, folder_path, total_duration_ms, total_size, media_count, folder_count, updated_at)
            VALUES (1, '/tv', 60000, 2048, 8, 3, 789)
            """.trimIndent(),
        )

        database.query(
            """
            SELECT media_count, folder_count
            FROM webdav_folder_metadata
            WHERE server_id = 1 AND folder_path = '/tv'
            """.trimIndent(),
        ).use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getInt(0) == 8)
            assert(cursor.getInt(1) == 3)
        }

        database.close()
        helper.close()
        context.deleteDatabase(TEST_DB)
    }

    private fun createV5Helper(name: String): SupportSQLiteOpenHelper {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = FrameworkSQLiteOpenHelperFactory()
        val callback = object : SupportSQLiteOpenHelper.Callback(5) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `webdav_servers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `base_path` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        return factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(callback)
                .build(),
        )
    }

    private fun createV7Helper(name: String): SupportSQLiteOpenHelper {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = FrameworkSQLiteOpenHelperFactory()
        val callback = object : SupportSQLiteOpenHelper.Callback(7) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `webdav_folder_metadata` (
                        `server_id` INTEGER NOT NULL,
                        `folder_path` TEXT NOT NULL,
                        `total_duration_ms` INTEGER NOT NULL,
                        `total_size` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`server_id`, `folder_path`)
                    )
                    """.trimIndent(),
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        return factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(callback)
                .build(),
        )
    }

    private companion object {
        private const val TEST_DB = "migration-test-db"
    }
}
