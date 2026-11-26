package com.example.ridesharingapp

/**
 * Holds the in-memory profile for the signed-in user (mirrors backend after login / profile refresh).
 */
object RideRepository {

    private fun emptyUser() = UserProfile(
        userId = "",
        name = "",
        collegeId = "—",
        rollNumber = "",
        collegeEmail = "",
    )

    var currentUser: UserProfile = emptyUser()

    /** The signed-in user's id, falling back to the persisted session. */
    val currentUserId: String
        get() = currentUser.userId.ifBlank { SessionManager.userId().orEmpty() }

    fun reset() {
        currentUser = emptyUser()
    }
}
