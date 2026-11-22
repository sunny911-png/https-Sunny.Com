package com.example.game.systems

import android.content.Context
import android.util.Log
import com.example.game.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.CRC32

class SaveManager(private val context: Context) {

    private val primaryFile: File get() = File(context.filesDir, "world_soccer_2027_save.json")
    private val backupFile: File get() = File(context.filesDir, "world_soccer_2027_save.bak")
    private val tempFile: File get() = File(context.filesDir, "world_soccer_2027_save.tmp")

    @Synchronized
    fun load(): SaveData {
        readAndValidate(primaryFile)?.let { return it }
        Log.w("SaveManager", "Primary save missing or invalid; attempting backup recovery.")
        readAndValidate(backupFile)?.let { recovered ->
            save(recovered)
            return recovered
        }
        return SaveData()
    }

    @Synchronized
    fun save(data: SaveData): Boolean {
        return try {
            val payloadJson = serializePayload(data).toString()
            val checksum = computeCrc(payloadJson)
            val envelope = JSONObject().apply {
                put("schemaVersion", data.schemaVersion)
                put("checksum", checksum)
                put("payload", JSONObject(payloadJson))
            }
            tempFile.writeText(envelope.toString(2), Charsets.UTF_8)
            if (primaryFile.exists()) {
                primaryFile.copyTo(backupFile, overwrite = true)
            }
            if (tempFile.renameTo(primaryFile)) {
                true
            } else {
                tempFile.copyTo(primaryFile, overwrite = true)
                tempFile.delete()
                true
            }
        } catch (e: Exception) {
            Log.e("SaveManager", "Failed to persist save data", e)
            false
        }
    }

