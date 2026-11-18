package com.dailytimer.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.dailytimer.app.data.model.Task
import com.dailytimer.app.data.model.TaskStatus
import com.dailytimer.app.domain.TaskTime
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class TaskDatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val _changes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Emits after every write that changed data, so screens stay in sync with receivers. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_TASKS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_DATE TEXT NOT NULL,
                $COLUMN_START_TIME TEXT NOT NULL,
                $COLUMN_END_TIME TEXT NOT NULL DEFAULT '',
                $COLUMN_START_MILLIS INTEGER NOT NULL DEFAULT 0,
                $COLUMN_TASK_NAME TEXT NOT NULL,
                $COLUMN_DESCRIPTION TEXT,
                $COLUMN_STATUS TEXT NOT NULL DEFAULT '${TaskStatus.UPCOMING}'
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX $INDEX_DATE_START ON $TABLE_TASKS($COLUMN_DATE, $COLUMN_START_MILLIS)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2 && !db.hasColumn(TABLE_TASKS, COLUMN_END_TIME)) {
            db.execSQL("ALTER TABLE $TABLE_TASKS ADD COLUMN $COLUMN_END_TIME TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) migrateToV3(db)
    }

    /** Adds a sortable epoch column; 12-hour "hh:mm a" strings cannot be ordered lexicographically. */
    private fun migrateToV3(db: SQLiteDatabase) {
        if (!db.hasColumn(TABLE_TASKS, COLUMN_START_MILLIS)) {
            db.execSQL("ALTER TABLE $TABLE_TASKS ADD COLUMN $COLUMN_START_MILLIS INTEGER NOT NULL DEFAULT 0")
        }
        // Read everything first: updating rows while iterating a live cursor is unsafe.
        val rows = db.query(TABLE_TASKS, arrayOf(COLUMN_ID, COLUMN_DATE, COLUMN_START_TIME), null, null, null, null, null)
            .use { c -> buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getString(1), c.getString(2))) } }
        val values = ContentValues()
        for ((id, date, startTime) in rows) {
            values.put(COLUMN_START_MILLIS, TaskTime.toEpochMillis(date, startTime) ?: 0L)
            db.update(TABLE_TASKS, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
        }
        db.execSQL("DROP INDEX IF EXISTS idx_tasks_date")
        db.execSQL("CREATE INDEX IF NOT EXISTS $INDEX_DATE_START ON $TABLE_TASKS($COLUMN_DATE, $COLUMN_START_MILLIS)")
    }

    fun insertTask(task: Task): Long =
        writableDatabase.insert(TABLE_TASKS, null, task.toContentValues())
            .also { if (it != -1L) notifyChanged() }

    fun updateTask(task: Task): Int =
        writableDatabase.update(TABLE_TASKS, task.toContentValues(), ID_CLAUSE, arrayOf(task.id.toString()))
            .also { if (it > 0) notifyChanged() }

    fun deleteTask(id: Long): Int =
        writableDatabase.delete(TABLE_TASKS, ID_CLAUSE, arrayOf(id.toString()))
            .also { if (it > 0) notifyChanged() }

    fun markTaskCompleted(id: Long): Int {
        val values = ContentValues().apply { put(COLUMN_STATUS, TaskStatus.COMPLETED) }
        return writableDatabase.update(TABLE_TASKS, values, ID_CLAUSE, arrayOf(id.toString()))
            .also { if (it > 0) notifyChanged() }
    }

    fun getTaskById(id: Long): Task? =
        readableDatabase.query(TABLE_TASKS, null, ID_CLAUSE, arrayOf(id.toString()), null, null, null, "1")
            .use { if (it.moveToFirst()) it.toTask() else null }

    fun getTasksForDate(date: String): List<Task> =
        readableDatabase.query(TABLE_TASKS, null, "$COLUMN_DATE = ?", arrayOf(date), null, null, ORDER_BY_START)
            .use { it.toTasks() }

    fun getAllUpcomingTasks(): List<Task> =
        readableDatabase.query(TABLE_TASKS, null, "$COLUMN_STATUS != ?", arrayOf(TaskStatus.COMPLETED), null, null, ORDER_BY_START)
            .use { it.toTasks() }

    fun getDistinctDatesWithCount(): List<Pair<String, Int>> =
        readableDatabase.rawQuery(
            "SELECT $COLUMN_DATE, COUNT($COLUMN_ID) FROM $TABLE_TASKS GROUP BY $COLUMN_DATE ORDER BY $COLUMN_DATE DESC",
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getInt(1)) } }

    private fun notifyChanged() {
        _changes.tryEmit(Unit)
    }

    private fun Task.toContentValues() = ContentValues().apply {
        put(COLUMN_DATE, date)
        put(COLUMN_START_TIME, startTime)
        put(COLUMN_END_TIME, endTime)
        put(COLUMN_START_MILLIS, TaskTime.toEpochMillis(date, startTime) ?: 0L)
        put(COLUMN_TASK_NAME, taskName)
        put(COLUMN_DESCRIPTION, description)
        put(COLUMN_STATUS, status)
    }

    private fun Cursor.toTasks(): List<Task> = buildList { while (moveToNext()) add(toTask()) }

    private fun Cursor.toTask() = Task(
        id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
        date = string(COLUMN_DATE),
        startTime = string(COLUMN_START_TIME),
        endTime = string(COLUMN_END_TIME),
        taskName = string(COLUMN_TASK_NAME),
        description = string(COLUMN_DESCRIPTION),
        status = string(COLUMN_STATUS),
    )

    private fun Cursor.string(column: String): String =
        getColumnIndexOrThrow(column).let { if (isNull(it)) "" else getString(it) }

    private fun SQLiteDatabase.hasColumn(table: String, column: String): Boolean =
        rawQuery("PRAGMA table_info($table)", null).use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            generateSequence { if (c.moveToNext()) c.getString(nameIdx) else null }.any { it == column }
        }

    companion object {
        private const val DATABASE_NAME = "dailytimer.db"
        private const val DATABASE_VERSION = 3
        private const val INDEX_DATE_START = "idx_tasks_date_start"
        private const val ID_CLAUSE = "id = ?"
        private const val ORDER_BY_START = "start_millis ASC, id ASC"

        const val TABLE_TASKS = "tasks"
        const val COLUMN_ID = "id"
        const val COLUMN_DATE = "date"
        const val COLUMN_START_TIME = "start_time"
        const val COLUMN_END_TIME = "end_time"
        const val COLUMN_START_MILLIS = "start_millis"
        const val COLUMN_TASK_NAME = "task_name"
        const val COLUMN_DESCRIPTION = "description"
        const val COLUMN_STATUS = "status"

        @Volatile private var instance: TaskDatabaseHelper? = null

        fun getInstance(context: Context): TaskDatabaseHelper =
            instance ?: synchronized(this) {
                instance ?: TaskDatabaseHelper(context.applicationContext).also { instance = it }
            }
    }
}
