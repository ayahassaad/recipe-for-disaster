package com.recipefordisaster.app.ui.game

import com.recipefordisaster.app.testing.FakeGameRepository
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The event rule library is empty until Phase 6, which makes
 * [DefaultDayTickEngine] deterministic enough to use directly here (no
 * event can fire to introduce extra randomness) — no need for a fake day
 * tick engine on top of the fake repository.
 */
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val dayTickEngine = DefaultDayTickEngine(EventEngine(rules = emptyList()))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starting a new game produces a playable day-one state and saves it`() = runTest(dispatcher) {
        val repository = FakeGameRepository()
        val viewModel = GameViewModel(repository, dayTickEngine)

        viewModel.startNewGame()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is GameUiState.Playing)
        assertEquals(1, (state as GameUiState.Playing).state.day)
        assertEquals(1, repository.saveCount)
    }

    @Test
    fun `continuing with no existing save reports an error`() = runTest(dispatcher) {
        val viewModel = GameViewModel(FakeGameRepository(), dayTickEngine)

        viewModel.continueGame()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is GameUiState.Error)
    }

    @Test
    fun `continuing with an existing save resumes it`() = runTest(dispatcher) {
        val saved = NewGameFactory.create(seed = 7L)
        val repository = FakeGameRepository(stored = saved)
        val viewModel = GameViewModel(repository, dayTickEngine)

        viewModel.continueGame()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is GameUiState.Playing)
        assertEquals(saved, (state as GameUiState.Playing).state)
    }

    @Test
    fun `opening for the day advances the day counter and saves again`() = runTest(dispatcher) {
        val repository = FakeGameRepository()
        val viewModel = GameViewModel(repository, dayTickEngine)
        viewModel.startNewGame()
        dispatcher.scheduler.advanceUntilIdle()
        val dayOneState = (viewModel.uiState.value as GameUiState.Playing).state

        viewModel.startService()
        dispatcher.scheduler.advanceUntilIdle()

        val afterState = viewModel.uiState.value
        assertTrue(afterState is GameUiState.Playing)
        val newState = (afterState as GameUiState.Playing).state
        assertEquals(dayOneState.day + 1, newState.day)
        assertTrue(afterState.dayLog.isNotEmpty())
        assertEquals(2, repository.saveCount)
    }

    @Test
    fun `planning a purchase previews its cost without spending anything yet`() = runTest(dispatcher) {
        val repository = FakeGameRepository()
        val viewModel = GameViewModel(repository, dayTickEngine)
        viewModel.startNewGame()
        dispatcher.scheduler.advanceUntilIdle()
        val dayOne = (viewModel.uiState.value as GameUiState.Playing).state
        val flour = dayOne.inventory.ingredients.keys.first()

        viewModel.adjustPurchase(flour, 5.0)

        val planning = viewModel.uiState.value as GameUiState.Playing
        assertEquals(5.0, planning.plan.purchases[flour]!!, 0.0001)
        assertTrue(planning.preview.spending.ingredients > 0)
        assertEquals(dayOne.restaurant.cash, planning.state.restaurant.cash)
        assertEquals(1, repository.saveCount)
    }

    @Test
    fun `opening for the day carries out the plan and starts the next day with a clean slate`() = runTest(dispatcher) {
        val viewModel = GameViewModel(FakeGameRepository(), dayTickEngine)
        viewModel.startNewGame()
        dispatcher.scheduler.advanceUntilIdle()
        val applicant = (viewModel.uiState.value as GameUiState.Playing).state.applicants.first()

        viewModel.toggleHire(applicant.id)
        viewModel.startService()
        dispatcher.scheduler.advanceUntilIdle()

        val after = viewModel.uiState.value as GameUiState.Playing
        assertTrue(after.state.employees.any { it.id == applicant.id })
        assertEquals(PlayerDecisions(), after.plan)
    }

    @Test
    fun `starting service shows the day's results until the player moves on to the next morning`() = runTest(dispatcher) {
        val viewModel = GameViewModel(FakeGameRepository(), dayTickEngine)
        viewModel.startNewGame()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.startService()
        dispatcher.scheduler.advanceUntilIdle()
        val results = viewModel.uiState.value as GameUiState.Playing
        assertTrue(results.report != null)
        assertEquals(1, results.report!!.summary.day)

        viewModel.nextMorning()
        assertEquals(null, (viewModel.uiState.value as GameUiState.Playing).report)
    }

    @Test
    fun `choices can't be changed while the results screen is up`() = runTest(dispatcher) {
        val viewModel = GameViewModel(FakeGameRepository(), dayTickEngine)
        viewModel.startNewGame()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.startService()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleDeepClean()

        assertEquals(PlayerDecisions(), (viewModel.uiState.value as GameUiState.Playing).plan)
    }

    @Test
    fun `cash shown this morning already reflects what's been bought`() = runTest(dispatcher) {
        val viewModel = GameViewModel(FakeGameRepository(), dayTickEngine)
        viewModel.startNewGame()
        dispatcher.scheduler.advanceUntilIdle()
        val before = (viewModel.uiState.value as GameUiState.Playing).cashNow

        viewModel.toggleDeepClean()

        val after = viewModel.uiState.value as GameUiState.Playing
        assertEquals(before - com.recipefordisaster.domain.decision.DecisionApplier.DEEP_CLEAN_COST, after.cashNow)
        assertTrue(after.morning.restaurant.cleanliness > after.state.restaurant.cleanliness)
    }

    @Test
    fun `a new game opens with how-to-play, a continued one doesn't`() = runTest(dispatcher) {
        val viewModel = GameViewModel(FakeGameRepository(), dayTickEngine)
        viewModel.startNewGame()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue((viewModel.uiState.value as GameUiState.Playing).showIntro)

        viewModel.dismissIntro()
        assertTrue(!(viewModel.uiState.value as GameUiState.Playing).showIntro)

        val resumed = GameViewModel(FakeGameRepository(stored = NewGameFactory.create(1L)), dayTickEngine)
        resumed.continueGame()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(!(resumed.uiState.value as GameUiState.Playing).showIntro)
    }

    @Test
    fun `restock all buys everything that's running low`() = runTest(dispatcher) {
        val start = NewGameFactory.create(1L)
        val bare = start.copy(inventory = start.inventory.copy(ingredients = start.inventory.ingredients.mapValues { it.value.copy(quantityOnHand = 0.0) }))
        val viewModel = GameViewModel(FakeGameRepository(stored = bare), dayTickEngine)
        viewModel.continueGame()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.restockAll()

        val after = viewModel.uiState.value as GameUiState.Playing
        assertTrue(after.plan.purchases.isNotEmpty())
        assertTrue(com.recipefordisaster.domain.simulation.MorningAdvisor.restockList(after.morning).isEmpty())
    }

    @Test
    fun `toggling a decision twice cancels it`() = runTest(dispatcher) {
        val viewModel = GameViewModel(FakeGameRepository(), dayTickEngine)
        viewModel.startNewGame()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleDeepClean()
        viewModel.toggleDeepClean()

        assertEquals(PlayerDecisions(), (viewModel.uiState.value as GameUiState.Playing).plan)
    }

    @Test
    fun `calling open for the day before a game is loaded does nothing`() = runTest(dispatcher) {
        val repository = FakeGameRepository()
        val viewModel = GameViewModel(repository, dayTickEngine)

        viewModel.startService()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(GameUiState.Loading, viewModel.uiState.value)
        assertEquals(0, repository.saveCount)
    }
}
