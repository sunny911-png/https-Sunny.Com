package com.example.game.model

import androidx.compose.ui.graphics.Color

enum class PlayerRole(val code: String) {
    GK("GK"),
    DEF("DEF"),
    MID("MID"),
    FWD("FWD")
}

data class FormationSlot(
    val label: String,
    val role: PlayerRole,
    // Normalized coordinates in own attacking orientation:
    // xNorm in [-1f, 1f] (left to right), zNorm in [-0.95f, 0.35f] (own goal at -1f, halfway at 0f)
    val xNorm: Float,
    val zNorm: Float
)

enum class FormationType(
    val displayName: String,
    val slots: List<FormationSlot>
) {
    F_4_3_3(
        displayName = "4-3-3",
        slots = listOf(
            FormationSlot("GK", PlayerRole.GK, 0.0f, -0.92f),
            FormationSlot("LB", PlayerRole.DEF, -0.68f, -0.60f),
            FormationSlot("LCB", PlayerRole.DEF, -0.24f, -0.66f),
            FormationSlot("RCB", PlayerRole.DEF, 0.24f, -0.66f),
            FormationSlot("RB", PlayerRole.DEF, 0.68f, -0.60f),
            FormationSlot("LCM", PlayerRole.MID, -0.42f, -0.28f),
            FormationSlot("CDM", PlayerRole.MID, 0.0f, -0.36f),
            FormationSlot("RCM", PlayerRole.MID, 0.42f, -0.28f),
            FormationSlot("LW", PlayerRole.FWD, -0.64f, 0.02f),
            FormationSlot("ST", PlayerRole.FWD, 0.0f, 0.12f),
            FormationSlot("RW", PlayerRole.FWD, 0.64f, 0.02f)
        )
    ),
    F_4_4_2(
        displayName = "4-4-2",
        slots = listOf(
            FormationSlot("GK", PlayerRole.GK, 0.0f, -0.92f),
            FormationSlot("LB", PlayerRole.DEF, -0.68f, -0.62f),
            FormationSlot("LCB", PlayerRole.DEF, -0.24f, -0.67f),
            FormationSlot("RCB", PlayerRole.DEF, 0.24f, -0.67f),
            FormationSlot("RB", PlayerRole.DEF, 0.68f, -0.62f),
            FormationSlot("LM", PlayerRole.MID, -0.66f, -0.24f),
            FormationSlot("LCM", PlayerRole.MID, -0.22f, -0.30f),
            FormationSlot("RCM", PlayerRole.MID, 0.22f, -0.30f),
            FormationSlot("RM", PlayerRole.MID, 0.66f, -0.24f),
            FormationSlot("LST", PlayerRole.FWD, -0.24f, 0.08f),
            FormationSlot("RST", PlayerRole.FWD, 0.24f, 0.08f)
        )
    ),
    F_4_2_3_1(
        displayName = "4-2-3-1",
        slots = listOf(
            FormationSlot("GK", PlayerRole.GK, 0.0f, -0.92f),
            FormationSlot("LB", PlayerRole.DEF, -0.68f, -0.62f),
            FormationSlot("LCB", PlayerRole.DEF, -0.24f, -0.67f),
            FormationSlot("RCB", PlayerRole.DEF, 0.24f, -0.67f),
            FormationSlot("RB", PlayerRole.DEF, 0.68f, -0.62f),
            FormationSlot("LDM", PlayerRole.MID, -0.25f, -0.38f),
            FormationSlot("RDM", PlayerRole.MID, 0.25f, -0.38f),
            FormationSlot("LAM", PlayerRole.MID, -0.58f, -0.06f),
            FormationSlot("CAM", PlayerRole.MID, 0.0f, -0.04f),
            FormationSlot("RAM", PlayerRole.MID, 0.58f, -0.06f),
            FormationSlot("ST", PlayerRole.FWD, 0.0f, 0.16f)
        )
    ),
    F_3_5_2(
        displayName = "3-5-2",
        slots = listOf(
            FormationSlot("GK", PlayerRole.GK, 0.0f, -0.92f),
            FormationSlot("LCB", PlayerRole.DEF, -0.45f, -0.66f),
            FormationSlot("CB", PlayerRole.DEF, 0.0f, -0.69f),
            FormationSlot("RCB", PlayerRole.DEF, 0.45f, -0.66f),
            FormationSlot("LWB", PlayerRole.MID, -0.74f, -0.24f),
            FormationSlot("LCM", PlayerRole.MID, -0.30f, -0.30f),
            FormationSlot("CAM", PlayerRole.MID, 0.0f, -0.10f),
            FormationSlot("RCM", PlayerRole.MID, 0.30f, -0.30f),
            FormationSlot("RWB", PlayerRole.MID, 0.74f, -0.24f),
            FormationSlot("LST", PlayerRole.FWD, -0.24f, 0.12f),
            FormationSlot("RST", PlayerRole.FWD, 0.24f, 0.12f)
        )
    ),
    F_5_3_2(
        displayName = "5-3-2",
        slots = listOf(
            FormationSlot("GK", PlayerRole.GK, 0.0f, -0.92f),
            FormationSlot("LWB", PlayerRole.DEF, -0.74f, -0.50f),
            FormationSlot("LCB", PlayerRole.DEF, -0.38f, -0.68f),
            FormationSlot("CB", PlayerRole.DEF, 0.0f, -0.71f),
            FormationSlot("RCB", PlayerRole.DEF, 0.38f, -0.68f),
            FormationSlot("RWB", PlayerRole.DEF, 0.74f, -0.50f),
            FormationSlot("LCM", PlayerRole.MID, -0.36f, -0.26f),
            FormationSlot("CM", PlayerRole.MID, 0.0f, -0.32f),
            FormationSlot("RCM", PlayerRole.MID, 0.36f, -0.26f),
            FormationSlot("LST", PlayerRole.FWD, -0.24f, 0.10f),
            FormationSlot("RST", PlayerRole.FWD, 0.24f, 0.10f)
        )
    )
}

