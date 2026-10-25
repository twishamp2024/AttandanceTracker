package com.twisham.attendance.util

import java.time.LocalDate
import java.time.LocalTime

/** Raised when a session already exists for a subject at a given date and time. */
class DuplicateSessionException(
    val date: LocalDate,
    val startTime: LocalTime,
    cause: Throwable? = null,
) : Exception("A class already exists for this subject on $date at $startTime", cause)

/** Raised when a backup file cannot be trusted. Carries a user-readable reason. */
class InvalidBackupException(val reason: String, cause: Throwable? = null) :
    Exception(reason, cause)

/** Validation failure for user-entered data, with the message shown verbatim. */
class ValidationException(val reason: String) : Exception(reason)
