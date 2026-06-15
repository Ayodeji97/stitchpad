package com.danzucker.stitchpad.feature.style.domain

import com.danzucker.stitchpad.core.data.repository.FakeStyleRepository
import com.danzucker.stitchpad.core.domain.model.ImageSyncState
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.StyleFolder
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ObserveAllInspirationStylesTest {

    private val repo = FakeStyleRepository()
    private val userId = "user-1"

    private fun style(id: String) = Style(
        id = id,
        customerId = "",
        description = id,
        photoUrl = "https://example.com/$id.jpg",
        photoStoragePath = "",
        createdAt = 0L,
        updatedAt = 0L,
        syncState = ImageSyncState.SYNCED,
    )

    @Test
    fun flattensStylesFromFlatDefaultAndNamedInspirationFolders() = runTest {
        val flatStyle = style("insp-flat")
        val folderStyle = style("insp-in-folder")
        val folder = StyleFolder(id = "f1", name = "Runway", createdAt = 0L, updatedAt = 0L)

        repo.foldersByLocation[StyleLocation.Inspiration(folderId = null)] = listOf(folder)
        repo.stylesByLocation[StyleLocation.Inspiration(folderId = null)] = listOf(flatStyle)
        repo.stylesByLocation[StyleLocation.Inspiration(folderId = "f1")] = listOf(folderStyle)

        // Collect all emissions and check the LAST one (folder list settles after initial empty fold).
        val emissions = repo.observeAllInspirationStyles(userId).toList()
        val ids = emissions.last().map { it.id }
        assertTrue(ids.contains("insp-flat"), "flat Inspiration style must be present")
        assertTrue(ids.contains("insp-in-folder"), "named-folder Inspiration style must be present")
    }

    @Test
    fun returnsEmptyWhenNoInspirationStyles() = runTest {
        val emissions = repo.observeAllInspirationStyles(userId).toList()
        // All emissions should be empty when repo is empty.
        assertTrue(emissions.all { it.isEmpty() }, "empty repo should yield only empty lists")
    }

    @Test
    fun transientFolderError_doesNotCrash() = runTest {
        val flatStyle = style("insp-flat")
        // observeError causes both folders AND styles flows to emit Result.Error.
        // The runningFold keeps the last known empty list — no crash.
        repo.observeError = com.danzucker.stitchpad.core.domain.error.DataError.Network.UNKNOWN
        repo.stylesList = listOf(flatStyle)

        // Should not throw — just return whatever survived the keep-last fold.
        val emissions = repo.observeAllInspirationStyles(userId).toList()
        assertTrue(emissions.all { it.isEmpty() || it.isNotEmpty() }) // tautology: just verify no crash
    }
}
