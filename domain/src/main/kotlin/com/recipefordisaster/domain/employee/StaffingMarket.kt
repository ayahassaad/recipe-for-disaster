package com.recipefordisaster.domain.employee

import com.recipefordisaster.domain.simulation.RandomSource

/**
 * Generates the people applying for a job (Phase 6 staffing decisions).
 * Applicants are ordinary [Employee]s that aren't on the payroll yet, kept
 * in [com.recipefordisaster.domain.simulation.GameState.applicants] so the
 * pool is part of the saved, seeded state rather than conjured fresh every
 * time the screen redraws.
 *
 * Like customers, every applicant is a generated fictional identity.
 */
object StaffingMarket {

    const val POOL_SIZE = 3

    /** The pool is replaced wholesale this often, so passing on someone is a real choice. */
    const val REFRESH_EVERY_DAYS = 3

    /** One-off cost of bringing someone on: paperwork, an apron, the awkward first-day tour. */
    fun hiringFee(applicant: Employee): Long = applicant.salaryPerDay * 2

    /** What it costs to let someone go. */
    fun severance(employee: Employee): Long = employee.salaryPerDay * 2

    private val FIRST_NAMES = listOf(
        "Gertrude", "Thaddeus", "Philippa", "Rupert", "Esmeralda", "Barnaby",
        "Clementine", "Horatio", "Winifred", "Percival", "Mildred", "Octavius",
    )
    private val LAST_NAMES = listOf(
        "Saucepan", "Muddlecott", "Pennywhistle", "Grimsby", "Featherstonehaugh",
        "Bumble", "Quill", "Thistlewood", "Scrimshaw", "Dimbleby",
    )

    fun generateApplicants(rng: RandomSource, day: Int, count: Int = POOL_SIZE): List<Employee> =
        (0 until count).map { index -> generateApplicant(rng, day, index) }

    internal fun generateApplicant(rng: RandomSource, day: Int, index: Int): Employee {
        val role = HIREABLE_ROLES[rng.nextInt(HIREABLE_ROLES.size)]
        val skill = 30 + rng.nextInt(51) // 30-80
        val speed = 30 + rng.nextInt(51)
        val reliability = 30 + rng.nextInt(61) // 30-90
        val traitCount = rng.nextInt(3) // 0-2 traits
        val traits = (0 until traitCount)
            .map { PersonalityTrait.entries[rng.nextInt(PersonalityTrait.entries.size)] }
            .toSet()

        // Better people cost more — but not perfectly, so a bargain or a dud is possible.
        val salary = when (role) {
            Role.COOK -> 35 + skill / 2
            Role.SERVER -> 25 + (skill + speed) / 6
            Role.DISHWASHER -> 20 + reliability / 6
            Role.MANAGER -> 60 + skill / 2
        } + rng.nextInt(10)

        return Employee(
            id = EmployeeId("hire-$day-$index-${rng.nextInt(1_000_000)}"),
            name = "${FIRST_NAMES[rng.nextInt(FIRST_NAMES.size)]} ${LAST_NAMES[rng.nextInt(LAST_NAMES.size)]}",
            role = role,
            skill = skill,
            speed = speed,
            reliability = reliability,
            morale = 60 + rng.nextInt(31),
            stress = 10 + rng.nextInt(20),
            salaryPerDay = salary.toLong(),
            experienceDays = 0,
            personalityTraits = traits,
            relationships = emptyMap(),
            status = EmployeeStatus.ACTIVE,
        )
    }

    // Managers exist in the model but have no distinct job yet, so they aren't offered.
    private val HIREABLE_ROLES = listOf(Role.COOK, Role.COOK, Role.SERVER, Role.SERVER, Role.DISHWASHER)
}
