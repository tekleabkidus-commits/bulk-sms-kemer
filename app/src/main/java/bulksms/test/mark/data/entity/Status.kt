package bulksms.test.mark.data.entity

enum class CampaignStatus { DRAFT, RUNNING, PAUSED, COMPLETED, CANCELLED }

enum class RecipientStatus {
    PENDING,    // not yet attempted, or scheduled for retry
    SENDING,    // dispatched, waiting for SENT broadcast
    SENT,       // SENT broadcast received with RESULT_OK
    DELIVERED,  // DELIVERED broadcast received
    FAILED,     // permanent failure (after all retries)
    UNKNOWN,    // submission uncertain, never automatically retried
    INVALID,    // bad number format, missing field, etc. — never attempted
    SKIPPED     // duplicate or otherwise filtered out
}

enum class PauseReason {
    USER,
    NO_SERVICE,
    RATE_LIMIT,
    DAILY_CAP,
    INSTABILITY,
    BALANCE_SUSPECTED,
    PHONE_CALL,
    BATTERY_CRITICAL,
    THERMAL,
    SIM_REMOVED,
    PERMISSION_REVOKED,
    STORAGE_FULL,
    ROAMING
}
