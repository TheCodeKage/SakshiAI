package com.runanywhere.kotlin_starter_example.data

import android.content.ContentValues
import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper

/**
 * Repository for managing incident records with SQLCipher encryption.
 * 
 * Security features:
 * - All data encrypted at rest using AES-256 via SQLCipher
 * - Encryption key derived from user's PIN
 * - No plaintext data stored on disk
 * - Database unreadable without correct PIN
 * 
 * @param context Android context
 * @param passphrase Encryption passphrase (derived from user's PIN)
 */
class IncidentRepository(context: Context, private val passphrase: String) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "saakshi.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "incidents"
        
        // Column names
        private const val COL_ID = "id"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_RAW_TRANSCRIPT = "rawTranscript"
        private const val COL_INCIDENT_TYPE = "incidentType"
        private const val COL_WHO_INVOLVED = "whoInvolved"
        private const val COL_THREAT_DOCUMENTED = "threatDocumented"
        private const val COL_WITNESSES_PRESENT = "witnessesPresent"
        private const val COL_PATTERN_FLAG = "patternFlag"
        private const val COL_SEVERITY_TAG = "severityTag"
        private const val COL_RAW_JSON = "rawJson"
        
        /**
         * Delete the database file (used when encryption key is wrong/corrupted)
         */
        fun deleteDatabase(context: Context): Boolean {
            return context.deleteDatabase(DATABASE_NAME)
        }
    }

    init {
        // Initialize SQLCipher native libraries
        SQLiteDatabase.loadLibs(context)
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COL_ID TEXT PRIMARY KEY,
                $COL_TIMESTAMP INTEGER,
                $COL_RAW_TRANSCRIPT TEXT,
                $COL_INCIDENT_TYPE TEXT,
                $COL_WHO_INVOLVED TEXT,
                $COL_THREAT_DOCUMENTED INTEGER,
                $COL_WITNESSES_PRESENT TEXT,
                $COL_PATTERN_FLAG INTEGER,
                $COL_SEVERITY_TAG TEXT,
                $COL_RAW_JSON TEXT
            )
        """.trimIndent()
        
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema upgrades would go here
    }

    /**
     * Save or update an incident record.
     * Uses REPLACE conflict resolution to update existing records.
     */
    fun saveIncident(record: IncidentRecord) {
        val db = getWritableDatabase(passphrase)
        
        val values = ContentValues().apply {
            put(COL_ID, record.id)
            put(COL_TIMESTAMP, record.timestamp)
            put(COL_RAW_TRANSCRIPT, record.rawTranscript)
            put(COL_INCIDENT_TYPE, record.incidentType)
            put(COL_WHO_INVOLVED, record.whoInvolved)
            put(COL_THREAT_DOCUMENTED, if (record.threatDocumented) 1 else 0)
            put(COL_WITNESSES_PRESENT, record.witnessesPresent)
            put(COL_PATTERN_FLAG, if (record.patternFlag) 1 else 0)
            put(COL_SEVERITY_TAG, record.severityTag)
            put(COL_RAW_JSON, record.rawJson)
        }
        
        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    /**
     * Retrieve all incident records, ordered by timestamp (newest first).
     */
    fun getAllIncidents(): List<IncidentRecord> {
        return try {
            val db = getReadableDatabase(passphrase)
            
            val cursor = db.query(
                TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                "$COL_TIMESTAMP DESC"
            )
            
            val records = mutableListOf<IncidentRecord>()
            
            cursor.use {
                while (it.moveToNext()) {
                    val record = IncidentRecord(
                        id = it.getString(it.getColumnIndexOrThrow(COL_ID)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP)),
                        rawTranscript = it.getString(it.getColumnIndexOrThrow(COL_RAW_TRANSCRIPT)),
                        incidentType = it.getString(it.getColumnIndexOrThrow(COL_INCIDENT_TYPE)),
                        whoInvolved = it.getString(it.getColumnIndexOrThrow(COL_WHO_INVOLVED)),
                        threatDocumented = it.getInt(it.getColumnIndexOrThrow(COL_THREAT_DOCUMENTED)) == 1,
                        witnessesPresent = it.getString(it.getColumnIndexOrThrow(COL_WITNESSES_PRESENT)),
                        patternFlag = it.getInt(it.getColumnIndexOrThrow(COL_PATTERN_FLAG)) == 1,
                        severityTag = it.getString(it.getColumnIndexOrThrow(COL_SEVERITY_TAG)),
                        rawJson = it.getString(it.getColumnIndexOrThrow(COL_RAW_JSON))
                    )
                    records.add(record)
                }
            }
            
            db.close()
            records
        } catch (e: Exception) {
            // If database can't be opened (wrong key, corruption, etc.), return empty list
            // This prevents crash and allows the app to continue
            emptyList()
        }
    }

    /**
     * Retrieve a single incident record by ID.
     */
    fun getIncidentById(id: String): IncidentRecord? {
        val db = getReadableDatabase(passphrase)
        
        val cursor = db.query(
            TABLE_NAME,
            null,
            "$COL_ID = ?",
            arrayOf(id),
            null,
            null,
            null
        )
        
        var record: IncidentRecord? = null
        
        cursor.use {
            if (it.moveToFirst()) {
                record = IncidentRecord(
                    id = it.getString(it.getColumnIndexOrThrow(COL_ID)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP)),
                    rawTranscript = it.getString(it.getColumnIndexOrThrow(COL_RAW_TRANSCRIPT)),
                    incidentType = it.getString(it.getColumnIndexOrThrow(COL_INCIDENT_TYPE)),
                    whoInvolved = it.getString(it.getColumnIndexOrThrow(COL_WHO_INVOLVED)),
                    threatDocumented = it.getInt(it.getColumnIndexOrThrow(COL_THREAT_DOCUMENTED)) == 1,
                    witnessesPresent = it.getString(it.getColumnIndexOrThrow(COL_WITNESSES_PRESENT)),
                    patternFlag = it.getInt(it.getColumnIndexOrThrow(COL_PATTERN_FLAG)) == 1,
                    severityTag = it.getString(it.getColumnIndexOrThrow(COL_SEVERITY_TAG)),
                    rawJson = it.getString(it.getColumnIndexOrThrow(COL_RAW_JSON))
                )
            }
        }
        
        db.close()
        return record
    }

    /**
     * Delete an incident record by ID.
     */
    fun deleteIncident(id: String) {
        val db = getWritableDatabase(passphrase)
        db.delete(TABLE_NAME, "$COL_ID = ?", arrayOf(id))
        db.close()
    }

    /**
     * Get count of all incidents.
     */
    fun getIncidentCount(): Int {
        val db = getReadableDatabase(passphrase)
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_NAME", null)
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        db.close()
        return count
    }
}
