package com.danzucker.stitchpad.feature.style.domain

import com.danzucker.stitchpad.core.data.repository.FakeStyleRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.StyleFolder
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CountStylesAcrossFoldersTest {

    private val repo = FakeStyleRepository()
    private val userId = "user-1"
    private val customerId = "cust-1"

    private fun style(id: String) = Style(
        id = id,
        customerId = customerId,
        description = id,
        photoUrl = "https://example.com/$id.jpg",
        photoStoragePath = "",
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun rootOnly_noFolders_returnsRootCount() = runTest {
        val root = StyleLocation.CustomerCloset(customerId)
        repo.stylesByLocation[root] = listOf(style("a"), style("b"), style("c"))
        repo.foldersByLocation[root] = emptyList()

        val count = repo.countStylesAcrossFolders(userId, root)

        assertEquals(3, count)
    }

    @Test
    fun rootPlusFolders_sumsBothCorrectly() = runTest {
        val root = StyleLocation.CustomerCloset(customerId)
        val folder1 = StyleLocation.CustomerCloset(customerId, "f1")
        val folder2 = StyleLocation.CustomerCloset(customerId, "f2")
        repo.stylesByLocation[root] = listOf(style("root-1"), style("root-2"))
        repo.stylesByLocation[folder1] = listOf(style("f1-1"), style("f1-2"), style("f1-3"))
        repo.stylesByLocation[folder2] = listOf(style("f2-1"))
        repo.foldersByLocation[root] = listOf(
            StyleFolder(id = "f1", name = "Folder 1", createdAt = 0L, updatedAt = 0L),
            StyleFolder(id = "f2", name = "Folder 2", createdAt = 0L, updatedAt = 0L),
        )

        val count = repo.countStylesAcrossFolders(userId, root)

        assertEquals(6, count) // 2 + 3 + 1
    }

    @Test
    fun inspirationRoot_sumsBothCorrectly() = runTest {
        val root = StyleLocation.Inspiration()
        val folder1 = StyleLocation.Inspiration("f1")
        repo.stylesByLocation[root] = listOf(style("insp-1"), style("insp-2"))
        repo.stylesByLocation[folder1] = listOf(style("insp-f1-1"))
        repo.foldersByLocation[root] = listOf(
            StyleFolder(id = "f1", name = "Inspo Folder", createdAt = 0L, updatedAt = 0L),
        )

        val count = repo.countStylesAcrossFolders(userId, root)

        assertEquals(3, count) // 2 + 1
    }

    @Test
    fun emptyCloset_returnsZero() = runTest {
        val root = StyleLocation.CustomerCloset(customerId)
        repo.stylesByLocation[root] = emptyList()
        repo.foldersByLocation[root] = emptyList()

        val count = repo.countStylesAcrossFolders(userId, root)

        assertEquals(0, count)
    }

    @Test
    fun observeError_returnsNull() = runTest {
        // A read error on any sub-read makes the count indeterminate → null (fail-closed signal).
        val root = StyleLocation.CustomerCloset(customerId)
        repo.observeError = DataError.Network.UNKNOWN

        val count = repo.countStylesAcrossFolders(userId, root)

        assertNull(count)
    }
}
