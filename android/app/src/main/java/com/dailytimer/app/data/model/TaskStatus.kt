package com.dailytimer.app.data.model

/** Statuses persisted in the `status` column. "In progress" and "overdue" are derived, never stored. */
object TaskStatus {
    const val UPCOMING = "UPCOMING"
    const val COMPLETED = "COMPLETED"
}
