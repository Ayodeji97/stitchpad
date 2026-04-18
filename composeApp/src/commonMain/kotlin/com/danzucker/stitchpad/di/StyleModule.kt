package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.feature.style.data.FirebaseStyleRepository
import com.danzucker.stitchpad.feature.style.presentation.form.StyleFormViewModel
import com.danzucker.stitchpad.feature.style.presentation.gallery.StyleGalleryViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val styleDataModule = module {
    singleOf(::FirebaseStyleRepository) bind StyleRepository::class
}

val stylePresentationModule = module {
    viewModelOf(::StyleGalleryViewModel)
    viewModelOf(::StyleFormViewModel)
}
