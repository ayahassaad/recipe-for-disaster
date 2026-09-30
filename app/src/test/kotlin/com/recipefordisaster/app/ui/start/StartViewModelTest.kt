package com.recipefordisaster.app.ui.start

import com.recipefordisaster.app.testing.FakeGameRepository
import com.recipefordisaster.domain.simulation.NewGameFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
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
}
