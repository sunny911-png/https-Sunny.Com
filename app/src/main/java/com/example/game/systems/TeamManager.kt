package com.example.game.systems

import com.example.game.model.*
import kotlin.math.abs

class TeamManager {

    fun buildEffectiveTeam(
        teamId: String,
        formationOverride: FormationType? = null,
        careerUpgrades: Map<String, CareerPlayerUpgrade> = emptyMap()
    ): TeamProfile {
        val base = TeamRepository.getById(teamId)
        val upgradedRoster = if (careerUpgrades.isEmpty()) {
            base.roster
        } else {
            base.roster.map { p ->
                val u = careerUpgrades[p.id] ?: return@map p
                p.copy(
                    attributes = p.attributes.copy(
                        pace = (p.attributes.pace + u.paceDelta).coerceAtMost(99),
                        shooting = (p.attributes.shooting + u.shootingDelta).coerceAtMost(99),
                        passing = (p.attributes.passing + u.passingDelta).coerceAtMost(99),
                        dribbling = (p.attributes.dribbling + u.dribblingDelta).coerceAtMost(99),
                        defending = (p.attributes.defending + u.defendingDelta).coerceAtMost(99),
                        physical = (p.attributes.physical + u.physicalDelta).coerceAtMost(99)
                    )
                )
            }
        }
        return base.copy(
            defaultFormation = formationOverride ?: base.defaultFormation,
            roster = upgradedRoster
        )
    }

    fun resolveKits(
        home: TeamProfile,
        away: TeamProfile,
        preferHomeUseAway: Boolean = false,
        preferAwayUseAway: Boolean = false
    ): Pair<KitColors, KitColors> {
        val homeKit = if (preferHomeUseAway) home.awayKit else home.homeKit
        var awayKit = if (preferAwayUseAway) away.awayKit else away.homeKit
        if (colorsAreSimilar(homeKit.jerseyPrimary, awayKit.jerseyPrimary)) {
            awayKit = if (preferAwayUseAway) away.homeKit else away.awayKit
        }
        return homeKit to awayKit
    }

    private fun colorsAreSimilar(c1: Long, c2: Long): Boolean {
        val r1 = ((c1 shr 16) and 0xFF).toInt()
        val g1 = ((c1 shr 8) and 0xFF).toInt()
        val b1 = (c1 and 0xFF).toInt()
        val r2 = ((c2 shr 16) and 0xFF).toInt()
        val g2 = ((c2 shr 8) and 0xFF).toInt()
        val b2 = (c2 and 0xFF).toInt()
        return abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2) < 130
    }
}

class FormationManager {

    fun swapPlayers(roster: List<PlayerProfile>, idxA: Int, idxB: Int): List<PlayerProfile> {
        if (idxA !in roster.indices || idxB !in roster.indices || idxA == idxB) return roster
        val mutable = roster.toMutableList()
        val tmp = mutable[idxA]
        mutable[idxA] = mutable[idxB]
        mutable[idxB] = tmp
        return mutable
    }

    /**
     * Converts a normalized formation slot into pitch world coordinates (meters).
     * Pitch length is along X (-52.5 to +52.5) and width is along Z (-34.0 to +34.0).
     * Home attacks +X (own goal at -52.5, opponent goal at +52.5).
     * Away attacks -X (own goal at +52.5, opponent goal at -52.5).
     */
    fun slotToWorld(slot: FormationSlot, isHome: Boolean): Pair<Float, Float> {
        val halfW = 31.0f
        val halfL = 49.0f
        return if (isHome) {
            (slot.zNorm * halfL) to (slot.xNorm * halfW)
        } else {
            (-slot.zNorm * halfL) to (-slot.xNorm * halfW)
        }
    }
}
