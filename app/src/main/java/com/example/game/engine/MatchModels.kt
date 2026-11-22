package com.example.game.engine

import com.example.game.model.*

object PitchConstants {
    const val HALF_LENGTH = 52.5f
    const val HALF_WIDTH = 34.0f
    const val GOAL_HALF_WIDTH = 3.95f
    const val GOAL_HEIGHT = 2.65f
    const val GOAL_DEPTH = 2.35f
    const val PENALTY_AREA_LENGTH = 16.5f
    const val PENALTY_AREA_HALF_WIDTH = 14.5f
    const val BALL_RADIUS = 0.28f
    const val PLAYER_RADIUS = 0.55f
}

enum class GameModeType(val displayName: String) {
    QUICK_MATCH("Quick Match"),
    TOURNAMENT("World Cup Tournament"),
    CAREER("Career Season"),
    TRAINING("Training Pitch")
}

enum class TrainingDrillType(
    val displayName: String,
    val description: String,
    val durationSeconds: Int,
    val targetScore: Int
) {
    PASSING("Precision Passing", "Thread crisp passes through moving target gates to teammates.", 60, 5),
    SHOOTING("Finishing & Power", "Beat the goalkeeper and hit the corners inside the box.", 60, 5),
    DRIBBLING("Slalom Dribbling", "Sprint and weave the ball through all glowing slalom gates.", 60, 6),
    FREE_KICK("Set-Piece Free Kicks", "Bend and drive free kicks past the 4-player defensive wall.", 60, 4),
    PENALTY("Penalty Duel", "Outsmart the goalkeeper from the spot across 5 kicks.", 90, 3)
}

enum class MatchPhase {
    PRE_KICKOFF,
    FIRST_HALF,
    HALF_TIME,
    SECOND_HALF,
    EXTRA_TIME_1,
    EXTRA_TIME_2,
    PENALTY_SHOOTOUT,
    GOAL_CELEBRATION,
    REPLAY,
    FULL_TIME,
    TRAINING_ACTIVE,
    TRAINING_COMPLETE
}

data class MatchConfig(
    val mode: GameModeType,
    val homeTeam: TeamProfile,
    val awayTeam: TeamProfile,
    val homeKit: KitColors,
    val awayKit: KitColors,
    val difficulty: DifficultyLevel,
    val durationMinutes: Int,
    val lighting: StadiumLighting,
    val allowExtraTimeAndPenalties: Boolean = false,
    val trainingDrill: TrainingDrillType? = null,
    val tournamentFixtureId: String? = null
)

class SimPlayer(
    val index: Int,
    val isHome: Boolean,
    val slotIndex: Int,
    var profile: PlayerProfile,
    val isGoalkeeper: Boolean
) {
    var x: Float = 0f
    var y: Float = 0f
    var z: Float = 0f
    var vx: Float = 0f
    var vz: Float = 0f
    var facingRad: Float = if (isHome) 0f else Math.PI.toFloat()
    var stamina: Float = 1f
    var stridePhase: Float = 0f
    var kickTimer: Float = 0f
    var tackleTimer: Float = 0f
    var diveTimer: Float = 0f
    var diveDirZ: Float = 0f
    var stunTimer: Float = 0f
    var anchorX: Float = 0f
    var anchorZ: Float = 0f
    var aiDecisionCooldown: Float = 0f
    var celebrateTimer: Float = 0f
}

class SimBall {
    var x: Float = 0f
    var y: Float = PitchConstants.BALL_RADIUS
    var z: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var vz: Float = 0f
    var spinX: Float = 0f
    var spinZ: Float = 0f
    var rotAngleDeg: Float = 0f
    var rotAxisX: Float = 0f
    var rotAxisZ: Float = 1f
    var ownerIndex: Int = -1
    var lastTouchIsHome: Boolean = true
    var lastTouchName: String = ""
    var pickupCooldown: Float = 0f
}

class ReplayFrame {
    var ballX = 0f
    var ballY = 0f
    var ballZ = 0f
    val playerX = FloatArray(22)
    val playerY = FloatArray(22)
    val playerZ = FloatArray(22)
    val playerFacing = FloatArray(22)
}

class TrainingMarker(
    var x: Float = 0f,
    var z: Float = 0f,
    var radius: Float = 1.6f,
    var active: Boolean = false,
    var isTargetGate: Boolean = false
)

data class MatchStatsSnapshot(
    val homePossessionPct: Int = 50,
    val homeShots: Int = 0,
    val awayShots: Int = 0,
    val homeOnTarget: Int = 0,
    val awayOnTarget: Int = 0,
    val homePasses: Int = 0,
    val awayPasses: Int = 0,
    val homeTackles: Int = 0,
    val awayTackles: Int = 0
)

data class RadarDot(
    val xNorm: Float,
    val zNorm: Float,
    val isHome: Boolean,
    val isControlled: Boolean
)

data class HudSnapshot(
    val phase: MatchPhase = MatchPhase.PRE_KICKOFF,
    val mode: GameModeType = GameModeType.QUICK_MATCH,
    val homeName: String = "Brazil",
    val awayName: String = "Argentina",
    val homeCode: String = "BRA",
    val awayCode: String = "ARG",
    val homeBadgePrimary: Long = 0xFF16A34A,
    val awayBadgePrimary: Long = 0xFF38BDF8,
    val homeScore: Int = 0,
    val awayScore: Int = 0,
    val formattedClock: String = "00:00",
    val halfLabel: String = "1ST",
    val activePlayerName: String = "",
    val activePlayerNumber: Int = 10,
    val activePlayerStamina: Float = 1f,
    val userHasPossession: Boolean = false,
    val shootCharge: Float = 0f,
    val goalBannerText: String? = null,
    val eventToast: String? = null,
    val stats: MatchStatsSnapshot = MatchStatsSnapshot(),
    val cameraMode: CameraMode = CameraMode.BROADCAST,
    val lighting: StadiumLighting = StadiumLighting.EVENING,
    val isPaused: Boolean = false,
    val radarDots: List<RadarDot> = emptyList(),
    val ballRadarX: Float = 0f,
    val ballRadarZ: Float = 0f,
    val penaltyHomeKicks: List<Boolean?> = emptyList(),
    val penaltyAwayKicks: List<Boolean?> = emptyList(),
    val penaltyIsUserTurn: Boolean = true,
    val trainingDrill: TrainingDrillType? = null,
    val trainingScore: Int = 0,
    val trainingTarget: Int = 5,
    val trainingTimeLeftSec: Int = 60,
    val trainingPrompt: String = ""
)
