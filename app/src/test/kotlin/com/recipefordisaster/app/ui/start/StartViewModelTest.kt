package com.recipefordisaster.app.ui.start

import com.recipefordisaster.app.testing.FakeGameRepository
import com.recipefordisaster.domain.simulation.NewGameFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StartViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `with no existing save, continue stays disabled`() = runTest(dispatcher) {
        val viewModel = StartViewModel(FakeGameRepository())

        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals(false, state.hasExistingSave)
    }

    @Test
    fun `with an existing save, continue becomes enabled`() = runTest(dispatcher) {
        val repository = FakeGameRepository(stored = NewGameFactory.create(seed = 1L))
        val viewModel = StartViewModel(repository)

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.hasExistingSave)
    }

    @Test
    fun `refresh picks up a save that appeared after the view model was created`() = runTest(dispatcher) {
        val repository = FakeGameRepository()
        val viewModel = StartViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.hasExistingSave)

        repository.save(NewGameFactory.create(seed = 1L))
        viewModel.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.hasExistingSave)
    }

    @Test
    fun `a saved restaurant that has already closed down counts as over, so starting again doesn't ask`() = runTest(dispatcher) {
        val start = NewGameFactory.create(seed = 1L)
        val bust = start.copy(restaurant = start.restaurant.copy(status = com.recipefordisaster.domain.restaurant.RestaurantStatus.BANKRUPT))
        val viewModel = StartViewModel(FakeGameRepository(bust))
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.savedGameIsOver)

        val running = StartViewModel(FakeGameRepository(start))
        advanceUntilIdle()
        assertEquals(false, running.uiState.value.savedGameIsOver)
    }

    @Test
    fun `the best run is shown, and only a longer run replaces it`() = runTest(dispatcher) {
        val repository = FakeGameRepository(NewGameFactory.create(seed = 1L))
        repository.recordRun(com.recipefordisaster.domain.simulation.BestRun(12, "The Leaky Ladle"))
        repository.recordRun(com.recipefordisaster.domain.simulation.BestRun(5, "Shorter"))
        val viewModel = StartViewModel(repository)
        advanceUntilIdle()
        assertEquals(com.recipefordisaster.domain.simulation.BestRun(12, "The Leaky Ladle"), viewModel.uiState.value.best)
    }
}
