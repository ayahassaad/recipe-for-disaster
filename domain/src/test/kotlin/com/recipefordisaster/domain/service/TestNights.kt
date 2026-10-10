package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.service.ServiceNight.Stage

/**
 * For tests about the food rather than the drinks: anyone who'd like a drink has one straight away,
 * so they go on to order food. (DrinksTest plays the drinks for real.)
 */
internal fun ServiceNight.drinksServed(): ServiceNight =
    if (parties.none { it.stage == Stage.WANTS_DRINKS }) this
    else copy(parties = parties.map { if (it.stage == Stage.WANTS_DRINKS) it.copy(stage = Stage.READY_TO_ORDER, stageSince = time) else it })
