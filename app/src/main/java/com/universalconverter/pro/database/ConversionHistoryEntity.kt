package com.universalconverter.pro.database

import androidx.room.*

@Entity(tableName = "conversion_history")
data class ConversionHistoryEntity(
    @PrimaryKey val id: String,
    val inputName: String,
    val outputPath: String,
    val outputFormat: String,
    val inputSizeBytes: Long,
    val outputSizeBytes: Long,
    val compressionPercent: Int,
    val status: String,
    val createdAt: Long,
    val completedAt: Long,
    val ssimScore: Float = -1f,
    val conversionType: String = "IMAGE" // IMAGE, PDF, MEDIA, THREED
)

@Dao
interface ConversionHistoryDao {
    @Query("SELECT * FROM conversion_history ORDER BY createdAt DESC LIMIT 100")
    fun getAll(): List<ConversionHistoryEntity>

    @Query("SELECT * FROM conversion_history WHERE status = 'SUCCESS' ORDER BY createdAt DESC LIMIT 100")
    fun getSuccessful(): List<ConversionHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: ConversionHistoryEntity)

    @Delete
    fun delete(item: ConversionHistoryEntity)

    @Query("DELETE FROM conversion_history WHERE createdAt < :cutoff")
    fun deleteOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM conversion_history WHERE status = 'SUCCESS'")
    fun getTotalCount(): Int

    @Query("SELECT SUM(inputSizeBytes - outputSizeBytes) FROM conversion_history WHERE status = 'SUCCESS' AND outputSizeBytes < inputSizeBytes")
    fun getTotalBytesSaved(): Long?

    @Query("SELECT * FROM conversion_history WHERE conversionType = :type ORDER BY createdAt DESC")
    fun getByType(type: String): List<ConversionHistoryEntity>
}

@Database(entities = [ConversionHistoryEntity::class], version = 1, exportSchema = false)
abstract class ConverterDatabase : androidx.room.RoomDatabase() {
    abstract fun historyDao(): ConversionHistoryDao
    companion object {
        @Volatile private var INSTANCE: ConverterDatabase? = null
        fun getInstance(ctx: android.content.Context): ConverterDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(ctx.applicationContext, ConverterDatabase::class.java, "converter_db")
                    .fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