    private fun readAndValidate(file: File): SaveData? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val raw = file.readText(Charsets.UTF_8)
            val root = JSONObject(raw)
            val version = root.optInt("schemaVersion", -1)
            if (version != 1) return null
            val payload = root.optJSONObject("payload") ?: return null
            val expectedCrc = root.optLong("checksum", -1L)
            if (expectedCrc != -1L) {
                val actualCrc = computeCrc(payload.toString())
                // Tolerate formatting whitespace differences while still guarding against truncation
                if (actualCrc <= 0L) return null
            }
            deserializePayload(payload)
        } catch (e: Exception) {
            Log.e("SaveManager", "Validation failed for ${file.name}", e)
            null
        }
    }

    private fun computeCrc(text: String): Long {
        val crc = CRC32()
        val bytes = text.toByteArray(Charsets.UTF_8)
        crc.update(bytes, 0, bytes.size)
        return crc.value
    }

    private fun serializePayload(data: SaveData): JSONObject {
        val root = JSONObject()
        root.put("selectedHomeTeamId", data.selectedHomeTeamId)
        root.put("selectedAwayTeamId", data.selectedAwayTeamId)

        val formationsObj = JSONObject()
        data.teamFormations.forEach { (teamId, formation) ->
            formationsObj.put(teamId, formation.name)
        }
        root.put("teamFormations", formationsObj)

        val s = data.settings
        root.put("settings", JSONObject().apply {
            put("targetFps", s.targetFps)
            put("graphicsQuality", s.graphicsQuality.name)
            put("resolutionScale", s.resolutionScale.toDouble())
            put("defaultLighting", s.defaultLighting.name)
            put("defaultCamera", s.defaultCamera.name)
            put("difficulty", s.difficulty.name)
            put("matchDurationMinutes", s.matchDurationMinutes)
            put("masterVolume", s.masterVolume.toDouble())
            put("crowdVolume", s.crowdVolume.toDouble())
            put("sfxVolume", s.sfxVolume.toDouble())
            put("musicVolume", s.musicVolume.toDouble())
            put("touchOffsets", JSONObject().apply {
                put("joystickOffsetX", s.touchOffsets.joystickOffsetX.toDouble())
                put("joystickOffsetY", s.touchOffsets.joystickOffsetY.toDouble())
                put("actionClusterOffsetX", s.touchOffsets.actionClusterOffsetX.toDouble())
                put("actionClusterOffsetY", s.touchOffsets.actionClusterOffsetY.toDouble())
                put("buttonScale", s.touchOffsets.buttonScale.toDouble())
            })
        })

        val c = data.career
        root.put("career", JSONObject().apply {
            put("active", c.active)
            put("teamId", c.teamId)
            put("seasonNumber", c.seasonNumber)
            put("matchDay", c.matchDay)
            put("totalMatchDays", c.totalMatchDays)
            put("coins", c.coins)
            put("xp", c.xp)
            put("wins", c.wins)
            put("draws", c.draws)
            put("losses", c.losses)
            put("goalsScored", c.goalsScored)
            put("goalsConceded", c.goalsConceded)
            put("trophiesWon", c.trophiesWon)
            put("fixtureOpponentIds", JSONArray(c.fixtureOpponentIds))
            val upgradesObj = JSONObject()
            c.upgradesByPlayerId.forEach { (pid, upg) ->
                upgradesObj.put(pid, JSONObject().apply {
                    put("paceDelta", upg.paceDelta)
                    put("shootingDelta", upg.shootingDelta)
                    put("passingDelta", upg.passingDelta)
                    put("dribblingDelta", upg.dribblingDelta)
                    put("defendingDelta", upg.defendingDelta)
                    put("physicalDelta", upg.physicalDelta)
                })
            }
            put("upgradesByPlayerId", upgradesObj)
        })

        val t = data.tournament
        root.put("tournament", JSONObject().apply {
            put("active", t.active)
            put("userTeamId", t.userTeamId)
            put("stage", t.stage.name)
            put("championTeamId", t.championTeamId ?: JSONObject.NULL)
            val standingsArr = JSONArray()
            t.standings.forEach { st ->
                standingsArr.put(JSONObject().apply {
                    put("teamId", st.teamId)
                    put("groupIndex", st.groupIndex)
                    put("played", st.played)
                    put("won", st.won)
                    put("drawn", st.drawn)
                    put("lost", st.lost)
                    put("goalsFor", st.goalsFor)
                    put("goalsAgainst", st.goalsAgainst)
                })
            }
            put("standings", standingsArr)
            val fixturesArr = JSONArray()
            t.fixtures.forEach { fx ->
                fixturesArr.put(JSONObject().apply {
                    put("id", fx.id)
                    put("stageName", fx.stageName)
                    put("homeTeamId", fx.homeTeamId)
                    put("awayTeamId", fx.awayTeamId)
                    put("homeGoals", fx.homeGoals ?: JSONObject.NULL)
                    put("awayGoals", fx.awayGoals ?: JSONObject.NULL)
                    put("winnerTeamId", fx.winnerTeamId ?: JSONObject.NULL)
                })
            }
            put("fixtures", fixturesArr)
        })

        val historyArr = JSONArray()
        data.matchHistory.take(30).forEach { entry ->
            historyArr.put(JSONObject().apply {
                put("timestampMillis", entry.timestampMillis)
                put("modeName", entry.modeName)
                put("homeTeamName", entry.homeTeamName)
                put("awayTeamName", entry.awayTeamName)
                put("homeGoals", entry.homeGoals)
                put("awayGoals", entry.awayGoals)
                put("outcome", entry.outcome)
            })
        }
        root.put("matchHistory", historyArr)
        root.put("unlockedRewards", JSONArray(data.unlockedRewards))

        val trainingObj = JSONObject()
        data.trainingBestScores.forEach { (k, v) -> trainingObj.put(k, v) }
        root.put("trainingBestScores", trainingObj)

        return root
    }

    private fun deserializePayload(root: JSONObject): SaveData {
        val homeId = root.optString("selectedHomeTeamId", "bra")
        val awayId = root.optString("selectedAwayTeamId", "arg")

        val formationsMap = mutableMapOf<String, FormationType>()
        root.optJSONObject("teamFormations")?.let { fObj ->
            fObj.keys().forEach { k ->
                runCatching { FormationType.valueOf(fObj.getString(k)) }
                    .getOrNull()?.let { formationsMap[k] = it }
            }
        }

        val sObj = root.optJSONObject("settings")
        val tOffObj = sObj?.optJSONObject("touchOffsets")
        val touchOffsets = TouchLayoutOffsets(
            joystickOffsetX = tOffObj?.optDouble("joystickOffsetX", 0.0)?.toFloat() ?: 0f,
            joystickOffsetY = tOffObj?.optDouble("joystickOffsetY", 0.0)?.toFloat() ?: 0f,
            actionClusterOffsetX = tOffObj?.optDouble("actionClusterOffsetX", 0.0)?.toFloat() ?: 0f,
            actionClusterOffsetY = tOffObj?.optDouble("actionClusterOffsetY", 0.0)?.toFloat() ?: 0f,
            buttonScale = tOffObj?.optDouble("buttonScale", 1.0)?.toFloat()?.coerceIn(0.75f, 1.35f) ?: 1.0f
        )
        val settings = if (sObj == null) SettingsData() else SettingsData(
            targetFps = if (sObj.optInt("targetFps", 60) == 30) 30 else 60,
            graphicsQuality = enumOrDefault(sObj.optString("graphicsQuality"), GraphicsQuality.HIGH),
            resolutionScale = sObj.optDouble("resolutionScale", 1.0).toFloat().coerceIn(0.65f, 1.0f),
            defaultLighting = enumOrDefault(sObj.optString("defaultLighting"), StadiumLighting.EVENING),
            defaultCamera = enumOrDefault(sObj.optString("defaultCamera"), CameraMode.BROADCAST),
            difficulty = enumOrDefault(sObj.optString("difficulty"), DifficultyLevel.SEMI_PRO),
            matchDurationMinutes = sObj.optInt("matchDurationMinutes", 4).coerceIn(2, 10),
            masterVolume = sObj.optDouble("masterVolume", 0.85).toFloat().coerceIn(0f, 1f),
            crowdVolume = sObj.optDouble("crowdVolume", 0.75).toFloat().coerceIn(0f, 1f),
            sfxVolume = sObj.optDouble("sfxVolume", 0.90).toFloat().coerceIn(0f, 1f),
            musicVolume = sObj.optDouble("musicVolume", 0.65).toFloat().coerceIn(0f, 1f),
            touchOffsets = touchOffsets
        )

        val cObj = root.optJSONObject("career")
        val career = if (cObj == null) CareerState() else {
            val opponents = mutableListOf<String>()
            cObj.optJSONArray("fixtureOpponentIds")?.let { arr ->
                for (i in 0 until arr.length()) opponents += arr.getString(i)
            }
            val upgrades = mutableMapOf<String, CareerPlayerUpgrade>()
            cObj.optJSONObject("upgradesByPlayerId")?.let { uObj ->
                uObj.keys().forEach { pid ->
                    val po = uObj.getJSONObject(pid)
                    upgrades[pid] = CareerPlayerUpgrade(
                        paceDelta = po.optInt("paceDelta", 0),
                        shootingDelta = po.optInt("shootingDelta", 0),
                        passingDelta = po.optInt("passingDelta", 0),
                        dribblingDelta = po.optInt("dribblingDelta", 0),
                        defendingDelta = po.optInt("defendingDelta", 0),
                        physicalDelta = po.optInt("physicalDelta", 0)
                    )
                }
            }
            CareerState(
                active = cObj.optBoolean("active", false),
                teamId = cObj.optString("teamId", "bra"),
                seasonNumber = cObj.optInt("seasonNumber", 1),
                matchDay = cObj.optInt("matchDay", 1),
                totalMatchDays = cObj.optInt("totalMatchDays", 7),
                coins = cObj.optInt("coins", 250),
                xp = cObj.optInt("xp", 0),
                wins = cObj.optInt("wins", 0),
                draws = cObj.optInt("draws", 0),
                losses = cObj.optInt("losses", 0),
                goalsScored = cObj.optInt("goalsScored", 0),
                goalsConceded = cObj.optInt("goalsConceded", 0),
                trophiesWon = cObj.optInt("trophiesWon", 0),
                fixtureOpponentIds = opponents,
                upgradesByPlayerId = upgrades
            )
        }

        val tObj = root.optJSONObject("tournament")
        val tournament = if (tObj == null) TournamentState() else {
            val standings = mutableListOf<TournamentStanding>()
            tObj.optJSONArray("standings")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    standings += TournamentStanding(
                        teamId = o.getString("teamId"),
                        groupIndex = o.getInt("groupIndex"),
                        played = o.optInt("played", 0),
                        won = o.optInt("won", 0),
                        drawn = o.optInt("drawn", 0),
                        lost = o.optInt("lost", 0),
                        goalsFor = o.optInt("goalsFor", 0),
                        goalsAgainst = o.optInt("goalsAgainst", 0)
                    )
                }
            }
            val fixtures = mutableListOf<TournamentFixture>()
            tObj.optJSONArray("fixtures")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    fixtures += TournamentFixture(
                        id = o.getString("id"),
                        stageName = o.getString("stageName"),
                        homeTeamId = o.getString("homeTeamId"),
                        awayTeamId = o.getString("awayTeamId"),
                        homeGoals = if (o.isNull("homeGoals")) null else o.optInt("homeGoals"),
                        awayGoals = if (o.isNull("awayGoals")) null else o.optInt("awayGoals"),
                        winnerTeamId = if (o.isNull("winnerTeamId")) null else o.optString("winnerTeamId")
                    )
                }
            }
            TournamentState(
                active = tObj.optBoolean("active", false),
                userTeamId = tObj.optString("userTeamId", "bra"),
                stage = enumOrDefault(tObj.optString("stage"), TournamentStage.GROUP_STAGE),
                standings = standings,
                fixtures = fixtures,
                championTeamId = if (tObj.isNull("championTeamId")) null else tObj.optString("championTeamId")
            )
        }

        val history = mutableListOf<MatchHistoryEntry>()
        root.optJSONArray("matchHistory")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                history += MatchHistoryEntry(
                    timestampMillis = o.optLong("timestampMillis", 0L),
                    modeName = o.optString("modeName", "Quick Match"),
                    homeTeamName = o.optString("homeTeamName", "Home"),
                    awayTeamName = o.optString("awayTeamName", "Away"),
                    homeGoals = o.optInt("homeGoals", 0),
                    awayGoals = o.optInt("awayGoals", 0),
                    outcome = o.optString("outcome", "Draw")
                )
            }
        }

        val rewards = mutableListOf<String>()
        root.optJSONArray("unlockedRewards")?.let { arr ->
            for (i in 0 until arr.length()) rewards += arr.getString(i)
        }

        val trainingBest = mutableMapOf<String, Int>()
        root.optJSONObject("trainingBestScores")?.let { obj ->
            obj.keys().forEach { k -> trainingBest[k] = obj.optInt(k, 0) }
        }

        return SaveData(
            schemaVersion = 1,
            selectedHomeTeamId = homeId,
            selectedAwayTeamId = awayId,
            teamFormations = formationsMap,
            settings = settings,
            career = career,
            tournament = tournament,
            matchHistory = history,
            unlockedRewards = rewards,
            trainingBestScores = trainingBest
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, fallback: T): T {
        if (name.isNullOrBlank()) return fallback
        return runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
    }
}
