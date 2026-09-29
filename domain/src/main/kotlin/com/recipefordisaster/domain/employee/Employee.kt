package com.recipefordisaster.domain.employee

/**
 * Employees are intentionally not interchangeable. Two cooks with identical
 * roles can behave very differently once skill, morale, stress and
 * personality traits diverge — that variance is what the event engine reads
 * from when it decides how likely a conflict or a service failure is.
 */
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
)

@JvmInline
value class EmployeeId(val value: String)

@JvmInline
value class RelationshipScore(val value: Int)

enum class Role {
    COOK,
    SERVER,
    DISHWASHER,
    MANAGER,
}

enum class PersonalityTrait {
    PERFECTIONIST,
    SLACKER,
    GOSSIP,
    LOYAL,
    HOTHEADED,
    ANXIOUS,
}

enum class EmployeeStatus {
    ACTIVE,
    ON_BREAK,
    SICK,
    QUIT,
    FIRED,
}
