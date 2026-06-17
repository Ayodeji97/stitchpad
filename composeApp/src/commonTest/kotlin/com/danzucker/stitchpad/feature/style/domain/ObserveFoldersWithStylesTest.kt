package com.danzucker.stitchpad.feature.style.domain

import com.danzucker.stitchpad.core.data.repository.FakeStyleRepository
import com.danzucker.stitchpad.core.domain.model.ImageSyncState
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.StyleFolder
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObserveFoldersWithStylesTest {

    private val repo = FakeStyleRepository()
    private val userId = "user-1"
    private val customerId = "cust-1"

    private fun style(id: String, createdAt: Long = 0L) = Style(
        id = id,
        customerId = customerId,
        description = id,
        photoUrl = "https://example.com/$id.jpg",
        photoStoragePath = "",
        createdAt = createdAt,
        updatedAt = 0L,
        syncState = ImageSyncState.SYNCED,
    )

    // ─── Customer Closet ─────────────────────────────────────────────────────

    @Test
    fun closet_defaultFolderFirst_namedFolderSecond() = runTest {
        val defaultStyle = style("s-default")
        val namedStyle = style("s-named")
        val folder = StyleFolder(id = "f1", name = "Evening Wear", createdAt = 0L, updatedAt = 0L)

        val root = StyleLocation.CustomerCloset(customerId)
        repo.foldersByLocation[root] = listOf(folder)
        repo.stylesByLocation[StyleLocation.CustomerCloset(customerId, folderId = null)] =
            listOf(defaultStyle)
        repo.stylesByLocation[StyleLocation.CustomerCloset(customerId, folderId = "f1")] =
            listOf(namedStyle)

        val result = repo.observeFoldersWithStyles(userId, root).toList().last()

        assertEquals(2, result.size)
        // Default folder must be first
        val defaultFolder = result[0]
        assertNull(defaultFolder.folderId, "default folder folderId must be null")
        assertNull(defaultFolder.name, "default folder name must be null")
        assertEquals(listOf("s-default"), defaultFolder.styles.map { it.id })

        // Named folder follows
        val named = result[1]
        assertEquals("f1", named.folderId)
        assertEquals("Evening Wear", named.name)
        assertEquals(listOf("s-named"), named.styles.map { it.id })
    }

    @Test
    fun closet_noNamedFolders_returnsOnlyDefaultFolder() = runTest {
        val defaultStyle = style("s-default")
        val root = StyleLocation.CustomerCloset(customerId)
        repo.stylesByLocation[StyleLocation.CustomerCloset(customerId, folderId = null)] =
            listOf(defaultStyle)

        val result = repo.observeFoldersWithStyles(userId, root).toList().last()

        assertEquals(1, result.size)
        assertNull(result.single().folderId)
        assertEquals(listOf("s-default"), result.single().styles.map { it.id })
    }

    @Test
    fun closet_emptyRepo_returnsSingleEmptyDefaultFolder() = runTest {
        val root = StyleLocation.CustomerCloset(customerId)
        val result = repo.observeFoldersWithStyles(userId, root).toList().last()

        assertEquals(1, result.size)
        assertNull(result.single().folderId)
        assertTrue(result.single().styles.isEmpty())
    }

    // ─── Inspiration ─────────────────────────────────────────────────────────

    @Test
    fun inspiration_defaultFolderFirst_namedFolderSecond() = runTest {
        val flatStyle = style("insp-flat")
        val folderStyle = style("insp-named")
        val folder = StyleFolder(id = "if1", name = "Runway", createdAt = 0L, updatedAt = 0L)

        val root = StyleLocation.Inspiration()
        repo.foldersByLocation[root] = listOf(folder)
        repo.stylesByLocation[StyleLocation.Inspiration(folderId = null)] = listOf(flatStyle)
        repo.stylesByLocation[StyleLocation.Inspiration(folderId = "if1")] = listOf(folderStyle)

        val result = repo.observeFoldersWithStyles(userId, root).toList().last()

        assertEquals(2, result.size)
        assertNull(result[0].folderId)
        assertEquals(listOf("insp-flat"), result[0].styles.map { it.id })
        assertEquals("if1", result[1].folderId)
        assertEquals(listOf("insp-named"), result[1].styles.map { it.id })
    }

    // ─── coverUrl ────────────────────────────────────────────────────────────

    @Test
    fun coverUrl_returnsUrlOfMostRecentStyle() = runTest {
        val older = style("old", createdAt = 100L).copy(photoUrl = "https://example.com/old.jpg")
        val newer = style("new", createdAt = 200L).copy(photoUrl = "https://example.com/new.jpg")
        val root = StyleLocation.CustomerCloset(customerId)
        repo.stylesByLocation[StyleLocation.CustomerCloset(customerId, folderId = null)] =
            listOf(older, newer)

        val result = repo.observeFoldersWithStyles(userId, root).toList().last()
        assertEquals("https://example.com/new.jpg", result.single().coverUrl)
    }

    @Test
    fun coverUrl_nullWhenAllStylesHaveNoImage() = runTest {
        val noImage = style("s1").copy(photoUrl = "")
        val root = StyleLocation.CustomerCloset(customerId)
        repo.stylesByLocation[StyleLocation.CustomerCloset(customerId, folderId = null)] =
            listOf(noImage)

        val result = repo.observeFoldersWithStyles(userId, root).toList().last()
        assertNull(result.single().coverUrl)
    }
}
