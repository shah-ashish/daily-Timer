package com.dailytimer.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.dailytimer.app.data.model.Task

class TaskDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        private const val DATABASE_NAME = "dailytimer.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_TASKS = "tasks"
        const val COLUMN_ID = "id"
        const val COLUMN_DATE = "date"
        const val COLUMN_START_TIME = "start_time"
        const val COLUMN_TASK_NAME = "task_name"
        const val COLUMN_DESCRIPTION = "description"
        const val COLUMN_STATUS = "status"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTasksTable = """
            CREATE TABLE $TABLE_TASKS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_DATE TEXT NOT NULL,
                $COLUMN_START_TIME TEXT NOT NULL,
                $COLUMN_TASK_NAME TEXT NOT NULL,
                $COLUMN_DESCRIPTION TEXT,
                $COLUMN_STATUS TEXT NOT NULL DEFAULT 'UPCOMING'
            )
        """.trimIndent()
        db.execSQL(createTasksTable)
        db.execSQL("CREATE INDEX idx_tasks_date ON $TABLE_TASKS($COLUMN_DATE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TASKS")
        onCreate(db)
    }

    fun insertTask(task: Task): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_DATE, task.date)
            put(COLUMN_START_TIME, task.startTime)
            put(COLUMN_TASK_NAME, task.taskName)
            put(COLUMN_DESCRIPTION, task.description)
            put(COLUMN_STATUS, task.status)
        }
        return db.insert(TABLE_TASKS, null, values)
    }

    fun getTasksForDate(date: String): List<Task> {
        val tasks = mutableListOf<Task>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TASKS,
            null,
            "$COLUMN_DATE = ?",
            arrayOf(date),
            null,
            null,
            "$COLUMN_START_TIME ASC"
        )

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val task = Task(
                        id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                        date = it.getString(it.getColumnIndexOrThrow(COLUMN_DATE)),
                        startTime = it.getString(it.getColumnIndexOrThrow(COLUMN_START_TIME)),
                        taskName = it.getString(it.getColumnIndexOrThrow(COLUMN_TASK_NAME)),
                        description = it.getString(it.getColumnIndexOrThrow(COLUMN_DESCRIPTION)) ?: "",
                        status = it.getString(it.getColumnIndexOrThrow(COLUMN_STATUS))
                    )
                    tasks.add(task)
                } while (it.moveToNext())
            }
        }
        return tasks
    }

    fun getDistinctDatesWithCount(): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        val db = readableDatabase
        val query = """
            SELECT $COLUMN_DATE, COUNT($COLUMN_ID) as count 
            FROM $TABLE_TASKS 
            GROUP BY $COLUMN_DATE 
            ORDER BY $COLUMN_DATE DESC
        """.trimIndent()

        val cursor = db.rawQuery(query, null)
        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val date = it.getString(0)
                    val count = it.getInt(1)
                    result.add(Pair(date, count))
                } while (it.moveToNext())
            }
        }
        return result
    }

    fun deleteTask(id: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_TASKS, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun updateTask(task: Task): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_DATE, task.date)
            put(COLUMN_START_TIME, task.startTime)
            put(COLUMN_TASK_NAME, task.taskName)
            put(COLUMN_DESCRIPTION, task.description)
            put(COLUMN_STATUS, task.status)
        }
        return db.update(TABLE_TASKS, values, "$COLUMN_ID = ?", arrayOf(task.id.toString()))
    }
}
