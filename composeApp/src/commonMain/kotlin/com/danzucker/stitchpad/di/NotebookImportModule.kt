package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.notebookimport.data.FirebaseNotebookImportRepository
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportRepository
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportSaver
import org.koin.dsl.module
import kotlin.time.Clock

// FirebaseFunctions comes from smartDataModule; FirebaseFirestore and FirebaseStorage from CoreModule.
val notebookImportDataModule = module {
    single<NotebookImportRepository> {
        FirebaseNotebookImportRepository(firestore = get(), storage = get(), functions = get())
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
