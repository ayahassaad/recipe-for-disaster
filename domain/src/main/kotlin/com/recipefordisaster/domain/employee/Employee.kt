package com.recipefordisaster.domain.employee

import kotlinx.serialization.Serializable

/**
 * Employees are intentionally not interchangeable. Two cooks with identical
 * roles can behave very differently once skill, morale, stress and
 * personality traits diverge — that variance is what the event engine reads
 * from when it decides how likely a conflict or a service failure is.
 */
@Serializable
data class Employee(
    val id: EmployeeId,
    val name: String,
    val role: Role,
    val skill: Int,
    val speed: Int,
    val reliability: Int,
    val morale: Int,
    val stress: Int,
    val salaryPerDay: Long,
    val experienceDays: Int,
    val personalityTraits: Set<PersonalityTrait>,
    val relationships: Map<EmployeeId, RelationshipScore>,
    val status: EmployeeStatus,
    /** Days left before a [EmployeeStatus.SICK] employee is back. Defaulted so pre-Phase-6 saves still load. */
    val sickDaysRemaining: Int = 0,
    /** The weekdays (0 = the first day of each week, up to 6) they never work: their rostered days off. */
    val offDays: Set<Int> = emptySet(),
    /** The day they last got a raise, so they can't be given another straight away. */
    val lastRaiseDay: Int = -100,
)

@Serializable
@JvmInline
value class EmployeeId(val value: String)

@Serializable
@JvmInline
value class RelationshipScore(val value: Int)

@Serializable
enum class Role {
    COOK,
    SERVER,
    DISHWASHER,
    MANAGER,
    /** Greets guests at the door: people wait longer, and one more party can wait inside. */
    HOST,
    /** Clears dirty tables and washes up during service, so the player doesn't have to. */
    BUSSER,

    /** Looks after the drinks: takes the orders, pours them at the bar and carries them over. */
    BARTENDER,
}

@Serializable
enum class PersonalityTrait {
    PERFECTIONIST,
    SLACKER,
    GOSSIP,
    LOYAL,
    HOTHEADED,
    ANXIOUS,
}

@Serializable
enum class EmployeeStatus {
    ACTIVE,
    ON_BREAK,
    SICK,
    QUIT,
    FIRED,
}
