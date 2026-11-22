package com.example.game.systems

import com.example.game.model.*

class CareerManager {

    fun startNewCareer(teamId: String, existingCoins: Int = 250): CareerState {
        val opponents = TeamRepository.allTeams
            .map { it.id }
            .filter { it != teamId }
            .shuffled()
        return CareerState(
            active = true,
            teamId = teamId,
            seasonNumber = 1,
            matchDay = 1,
            totalMatchDays = opponents.size,
            coins = existingCoins,
            xp = 0,
            wins = 0,
            draws = 0,
            losses = 0,
            goalsScored = 0,
            goalsConceded = 0,
            trophiesWon = 0,
            fixtureOpponentIds = opponents,
            upgradesByPlayerId = emptyMap()
        )
    }

    fun nextOpponentId(state: CareerState): String? {
        if (!state.active) return null
        val idx = state.matchDay - 1
        return state.fixtureOpponentIds.getOrNull(idx)
    }

    fun recordMatchResult(
        state: CareerState,
        userGoals: Int,
        opponentGoals: Int,
        difficulty: DifficultyLevel
    ): CareerState {
        val win = userGoals > opponentGoals
        val draw = userGoals == opponentGoals
        val baseCoins = when {
            win -> 160
            draw -> 90
            else -> 45
        }
        val goalBonus = userGoals * 25
        val cleanSheetBonus = if (opponentGoals == 0) 40 else 0
        val earnedCoins = ((baseCoins + goalBonus + cleanSheetBonus) * difficulty.rewardMultiplier).toInt()
        val earnedXp = if (win) 120 + userGoals * 15 else if (draw) 70 else 35

        val nextDay = state.matchDay + 1
        val seasonFinished = nextDay > state.totalMatchDays
        val wonSeasonTrophy = seasonFinished && (state.wins + if (win) 1 else 0) >= 4

        return if (seasonFinished) {
            val nextOpponents = TeamRepository.allTeams.map { it.id }.filter { it != state.teamId }.shuffled()
            state.copy(
                seasonNumber = state.seasonNumber + 1,
                matchDay = 1,
                coins = state.coins + earnedCoins + if (wonSeasonTrophy) 400 else 100,
                xp = state.xp + earnedXp,
                wins = state.wins + if (win) 1 else 0,
                draws = state.draws + if (draw) 1 else 0,
                losses = state.losses + if (!win && !draw) 1 else 0,
                goalsScored = state.goalsScored + userGoals,
                goalsConceded = state.goalsConceded + opponentGoals,
                trophiesWon = state.trophiesWon + if (wonSeasonTrophy) 1 else 0,
                fixtureOpponentIds = nextOpponents
            )
        } else {
            state.copy(
                matchDay = nextDay,
                coins = state.coins + earnedCoins,
                xp = state.xp + earnedXp,
                wins = state.wins + if (win) 1 else 0,
                draws = state.draws + if (draw) 1 else 0,
                losses = state.losses + if (!win && !draw) 1 else 0,
                goalsScored = state.goalsScored + userGoals,
                goalsConceded = state.goalsConceded + opponentGoals
            )
        }
    }

    fun upgradePlayerAttribute(
        state: CareerState,
        playerId: String,
        statType: StatType,
        cost: Int = 80
    ): CareerState? {
        if (state.coins < cost) return null
        val cur = state.upgradesByPlayerId[playerId] ?: CareerPlayerUpgrade()
        val updated = when (statType) {
            StatType.PACE -> if (cur.paceDelta >= 12) return null else cur.copy(paceDelta = cur.paceDelta + 2)
            StatType.SHOOTING -> if (cur.shootingDelta >= 12) return null else cur.copy(shootingDelta = cur.shootingDelta + 2)
            StatType.PASSING -> if (cur.passingDelta >= 12) return null else cur.copy(passingDelta = cur.passingDelta + 2)
            StatType.DRIBBLING -> if (cur.dribblingDelta >= 12) return null else cur.copy(dribblingDelta = cur.dribblingDelta + 2)
            StatType.DEFENDING -> if (cur.defendingDelta >= 12) return null else cur.copy(defendingDelta = cur.defendingDelta + 2)
            StatType.PHYSICAL -> if (cur.physicalDelta >= 12) return null else cur.copy(physicalDelta = cur.physicalDelta + 2)
        }
        val newMap = state.upgradesByPlayerId.toMutableMap()
        newMap[playerId] = updated
        return state.copy(
            coins = state.coins - cost,
            upgradesByPlayerId = newMap
        )
    }
}
