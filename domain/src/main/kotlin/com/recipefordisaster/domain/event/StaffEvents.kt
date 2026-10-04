package com.recipefordisaster.domain.event

import com.recipefordisaster.domain.employee.PersonalityTrait
import com.recipefordisaster.domain.employee.RelationshipScore
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.simulation.LogTone

/**
 * Events driven by the people working here: stress, morale and personality
 * traits. These are the "exhausted employee" end of the product brief's
 * chain — an overworked team doesn't just serve slower (that's
 * [com.recipefordisaster.domain.simulation.ServiceSimulator]), it gets sick,
 * fights, and eventually walks out.
 */
internal object StaffEvents {

    /** Below this morale, anyone who isn't [PersonalityTrait.LOYAL] may hand in their notice. */
    private const val QUIT_MORALE = 30

    val staffSick = EventRule(
        id = "staff_sick",
        title = "Called in sick",
        severity = Severity.MODERATE,
        cooldownDays = 4,
        unique = false,
        prerequisite = { state -> state.activeEmployees.isNotEmpty() },
        weight = { state ->
            val averageStress = state.activeEmployees.map { it.stress }.average()
            val anxious = state.activeEmployees.count { PersonalityTrait.ANXIOUS in it.personalityTraits }
            (0.2 + averageStress / 70.0 + anxious * 0.2).toFloat()
        },
        resolve = { state, rng ->
            val victim = rng.pickWeighted(state.activeEmployees) { it.stress + 10.0 }
            val days = 1 + rng.nextInt(2)
            EventOutcome(
                ruleId = "staff_sick",
                description = "${victim.name} called in sick for $days day${if (days > 1) "s" else ""}. They sounded suspiciously fine.",
                resultingState = state.updateEmployee(victim.id) { it.fallSick(days) },
                tone = LogTone.BAD,
            )
        },
    )

    val burnout = EventRule(
        id = "burnout",
        title = "Burnout",
        severity = Severity.SEVERE,
        cooldownDays = 5,
        unique = false,
        prerequisite = { state -> state.activeEmployees.any { it.stress >= 85 } },
        weight = { 1.5f },
        resolve = { state, rng ->
            val victim = rng.pick(state.activeEmployees.filter { it.stress >= 85 })
            EventOutcome(
                ruleId = "burnout",
                description = "${victim.name} quietly lay down in the walk-in fridge and refused to come out. They're off for 3 days.",
                resultingState = state.updateEmployee(victim.id) { it.fallSick(3).adjustMorale(-15) },
                tone = LogTone.BAD,
            )
        },
    )

    val staffQuits = EventRule(
        id = "staff_quits",
        title = "Resignation",
        severity = Severity.SEVERE,
        cooldownDays = 3,
        unique = false,
        // Loyal staff grumble, but they don't leave.
        prerequisite = { state -> state.employees.any { it.morale < QUIT_MORALE && PersonalityTrait.LOYAL !in it.personalityTraits } },
        weight = { state ->
            val lowest = state.employees.filter { PersonalityTrait.LOYAL !in it.personalityTraits }.minOf { it.morale }
            (0.8 + (QUIT_MORALE - lowest) / 10.0).toFloat()
        },
        resolve = { state, rng ->
            val leaving = rng.pickWeighted(state.employees.filter { it.morale < QUIT_MORALE && PersonalityTrait.LOYAL !in it.personalityTraits }) {
                QUIT_MORALE + 1.0 - it.morale
            }
            EventOutcome(
                ruleId = "staff_quits",
                description = "${leaving.name} quit, leaving behind their apron and a strongly worded note taped to the fryer.",
                resultingState = state.copy(employees = state.employees.filter { it.id != leaving.id })
                    .updateAllEmployees { it.adjustMorale(-3) },
                tone = LogTone.BAD,
            )
        },
    )

