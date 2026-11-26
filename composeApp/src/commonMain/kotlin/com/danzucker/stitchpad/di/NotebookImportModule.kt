package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.data.appLifetimeScope
import com.danzucker.stitchpad.feature.notebookimport.data.FirebaseNotebookImportRepository
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportRepository
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportSaver
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookPageUploader
import com.danzucker.stitchpad.feature.notebookimport.presentation.capture.NotebookCaptureViewModel
import com.danzucker.stitchpad.feature.notebookimport.presentation.card.NotebookCardViewModel
import com.danzucker.stitchpad.feature.notebookimport.presentation.review.NotebookReviewSession
import com.danzucker.stitchpad.feature.notebookimport.presentation.review.NotebookReviewViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import kotlin.time.Clock

// FirebaseFunctions comes from smartDataModule; FirebaseFirestore and FirebaseStorage from CoreModule.
val notebookImportDataModule = module {
    single<NotebookImportRepository> {
        FirebaseNotebookImportRepository(
            firestore = get(),
            storage = get(),
            functions = get(),
            offlineWrites = get(),
        )
    }
    // App-lifetime so queued pages keep uploading after the capture screen is closed.
    single {
        NotebookPageUploader(
            repository = get(),
            imageCompressor = get(),
            activeWorkshop = get(),
            entitlements = get(),
            scope = appLifetimeScope("NotebookPageUploader"),
        )
    }
    // In-memory "Keep separate" decisions, shared by the review list and the card editor.
    single { NotebookReviewSession() }
    factory {
        NotebookImportSaver(
            customerRepository = get(),
            measurementRepository = get(),
            importRepository = get(),
            clock = Clock.System,
        )
    }
}

val notebookImportPresentationModule = module {
    // Explicit so the clock parameter keeps its default (viewModelOf would try to inject it).
    viewModel {
        NotebookCaptureViewModel(
            repository = get(),
            activeWorkshop = get(),
            entitlements = get(),
            uploader = get(),
        )
    }
    viewModelOf(::NotebookReviewViewModel)
    viewModelOf(::NotebookCardViewModel)
}
