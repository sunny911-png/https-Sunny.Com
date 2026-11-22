package com.example.game.systems

import com.example.game.model.*
import kotlin.random.Random

class TournamentManager {

    fun createNewTournament(userTeamId: String): TournamentState {
        val allIds = TeamRepository.allTeams.map { it.id }.toMutableList()
        allIds.remove(userTeamId)
        allIds.shuffle(Random(System.currentTimeMillis()))
        val groupA = listOf(userTeamId) + allIds.take(3)
        val groupB = allIds.drop(3).take(4)

        val standings = groupA.map { TournamentStanding(it, 0) } +
            groupB.map { TournamentStanding(it, 1) }

        val fixtures = mutableListOf<TournamentFixture>()
        fixtures += buildGroupFixtures(groupA, "Group A")
        fixtures += buildGroupFixtures(groupB, "Group B")

        return TournamentState(
            active = true,
            userTeamId = userTeamId,
            stage = TournamentStage.GROUP_STAGE,
            standings = standings,
            fixtures = fixtures,
            championTeamId = null
        )
    }

    private fun buildGroupFixtures(teams: List<String>, groupName: String): List<TournamentFixture> {
        // 3 Matchdays in round robin
        return listOf(
            TournamentFixture("${groupName}_1", "$groupName • MD1", teams[0], teams[1]),
            TournamentFixture("${groupName}_2", "$groupName • MD1", teams[2], teams[3]),
            TournamentFixture("${groupName}_3", "$groupName • MD2", teams[0], teams[2]),
            TournamentFixture("${groupName}_4", "$groupName • MD2", teams[1], teams[3]),
            TournamentFixture("${groupName}_5", "$groupName • MD3", teams[0], teams[3]),
            TournamentFixture("${groupName}_6", "$groupName • MD3", teams[1], teams[2])
        )
    }

    fun nextUserFixture(state: TournamentState): TournamentFixture? {
        if (!state.active || state.stage == TournamentStage.CHAMPION_CROWNED) return null
        return state.fixtures.firstOrNull {
            !it.isCompleted && (it.homeTeamId == state.userTeamId || it.awayTeamId == state.userTeamId)
        }
    }

    fun recordUserMatchResult(
        state: TournamentState,
        fixtureId: String,
        homeGoals: Int,
        awayGoals: Int,
        winnerOverride: String? = null
    ): TournamentState {
        val updatedFixtures = state.fixtures.map { fx ->
            if (fx.id != fixtureId) fx
            else {
                val winId = winnerOverride ?: when {
                    homeGoals > awayGoals -> fx.homeTeamId
                    awayGoals > homeGoals -> fx.awayTeamId
                    else -> null
                }
                fx.copy(homeGoals = homeGoals, awayGoals = awayGoals, winnerTeamId = winId)
            }
        }

        // Simulate same-round AI fixtures up to the next unplayed user fixture or stage completion
        val simulatedFixtures = updatedFixtures.map { fx ->
            if (fx.isCompleted || fx.homeTeamId == state.userTeamId || fx.awayTeamId == state.userTeamId) {
                fx
            } else {
                simulateFixture(fx, allowDraw = state.stage == TournamentStage.GROUP_STAGE)
            }
        }

        val recomputedStandings = recomputeGroupStandings(state.standings, simulatedFixtures)
        val afterStage = state.copy(
            standings = recomputedStandings,
            fixtures = simulatedFixtures
        )
        return advanceStageIfReady(afterStage)
    }

    private fun simulateFixture(fx: TournamentFixture, allowDraw: Boolean): TournamentFixture {
        val home = TeamRepository.getById(fx.homeTeamId)
        val away = TeamRepository.getById(fx.awayTeamId)
        val diff = (home.overallRating - away.overallRating) / 10f
        var hg = (Random.nextInt(0, 3) + if (diff > 0.3f) 1 else 0).coerceAtMost(5)
        var ag = (Random.nextInt(0, 3) + if (diff < -0.3f) 1 else 0).coerceAtMost(5)
        var winnerId: String? = null
        if (hg > ag) winnerId = home.id
        else if (ag > hg) winnerId = away.id
        else if (!allowDraw) {
            winnerId = if (home.overallRating + Random.nextInt(-5, 6) >= away.overallRating) home.id else away.id
        }
        return fx.copy(homeGoals = hg, awayGoals = ag, winnerTeamId = winnerId)
    }

