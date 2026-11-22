package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.game.engine.BallEvent
import com.example.game.engine.BallPhysics
import com.example.game.engine.PitchConstants
import com.example.game.engine.SimBall
import com.example.game.engine.SimPlayer
import com.example.game.model.*
import com.example.game.systems.CareerManager
import com.example.game.systems.SaveManager
import com.example.game.systems.TournamentManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorldSoccerSystemsTest {

    @Test
    fun `all eight nations and five formations are valid`() {
        val ids = TeamRepository.allTeams.map { it.id }.toSet()
        assertEquals(setOf("bra", "arg", "fra", "ger", "por", "esp", "eng", "ban"), ids)
        TeamRepository.allTeams.forEach { team ->
            assertEquals(16, team.roster.size)
        }
        FormationType.entries.forEach { f ->
            assertEquals(11, f.slots.size)
        }
    }

    @Test
    fun `ball physics detects home goal scored`() {
        val physics = BallPhysics()
        val ball = SimBall().apply {
            x = PitchConstants.HALF_LENGTH + 0.1f
            y = 1.0f
            z = 0.0f
            vx = 18.0f
            ownerIndex = -1
        }
        val players = emptyArray<SimPlayer>()
        val evt = physics.step(ball, players, 1f / 60f)
        assertTrue(evt is BallEvent.GoalScored && evt.scoredByHome)
    }

    @Test
    fun `tournament manager advances from group stage to final`() {
        val tm = TournamentManager()
        var state = tm.createNewTournament("bra")
        assertEquals(TournamentStage.GROUP_STAGE, state.stage)

        // Play all 3 user group matches as wins
        repeat(3) {
            val fx = tm.nextUserFixture(state)!!
            val hg = if (fx.homeTeamId == "bra") 2 else 0
            val ag = if (fx.awayTeamId == "bra") 2 else 0
            state = tm.recordUserMatchResult(state, fx.id, hg, ag, winnerOverride = "bra")
        }
        assertEquals(TournamentStage.SEMI_FINALS, state.stage)

        val sf = tm.nextUserFixture(state)!!
        state = tm.recordUserMatchResult(state, sf.id, 3, 1, winnerOverride = "bra")
        assertEquals(TournamentStage.FINAL, state.stage)

        val fin = tm.nextUserFixture(state)!!
        state = tm.recordUserMatchResult(state, fin.id, 2, 1, winnerOverride = "bra")
        assertEquals(TournamentStage.CHAMPION_CROWNED, state.stage)
        assertEquals("bra", state.championTeamId)
    }

    @Test
    fun `career manager awards coins and upgrades player stats`() {
        val cm = CareerManager()
        var state = cm.startNewCareer("ban")
        val initialCoins = state.coins
        state = cm.recordMatchResult(state, userGoals = 3, opponentGoals = 0, DifficultyLevel.PRO)
        assertTrue(state.coins > initialCoins)

        val player = TeamRepository.getById("ban").roster[9]
        val upgraded = cm.upgradePlayerAttribute(state, player.id, StatType.SHOOTING)
        assertNotNull(upgraded)
        assertEquals(2, upgraded!!.upgradesByPlayerId[player.id]?.shootingDelta)
    }

    @Test
    fun `save manager persists json and recovers from backup when primary corrupted`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sm = SaveManager(context)
        val initial = SaveData(
            selectedHomeTeamId = "ban",
            settings = SettingsData(targetFps = 30, graphicsQuality = GraphicsQuality.MEDIUM)
        )
        assertTrue(sm.save(initial))
        // Save a second time so .bak is created
        val second = initial.copy(selectedAwayTeamId = "fra")
        assertTrue(sm.save(second))

        // Corrupt primary save file
        val primary = File(context.filesDir, "world_soccer_2027_save.json")
        primary.writeText("{corrupted_json", Charsets.UTF_8)

        val recovered = sm.load()
        assertEquals("ban", recovered.selectedHomeTeamId)
        assertEquals(30, recovered.settings.targetFps)
    }
}
