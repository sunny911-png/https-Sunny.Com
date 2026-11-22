package com.example.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.game.engine.GameModeType
import com.example.game.engine.MatchConfig
import com.example.game.engine.MatchEngine
import com.example.game.engine.TrainingDrillType
import com.example.game.model.*
import com.example.game.systems.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed class ScreenRoute {
    object MainMenu : ScreenRoute()
    object QuickMatchSetup : ScreenRoute()
    data class TeamTactics(val inMatch: Boolean = false) : ScreenRoute()
    object Tournament : ScreenRoute()
    object Career : ScreenRoute()
    object Training : ScreenRoute()
    object Settings : ScreenRoute()
    object Match : ScreenRoute()
}

data class AppUiState(
    val currentRoute: ScreenRoute = ScreenRoute.MainMenu,
    val saveData: SaveData = SaveData(),
    val homeTeam: TeamProfile = TeamRepository.getById("bra"),
    val awayTeam: TeamProfile = TeamRepository.getById("arg"),
    val preferHomeAwayKit: Boolean = false,
    val preferAwayAwayKit: Boolean = false,
    val quickMatchLighting: StadiumLighting = StadiumLighting.EVENING,
    val quickMatchDurationMin: Int = 4,
    val quickMatchDifficulty: DifficultyLevel = DifficultyLevel.SEMI_PRO,
    val quickMatchAllowPenalties: Boolean = true,
    val activeMatchConfig: MatchConfig? = null,
    val statusMessage: String? = null
)

class WorldSoccerViewModel(application: Application) : AndroidViewModel(application) {

    private val saveManager = SaveManager(application.applicationContext)
    val audioManager = AudioManager()
    val teamManager = TeamManager()
    val formationManager = FormationManager()
    val tournamentManager = TournamentManager()
    val careerManager = CareerManager()
    val inputManager = MobileInputManager()

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    var activeMatchEngine: MatchEngine? = null
        private set

    // Mutable roster overrides during session / match substitutions
    private val customRosters = mutableMapOf<String, List<PlayerProfile>>()
    private var matchResultRecorded = false

    init {
        val loaded = saveManager.load()
        audioManager.applySettings(loaded.settings)
        val home = buildEffectiveTeam(loaded.selectedHomeTeamId, loaded)
        val away = buildEffectiveTeam(loaded.selectedAwayTeamId, loaded)
        _uiState.value = AppUiState(
            currentRoute = ScreenRoute.MainMenu,
            saveData = loaded,
            homeTeam = home,
            awayTeam = away,
            quickMatchLighting = loaded.settings.defaultLighting,
            quickMatchDurationMin = loaded.settings.matchDurationMinutes,
            quickMatchDifficulty = loaded.settings.difficulty
        )
        audioManager.startMenuMusic()
    }

    private fun buildEffectiveTeam(teamId: String, save: SaveData): TeamProfile {
        val formation = save.teamFormations[teamId]
        val upgrades = if (save.career.active && save.career.teamId == teamId) {
            save.career.upgradesByPlayerId
        } else {
            emptyMap()
        }
        val base = teamManager.buildEffectiveTeam(teamId, formation, upgrades)
        val custom = customRosters[teamId]
        return if (custom != null) base.copy(roster = custom) else base
    }

    fun navigate(route: ScreenRoute) {
        audioManager.playUiClick()
        _uiState.update { it.copy(currentRoute = route, statusMessage = null) }
    }

