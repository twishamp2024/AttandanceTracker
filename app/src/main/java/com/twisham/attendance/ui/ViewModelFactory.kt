package com.twisham.attendance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.twisham.attendance.AttendanceApp
import com.twisham.attendance.di.AppContainer

/**
 * Bridges the hand-rolled [AppContainer] to ViewModel construction without a DI
 * framework. Each ViewModel exposes `Factory` built with this helper, so screens
 * call `viewModel(factory = XViewModel.Factory)` and nothing else needs wiring.
 */
inline fun <reified VM : ViewModel> containerViewModelFactory(
    crossinline create: (AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AttendanceApp
        create(app.container)
    }
}

val CreationExtras.appContainer: AppContainer
    get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AttendanceApp).container
