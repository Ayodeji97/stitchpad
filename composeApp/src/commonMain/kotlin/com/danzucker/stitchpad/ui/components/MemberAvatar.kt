package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.danzucker.stitchpad.ui.theme.DesignTokens

/**
 * Solid accent hues for [TeamMember][com.danzucker.stitchpad.core.domain.staff.TeamMember]
 * avatars, picked by [memberColor] from a stable `colorSeed` (assigned once, server-side or
 * by [FakeTeamRosterRepository][com.danzucker.stitchpad.core.data.repository.FakeTeamRosterRepository],
 * never re-derived from the name — renaming a member must not shuffle their color).
 *
 * Deliberately excludes [DesignTokens.saffron500]: the design system reserves saffron as a
 * rare heritage accent (PRO badges, the logo tick), never a body/decorative fill. All hues
 * here are dark enough (500-700 range) to read well with white initials in both themes, so
 * unlike [CustomerAvatar] this doesn't need separate light/dark bg+text pairs.
 */
private val MEMBER_ACCENT_HUES = listOf(
    DesignTokens.indigo500,
    DesignTokens.sienna500,
    DesignTokens.success500,
    DesignTokens.info500,
    DesignTokens.warning500,
    DesignTokens.statusSewing,
    DesignTokens.indigo700,
    DesignTokens.sienna700,
)

/** Pure seed -> color mapping so it's trivially testable and reusable outside Compose. */
fun memberColor(seed: Int): Color = MEMBER_ACCENT_HUES[seed.mod(MEMBER_ACCENT_HUES.size)]

/**
 * Fallback avatar color seed for call sites that have no roster to resolve
 * [com.danzucker.stitchpad.core.domain.staff.TeamMember.colorSeed] from — e.g. an order-list
 * row, which only has [com.danzucker.stitchpad.core.domain.model.Order.assignedMemberId] /
 * `assignedMemberName`, never the roster itself.
 *
 * Hashes the id (preferred — never edited) or, failing that, the name, so a rename alone
 * doesn't reshuffle the hue. This is deliberately NOT the same hue a roster-resolved seed
 * would produce for the same member; callers that *do* have the roster (the assignee card,
 * the picker sheet, the Team screen) MUST resolve [TeamMember.colorSeed][com.danzucker.stitchpad.core.domain.staff.TeamMember.colorSeed]
 * from it instead of calling this, or the same member renders two different hues at once.
 */
fun fallbackMemberColorSeed(memberId: String?, memberName: String?): Int = (memberId ?: memberName).hashCode()

/**
 * Initials-on-a-solid-accent avatar for a team roster row (staff or name-only). Reused by
 * the assignment picker (Task 7) so a tailor's color stays consistent everywhere they show up.
 */
@Composable
fun MemberAvatar(
    name: String,
    colorSeed: Int,
    modifier: Modifier = Modifier,
    size: Dp = DesignTokens.space10,
) {
    val bg = memberColor(colorSeed)
    val initials = name.trim()
        .split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(DesignTokens.radiusFull))
            .background(bg),
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }
}
