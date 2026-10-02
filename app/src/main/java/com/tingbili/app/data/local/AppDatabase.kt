package com.tingbili.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BookRecord::class, CrashLog::class], version = 9, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookRecordDao(): BookRecordDao
    abstract fun crashLogDao(): CrashLogDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "bili_ting.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_records ADD COLUMN cover TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_records ADD COLUMN ownerMid INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_records ADD COLUMN ownerAvatar TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_records ADD COLUMN speed REAL NOT NULL DEFAULT 1.0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_records ADD COLUMN isFinished INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE book_records ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v6 → v7：听单标签（tag） */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_records ADD COLUMN tag TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v7 → v8：
         *  1) book_records 加睡前标记三列（markPart/markMs/markAt，均可空）；
         *  2) 新建 crash_logs 全局异常兜底表。
         * 两条 SQL 同一次迁移并列执行（功能1 的 book_records 迁移与功能3 的 crash_logs 合并到同一版本）。
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_records ADD COLUMN markPart INTEGER")
                db.execSQL("ALTER TABLE book_records ADD COLUMN markMs INTEGER")
                db.execSQL("ALTER TABLE book_records ADD COLUMN markAt INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS crash_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        threadName TEXT NOT NULL,
                        exceptionType TEXT NOT NULL,
                        message TEXT,
                        stackTrace TEXT NOT NULL,
                        deviceModel TEXT NOT NULL,
                        androidVersion TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** v8 → v9：跳过片头秒数（每本书一个值，0 = 不跳） */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_records ADD COLUMN introSec INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
