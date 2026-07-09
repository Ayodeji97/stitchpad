package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.core.sharing.ImageSharer
import com.danzucker.stitchpad.feature.style.data.FirebaseStyleRepository
import com.danzucker.stitchpad.feature.style.presentation.folders.StyleFoldersViewModel
import com.danzucker.stitchpad.feature.style.presentation.form.StyleFormViewModel
import com.danzucker.stitchpad.feature.style.presentation.gallery.StyleGalleryViewModel
import com.danzucker.stitchpad.feature.style.presentation.share.CoilStyleImageBytesLoader
import com.danzucker.stitchpad.feature.style.presentation.share.ShareStyle
import com.danzucker.stitchpad.feature.style.presentation.share.StyleImageBytesLoader
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val styleDataModule = module {
    singleOf(::FirebaseStyleRepository) bind StyleRepository::class
    singleOf(::CoilStyleImageBytesLoader) bind StyleImageBytesLoader::class
}

val stylePresentationModule = module {
    factory { ShareStyle(get(), get(), get<ImageSharer>()) }
    viewModelOf(::StyleFoldersViewModel)
    viewModelOf(::StyleGalleryViewModel)
    viewModelOf(::StyleFormViewModel)
}
