package com.aether.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: String,
    val primaryLanguage: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "project_memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val category: String, // ARCHITECTURE, SECURITY, DECISION, SNIPPET
    val title: String,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_findings")
data class AuditFindingEntity(
    @PrimaryKey val id: String,
    val filePath: String,
    val lineNumber: Int,
    val severity: String,
    val title: String,
    val description: String,
    val timestampMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "tool_logs")
data class ToolLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolName: String,
    val command: String,
    val exitCode: Int,
    val durationMs: Long,
    val timestampMs: Long = System.currentTimeMillis()
)

@Dao
interface AetherDao {
    @Query("SELECT * FROM projects ORDER BY updatedAtMs DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Query("SELECT * FROM project_memories ORDER BY timestampMs DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Query("SELECT * FROM audit_findings ORDER BY timestampMs DESC")
    fun getAllFindings(): Flow<List<AuditFindingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFindings(findings: List<AuditFindingEntity>)

    @Query("DELETE FROM audit_findings")
    suspend fun clearFindings()

    @Query("SELECT * FROM tool_logs ORDER BY id DESC LIMIT 50")
    fun getRecentToolLogs(): Flow<List<ToolLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToolLog(log: ToolLogEntity)
}

@Database(
    entities = [
        ProjectEntity::class,
        MemoryEntity::class,
        AuditFindingEntity::class,
        ToolLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AetherDatabase : RoomDatabase() {
    abstract fun dao(): AetherDao

    companion object {
        @Volatile
        private var INSTANCE: AetherDatabase? = null

        fun getInstance(context: Context): AetherDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AetherDatabase::class.java,
                    "aether_workstation_secure.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
