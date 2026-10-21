package com.recipefordisaster.domain.employee

/**
 * Staff stories: the longer someone works for you, the more you learn about them. Each person has a short
 * story in four chapters, which one depends on their character, and a new chapter turns up after they've
 * worked [UNLOCK_AT] days. Nothing is stored: what's been told follows from how long they've worked here.
 */
object StaffStories {

    /** Days worked before each chapter is told. */
    val UNLOCK_AT = listOf(3, 8, 15, 25)

    /** The four chapters of [employee]'s story, whether told yet or not. */
    fun chapters(employee: Employee): List<String> =
        arcFor(employee).map { it.replace("{name}", employee.name.substringBefore(' ')) }

    /** The chapters they've told you so far. */
    fun told(employee: Employee): List<String> = chapters(employee).take(UNLOCK_AT.count { it <= employee.experienceDays })

    /** The chapter that turned up between [before] and [after] (a day apart), if there was one. */
    fun newChapter(before: Employee, after: Employee): String? {
        val had = told(before).size
        val has = told(after).size
        return if (has > had) told(after)[has - 1] else null
    }

    /** Whether there's still more to learn about them. */
    fun hasMore(employee: Employee): Boolean = told(employee).size < UNLOCK_AT.size

    private fun arcFor(employee: Employee): List<String> {
        val trait = employee.personalityTraits.minOrNull()
        return ARCS[trait] ?: GENERIC
    }

    private val ARCS: Map<PersonalityTrait, List<String>> = mapOf(
        PersonalityTrait.PERFECTIONIST to listOf(
            "{name} straightens every fork on the table, twice. \"It's just how I'm wired,\" they say.",
            "{name} once sent back a wedding cake for being a millimetre off-centre. It was their own wedding.",
            "{name} keeps a notebook of every plate that ever left the kitchen looking less than perfect. It is a thick notebook.",
            "{name} admits the notebook is for you: \"I'd like to help make this place worth five stars.\"",
        ),
        PersonalityTrait.SLACKER to listOf(
            "{name} can find the quietest corner of any room within ten seconds. It's a gift.",
            "{name} says they have a \"work smarter, not harder\" philosophy. They have never explained the second half.",
            "{name} used to hold a record in their old town: longest lunch break, four hours and ten minutes.",
            "{name} quietly stays late to finish a job for you. \"Don't tell anyone,\" they mutter. \"I have a reputation.\"",
        ),
        PersonalityTrait.GOSSIP to listOf(
            "{name} knows what everyone in the street had for breakfast. Nobody knows how.",
            "{name} swears that the baker next door is secretly a retired pirate. They have a lot of evidence.",
            "{name} admits that half the rumours about this restaurant started with them. The nice half.",
            "{name} has told every guest on the street that yours is the best place in town. Nobody asked them to.",
        ),
        PersonalityTrait.LOYAL to listOf(
            "{name} stayed at their last job for eleven years, even after the roof fell in.",
            "{name} still sends a birthday card to every boss they've ever had. They have a spreadsheet.",
            "{name} keeps a photo of the old restaurant team in a pocket. \"They were family,\" they say softly.",
            "{name} tells you they'd run into a burning kitchen for you. Then they check which kitchen. A burning one, they decide.",
        ),
        PersonalityTrait.HOTHEADED to listOf(
            "{name} has a very short fuse, and a very large rolling pin.",
            "{name} was once banned from a quiz night for arguing that the answer was \"wrong\". It was right.",
            "{name} goes to a pottery class to calm down. They have made forty-one very angry mugs.",
            "{name} says it's the first job where they've counted to ten before shouting. They got to seven. It's progress.",
        ),
        PersonalityTrait.ANXIOUS to listOf(
            "{name} checks the oven three times before leaving, then once more from the street.",
            "{name} brings a spare apron, a spare spare apron, and a small emergency pie.",
            "{name} used to lie awake wondering whether the soup could tell they were nervous.",
            "{name} finally slept right through a whole night. \"The soup knows I tried my best,\" they say, smiling.",
        ),
    )

    private val GENERIC = listOf(
        "{name} moved to town last spring, looking for somewhere to call their own.",
        "{name} has a collection of tiny spoons from every café they've ever visited.",
        "{name} confides that they once dreamed of opening a restaurant of their own. They're learning a lot here.",
        "{name} says this place has started to feel like home. Then they go and wipe the same clean table again.",
    )
}