data class PlayerAttributes(
    val pace: Int,
    val shooting: Int,
    val passing: Int,
    val dribbling: Int,
    val defending: Int,
    val physical: Int
) {
    val overall: Int
        get() = ((pace + shooting + passing + dribbling + defending + physical) / 6f).toInt().coerceIn(50, 99)

    fun withUpgrade(statKey: StatType, delta: Int): PlayerAttributes = when (statKey) {
        StatType.PACE -> copy(pace = (pace + delta).coerceAtMost(99))
        StatType.SHOOTING -> copy(shooting = (shooting + delta).coerceAtMost(99))
        StatType.PASSING -> copy(passing = (passing + delta).coerceAtMost(99))
        StatType.DRIBBLING -> copy(dribbling = (dribbling + delta).coerceAtMost(99))
        StatType.DEFENDING -> copy(defending = (defending + delta).coerceAtMost(99))
        StatType.PHYSICAL -> copy(physical = (physical + delta).coerceAtMost(99))
    }
}

enum class StatType(val label: String, val shortCode: String) {
    PACE("Pace", "PAC"),
    SHOOTING("Shooting", "SHO"),
    PASSING("Passing", "PAS"),
    DRIBBLING("Dribbling", "DRI"),
    DEFENDING("Defending", "DEF"),
    PHYSICAL("Physical", "PHY")
}

data class PlayerProfile(
    val id: String,
    val name: String,
    val number: Int,
    val role: PlayerRole,
    val attributes: PlayerAttributes,
    val skinToneHex: Long = 0xFFD9A37C,
    val hairColorHex: Long = 0xFF1F1B18
)

data class KitColors(
    val jerseyPrimary: Long,
    val jerseySecondary: Long,
    val shorts: Long,
    val socks: Long,
    val gkJersey: Long
) {
    val primaryCompose: Color get() = Color(jerseyPrimary)
    val secondaryCompose: Color get() = Color(jerseySecondary)
}

enum class BadgeStyle {
    VERTICAL_STRIPES,
    HORIZONTAL_BAND,
    CHEVRON_SHIELD,
    STAR_CREST,
    SUN_DISC
}

data class TeamProfile(
    val id: String,
    val name: String,
    val shortCode: String,
    val confederation: String,
    val homeKit: KitColors,
    val awayKit: KitColors,
    val defaultFormation: FormationType,
    val badgePrimary: Long,
    val badgeAccent: Long,
    val badgeStyle: BadgeStyle,
    val roster: List<PlayerProfile> // 11 starters (0..10) + 5 substitutes (11..15)
) {
    val attackRating: Int
        get() = roster.take(11).filter { it.role == PlayerRole.FWD || it.role == PlayerRole.MID }
            .map { (it.attributes.shooting + it.attributes.pace + it.attributes.dribbling) / 3 }
            .average().toInt().coerceIn(60, 99)

    val midfieldRating: Int
        get() = roster.take(11).filter { it.role == PlayerRole.MID }
            .map { (it.attributes.passing + it.attributes.dribbling) / 2 }
            .average().toInt().coerceIn(60, 99)

    val defenseRating: Int
        get() = roster.take(11).filter { it.role == PlayerRole.DEF || it.role == PlayerRole.GK }
            .map { (it.attributes.defending + it.attributes.physical) / 2 }
            .average().toInt().coerceIn(60, 99)

    val overallRating: Int
        get() = ((attackRating + midfieldRating + defenseRating) / 3f).toInt()
}

enum class DifficultyLevel(
    val displayName: String,
    val aiSpeedMult: Float,
    val aiReactionSeconds: Float,
    val aiShotAccuracy: Float,
    val gkReflexMult: Float,
    val rewardMultiplier: Float
) {
    AMATEUR("Amateur", 0.80f, 0.34f, 0.65f, 0.72f, 1.0f),
    SEMI_PRO("Semi-Pro", 0.90f, 0.24f, 0.78f, 0.85f, 1.25f),
    PRO("Professional", 0.98f, 0.16f, 0.88f, 0.96f, 1.6f),
    WORLD_CLASS("World Class", 1.05f, 0.10f, 0.95f, 1.08f, 2.0f)
}

enum class StadiumLighting(val displayName: String) {
    EVENING("Evening Floodlights"),
    DAY("Bright Daylight")
}

enum class GraphicsQuality(
    val displayName: String,
    val spectatorRows: Int,
    val renderShadows: Boolean,
    val pitchStripesDetail: Boolean
) {
    LOW("Low", 0, false, false),
    MEDIUM("Medium", 2, true, true),
    HIGH("High", 4, true, true)
}

enum class CameraMode(val displayName: String) {
    BROADCAST("Broadcast"),
    BALL_FOLLOW("Ball Follow"),
    GOAL_CELEBRATION("Celebration"),
    REPLAY("Instant Replay")
}
