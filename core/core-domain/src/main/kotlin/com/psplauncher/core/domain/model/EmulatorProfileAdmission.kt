package com.psplauncher.core.domain.model

object EmulatorProfileAdmission {
    data class Refusal(val id: String, val name: String, val reason: String)

    data class Result(
        val admitted: List<EmulatorProfile>,
        val refused: List<Refusal>,
    )

    private val ALLOWED_INTENT_FLAGS = setOf("NEW_TASK", "CLEAR_TOP", "CLEAR_TASK")

    private val PACKAGE_NAME = Regex("""[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+""")

    fun admit(
        profiles: List<EmulatorProfile>,
        selfPackage: String? = null,
    ): Result {
        val admitted = mutableListOf<EmulatorProfile>()
        val refused = mutableListOf<Refusal>()

        profiles.forEach { profile ->
            val reason = reasonToRefuse(profile, selfPackage)
            if (reason == null) admitted += profile else refused += Refusal(profile.id, profile.name, reason)
        }
        return Result(admitted, refused)
    }

    private fun reasonToRefuse(profile: EmulatorProfile, selfPackage: String?): String? {
        if (profile.intentType == IntentType.CUSTOM_COMMAND || profile.customCommand != null) {
            return "carries a custom command"
        }
        if (!PACKAGE_NAME.matches(profile.packageName)) {
            return "package name '${profile.packageName}' is not a valid Android package"
        }
        if (selfPackage != null && profile.packageName == selfPackage) {
            return "targets this app's own package"
        }

        if (profile.intentType == IntentType.COMPONENT && profile.activityClass.isNullOrBlank()) {
            return "is a component intent with no activity class"
        }
        profile.intentFlags.firstOrNull { it !in ALLOWED_INTENT_FLAGS }?.let {
            return "requests unsupported intent flag '$it'"
        }
        return null
    }
}
