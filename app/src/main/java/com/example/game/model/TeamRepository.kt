package com.example.game.model

object TeamRepository {

    private fun rosterFor(
        prefix: String,
        gkNames: List<String>,
        defNames: List<String>,
        midNames: List<String>,
        fwdNames: List<String>,
        baseRating: Int,
        skinTones: List<Long>,
        hairColors: List<Long>
    ): List<PlayerProfile> {
        val list = mutableListOf<PlayerProfile>()
        fun attr(role: PlayerRole, boost: Int): PlayerAttributes {
            val r = (baseRating + boost).coerceIn(66, 94)
            return when (role) {
                PlayerRole.GK -> PlayerAttributes(r - 12, r - 28, r - 8, r - 18, r + 2, r)
                PlayerRole.DEF -> PlayerAttributes(r - 2, r - 14, r - 4, r - 7, r + 3, r + 2)
                PlayerRole.MID -> PlayerAttributes(r - 1, r - 3, r + 3, r + 2, r - 6, r - 3)
                PlayerRole.FWD -> PlayerAttributes(r + 3, r + 4, r - 2, r + 3, r - 18, r - 2)
            }
        }
        // Starters 0..10: 1 GK, 4 DEF, 3 MID, 3 FWD (ordered cleanly for any formation swap)
        list += PlayerProfile("${prefix}_gk1", gkNames[0], 1, PlayerRole.GK, attr(PlayerRole.GK, 2), skinTones[0], hairColors[0])
        val defNums = listOf(2, 4, 5, 3)
        for (i in 0..3) {
            list += PlayerProfile(
                "${prefix}_d$i",
                defNames[i],
                defNums[i],
                PlayerRole.DEF,
                attr(PlayerRole.DEF, if (i == 1) 2 else 0),
                skinTones[(i + 1) % skinTones.size],
                hairColors[(i + 1) % hairColors.size]
            )
        }
        val midNums = listOf(8, 6, 10)
        for (i in 0..2) {
            list += PlayerProfile(
                "${prefix}_m$i",
                midNames[i],
                midNums[i],
                PlayerRole.MID,
                attr(PlayerRole.MID, if (i == 2) 3 else 1),
                skinTones[(i + 2) % skinTones.size],
                hairColors[(i + 2) % hairColors.size]
            )
        }
        val fwdNums = listOf(11, 9, 7)
        for (i in 0..2) {
            list += PlayerProfile(
                "${prefix}_f$i",
                fwdNames[i],
                fwdNums[i],
                PlayerRole.FWD,
                attr(PlayerRole.FWD, if (i == 1) 4 else 2),
                skinTones[(i + 3) % skinTones.size],
                hairColors[(i + 3) % hairColors.size]
            )
        }
        // 5 Substitutes (11..15)
        list += PlayerProfile("${prefix}_gk2", gkNames[1], 12, PlayerRole.GK, attr(PlayerRole.GK, -3), skinTones[1 % skinTones.size], hairColors[0])
        list += PlayerProfile("${prefix}_d4", defNames[4], 15, PlayerRole.DEF, attr(PlayerRole.DEF, -2), skinTones[2 % skinTones.size], hairColors[1 % hairColors.size])
        list += PlayerProfile("${prefix}_m3", midNames[3], 16, PlayerRole.MID, attr(PlayerRole.MID, -1), skinTones[0], hairColors[2 % hairColors.size])
        list += PlayerProfile("${prefix}_m4", midNames[4], 18, PlayerRole.MID, attr(PlayerRole.MID, -2), skinTones[3 % skinTones.size], hairColors[1 % hairColors.size])
        list += PlayerProfile("${prefix}_f3", fwdNames[3], 20, PlayerRole.FWD, attr(PlayerRole.FWD, 0), skinTones[1 % skinTones.size], hairColors[0])
        return list
    }

