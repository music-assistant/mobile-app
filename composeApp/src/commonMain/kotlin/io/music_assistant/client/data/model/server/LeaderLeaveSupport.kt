package io.music_assistant.client.data.model.server

/**
 * First server API schema that guarantees ad-hoc leadership handoff when a sync leader
 * ungroups itself (server PR #4412). That PR did not bump `API_SCHEMA_VERSION` — it
 * merged at 34 and 34 stayed for 13 more days — so 35, the first bump strictly after
 * the merge, is the lowest value that implies the code path is present.
 *
 * Below this floor `players/cmd/ungroup` on a leader dissolves the group and stops
 * playback, so the gesture stays hidden.
 */
const val LEADER_LEAVE_MIN_SCHEMA = 35

fun supportsLeaderLeave(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= LEADER_LEAVE_MIN_SCHEMA