    fun navigateBack() {
        audioManager.playUiClick()
        val cur = _uiState.value.currentRoute
        when (cur) {
            is ScreenRoute.TeamTactics -> {
                if (cur.inMatch) {
                    _uiState.update { it.copy(currentRoute = ScreenRoute.Match) }
                } else {
                    _uiState.update { it.copy(currentRoute = ScreenRoute.MainMenu) }
                }
            }
            ScreenRoute.Match -> exitActiveMatch()
            ScreenRoute.MainMenu -> Unit
            else -> _uiState.update { it.copy(currentRoute = ScreenRoute.MainMenu) }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    // ==================== QUICK MATCH SETUP ====================

    fun selectHomeTeam(teamId: String) {
        audioManager.playUiClick()
        val save = _uiState.value.saveData.copy(selectedHomeTeamId = teamId)
        persistSave(save)
        val home = buildEffectiveTeam(teamId, save)
        _uiState.update { it.copy(saveData = save, homeTeam = home) }
    }

    fun selectAwayTeam(teamId: String) {
        audioManager.playUiClick()
        val save = _uiState.value.saveData.copy(selectedAwayTeamId = teamId)
        persistSave(save)
        val away = buildEffectiveTeam(teamId, save)
        _uiState.update { it.copy(saveData = save, awayTeam = away) }
    }

    fun toggleHomeKit() {
        audioManager.playUiClick()
        _uiState.update { it.copy(preferHomeAwayKit = !it.preferHomeAwayKit) }
    }

    fun toggleAwayKit() {
        audioManager.playUiClick()
        _uiState.update { it.copy(preferAwayAwayKit = !it.preferAwayAwayKit) }
    }

    fun setQuickMatchDuration(minutes: Int) {
        audioManager.playUiClick()
        _uiState.update { it.copy(quickMatchDurationMin = minutes) }
    }

    fun setQuickMatchDifficulty(diff: DifficultyLevel) {
        audioManager.playUiClick()
        _uiState.update { it.copy(quickMatchDifficulty = diff) }
    }

    fun setQuickMatchLighting(lighting: StadiumLighting) {
        audioManager.playUiClick()
        _uiState.update { it.copy(quickMatchLighting = lighting) }
    }

    // ==================== FORMATION & SUBSTITUTIONS ====================

    fun setTeamFormation(teamId: String, formation: FormationType) {
        audioManager.playUiClick()
        val map = _uiState.value.saveData.teamFormations.toMutableMap()
        map[teamId] = formation
        val save = _uiState.value.saveData.copy(teamFormations = map)
        persistSave(save)
        refreshTeams(save)
    }

    fun swapTeamPlayers(teamId: String, indexA: Int, indexB: Int) {
        audioManager.playUiClick()
        val currentTeam = if (teamId == _uiState.value.homeTeam.id) _uiState.value.homeTeam else buildEffectiveTeam(teamId, _uiState.value.saveData)
        val swapped = formationManager.swapPlayers(currentTeam.roster, indexA, indexB)
        customRosters[teamId] = swapped
        refreshTeams(_uiState.value.saveData)
        activeMatchEngine?.updateRosterAndFormation(_uiState.value.homeTeam, _uiState.value.awayTeam)
    }

    private fun refreshTeams(save: SaveData) {
        val home = buildEffectiveTeam(save.selectedHomeTeamId, save)
        val away = buildEffectiveTeam(save.selectedAwayTeamId, save)
        _uiState.update { it.copy(saveData = save, homeTeam = home, awayTeam = away) }
    }

    // ==================== MATCH LAUNCH & LIFECYCLE ====================

    fun startQuickMatch() {
        val s = _uiState.value
        val (hKit, aKit) = teamManager.resolveKits(s.homeTeam, s.awayTeam, s.preferHomeAwayKit, s.preferAwayAwayKit)
        launchMatch(
            MatchConfig(
                mode = GameModeType.QUICK_MATCH,
                homeTeam = s.homeTeam,
                awayTeam = s.awayTeam,
                homeKit = hKit,
                awayKit = aKit,
                difficulty = s.quickMatchDifficulty,
                durationMinutes = s.quickMatchDurationMin,
                lighting = s.quickMatchLighting,
                allowExtraTimeAndPenalties = s.quickMatchAllowPenalties
            )
        )
    }

    fun startTournament(userTeamId: String) {
        audioManager.playUiClick()
        val tState = tournamentManager.createNewTournament(userTeamId)
        val save = _uiState.value.saveData.copy(
            selectedHomeTeamId = userTeamId,
            tournament = tState
        )
        persistSave(save)
        refreshTeams(save)
    }

    fun playNextTournamentMatch() {
        val s = _uiState.value
        val fx = tournamentManager.nextUserFixture(s.saveData.tournament) ?: return
        val userIsHome = fx.homeTeamId == s.saveData.tournament.userTeamId
        val userTeam = buildEffectiveTeam(s.saveData.tournament.userTeamId, s.saveData)
        val oppId = if (userIsHome) fx.awayTeamId else fx.homeTeamId
        val oppTeam = buildEffectiveTeam(oppId, s.saveData)
        val (hKit, aKit) = teamManager.resolveKits(userTeam, oppTeam)
        val isKnockout = !fx.stageName.startsWith("Group")
        launchMatch(
            MatchConfig(
                mode = GameModeType.TOURNAMENT,
                homeTeam = userTeam,
                awayTeam = oppTeam,
                homeKit = hKit,
                awayKit = aKit,
                difficulty = s.saveData.settings.difficulty,
                durationMinutes = s.saveData.settings.matchDurationMinutes,
                lighting = s.saveData.settings.defaultLighting,
                allowExtraTimeAndPenalties = isKnockout,
                tournamentFixtureId = fx.id
            )
        )
    }

    fun startCareer(teamId: String) {
        audioManager.playUiClick()
        val existingCoins = _uiState.value.saveData.career.coins.coerceAtLeast(250)
        val cState = careerManager.startNewCareer(teamId, existingCoins)
        val save = _uiState.value.saveData.copy(
            selectedHomeTeamId = teamId,
            career = cState
        )
        persistSave(save)
        refreshTeams(save)
    }

    fun playNextCareerMatch() {
        val s = _uiState.value
        val oppId = careerManager.nextOpponentId(s.saveData.career) ?: return
        val userTeam = buildEffectiveTeam(s.saveData.career.teamId, s.saveData)
        val oppTeam = buildEffectiveTeam(oppId, s.saveData)
        val (hKit, aKit) = teamManager.resolveKits(userTeam, oppTeam)
        launchMatch(
            MatchConfig(
                mode = GameModeType.CAREER,
                homeTeam = userTeam,
                awayTeam = oppTeam,
                homeKit = hKit,
                awayKit = aKit,
                difficulty = s.saveData.settings.difficulty,
                durationMinutes = s.saveData.settings.matchDurationMinutes,
                lighting = s.saveData.settings.defaultLighting,
                allowExtraTimeAndPenalties = false
            )
        )
    }

    fun upgradeCareerPlayer(playerId: String, stat: StatType) {
        audioManager.playUiClick()
        val s = _uiState.value
        val updatedCareer = careerManager.upgradePlayerAttribute(s.saveData.career, playerId, stat)
        if (updatedCareer == null) {
            _uiState.update { it.copy(statusMessage = "Need 80 Coins or stat already maxed.") }
            return
        }
        val save = s.saveData.copy(career = updatedCareer)
        persistSave(save)
        refreshTeams(save)
        _uiState.update { it.copy(statusMessage = "Upgraded ${stat.label} +2!") }
    }

    fun startTrainingDrill(drill: TrainingDrillType) {
        val s = _uiState.value
        val userTeam = buildEffectiveTeam(s.saveData.selectedHomeTeamId, s.saveData)
        val oppTeam = buildEffectiveTeam(s.saveData.selectedAwayTeamId, s.saveData)
        val (hKit, aKit) = teamManager.resolveKits(userTeam, oppTeam)
        launchMatch(
            MatchConfig(
                mode = GameModeType.TRAINING,
                homeTeam = userTeam,
                awayTeam = oppTeam,
                homeKit = hKit,
                awayKit = aKit,
                difficulty = s.saveData.settings.difficulty,
                durationMinutes = 2,
                lighting = s.saveData.settings.defaultLighting,
                trainingDrill = drill
            )
        )
    }

    private fun launchMatch(config: MatchConfig) {
        audioManager.playWhistleShort()
        audioManager.stopMenuMusic()
        audioManager.startCrowdAmbience()
        inputManager.reset()
        matchResultRecorded = false

        val engine = MatchEngine(config, inputManager, audioManager, formationManager).apply {
            userCameraMode = _uiState.value.saveData.settings.defaultCamera
        }
        activeMatchEngine = engine
        _uiState.update {
            it.copy(
                currentRoute = ScreenRoute.Match,
                activeMatchConfig = config,
                statusMessage = null
            )
        }
    }

    fun restartActiveMatch() {
        val cfg = _uiState.value.activeMatchConfig ?: return
        launchMatch(cfg)
    }

    fun finalizeMatchResultIfNeeded() {
        if (matchResultRecorded) return
        val cfg = _uiState.value.activeMatchConfig ?: return
        val engine = activeMatchEngine ?: return
        matchResultRecorded = true

        var save = _uiState.value.saveData
        if (cfg.mode == GameModeType.TRAINING) {
            val drill = cfg.trainingDrill ?: return
            val score = engine.getTrainingFinalScore()
            val prevBest = save.trainingBestScores[drill.name] ?: 0
            val bestMap = save.trainingBestScores.toMutableMap()
            bestMap[drill.name] = maxOf(prevBest, score)
            val bonusCoins = score * 15
            val updatedCareer = if (save.career.active) {
                save.career.copy(coins = save.career.coins + bonusCoins)
            } else save.career
            save = save.copy(trainingBestScores = bestMap, career = updatedCareer)
            persistSave(save)
            _uiState.update { it.copy(saveData = save) }
            return
        }

        val (hg, ag) = engine.getCurrentGoalResult()
        val outcome = when {
            hg > ag -> "Win"
            hg < ag -> "Loss"
            else -> "Draw"
        }
        val historyEntry = MatchHistoryEntry(
            timestampMillis = System.currentTimeMillis(),
            modeName = cfg.mode.displayName,
            homeTeamName = cfg.homeTeam.name,
            awayTeamName = cfg.awayTeam.name,
            homeGoals = hg,
            awayGoals = ag,
            outcome = outcome
        )
        val rewards = save.unlockedRewards.toMutableSet()
        if (hg > ag) rewards.add("First Match Victory")
        if (ag == 0 && hg > 0) rewards.add("Clean Sheet Wall")

        var newTournament = save.tournament
        if (cfg.mode == GameModeType.TOURNAMENT && cfg.tournamentFixtureId != null) {
            val fx = save.tournament.fixtures.firstOrNull { it.id == cfg.tournamentFixtureId }
            val userIsHomeInFx = fx?.homeTeamId == save.tournament.userTeamId
            val fxHomeGoals = if (userIsHomeInFx) hg else ag
            val fxAwayGoals = if (userIsHomeInFx) ag else hg
            val winnerId = if (hg > ag) cfg.homeTeam.id else if (ag > hg) cfg.awayTeam.id else null
            newTournament = tournamentManager.recordUserMatchResult(
                state = save.tournament,
                fixtureId = cfg.tournamentFixtureId,
                homeGoals = fxHomeGoals,
                awayGoals = fxAwayGoals,
                winnerOverride = winnerId
            )
            if (newTournament.championTeamId == save.tournament.userTeamId) {
                rewards.add("World Cup Champion 2027")
            }
        }

        var newCareer = save.career
        if (cfg.mode == GameModeType.CAREER && save.career.active) {
            newCareer = careerManager.recordMatchResult(save.career, hg, ag, cfg.difficulty)
            if (newCareer.trophiesWon > 0) rewards.add("Career Season Glory")
        }

        save = save.copy(
            tournament = newTournament,
            career = newCareer,
            matchHistory = (listOf(historyEntry) + save.matchHistory).take(25),
            unlockedRewards = rewards.toList()
        )
        persistSave(save)
        refreshTeams(save)
    }

    fun exitActiveMatch() {
        audioManager.stopCrowdAmbience()
        audioManager.startMenuMusic()
        val mode = _uiState.value.activeMatchConfig?.mode
        activeMatchEngine = null
        val dest = when (mode) {
            GameModeType.TOURNAMENT -> ScreenRoute.Tournament
            GameModeType.CAREER -> ScreenRoute.Career
            GameModeType.TRAINING -> ScreenRoute.Training
            else -> ScreenRoute.MainMenu
        }
        _uiState.update { it.copy(currentRoute = dest, activeMatchConfig = null) }
    }

    // ==================== SETTINGS ====================

    fun updateSettings(transform: (SettingsData) -> SettingsData) {
        val current = _uiState.value.saveData
        val updatedSettings = transform(current.settings)
        audioManager.applySettings(updatedSettings)
        val save = current.copy(settings = updatedSettings)
        persistSave(save)
        _uiState.update {
            it.copy(
                saveData = save,
                quickMatchLighting = updatedSettings.defaultLighting
            )
        }
    }

    private fun persistSave(save: SaveData) {
        saveManager.save(save)
    }

    override fun onCleared() {
        super.onCleared()
        audioManager.release()
    }
}