    val allTeams: List<TeamProfile> = listOf(
        TeamProfile(
            id = "bra",
            name = "Brazil",
            shortCode = "BRA",
            confederation = "CONMEBOL",
            homeKit = KitColors(0xFFFACC15, 0xFF16A34A, 0xFF1D4ED8, 0xFFFFFFFF, 0xFF0F172A),
            awayKit = KitColors(0xFF1D4ED8, 0xFFFACC15, 0xFFFFFFFF, 0xFF1D4ED8, 0xFF16A34A),
            defaultFormation = FormationType.F_4_3_3,
            badgePrimary = 0xFF16A34A,
            badgeAccent = 0xFFFACC15,
            badgeStyle = BadgeStyle.CHEVRON_SHIELD,
            roster = rosterFor(
                prefix = "bra",
                gkNames = listOf("Alisson Rocha", "Ederson Costa"),
                defNames = listOf("Danilo Moura", "Marquinhos Silva", "Gabriel Magalhaes", "Wendell Borges", "Bremer Santos"),
                midNames = listOf("Bruno Guimaraes", "Casemiro Pires", "Lucas Paqueta", "Joelinton Neves", "Andreas Viana"),
                fwdNames = listOf("Vini Ribeiro", "Rodrygo Teixeira", "Raphinha Dias", "Endrick Gomes"),
                baseRating = 86,
                skinTones = listOf(0xFFD69E74, 0xFF8C5A3C, 0xFFE5B896, 0xFFB07852),
                hairColors = listOf(0xFF181412, 0xFF2B2018, 0xFF101010)
            )
        ),
        TeamProfile(
            id = "arg",
            name = "Argentina",
            shortCode = "ARG",
            confederation = "CONMEBOL",
            homeKit = KitColors(0xFF38BDF8, 0xFFFFFFFF, 0xFF0F172A, 0xFFFFFFFF, 0xFFDC2626),
            awayKit = KitColors(0xFF1E3A8A, 0xFF38BDF8, 0xFF1E3A8A, 0xFF1E3A8A, 0xFF22C55E),
            defaultFormation = FormationType.F_4_4_2,
            badgePrimary = 0xFF38BDF8,
            badgeAccent = 0xFFFBBF24,
            badgeStyle = BadgeStyle.VERTICAL_STRIPES,
            roster = rosterFor(
                prefix = "arg",
                gkNames = listOf("Emiliano Vargas", "Geronimo Rulli"),
                defNames = listOf("Nahuel Molina", "Cristian Romero", "Lisandro Ayala", "Nicolas Taglia", "German Pezzella"),
                midNames = listOf("Rodrigo De Paul", "Enzo Fernandez", "Alexis MacAllister", "Leandro Paredes", "Exequiel Palacios"),
                fwdNames = listOf("Julian Alvarez", "Lautaro Martinez", "Mateo Solari", "Alejandro Garnacho"),
                baseRating = 87,
                skinTones = listOf(0xFFE6BC98, 0xFFD69F78, 0xFFF0CBB0),
                hairColors = listOf(0xFF1C1613, 0xFF3B2B20, 0xFF12100E)
            )
        ),
        TeamProfile(
            id = "fra",
            name = "France",
            shortCode = "FRA",
            confederation = "UEFA",
            homeKit = KitColors(0xFF1D4ED8, 0xFFEF4444, 0xFFFFFFFF, 0xFFDC2626, 0xFFFACC15),
            awayKit = KitColors(0xFFF8FAFC, 0xFF1D4ED8, 0xFF1D4ED8, 0xFFF8FAFC, 0xFF0F172A),
            defaultFormation = FormationType.F_4_2_3_1,
            badgePrimary = 0xFF1D4ED8,
            badgeAccent = 0xFFEF4444,
            badgeStyle = BadgeStyle.STAR_CREST,
            roster = rosterFor(
                prefix = "fra",
                gkNames = listOf("Mike Maignan", "Brice Samba"),
                defNames = listOf("Jules Kounde", "William Saliba", "Dayot Upamecano", "Theo Hernandez", "Ibrahima Konate"),
                midNames = listOf("Aurelien Tchoua", "Eduardo Camavinga", "Antoine Moreau", "Adrien Rabiot", "Warren Zaire"),
                fwdNames = listOf("Kylian Laurent", "Marcus Thuram", "Ousmane Diallo", "Randal Kolo"),
                baseRating = 87,
                skinTones = listOf(0xFF7D4E32, 0xFFEAC2A4, 0xFFA26D4A, 0xFF5E3924),
                hairColors = listOf(0xFF111111, 0xFF281E18, 0xFF4A3728)
            )
        ),
        TeamProfile(
            id = "ger",
            name = "Germany",
            shortCode = "GER",
            confederation = "UEFA",
            homeKit = KitColors(0xFFF8FAFC, 0xFF111827, 0xFF111827, 0xFFF8FAFC, 0xFF10B981),
            awayKit = KitColors(0xFF7C3AED, 0xFFEC4899, 0xFF1E1B4B, 0xFF7C3AED, 0xFFFACC15),
            defaultFormation = FormationType.F_4_2_3_1,
            badgePrimary = 0xFF1E293B,
            badgeAccent = 0xFFF59E0B,
            badgeStyle = BadgeStyle.HORIZONTAL_BAND,
            roster = rosterFor(
                prefix = "ger",
                gkNames = listOf("Marc Ter Stegen", "Oliver Baumann"),
                defNames = listOf("Joshua Kimmich", "Antonio Rudiger", "Jonathan Tah", "David Raum", "Nico Schlotter"),
                midNames = listOf("Toni Lindner", "Robert Andrich", "Jamal Musiala", "Florian Wirtz", "Ilkay Demir"),
                fwdNames = listOf("Kai Havertz", "Niclas Fullkrug", "Leroy Weber", "Karim Adeyemi"),
                baseRating = 85,
                skinTones = listOf(0xFFF2CEB2, 0xFFE2B492, 0xFF8C5B3E),
                hairColors = listOf(0xFF3E2D20, 0xFFC59B5F, 0xFF151210)
            )
        ),
        TeamProfile(
            id = "por",
            name = "Portugal",
            shortCode = "POR",
            confederation = "UEFA",
            homeKit = KitColors(0xFFDC2626, 0xFF15803D, 0xFF15803D, 0xFFDC2626, 0xFF0EA5E9),
            awayKit = KitColors(0xFFF1F5F9, 0xFF1E3A8A, 0xFF1E3A8A, 0xFFF1F5F9, 0xFFF59E0B),
            defaultFormation = FormationType.F_4_3_3,
            badgePrimary = 0xFFDC2626,
            badgeAccent = 0xFF16A34A,
            badgeStyle = BadgeStyle.CHEVRON_SHIELD,
            roster = rosterFor(
                prefix = "por",
                gkNames = listOf("Diogo Costa", "Jose Sa"),
                defNames = listOf("Joao Cancelo", "Ruben Dias", "Antonio Silva", "Nuno Mendes", "Diogo Dalot"),
                midNames = listOf("Vitinha Ferreira", "Joao Palhinha", "Bruno Fernandes", "Bernardo Silva", "Joao Neves"),
                fwdNames = listOf("Rafael Leao", "Cristiano Ramos", "Pedro Neto", "Diogo Jota"),
                baseRating = 85,
                skinTones = listOf(0xFFE7BD9A, 0xFFD49D76, 0xFF8D5B3E),
                hairColors = listOf(0xFF1A1412, 0xFF38291E, 0xFF101010)
            )
        ),
        TeamProfile(
            id = "esp",
            name = "Spain",
            shortCode = "ESP",
            confederation = "UEFA",
            homeKit = KitColors(0xFFEF4444, 0xFFFACC15, 0xFF1E3A8A, 0xFFEF4444, 0xFF14B8A6),
            awayKit = KitColors(0xFFFDE047, 0xFFEF4444, 0xFF1E3A8A, 0xFFFDE047, 0xFF1E293B),
            defaultFormation = FormationType.F_4_3_3,
            badgePrimary = 0xFFEF4444,
            badgeAccent = 0xFFFACC15,
            badgeStyle = BadgeStyle.HORIZONTAL_BAND,
            roster = rosterFor(
                prefix = "esp",
                gkNames = listOf("Unai Simon", "David Raya"),
                defNames = listOf("Dani Carvajal", "Robin Le Normand", "Aymeric Laporte", "Marc Cucurella", "Pau Cubarsi"),
                midNames = listOf("Rodri Cascante", "Fabian Ruiz", "Pedri Gonzalez", "Dani Olmo", "Gavi Paez"),
                fwdNames = listOf("Nico Williams", "Alvaro Morata", "Lamine Navarro", "Ferran Torres"),
                baseRating = 86,
                skinTones = listOf(0xFFE8BF9C, 0xFFD9A47E, 0xFF8B583B),
                hairColors = listOf(0xFF1B1512, 0xFF3A2B1F, 0xFF101010)
            )
        ),
        TeamProfile(
            id = "eng",
            name = "England",
            shortCode = "ENG",
            confederation = "UEFA",
            homeKit = KitColors(0xFFF8FAFC, 0xFF1E3A8A, 0xFF1E3A8A, 0xFFF8FAFC, 0xFF22C55E),
            awayKit = KitColors(0xFF4C1D95, 0xFFF59E0B, 0xFF4C1D95, 0xFF4C1D95, 0xFF38BDF8),
            defaultFormation = FormationType.F_4_2_3_1,
            badgePrimary = 0xFFF8FAFC,
            badgeAccent = 0xFF1D4ED8,
            badgeStyle = BadgeStyle.STAR_CREST,
            roster = rosterFor(
                prefix = "eng",
                gkNames = listOf("Jordan Pickford", "Aaron Ramsdale"),
                defNames = listOf("Kyle Walker", "John Stones", "Marc Guehi", "Luke Shaw", "Trent Arnold"),
                midNames = listOf("Declan Rice", "Kobbie Mainoo", "Jude Carter", "Phil Foden", "Cole Palmer"),
                fwdNames = listOf("Anthony Gordon", "Harry Clarke", "Bukayo Okafor", "Ollie Watkins"),
                baseRating = 85,
                skinTones = listOf(0xFFF1CCB0, 0xFF8C5B3E, 0xFFD8A078, 0xFF6B4229),
                hairColors = listOf(0xFF4B3829, 0xFF151210, 0xFFB88F52)
            )
        ),
        TeamProfile(
            id = "ban",
            name = "Bangladesh",
            shortCode = "BAN",
            confederation = "AFC",
            homeKit = KitColors(0xFF006A4E, 0xFFF42A41, 0xFF006A4E, 0xFFF42A41, 0xFFF59E0B),
            awayKit = KitColors(0xFFF42A41, 0xFF006A4E, 0xFFFFFFFF, 0xFF006A4E, 0xFF0284C7),
            defaultFormation = FormationType.F_4_4_2,
            badgePrimary = 0xFF006A4E,
            badgeAccent = 0xFFF42A41,
            badgeStyle = BadgeStyle.SUN_DISC,
            roster = rosterFor(
                prefix = "ban",
                gkNames = listOf("Anisur Rahman", "Mitul Marma"),
                defNames = listOf("Tariq Kazi", "Topu Barman", "Bishwanath Ghosh", "Isa Faysal", "Rahmat Mia"),
                midNames = listOf("Jamal Bhuyan", "Sohel Rana", "Md Ridoy", "Mojibur Jony", "Syed Kazim"),
                fwdNames = listOf("Rakib Hossain", "Shekh Morsalin", "Foysal Fahim", "Sumon Reza"),
                baseRating = 76,
                skinTones = listOf(0xFFC68E68, 0xFFB87F58, 0xFFD29C76),
                hairColors = listOf(0xFF12100E, 0xFF1D1815)
            )
        )
    )

    fun getById(id: String): TeamProfile =
        allTeams.firstOrNull { it.id == id } ?: allTeams.first()
}
