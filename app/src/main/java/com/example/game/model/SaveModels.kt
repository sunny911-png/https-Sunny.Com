package com.example.game.model

data class TouchLayoutOffsets(
    val joystickOffsetX: Float = 0f,
    val joystickOffsetY: Float = 0f,
    val actionClusterOffsetX: Float = 0f,
    val actionClusterOffsetY: Float = 0f,
    val buttonScale: Float = 1.0f
)

data class SettingsData(
    val targetFps: Int = 60,
    val graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH,
    val resolutionScale: Float = 1.0f,
    val defaultLighting: StadiumLighting = StadiumLighting.EVENING,
    val defaultCamera: CameraMode = CameraMode.BROADCAST,
    val difficulty: DifficultyLevel = DifficultyLevel.SEMI_PRO,
    val matchDurationMinutes: Int = 4,
    val masterVolume: Float = 0.85f,
    val crowdVolume: Float = 0.75f,
    val sfxVolume: Float = 0.90f,
    val musicVolume: Float = 0.65f,
    val touchOffsets: TouchLayoutOffsets = TouchLayoutOffsets()
)

data class CareerPlayerUpgrade(
    val paceDelta: Int = 0,
    val shootingDelta: Int = 0,
    val passingDelta: Int = 0,
    val dribblingDelta: Int = 0,
    val defendingDelta: Int = 0,
    val physicalDelta: Int = 0
)

data class CareerState(
    val active: Boolean = false,
    val teamId: String = "bra",
    val seasonNumber: Int = 1,
    val matchDay: Int = 1,
    val totalMatchDays: Int = 7,
    val coins: Int = 250,
    val xp: Int = 0,
    val wins: Int = 0,
    val draws: Int = 0,
    val losses: Int = 0,
    val goalsScored: Int = 0,
    val goalsConceded: Int = 0,
    val trophiesWon: Int = 0,
    val fixtureOpponentIds: List<String> = emptyList(),
    val upgradesByPlayerId: Map<String, CareerPlayerUpgrade> = emptyMap()
)

data class TournamentStanding(
    val teamId: String,
    val groupIndex: Int, // 0 = Group A, 1 = Group B
    val played: Int = 0,
    val won: Int = 0,
    val drawn: Int = 0,
    val lost: Int = 0,
    val goalsFor: Int = 0,
    val goalsAgainst: Int = 0
) {
    val points: Int get() = won * 3 + drawn
    val goalDifference: Int get() = goalsFor - goalsAgainst
}

data class TournamentFixture(
    val id: String,
    val stageName: String,
    val homeTeamId: String,
    val awayTeamId: String,
    val homeGoals: Int? = null,
    val awayGoals: Int? = null,
    val winnerTeamId: String? = null
) {
    val isCompleted: Boolean get() = homeGoals != null && awayGoals != null
}

enum class TournamentStage {
    GROUP_STAGE,
    SEMI_FINALS,
    FINAL,
    CHAMPION_CROWNED
}

data class TournamentState(
    val active: Boolean = false,
    val userTeamId: String = "bra",
    val stage: TournamentStage = TournamentStage.GROUP_STAGE,
    val standings: List<TournamentStanding> = emptyList(),
    val fixtures: List<TournamentFixture> = emptyList(),
    val championTeamId: String? = null
)

data class MatchHistoryEntry(
    val timestampMillis: Long,
    val modeName: String,
    val homeTeamName: String,
    val awayTeamName: String,
    val homeGoals: Int,
    val awayGoals: Int,
    val outcome: String
)

data class SaveData(
    val schemaVersion: Int = 1,
    val selectedHomeTeamId: String = "bra",
    val selectedAwayTeamId: String = "arg",
    val teamFormations: Map<String, FormationType> = emptyMap(),
    val settings: SettingsData = SettingsData(),
    val career: CareerState = CareerState(),
    val tournament: TournamentState = TournamentState(),
    val matchHistory: List<MatchHistoryEntry> = emptyList(),
    val unlockedRewards: List<String> = emptyList(),
    val trainingBestScores: Map<String, Int> = emptyMap()
)
