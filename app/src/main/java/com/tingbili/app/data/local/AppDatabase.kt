package com.tingbili.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BookRecord::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookRecordDao(): BookRecordDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "bili_ting.db"
                )
                    // 先 migrations 升级；万一迁移异常时直接重建（会丢本地历史/收藏，先保证能打开）。
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }

        /** v1 → v2：新增 cover 列（封面 URL） */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_records ADD COLUMN cover TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v2 → v3：增加 ownerMid（UP主 mid） */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_records ADD COLUMN ownerMid INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3 → v4：增加 ownerAvatar（UP主头像 URL） */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_records ADD COLUMN ownerAvatar TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4 → v5：增加 speed（每条记录上次播放倍速，resume 时恢复） */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_records ADD COLUMN speed REAL NOT NULL DEFAULT 1.0")
            }
        }
    }
}