package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.data.appLifetimeScope
import com.danzucker.stitchpad.feature.notebookimport.data.FirebaseNotebookImportRepository
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportRepository
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportSaver
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookPageUploader
import com.danzucker.stitchpad.feature.notebookimport.presentation.capture.NotebookCaptureViewModel
import com.danzucker.stitchpad.feature.notebookimport.presentation.card.NotebookCardViewModel
import com.danzucker.stitchpad.feature.notebookimport.presentation.review.NotebookReviewViewModel
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
    viewModelOf(::NotebookCaptureViewModel)
    viewModelOf(::NotebookReviewViewModel)
    viewModelOf(::NotebookCardViewModel)
}