    private fun recomputeGroupStandings(
        initial: List<TournamentStanding>,
        fixtures: List<TournamentFixture>
    ): List<TournamentStanding> {
        val map = initial.associate { it.teamId to TournamentStanding(it.teamId, it.groupIndex) }.toMutableMap()
        fixtures.filter { it.isCompleted && it.stageName.startsWith("Group") }.forEach { fx ->
            val hg = fx.homeGoals ?: return@forEach
            val ag = fx.awayGoals ?: return@forEach
            val h = map[fx.homeTeamId] ?: return@forEach
            val a = map[fx.awayTeamId] ?: return@forEach
            map[fx.homeTeamId] = h.copy(
                played = h.played + 1,
                won = h.won + if (hg > ag) 1 else 0,
                drawn = h.drawn + if (hg == ag) 1 else 0,
                lost = h.lost + if (hg < ag) 1 else 0,
                goalsFor = h.goalsFor + hg,
                goalsAgainst = h.goalsAgainst + ag
            )
            map[fx.awayTeamId] = a.copy(
                played = a.played + 1,
                won = a.won + if (ag > hg) 1 else 0,
                drawn = a.drawn + if (ag == hg) 1 else 0,
                lost = a.lost + if (ag < hg) 1 else 0,
                goalsFor = a.goalsFor + ag,
                goalsAgainst = a.goalsAgainst + hg
            )
        }
        return map.values.sortedWith(
            compareBy<TournamentStanding> { it.groupIndex }
                .thenByDescending { it.points }
                .thenByDescending { it.goalDifference }
                .thenByDescending { it.goalsFor }
        )
    }

    private fun advanceStageIfReady(state: TournamentState): TournamentState {
        return when (state.stage) {
            TournamentStage.GROUP_STAGE -> {
                val allGroupDone = state.fixtures.filter { it.stageName.startsWith("Group") }.all { it.isCompleted }
                if (!allGroupDone) return state
                val groupA = state.standings.filter { it.groupIndex == 0 }
                val groupB = state.standings.filter { it.groupIndex == 1 }
                val semis = listOf(
                    TournamentFixture("SF_1", "Semi-Final 1", groupA[0].teamId, groupB[1].teamId),
                    TournamentFixture("SF_2", "Semi-Final 2", groupB[0].teamId, groupA[1].teamId)
                )
                val userQualified = semis.any { it.homeTeamId == state.userTeamId || it.awayTeamId == state.userTeamId }
                if (!userQualified) {
                    // Auto-resolve remaining knockout if user eliminated in groups
                    val simSemis = semis.map { simulateFixture(it, allowDraw = false) }
                    val finalFx = simulateFixture(
                        TournamentFixture("FIN", "World Final", simSemis[0].winnerTeamId!!, simSemis[1].winnerTeamId!!),
                        allowDraw = false
                    )
                    state.copy(
                        stage = TournamentStage.CHAMPION_CROWNED,
                        fixtures = state.fixtures + simSemis + finalFx,
                        championTeamId = finalFx.winnerTeamId
                    )
                } else {
                    state.copy(
                        stage = TournamentStage.SEMI_FINALS,
                        fixtures = state.fixtures + semis
                    )
                }
            }
            TournamentStage.SEMI_FINALS -> {
                val sf1 = state.fixtures.firstOrNull { it.id == "SF_1" } ?: return state
                val sf2 = state.fixtures.firstOrNull { it.id == "SF_2" } ?: return state
                if (!sf1.isCompleted || !sf2.isCompleted) return state
                val w1 = sf1.winnerTeamId ?: sf1.homeTeamId
                val w2 = sf2.winnerTeamId ?: sf2.homeTeamId
                val finalFx = TournamentFixture("FIN", "World Final", w1, w2)
                if (w1 != state.userTeamId && w2 != state.userTeamId) {
                    val simFinal = simulateFixture(finalFx, allowDraw = false)
                    state.copy(
                        stage = TournamentStage.CHAMPION_CROWNED,
                        fixtures = state.fixtures + simFinal,
                        championTeamId = simFinal.winnerTeamId
                    )
                } else {
                    state.copy(
                        stage = TournamentStage.FINAL,
                        fixtures = state.fixtures + finalFx
                    )
                }
            }
            TournamentStage.FINAL -> {
                val fin = state.fixtures.firstOrNull { it.id == "FIN" } ?: return state
                if (!fin.isCompleted) return state
                state.copy(
                    stage = TournamentStage.CHAMPION_CROWNED,
                    championTeamId = fin.winnerTeamId ?: fin.homeTeamId
                )
            }
            TournamentStage.CHAMPION_CROWNED -> state
        }
    }
}