    val kitchenArgument = EventRule(
        id = "kitchen_argument",
        title = "Kitchen argument",
        severity = Severity.MINOR,
        cooldownDays = 4,
        unique = false,
        prerequisite = { state ->
            state.activeEmployees.size >= 2 &&
                state.activeEmployees.any { PersonalityTrait.HOTHEADED in it.personalityTraits || it.stress >= 60 }
        },
        weight = { state ->
            val hotheads = state.activeEmployees.count { PersonalityTrait.HOTHEADED in it.personalityTraits }
            val frazzled = state.activeEmployees.count { it.stress >= 70 }
            (0.4 + hotheads * 0.5 + frazzled * 0.3).toFloat()
        },
        resolve = { state, rng ->
            val instigator = rng.pickWeighted(state.activeEmployees) {
                it.stress + if (PersonalityTrait.HOTHEADED in it.personalityTraits) 60.0 else 0.0
            }
            val other = rng.pick(state.activeEmployees.filter { it.id != instigator.id })
            val topic = rng.pick(ARGUMENT_TOPICS)
            fun soured(score: RelationshipScore?) = RelationshipScore(((score?.value ?: 0) - 20).coerceIn(-100, 100))
            val resulting = state
                .updateEmployee(instigator.id) {
                    it.adjustMorale(-8).adjustStress(5).copy(relationships = it.relationships + (other.id to soured(it.relationships[other.id])))
                }
                .updateEmployee(other.id) {
                    it.adjustMorale(-8).adjustStress(5).copy(relationships = it.relationships + (instigator.id to soured(it.relationships[instigator.id])))
                }
            EventOutcome(
                ruleId = "kitchen_argument",
                description = "${instigator.name} and ${other.name} had a screaming match about $topic.",
                resultingState = resulting,
                tone = LogTone.BAD,
            )
        },
    )

    val starShift = EventRule(
        id = "star_shift",
        title = "Star of the shift",
        severity = Severity.MINOR,
        cooldownDays = 5,
        unique = false,
        prerequisite = { state -> state.activeEmployees.any { it.morale >= 75 && it.stress <= 40 } },
        weight = { 0.6f },
        resolve = { state, rng ->
            val star = rng.pick(state.activeEmployees.filter { it.morale >= 75 && it.stress <= 40 })
            EventOutcome(
                ruleId = "star_shift",
                description = "${star.name} was on fire tonight (not literally, for once). Customers noticed.",
                resultingState = state.updateEmployee(star.id) { it.adjustSkill(2) }.adjustReputation(2),
                tone = LogTone.GOOD,
            )
        },
    )

    val gossip = EventRule(
        id = "gossip",
        title = "Gossip",
        severity = Severity.TRIVIAL,
        cooldownDays = 5,
        unique = false,
        prerequisite = { state -> state.employees.size >= 2 && state.activeEmployees.any { PersonalityTrait.GOSSIP in it.personalityTraits } },
        weight = { 0.7f },
        resolve = { state, rng ->
            val gossiper = rng.pick(state.activeEmployees.filter { PersonalityTrait.GOSSIP in it.personalityTraits })
            EventOutcome(
                ruleId = "gossip",
                description = "${gossiper.name} has been telling everyone the soup is \"mostly\" broth. Morale dipped.",
                resultingState = state.updateAllEmployees { if (it.id == gossiper.id) it else it.adjustMorale(-4) },
                tone = LogTone.BAD,
            )
        },
    )

    val teamBonding = EventRule(
        id = "team_bonding",
        title = "Team night out",
        severity = Severity.MINOR,
        cooldownDays = 7,
        unique = false,
        prerequisite = { state -> state.employees.size >= 2 && state.employees.map { it.morale }.average() >= 60 },
        weight = { 0.4f },
        resolve = { state, rng ->
            val singer = rng.pick(state.employees)
            EventOutcome(
                ruleId = "team_bonding",
                description = "The staff went out for karaoke. Nobody will discuss what ${singer.name} sang.",
                resultingState = state.updateAllEmployees { it.adjustMorale(6).adjustStress(-8) },
                tone = LogTone.GOOD,
            )
        },
    )

    val perfectionistScrub = EventRule(
        id = "perfectionist_scrub",
        title = "Cleaning frenzy",
        severity = Severity.MINOR,
        cooldownDays = 6,
        unique = false,
        prerequisite = { state ->
            state.restaurant.cleanliness < 60 && state.activeEmployees.any { PersonalityTrait.PERFECTIONIST in it.personalityTraits }
        },
        weight = { 0.8f },
        resolve = { state, rng ->
            val cleaner = rng.pick(state.activeEmployees.filter { PersonalityTrait.PERFECTIONIST in it.personalityTraits })
            val where = if (cleaner.role == Role.COOK) "the kitchen" else "the dining room"
            EventOutcome(
                ruleId = "perfectionist_scrub",
                description = "${cleaner.name} couldn't stand the state of $where and scrubbed it until 3am. Sparkling, but exhausted.",
                resultingState = state.adjustCleanliness(15).updateEmployee(cleaner.id) { it.adjustStress(10) },
                tone = LogTone.GOOD,
            )
        },
    )

    val all = listOf(staffSick, burnout, staffQuits, kitchenArgument, starShift, gossip, teamBonding, perfectionistScrub)

    private val ARGUMENT_TOPICS = listOf(
        "the correct way to fold a napkin",
        "whose turn it was to descale the kettle",
        "whether soup counts as a drink",
        "a missing ladle",
        "the radio station",
    )
}
