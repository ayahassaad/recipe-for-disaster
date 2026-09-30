package com.recipefordisaster.app

import android.app.Application

/**
 * No DI framework (Phase 0/1 decision, see docs/architecture.md). Instead,
 * the small set of app-wide singletons — the database, the repository, the
 * day-tick engine — are constructed once here, in [AppContainer], and
 * handed to ViewModels via plain [androidx.lifecycle.ViewModelProvider.Factory]
 * classes. This is "manual DI": more typing than Hilt, zero new
 * dependencies, and easy to follow for a project this size.
 */
class RecipeForDisasterApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
