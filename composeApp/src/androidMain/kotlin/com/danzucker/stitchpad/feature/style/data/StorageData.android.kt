package com.danzucker.stitchpad.feature.style.data

import dev.gitlive.firebase.storage.Data

actual fun ByteArray.toStorageData(): Data = Data(this)
