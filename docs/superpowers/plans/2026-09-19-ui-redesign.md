# UI/UX Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the design system, navigation shell, and new display features from `docs/superpowers/specs/2026-09-19-ui-redesign-design.md` across every screen of the Daily Calls Review app, without changing any existing call-counting/aggregation/permission/export behavior.

**Architecture:** Introduce a small set of pure, framework-free utility functions (avatar color hashing, name initials, week/month grouping, off-hours-by-coworker breakdown, suggested-contact ranking) in the `core` package, each unit-tested in isolation like the existing `CallAggregator`. Build a set of shared Compose components (`StatTile`, `SummaryCard`, `CoworkerRow`, `ToggleChip`, `DayCircle`, `WarningBadge`/`InfoBadge`, `DayCard`, `OffHoursBreakdownList`) once in `ui/common`, then reuse them across Home, Team Setup, Settings, History, Range Detail, and Day Detail. Add a `Theme.kt`/`Color.kt`/`Type.kt` design-token layer and a shared `AppScaffold` (top app bar + bottom navigation) that every top-level screen renders through.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Navigation-Compose (all already in use). Adds one new dependency: `androidx.compose.material:material-icons-extended`.

**Project has no Compose UI test harness** (confirmed: zero `androidx.compose.ui.test` usage anywhere in the codebase). Consistent with that existing convention, this plan unit-tests every new *pure* function (JUnit, `app/src/test`) but verifies Compose screens themselves manually on-device in Task 20, the same way the original 20-task build did.

**Known implementation-level decisions made while writing this plan** (spec left these as reasonable engineering judgment calls, not explicit brainstorm decisions — flagging here so no task re-litigates them):
- **Avatar color key:** the spec (§3.1) says "hash of `contactId`", but `CoworkerStat` (used by Home/Range Detail/off-hours breakdown) has no `contactId` field — only `phoneNumberLast10`. Hashing `contactId` in Team Setup but `phoneNumberLast10` elsewhere would give the **same person a different avatar color on different screens**, breaking the spec's own "stable across screens" requirement. This plan hashes `phoneNumberLast10` everywhere (Team Setup uses a contact's first tagged/first-listed number as its key), since that's the one identifier available at every call site.
- **A–Z rail:** implemented as a fixed overlay pinned to the right edge of the viewport (standard native-Contacts-app pattern), not literally redrawn inside the "All Contacts" card's own bounds as it scrolls — tapping a letter scrolls the shared list state to that letter's first contact. This achieves the same "layered on the card" *ambition* (jump-to-letter navigation, hidden during search) via a simpler, standard Compose pattern.
- **Icon names:** use `androidx.compose.material.icons.filled.*` (and `.automirrored.filled.*` for direction-sensitive icons like back-arrow, matching current Compose Material Icons API). If a specific icon name doesn't resolve at compile time, substitute the closest available icon from the `material-icons-extended` artifact and note the substitution in the task report — icon choice is cosmetic, not a spec requirement. (The original app build hit an analogous real API-surface mismatch with Glance's `dp` import — resolved by checking the actual library, not guessing. Apply the same approach here if needed.)

---

## Task 1: Add Material Icons Extended dependency

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the dependency**

In `app/build.gradle.kts`, inside the `dependencies { ... }` block, add this line directly below the existing `implementation("androidx.compose.ui:ui-tooling-preview")` line:

```kotlin
    implementation("androidx.compose.material:material-icons-extended")
```

(No version needed — it's resolved from the existing `platform("androidx.compose:compose-bom:2024.06.00")` BOM already declared in this file.)

- [ ] **Step 2: Sync and verify the build**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: add material-icons-extended dependency"
```

---

## Task 2: Design tokens and AppTheme

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/ui/theme/Color.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/theme/AppColors.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/theme/Type.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/dailycallsreview/app/MainActivity.kt`

This is pure Compose theming (no business logic to unit test) — verified by successful compile plus visual confirmation in Task 20.

- [ ] **Step 1: Create the raw color tokens**

Create `app/src/main/java/com/dailycallsreview/app/ui/theme/Color.kt`:

```kotlin
package com.dailycallsreview.app.ui.theme

import androidx.compose.ui.graphics.Color

// Light scheme (spec section 3.1)
val LightBackground = Color(0xFFF5F7FA)
val LightSurface = Color(0xFFFFFFFF)
val LightPrimary = Color(0xFF6C4FF5)
val LightPrimaryContainer = Color(0xFFEDE9FE)
val LightOnPrimaryContainer = Color(0xFF6C4FF5)
val LightWarning = Color(0xFFC2540A)
val LightWarningContainer = Color(0xFFFFF1E6)
val LightDestructive = Color(0xFFDC2626)
val LightDestructiveContainer = Color(0xFFFEE2E2)
val LightOnBackground = Color(0xFF1A1F36)
val LightOnSurfaceVariant = Color(0xFF6B7280)
val LightDivider = Color(0xFFF3F4F6)

// Dark scheme (spec section 3.1)
val DarkBackground = Color(0xFF14151C)
val DarkSurface = Color(0xFF1E2030)
val DarkPrimary = Color(0xFF8B6CFF)
val DarkPrimaryContainer = Color(0xFF2E2450)
val DarkOnPrimaryContainer = Color(0xFFA78BFA)
val DarkWarning = Color(0xFFFF9F5A)
val DarkWarningContainer = Color(0xFF3A2A17)
val DarkDestructive = Color(0xFFDC2626)
val DarkDestructiveContainer = Color(0xFF4A1E1E)
val DarkOnBackground = Color(0xFFF3F4F6)
val DarkOnSurfaceVariant = Color(0xFF9CA3AF)
val DarkDivider = Color(0xFF2A2C3D)
```

- [ ] **Step 2: Create the semantic `AppColors` extension (warning/destructive roles Material3's `ColorScheme` doesn't have)**

Create `app/src/main/java/com/dailycallsreview/app/ui/theme/AppColors.kt`:

```kotlin
package com.dailycallsreview.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val warning: Color,
    val warningContainer: Color,
    val destructive: Color,
    val destructiveContainer: Color
)

val LightAppColors = AppColors(
    warning = LightWarning,
    warningContainer = LightWarningContainer,
    destructive = LightDestructive,
    destructiveContainer = LightDestructiveContainer
)

val DarkAppColors = AppColors(
    warning = DarkWarning,
    warningContainer = DarkWarningContainer,
    destructive = DarkDestructive,
    destructiveContainer = DarkDestructiveContainer
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
```

- [ ] **Step 3: Create the Typography definition**

Create `app/src/main/java/com/dailycallsreview/app/ui/theme/Type.kt`:

```kotlin
package com.dailycallsreview.app.ui.theme

import androidx.compose.material3.Typography

// Explicit, intentional default type scale (spec section 3.3: "apply it consistently via a
// real Typography definition instead of default" — no custom font, just an explicit instance
// instead of relying on MaterialTheme's implicit fallback).
val AppTypography = Typography()
```

- [ ] **Step 4: Create the `AppTheme` composable**

Create `app/src/main/java/com/dailycallsreview/app/ui/theme/Theme.kt`:

```kotlin
package com.dailycallsreview.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightDivider
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color.White,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkDivider
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val appColors = if (isDark) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
```

- [ ] **Step 5: Wire `AppTheme` into `MainActivity`**

In `app/src/main/java/com/dailycallsreview/app/MainActivity.kt`, replace:

```kotlin
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
```

with:

```kotlin
import androidx.compose.material3.Surface
import com.dailycallsreview.app.ui.theme.AppTheme
```

and replace:

```kotlin
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
```

with:

```kotlin
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
```

(The closing braces already match — `MaterialTheme { ... }` and `AppTheme { ... }` both take a single trailing lambda, no other changes needed in this file.)

- [ ] **Step 6: Build and verify**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/theme/ app/src/main/java/com/dailycallsreview/app/MainActivity.kt
git commit -m "feat: add design tokens and AppTheme (light/dark)"
```

---

## Task 3: Pure utilities — name initials and avatar color

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/core/NameInitials.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/core/CoworkerAvatarColors.kt`
- Test: `app/src/test/java/com/dailycallsreview/app/core/NameInitialsTest.kt`
- Test: `app/src/test/java/com/dailycallsreview/app/core/CoworkerAvatarColorsTest.kt`

- [ ] **Step 1: Write the failing tests for `NameInitials`**

Create `app/src/test/java/com/dailycallsreview/app/core/NameInitialsTest.kt`:

```kotlin
package com.dailycallsreview.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NameInitialsTest {

    @Test
    fun `two word name returns first letter of each word`() {
        assertEquals("KS", NameInitials.of("Kiran Saini"))
    }

    @Test
    fun `single word name returns first two letters`() {
        assertEquals("RA", NameInitials.of("Rahul"))
    }

    @Test
    fun `three word name uses first and second word only`() {
        assertEquals("AB", NameInitials.of("Amit Bhushan Kumar"))
    }

    @Test
    fun `blank name returns question mark`() {
        assertEquals("?", NameInitials.of("   "))
    }

    @Test
    fun `lowercase name is uppercased`() {
        assertEquals("KS", NameInitials.of("kiran saini"))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dailycallsreview.app.core.NameInitialsTest" --console=plain`
Expected: FAIL — `NameInitials` unresolved reference

- [ ] **Step 3: Implement `NameInitials`**

Create `app/src/main/java/com/dailycallsreview/app/core/NameInitials.kt`:

```kotlin
package com.dailycallsreview.app.core

object NameInitials {
    fun of(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dailycallsreview.app.core.NameInitialsTest" --console=plain`
Expected: PASS (5 tests)

- [ ] **Step 5: Write the failing tests for `CoworkerAvatarColors`**

Create `app/src/test/java/com/dailycallsreview/app/core/CoworkerAvatarColorsTest.kt`:

```kotlin
package com.dailycallsreview.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CoworkerAvatarColorsTest {

    @Test
    fun `same key always returns the same color pair`() {
        val first = CoworkerAvatarColors.colorFor("9315337872")
        val second = CoworkerAvatarColors.colorFor("9315337872")
        assertEquals(first, second)
    }

    @Test
    fun `different keys can return different color pairs`() {
        val colors = (0..5).map { CoworkerAvatarColors.colorFor("55500000$it") }.toSet()
        assert(colors.size > 1) { "expected more than one distinct color across 6 different keys" }
    }

    @Test
    fun `empty key does not throw and returns a valid palette entry`() {
        val pair = CoworkerAvatarColors.colorFor("")
        assertEquals(6, CoworkerAvatarColors.PALETTE_SIZE)
        assert(pair.background != 0L)
    }
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dailycallsreview.app.core.CoworkerAvatarColorsTest" --console=plain`
Expected: FAIL — `CoworkerAvatarColors` unresolved reference

- [ ] **Step 7: Implement `CoworkerAvatarColors`**

Create `app/src/main/java/com/dailycallsreview/app/core/CoworkerAvatarColors.kt`:

```kotlin
package com.dailycallsreview.app.core

// ARGB color longs (e.g. 0xFFEDE9FE) rather than android.graphics.Color, to keep this file
// framework-free like the rest of core/ — callers convert via androidx.compose.ui.graphics.Color(long).
data class AvatarColorPair(val background: Long, val text: Long)

object CoworkerAvatarColors {
    private val PALETTE = listOf(
        AvatarColorPair(0xFFEDE9FE, 0xFF6C4FF5), // purple
        AvatarColorPair(0xFFFEF3E2, 0xFFC2540A), // orange
        AvatarColorPair(0xFFE0F2FE, 0xFF0369A1), // blue
        AvatarColorPair(0xFFDCFCE7, 0xFF15803D), // green
        AvatarColorPair(0xFFFCE7F3, 0xFFBE185D), // pink
        AvatarColorPair(0xFFCCFBF1, 0xFF0F766E)  // teal
    )

    val PALETTE_SIZE = PALETTE.size

    // key should be a stable per-person identifier available on every screen — this app uses
    // phoneNumberLast10 (see plan header note on why contactId can't be used consistently).
    fun colorFor(key: String): AvatarColorPair {
        val index = Math.floorMod(key.hashCode(), PALETTE.size)
        return PALETTE[index]
    }
}
```

- [ ] **Step 8: Run it to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dailycallsreview.app.core.CoworkerAvatarColorsTest" --console=plain`
Expected: PASS (3 tests)

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/core/NameInitials.kt app/src/main/java/com/dailycallsreview/app/core/CoworkerAvatarColors.kt app/src/test/java/com/dailycallsreview/app/core/NameInitialsTest.kt app/src/test/java/com/dailycallsreview/app/core/CoworkerAvatarColorsTest.kt
git commit -m "feat: add name-initials and avatar-color pure utilities"
```

---

## Task 4: Shared components — StatTile, WarningBadge/InfoBadge, ToggleChip, DayCircle

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/ui/common/StatTile.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/common/Badges.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/common/ToggleChip.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/common/DayCircle.kt`

No business logic here — pure Compose UI, verified by compile + Task 20 manual pass.

- [ ] **Step 1: Create `StatTile`**

Create `app/src/main/java/com/dailycallsreview/app/ui/common/StatTile.kt`:

```kotlin
package com.dailycallsreview.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onBackground
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )
    }
}
```

- [ ] **Step 2: Create `WarningBadge` and `InfoBadge`**

Create `app/src/main/java/com/dailycallsreview/app/ui/common/Badges.kt`:

```kotlin
package com.dailycallsreview.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailycallsreview.app.ui.theme.LocalAppColors

@Composable
fun WarningBadge(text: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Text(
        text = text,
        modifier = modifier
            .background(colors.warningContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = colors.warning
    )
}

@Composable
fun InfoBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
```

- [ ] **Step 3: Create `ToggleChip`**

Create `app/src/main/java/com/dailycallsreview/app/ui/common/ToggleChip.kt`:

```kotlin
package com.dailycallsreview.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary

    var chipModifier = modifier
        .clip(shape)
        .background(backgroundColor)
    if (!selected) {
        chipModifier = chipModifier.border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), shape)
    }
    chipModifier = chipModifier
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 6.dp)

    Text(
        text = text,
        modifier = chipModifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = textColor
    )
}
```

- [ ] **Step 4: Create `DayCircle`**

Create `app/src/main/java/com/dailycallsreview/app/ui/common/DayCircle.kt`:

```kotlin
package com.dailycallsreview.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DayCircle(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
```

- [ ] **Step 5: Build and verify**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/common/StatTile.kt app/src/main/java/com/dailycallsreview/app/ui/common/Badges.kt app/src/main/java/com/dailycallsreview/app/ui/common/ToggleChip.kt app/src/main/java/com/dailycallsreview/app/ui/common/DayCircle.kt
git commit -m "feat: add StatTile, badge, ToggleChip, DayCircle shared components"
```

---

## Task 5: Shared components — SummaryCard and CoworkerRow

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/core/RelativeVolume.kt`
- Test: `app/src/test/java/com/dailycallsreview/app/core/RelativeVolumeTest.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/common/SummaryCard.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/common/CoworkerRow.kt`

- [ ] **Step 1: Write the failing test for the relative-volume-bar fraction**

Create `app/src/test/java/com/dailycallsreview/app/core/RelativeVolumeTest.kt`:

```kotlin
package com.dailycallsreview.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeVolumeTest {

    @Test
    fun `count equal to max returns 1`() {
        assertEquals(1f, relativeVolumeFraction(count = 8, maxCount = 8), 0.001f)
    }

    @Test
    fun `count half of max returns half`() {
        assertEquals(0.5f, relativeVolumeFraction(count = 4, maxCount = 8), 0.001f)
    }

    @Test
    fun `zero max returns zero without dividing by zero`() {
        assertEquals(0f, relativeVolumeFraction(count = 0, maxCount = 0), 0.001f)
    }

    @Test
    fun `count above max is clamped to 1`() {
        assertEquals(1f, relativeVolumeFraction(count = 10, maxCount = 8), 0.001f)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dailycallsreview.app.core.RelativeVolumeTest" --console=plain`
Expected: FAIL — `relativeVolumeFraction` unresolved reference

- [ ] **Step 3: Implement it**

Create `app/src/main/java/com/dailycallsreview/app/core/RelativeVolume.kt`:

```kotlin
package com.dailycallsreview.app.core

fun relativeVolumeFraction(count: Int, maxCount: Int): Float =
    if (maxCount <= 0) 0f else (count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dailycallsreview.app.core.RelativeVolumeTest" --console=plain`
Expected: PASS (4 tests)

- [ ] **Step 5: Create `SummaryCard`**

Create `app/src/main/java/com/dailycallsreview/app/ui/common/SummaryCard.kt`:

```kotlin
package com.dailycallsreview.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SummaryCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
```

(`contentPadding` defaults to `16.dp` for stat-grid-style content; pass `0.dp` for list-style content whose rows already carry their own padding, e.g. a `CoworkerRow` list — used starting in Task 7.)

- [ ] **Step 6: Create `CoworkerRow`**

Create `app/src/main/java/com/dailycallsreview/app/ui/common/CoworkerRow.kt`:

```kotlin
package com.dailycallsreview.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailycallsreview.app.core.CoworkerAvatarColors
import com.dailycallsreview.app.core.CoworkerStat
import com.dailycallsreview.app.core.NameInitials
import com.dailycallsreview.app.core.relativeVolumeFraction

@Composable
fun CoworkerRow(
    stat: CoworkerStat,
    maxCallCount: Int,
    modifier: Modifier = Modifier
) {
    val colors = CoworkerAvatarColors.colorFor(stat.phoneNumberLast10)
    val talkMinutes = stat.totalTalkTimeSeconds / 60

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(colors.background)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = NameInitials.of(stat.displayName),
                color = Color(colors.text),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stat.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${stat.callCount} calls · ${talkMinutes}m",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(relativeVolumeFraction(stat.callCount, maxCallCount))
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
```

- [ ] **Step 7: Build and verify**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/core/RelativeVolume.kt app/src/test/java/com/dailycallsreview/app/core/RelativeVolumeTest.kt app/src/main/java/com/dailycallsreview/app/ui/common/SummaryCard.kt app/src/main/java/com/dailycallsreview/app/ui/common/CoworkerRow.kt
git commit -m "feat: add SummaryCard and CoworkerRow shared components"
```

---

## Task 6: App launcher icon — "Team + phone badge" adaptive icon

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`

Per the spec (§9), final pixel-perfect launcher artwork is out of scope for the design doc itself — this task implements the approved "team + phone badge" concept (§4) as a real, valid Android adaptive icon: a purple gradient background, two overlapping white avatar circles ("team"), and a white circular badge in the bottom-right corner containing a simple purple phone-handset glyph.

- [ ] **Step 1: Create the gradient background layer**

Create `app/src/main/res/drawable/ic_launcher_background.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient
        android:angle="315"
        android:startColor="#6C4FF5"
        android:endColor="#8B6CFF"
        android:type="linear" />
</shape>
```

- [ ] **Step 2: Create the foreground glyph layer**

Create `app/src/main/res/drawable/ic_launcher_foreground.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- "Team": two overlapping avatar circles -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M 38,44 m -16,0 a 16,16 0 1,0 32,0 a 16,16 0 1,0 -32,0" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M 66,44 m -16,0 a 16,16 0 1,0 32,0 a 16,16 0 1,0 -32,0" />

    <!-- Phone badge background circle -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M 80,78 m -18,0 a 18,18 0 1,0 36,0 a 18,18 0 1,0 -36,0" />

    <!-- Phone glyph: a rounded bar rotated to read as a handset, inside the badge -->
    <group
        android:pivotX="80"
        android:pivotY="78"
        android:rotation="-45">
        <path
            android:fillColor="#6C4FF5"
            android:pathData="M 74.5,74.5 L 85.5,74.5 A 2.5,2.5 0 0 1 88,77 L 88,79 A 2.5,2.5 0 0 1 85.5,81.5 L 74.5,81.5 A 2.5,2.5 0 0 1 72,79 L 72,77 A 2.5,2.5 0 0 1 74.5,74.5 Z" />
    </group>
</vector>
```

- [ ] **Step 3: Point the adaptive icon at the new layers**

Overwrite `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL` (resource compilation is part of this task graph — an invalid vector/XML would fail here)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/drawable/ic_launcher_background.xml app/src/main/res/drawable/ic_launcher_foreground.xml app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
git commit -m "feat: add team+phone-badge adaptive launcher icon"
```

---

## Task 7: Redesign DailySummaryCard (used by Home and Day Detail)

**Files:**
- Modify: `app/src/main/java/com/dailycallsreview/app/ui/common/DailySummaryCard.kt`

- [ ] **Step 1: Replace the full file**

Replace the full contents of `app/src/main/java/com/dailycallsreview/app/ui/common/DailySummaryCard.kt` with:

```kotlin
package com.dailycallsreview.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailycallsreview.app.core.DailySummary
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

@Composable
fun DailySummaryCard(summary: DailySummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SummaryCard {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    value = summary.totalCalls.toString(),
                    label = "Calls",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = "${summary.totalTalkTimeSeconds / 60}m",
                    label = "Talk time",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    value = summary.firstCallTime?.format(TIME_FORMAT) ?: "—",
                    label = "First call",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = summary.lastCallTime?.format(TIME_FORMAT) ?: "—",
                    label = "Last call",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Shift span: ${summary.shiftSpanSeconds?.let { formatDuration(it) } ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                if (summary.offHoursCallCount > 0) {
                    WarningBadge("⚠ ${summary.offHoursCallCount} outside hours")
                }
            }
            if (summary.isHoliday || summary.isNonWorkingDay) {
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    if (summary.isHoliday) InfoBadge("Holiday")
                    if (summary.isNonWorkingDay) InfoBadge("Off day")
                }
            }
        }

        val sortedStats = summary.perCoworker.values.sortedByDescending { it.callCount }
        if (sortedStats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "PER COWORKER (${sortedStats.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val maxCallCount = sortedStats.maxOf { it.callCount }
            SummaryCard(contentPadding = 0.dp) {
                sortedStats.forEachIndexed { index, stat ->
                    CoworkerRow(stat = stat, maxCallCount = maxCallCount)
                    if (index != sortedStats.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return "${hours}h ${minutes}m"
}
```

This adds the `modifier` parameter both `HomeScreen` (Task 11) and `DayDetailScreen` (Task 11) already pass in, sorts coworkers by call count descending (spec §8), and replaces every plain `Text` line with `StatTile`/`WarningBadge`/`InfoBadge`/`CoworkerRow`.

- [ ] **Step 2: Build and verify**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/common/DailySummaryCard.kt
git commit -m "feat: redesign DailySummaryCard with StatTile grid and CoworkerRow list"
```

---

## Task 8: CallLogRepository — add getAllCallsBetween (untagged-inclusive query)

**Files:**
- Modify: `app/src/main/java/com/dailycallsreview/app/data/calllog/CallLogRepository.kt`

Needed by Task 9's Suggested-contacts ranking, which must rank *untagged* contacts by call frequency — the existing `getCallsBetween` filters to only `taggedNumbers` and returns immediately if that set is empty, so it can't serve this. This step is a behavior-preserving refactor of `getCallsBetween` (extracts the raw query into a private helper) plus one new public method; there's no existing unit test for this file (it's `ContentProvider`-backed, consistent with the rest of `data/calllog` and `data/contacts` having no unit tests today) — correctness is verified by the unchanged existing behavior compiling identically, plus manual on-device verification in Task 20.

- [ ] **Step 1: Replace the full file**

Replace the full contents of `app/src/main/java/com/dailycallsreview/app/data/calllog/CallLogRepository.kt` with:

```kotlin
package com.dailycallsreview.app.data.calllog

import android.content.Context
import android.provider.CallLog
import com.dailycallsreview.app.core.CallRecord
import com.dailycallsreview.app.core.CallType
import com.dailycallsreview.app.core.PhoneNumberNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallLogRepository(private val context: Context) {

    suspend fun getCallsBetween(startMillis: Long, endMillis: Long, taggedNumbers: Set<String>): List<CallRecord> =
        withContext(Dispatchers.IO) {
            if (taggedNumbers.isEmpty()) return@withContext emptyList()
            queryCallLog(startMillis, endMillis).filter { it.phoneNumberLast10 in taggedNumbers }
        }

    // Unlike getCallsBetween, this returns every call in range regardless of tag — used to rank
    // untagged contacts by call frequency for the Team Setup "Suggested" section (Task 9/10).
    suspend fun getAllCallsBetween(startMillis: Long, endMillis: Long): List<CallRecord> =
        withContext(Dispatchers.IO) {
            queryCallLog(startMillis, endMillis)
        }

    private fun queryCallLog(startMillis: Long, endMillis: Long): List<CallRecord> {
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )
        val selection = "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.DATE} < ?"
        val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())

        val records = mutableListOf<CallRecord>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CallLog.Calls.DATE} ASC"
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)

            while (cursor.moveToNext()) {
                val rawNumber = cursor.getString(numberIndex) ?: continue
                val normalized = PhoneNumberNormalizer.last10Digits(rawNumber)

                val callType = when (cursor.getInt(typeIndex)) {
                    CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                    CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                    CallLog.Calls.BLOCKED_TYPE -> continue
                    else -> CallType.ANSWERED
                }

                records.add(
                    CallRecord(
                        phoneNumberLast10 = normalized,
                        contactName = cursor.getString(nameIndex),
                        timestampMillis = cursor.getLong(dateIndex),
                        durationSeconds = cursor.getInt(durationIndex),
                        type = callType
                    )
                )
            }
        }
        return records
    }
}
```

- [ ] **Step 2: Build and verify**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run the existing full unit test suite to confirm nothing else regressed**

Run: `./gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL` (all existing `CallAggregatorTest`/`PhoneNumberNormalizerTest`/`WorkingDaysMaskTest` tests still pass — this file has no direct unit tests of its own, but nothing here should have changed their behavior)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/data/calllog/CallLogRepository.kt
git commit -m "refactor: extract CallLogRepository query, add getAllCallsBetween"
```

---

## Task 9: Redesign HomeScreen (wire up the redesigned DailySummaryCard)

**Files:**
- Modify: `app/src/main/java/com/dailycallsreview/app/ui/home/HomeScreen.kt`

- [ ] **Step 1: Replace the full file**

Replace the full contents of `app/src/main/java/com/dailycallsreview/app/ui/home/HomeScreen.kt` with:

```kotlin
package com.dailycallsreview.app.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.ui.common.DailySummaryCard

@Composable
fun HomeScreen(app: DailyCallsReviewApplication) {
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(app.callLogRepository, app.teamRepository, app.settingsRepository)
            }
        }
    )
    val summary by viewModel.todaySummary.collectAsState()

    summary?.let {
        DailySummaryCard(it, modifier = Modifier.padding(16.dp))
    } ?: Text("Loading today's calls…", modifier = Modifier.padding(16.dp))
}
```

This step deliberately drops the old `HomeActionBar` button row and the `refreshNow`-triggering Refresh button, and the `onOpenHistory`/`onOpenTeamSetup`/`onOpenSettings` params — Task 11 (navigation shell) wraps this screen's content in `AppScaffold`, which is where the Refresh action and History/Team/Settings navigation (via the bottom nav bar) live instead. Writing it this way now means `HomeScreen` is already in its final content-only shape before Task 11 touches it, so Task 11 only needs to add the `AppScaffold` wrapper, not restructure this file's body.

- [ ] **Step 2: Build and verify**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: FAIL — `NavGraph.kt` still calls `HomeScreen(app, onOpenHistory = ..., onOpenTeamSetup = ..., onOpenSettings = ...)`, which no longer matches this signature.

This is expected and intentional: `NavGraph.kt`'s call site is updated in Task 11 alongside adding `AppScaffold` to every screen. Do not modify `NavGraph.kt` in this task — that keeps this diff focused on `HomeScreen.kt`'s own content, matching the "one clear responsibility per task" principle. Confirm the failure is exactly this one call-site mismatch (not some other new error) before moving on.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/home/HomeScreen.kt
git commit -m "feat: redesign HomeScreen content, defer nav wiring to next task"
```

---

## Task 10: Pure ranking — SuggestedContactsRanking

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/core/SuggestedContactsRanking.kt`
- Test: `app/src/test/java/com/dailycallsreview/app/core/SuggestedContactsRankingTest.kt`

Ranks untagged contacts by how many calls they account for in a given set of `CallRecord`s (the current-calendar-month-to-date window, per spec §8). Framework-free — takes already-fetched data as input, same pattern as `CallAggregator`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/dailycallsreview/app/core/SuggestedContactsRankingTest.kt`:

```kotlin
package com.dailycallsreview.app.core

import com.dailycallsreview.app.data.contacts.PickableContact
import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestedContactsRankingTest {

    private fun call(number: String) = CallRecord(
        phoneNumberLast10 = number,
        contactName = null,
        timestampMillis = 0L,
        durationSeconds = 30,
        type = CallType.ANSWERED
    )

    @Test
    fun `ranks untagged contacts by call count descending`() {
        val contacts = listOf(
            PickableContact(1L, "Aarav", listOf("1111111111")),
            PickableContact(2L, "Bhavna", listOf("2222222222"))
        )
        val calls = listOf(call("1111111111"), call("2222222222"), call("2222222222"))

        val result = SuggestedContactsRanking.rank(contacts, emptySet(), calls)

        assertEquals(listOf(2L, 1L), result.map { it.contact.contactId })
        assertEquals(2, result.first().callCountThisMonth)
    }

    @Test
    fun `excludes already-tagged contacts`() {
        val contacts = listOf(PickableContact(1L, "Aarav", listOf("1111111111")))
        val calls = listOf(call("1111111111"))

        val result = SuggestedContactsRanking.rank(contacts, taggedContactIds = setOf(1L), callsThisMonth = calls)

        assert(result.isEmpty())
    }

    @Test
    fun `excludes untagged contacts with zero calls this month`() {
        val contacts = listOf(
            PickableContact(1L, "Aarav", listOf("1111111111")),
            PickableContact(2L, "Bhavna", listOf("2222222222"))
        )
        val calls = listOf(call("1111111111"))

        val result = SuggestedContactsRanking.rank(contacts, emptySet(), calls)

        assertEquals(listOf(1L), result.map { it.contact.contactId })
    }

    @Test
    fun `respects the limit parameter`() {
        val contacts = (1..5).map { PickableContact(it.toLong(), "Contact $it", listOf("100000000$it")) }
        val calls = (1..5).map { call("100000000$it") }

        val result = SuggestedContactsRanking.rank(contacts, emptySet(), calls, limit = 3)

        assertEquals(3, result.size)
    }

    @Test
    fun `a contact with multiple numbers is credited for calls to any of them`() {
        val contacts = listOf(PickableContact(1L, "Aarav", listOf("1111111111", "3333333333")))
        val calls = listOf(call("1111111111"), call("3333333333"))

        val result = SuggestedContactsRanking.rank(contacts, emptySet(), calls)

        assertEquals(2, result.single().callCountThisMonth)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dailycallsreview.app.core.SuggestedContactsRankingTest" --console=plain`
Expected: FAIL — `SuggestedContactsRanking` unresolved reference

- [ ] **Step 3: Implement it**

Create `app/src/main/java/com/dailycallsreview/app/core/SuggestedContactsRanking.kt`:

```kotlin
package com.dailycallsreview.app.core

import com.dailycallsreview.app.data.contacts.PickableContact

data class SuggestedContact(val contact: PickableContact, val callCountThisMonth: Int)

object SuggestedContactsRanking {
    fun rank(
        allContacts: List<PickableContact>,
        taggedContactIds: Set<Long>,
        callsThisMonth: List<CallRecord>,
        limit: Int = 10
    ): List<SuggestedContact> {
        val untagged = allContacts.filter { it.contactId !in taggedContactIds }
        if (untagged.isEmpty() || callsThisMonth.isEmpty()) return emptyList()

        val numberToContactId = mutableMapOf<String, Long>()
        untagged.forEach { contact ->
            contact.phoneNumbersLast10.forEach { number -> numberToContactId.putIfAbsent(number, contact.contactId) }
        }

        val callCountByContactId = mutableMapOf<Long, Int>()
        callsThisMonth.forEach { call ->
            val contactId = numberToContactId[call.phoneNumberLast10] ?: return@forEach
            callCountByContactId[contactId] = (callCountByContactId[contactId] ?: 0) + 1
        }

        return untagged
            .mapNotNull { contact -> callCountByContactId[contact.contactId]?.let { count -> SuggestedContact(contact, count) } }
            .sortedByDescending { it.callCountThisMonth }
            .take(limit)
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dailycallsreview.app.core.SuggestedContactsRankingTest" --console=plain`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/core/SuggestedContactsRanking.kt app/src/test/java/com/dailycallsreview/app/core/SuggestedContactsRankingTest.kt
git commit -m "feat: add SuggestedContactsRanking pure utility"
```

---

## Task 11: Redesign TeamSetupViewModel (suggestions, search, bulk select, edit mode)

**Files:**
- Modify: `app/src/main/java/com/dailycallsreview/app/ui/team/TeamSetupViewModel.kt`

This adds new state and methods on top of the existing `allContacts` / `taggedContactIds` / `toggleTag(contact, currentlyTagged)` API, which is left unchanged so `TeamSetupScreen.kt`'s current body keeps compiling until Task 13 redesigns it.

- [ ] **Step 1: Replace the full file**

Replace the full contents of `app/src/main/java/com/dailycallsreview/app/ui/team/TeamSetupViewModel.kt` with:

```kotlin
package com.dailycallsreview.app.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailycallsreview.app.core.SuggestedContact
import com.dailycallsreview.app.core.SuggestedContactsRanking
import com.dailycallsreview.app.data.TeamRepository
import com.dailycallsreview.app.data.calllog.CallLogRepository
import com.dailycallsreview.app.data.contacts.ContactsRepository
import com.dailycallsreview.app.data.contacts.PickableContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class TeamSetupViewModel(
    private val contactsRepository: ContactsRepository,
    private val teamRepository: TeamRepository,
    private val callLogRepository: CallLogRepository,
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _allContacts = MutableStateFlow<List<PickableContact>>(emptyList())
    val allContacts: StateFlow<List<PickableContact>> = _allContacts.asStateFlow()

    private val _suggestedContacts = MutableStateFlow<List<SuggestedContact>>(emptyList())
    val suggestedContacts: StateFlow<List<SuggestedContact>> = _suggestedContacts.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isEditingTagged = MutableStateFlow(false)
    val isEditingTagged: StateFlow<Boolean> = _isEditingTagged.asStateFlow()

    val taggedContactIds: StateFlow<Set<Long>> = teamRepository.observeTaggedContacts()
        .map { entities -> entities.map { it.contactId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        viewModelScope.launch {
            _allContacts.value = contactsRepository.getAllContactsWithPhoneNumbers()
            refreshSuggestions()
        }
    }

    private suspend fun refreshSuggestions() {
        // Read tagged-contact ids fresh from the repository rather than via the taggedContactIds
        // StateFlow above, since that StateFlow only starts collecting once something in the UI
        // calls collectAsState() on it — relying on it here could race and see a stale/empty set.
        val currentTaggedIds = teamRepository.observeTaggedContacts().first().map { it.contactId }.toSet()

        val today = LocalDate.now(zone)
        val monthStart = YearMonth.from(today).atDay(1)
        val startMillis = monthStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val callsThisMonth = callLogRepository.getAllCallsBetween(startMillis, endMillis)

        _suggestedContacts.value = SuggestedContactsRanking.rank(
            allContacts = _allContacts.value,
            taggedContactIds = currentTaggedIds,
            callsThisMonth = callsThisMonth
        )
    }

    fun toggleTag(contact: PickableContact, currentlyTagged: Boolean) {
        viewModelScope.launch {
            if (currentlyTagged) {
                teamRepository.untagContact(contact.contactId)
            } else {
                teamRepository.tagContact(contact)
            }
            refreshSuggestions()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setEditingTagged(editing: Boolean) {
        _isEditingTagged.value = editing
    }

    fun selectAll(visibleUntagged: List<PickableContact>) {
        viewModelScope.launch {
            visibleUntagged.forEach { teamRepository.tagContact(it) }
            refreshSuggestions()
        }
    }

    fun clearAll(visibleTagged: List<PickableContact>) {
        viewModelScope.launch {
            visibleTagged.forEach { teamRepository.untagContact(it.contactId) }
            refreshSuggestions()
        }
    }
}
```

- [ ] **Step 2: Build and verify (expect a call-site failure, which is expected until the next task)**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: FAIL — `TeamSetupScreen.kt`'s `viewModelFactory` still calls the 2-arg `TeamSetupViewModel(app.contactsRepository, app.teamRepository)`.

- [ ] **Step 3: Update the one call site**

In `app/src/main/java/com/dailycallsreview/app/ui/team/TeamSetupScreen.kt`, replace:

```kotlin
            initializer { TeamSetupViewModel(app.contactsRepository, app.teamRepository) }
```

with:

```kotlin
            initializer { TeamSetupViewModel(app.contactsRepository, app.teamRepository, app.callLogRepository) }
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/team/TeamSetupViewModel.kt app/src/main/java/com/dailycallsreview/app/ui/team/TeamSetupScreen.kt
git commit -m "feat: add suggestions/search/bulk-select/edit-mode state to TeamSetupViewModel"
```

---

## Task 12: Navigation shell — AppScaffold (top app bar + bottom navigation)

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/ui/nav/AppScaffold.kt`
- Modify: `app/src/main/java/com/dailycallsreview/app/ui/nav/NavGraph.kt`
- Modify: `app/src/main/java/com/dailycallsreview/app/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/dailycallsreview/app/ui/history/HistoryScreen.kt`
- Modify: `app/src/main/java/com/dailycallsreview/app/ui/team/TeamSetupScreen.kt`
- Modify: `app/src/main/java/com/dailycallsreview/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/dailycallsreview/app/ui/daydetail/DayDetailScreen.kt`
- Modify: `app/src/main/java/com/dailycallsreview/app/ui/rangedetail/RangeDetailScreen.kt`

This task wires the shell (app bar + bottom nav + back button) into every screen and removes now-redundant navigation (Home's old History/Team/Settings button row — superseded by the bottom nav bar). It intentionally does **not** redesign each screen's inner content yet — that's Tasks 8, 9, 13, 14, 18, 19. The one exception is relocating the two existing "Export CSV" buttons (History, Range Detail) into the app bar, since that's simply moving an existing click handler, not new UI.

- [ ] **Step 1: Create `AppScaffold`**

Create `app/src/main/java/com/dailycallsreview/app/ui/nav/AppScaffold.kt`:

```kotlin
package com.dailycallsreview.app.ui.nav

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val BOTTOM_TABS = listOf(
    BottomTab(Routes.HOME, "Home", Icons.Filled.Home),
    BottomTab(Routes.HISTORY, "History", Icons.Filled.History),
    BottomTab(Routes.TEAM_SETUP, "Team", Icons.Filled.Groups),
    BottomTab(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

@Composable
fun AppScaffold(
    title: String,
    navController: NavHostController,
    showBackButton: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = actions
            )
        },
        bottomBar = {
            if (!showBackButton) {
                BottomNavBar(navController)
            }
        }
    ) { paddingValues ->
        content(paddingValues)
    }
}

@Composable
private fun BottomNavBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        BOTTOM_TABS.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) }
            )
        }
    }
}
```

- [ ] **Step 2: Pass `navController` into every top-level screen from `NavGraph`**

Replace the full contents of `app/src/main/java/com/dailycallsreview/app/ui/nav/NavGraph.kt` with:

```kotlin
package com.dailycallsreview.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.ui.daydetail.DayDetailScreen
import com.dailycallsreview.app.ui.history.HistoryScreen
import com.dailycallsreview.app.ui.home.HomeScreen
import com.dailycallsreview.app.ui.rangedetail.RangeDetailScreen
import com.dailycallsreview.app.ui.settings.SettingsScreen
import com.dailycallsreview.app.ui.team.TeamSetupScreen
import java.time.LocalDate

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val TEAM_SETUP = "team_setup"
    const val SETTINGS = "settings"
    const val DAY_DETAIL = "day_detail/{date}"
    const val RANGE_DETAIL = "range_detail/{start}/{end}"

    fun dayDetail(date: LocalDate) = "day_detail/$date"
    fun rangeDetail(start: LocalDate, end: LocalDate) = "range_detail/$start/$end"
}

@Composable
fun AppNavGraph(app: DailyCallsReviewApplication, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            // History/Team/Settings navigation now goes through the bottom nav bar inside
            // AppScaffold (which HomeScreen renders using this same navController), so the
            // old onOpenHistory/onOpenTeamSetup/onOpenSettings callbacks are no longer needed.
            HomeScreen(app = app, navController = navController)
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                app = app,
                navController = navController,
                onOpenDay = { date -> navController.navigate(Routes.dayDetail(date)) { launchSingleTop = true } },
                onOpenRange = { start, end -> navController.navigate(Routes.rangeDetail(start, end)) { launchSingleTop = true } }
            )
        }
        composable(Routes.TEAM_SETUP) {
            TeamSetupScreen(app = app, navController = navController)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(app = app, navController = navController)
        }
        composable(
            route = Routes.DAY_DETAIL,
            arguments = listOf(navArgument("date") { type = NavType.StringType })
        ) { backStackEntry ->
            val date = LocalDate.parse(backStackEntry.arguments?.getString("date"))
            DayDetailScreen(app = app, navController = navController, date = date)
        }
        composable(
            route = Routes.RANGE_DETAIL,
            arguments = listOf(
                navArgument("start") { type = NavType.StringType },
                navArgument("end") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val start = LocalDate.parse(backStackEntry.arguments?.getString("start"))
            val end = LocalDate.parse(backStackEntry.arguments?.getString("end"))
            RangeDetailScreen(
                app = app,
                navController = navController,
                startDate = start,
                endDate = end,
                onOpenDay = { date -> navController.navigate(Routes.dayDetail(date)) { launchSingleTop = true } }
            )
        }
    }
}
```

- [ ] **Step 3: Build and verify (expect failures — the 6 screens don't accept `navController` yet)**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: FAIL — Kotlin "too many arguments" / "no parameter found" errors at each `composable(...) { ... Screen(...) }` call in `NavGraph.kt`, since none of the 6 screens accept a `navController` parameter yet. This confirms `NavGraph.kt` is correctly wired ahead of the screens; continue to the next steps.

- [ ] **Step 4: Wrap `HomeScreen` in `AppScaffold`, drop the now-redundant button row**

Replace the full contents of `app/src/main/java/com/dailycallsreview/app/ui/home/HomeScreen.kt` with:

```kotlin
package com.dailycallsreview.app.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.ui.common.DailySummaryCard
import com.dailycallsreview.app.ui.nav.AppScaffold

@Composable
fun HomeScreen(
    app: DailyCallsReviewApplication,
    navController: NavHostController
) {
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(app.callLogRepository, app.teamRepository, app.settingsRepository)
            }
        }
    )
    val summary by viewModel.todaySummary.collectAsState()

    AppScaffold(
        title = "Daily Calls Review",
        navController = navController,
        actions = {
            IconButton(onClick = viewModel::refreshNow) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
    ) { paddingValues ->
        summary?.let {
            DailySummaryCard(it, modifier = Modifier.padding(paddingValues))
        } ?: Text("Loading today's calls…", modifier = Modifier.padding(paddingValues))
    }
}
```

- [ ] **Step 5: Wrap `HistoryScreen` in `AppScaffold`, move Export CSV into the app bar**

In `app/src/main/java/com/dailycallsreview/app/ui/history/HistoryScreen.kt`, replace the full file with:

```kotlin
package com.dailycallsreview.app.ui.history

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.ui.export.CsvExporter
import com.dailycallsreview.app.ui.nav.AppScaffold
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HistoryScreen(
    app: DailyCallsReviewApplication,
    navController: NavHostController,
    onOpenDay: (LocalDate) -> Unit,
    onOpenRange: (LocalDate, LocalDate) -> Unit
) {
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HistoryViewModel(app.callLogRepository, app.teamRepository, app.settingsRepository) }
        }
    )
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    AppScaffold(
        title = "History",
        navController = navController,
        actions = {
            IconButton(onClick = {
                if (state.filter == HistoryFilter.OUTSIDE_HOURS) {
                    CsvExporter.exportCallsAndShare(context, "history_export.csv", state.offHoursCalls, ZoneId.systemDefault())
                } else {
                    CsvExporter.exportDailySummariesAndShare(context, "history_export.csv", state.days)
                }
            }) {
                Icon(Icons.Filled.Download, contentDescription = "Export CSV")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            Row {
                Button(onClick = { viewModel.setViewMode(HistoryViewMode.MONTH) }) { Text("Month") }
                Button(onClick = { viewModel.setViewMode(HistoryViewMode.WEEK) }) { Text("Week") }
                Button(onClick = {
                    DatePickerDialog(context, { _, y1, m1, d1 ->
                        val start = LocalDate.of(y1, m1 + 1, d1)
                        DatePickerDialog(context, { _, y2, m2, d2 ->
                            onOpenRange(start, LocalDate.of(y2, m2 + 1, d2))
                        }, start.year, start.monthValue - 1, start.dayOfMonth).show()
                    }, state.anchorDate.year, state.anchorDate.monthValue - 1, state.anchorDate.dayOfMonth).show()
                }) { Text("Custom range") }
            }
            Row {
                HistoryFilter.entries.forEach { filterOption ->
                    FilterChip(
                        selected = state.filter == filterOption,
                        onClick = { viewModel.setFilter(filterOption) },
                        label = { Text(filterOption.name) }
                    )
                }
            }
            if (state.filter == HistoryFilter.OUTSIDE_HOURS) {
                LazyColumn {
                    items(state.offHoursCalls) { call ->
                        val time = Instant.ofEpochMilli(call.timestampMillis).atZone(ZoneId.systemDefault())
                        Text("${call.contactName ?: call.phoneNumberLast10} — $time")
                    }
                }
            } else {
                LazyColumn {
                    items(state.days) { day ->
                        Row {
                            Text("${day.date}: ${day.totalCalls} calls")
                            Button(onClick = { onOpenDay(day.date) }) { Text("View") }
                        }
                    }
                }
            }
        }
    }
}
```

This step only relocates the Export CSV button and adds the `AppScaffold`/`navController` wrapper — the Month/Week/filter/day-list body is untouched here and gets fully redesigned in Task 18 (Year mode doesn't exist as a button yet either — that's Task 16/18).

- [ ] **Step 6: Wrap `TeamSetupScreen` in `AppScaffold`**

Replace the full contents of `app/src/main/java/com/dailycallsreview/app/ui/team/TeamSetupScreen.kt` with:

```kotlin
package com.dailycallsreview.app.ui.team

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.ui.nav.AppScaffold

@Composable
fun TeamSetupScreen(app: DailyCallsReviewApplication, navController: NavHostController) {
    val viewModel: TeamSetupViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TeamSetupViewModel(app.contactsRepository, app.teamRepository, app.callLogRepository) }
        }
    )
    val contacts by viewModel.allContacts.collectAsState()
    val taggedIds by viewModel.taggedContactIds.collectAsState()

    AppScaffold(title = "Team Setup", navController = navController) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            items(contacts) { contact ->
                val isTagged = contact.contactId in taggedIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isTagged,
                        onCheckedChange = { viewModel.toggleTag(contact, isTagged) }
                    )
                    Text(contact.displayName)
                }
            }
        }
    }
}
```

The body here is still the plain checkbox list (no search/suggested/edit-mode UI yet) — Task 11 already extended the ViewModel with that state, and Task 13 wires it into this screen's body. This step only adds the `AppScaffold` wrapper.

- [ ] **Step 7: Wrap `SettingsScreen` in `AppScaffold`**

In `app/src/main/java/com/dailycallsreview/app/ui/settings/SettingsScreen.kt`, change the function signature and outermost wrapper only — replace:

```kotlin
@Composable
fun SettingsScreen(app: DailyCallsReviewApplication) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(app.settingsRepository) }
        }
    )
    val schedule by viewModel.schedule.collectAsState()
    val context = LocalContext.current

    schedule?.let { current ->
        var holidayLabel by remember { mutableStateOf("") }
        var holidayDate by remember { mutableStateOf(LocalDate.now()) }

        LazyColumn(modifier = Modifier.padding(16.dp)) {
```

with:

```kotlin
@Composable
fun SettingsScreen(app: DailyCallsReviewApplication, navController: androidx.navigation.NavHostController) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(app.settingsRepository) }
        }
    )
    val schedule by viewModel.schedule.collectAsState()
    val context = LocalContext.current

    com.dailycallsreview.app.ui.nav.AppScaffold(title = "Settings", navController = navController) { paddingValues ->
    schedule?.let { current ->
        var holidayLabel by remember { mutableStateOf("") }
        var holidayDate by remember { mutableStateOf(LocalDate.now()) }

        LazyColumn(modifier = Modifier.padding(paddingValues)) {
```

And at the very end of the same file, the existing closing braces are `}` (closes `LazyColumn`), `}` (closes `schedule?.let`), `}` (closes the function) — add one more closing `}` before the final function-closing brace to close the new `AppScaffold` trailing lambda, so the tail of the file reads:

```kotlin
            items(current.holidays.sorted(), key = { it.toString() }) { date ->
                Row {
                    Text(date.toString())
                    Button(onClick = { viewModel.removeHoliday(date) }) { Text("Remove") }
                }
            }
        }
    }
    }
}
```

Fully-qualifying `NavHostController` and `AppScaffold` inline (rather than adding `import` lines) avoids having to locate and edit this file's import block by hand for this mechanical step — Task 14 rewrites this entire file anyway and will clean the imports up properly then.

- [ ] **Step 8: Wrap `DayDetailScreen` in `AppScaffold` with a back button**

Replace the full contents of `app/src/main/java/com/dailycallsreview/app/ui/daydetail/DayDetailScreen.kt` with:

```kotlin
package com.dailycallsreview.app.ui.daydetail

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.ui.common.DailySummaryCard
import com.dailycallsreview.app.ui.nav.AppScaffold
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val TITLE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d yyyy")

@Composable
fun DayDetailScreen(app: DailyCallsReviewApplication, navController: NavHostController, date: LocalDate) {
    val viewModel: DayDetailViewModel = viewModel(
        key = date.toString(),
        factory = viewModelFactory {
            initializer {
                DayDetailViewModel(date, app.callLogRepository, app.teamRepository, app.settingsRepository)
            }
        }
    )
    val summary by viewModel.summary.collectAsState()

    AppScaffold(title = date.format(TITLE_FORMAT), navController = navController, showBackButton = true) { paddingValues ->
        summary?.let {
            DailySummaryCard(it, modifier = Modifier.padding(paddingValues))
        } ?: Text("Loading…", modifier = Modifier.padding(paddingValues))
    }
}
```

- [ ] **Step 9: Wrap `RangeDetailScreen` in `AppScaffold` with a back button, move Export CSV into the app bar**

Replace the full contents of `app/src/main/java/com/dailycallsreview/app/ui/rangedetail/RangeDetailScreen.kt` with:

```kotlin
package com.dailycallsreview.app.ui.rangedetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.ui.export.CsvExporter
import com.dailycallsreview.app.ui.history.HistoryFilter
import com.dailycallsreview.app.ui.nav.AppScaffold
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TITLE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun RangeDetailScreen(
    app: DailyCallsReviewApplication,
    navController: NavHostController,
    startDate: LocalDate,
    endDate: LocalDate,
    onOpenDay: (LocalDate) -> Unit
) {
    val viewModel: RangeDetailViewModel = viewModel(
        key = "$startDate-$endDate",
        factory = viewModelFactory {
            initializer {
                RangeDetailViewModel(startDate, endDate, app.callLogRepository, app.teamRepository, app.settingsRepository)
            }
        }
    )
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    AppScaffold(
        title = "${startDate.format(TITLE_FORMAT)} – ${endDate.format(TITLE_FORMAT)}",
        navController = navController,
        showBackButton = true,
        actions = {
            IconButton(onClick = {
                when (state.filter) {
                    HistoryFilter.ALL -> state.summary?.let {
                        CsvExporter.exportRangeAndShare(context, "range_export.csv", it)
                    }
                    HistoryFilter.OUTSIDE_HOURS -> CsvExporter.exportCallsAndShare(
                        context, "range_export.csv", state.offHoursCalls, ZoneId.systemDefault()
                    )
                    else -> CsvExporter.exportDailySummariesAndShare(context, "range_export.csv", state.filteredDays)
                }
            }) {
                Icon(Icons.Filled.Download, contentDescription = "Export CSV")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            Row {
                HistoryFilter.entries.forEach { filterOption ->
                    FilterChip(
                        selected = state.filter == filterOption,
                        onClick = { viewModel.setFilter(filterOption) },
                        label = { Text(filterOption.name) }
                    )
                }
            }

            when (state.filter) {
                HistoryFilter.ALL -> state.summary?.let { s ->
                    Column {
                        Text("Range: ${s.startDate} to ${s.endDate}")
                        Text("Total calls: ${s.totalCalls}")
                        Text("Total talk time: ${s.totalTalkTimeSeconds / 60} min")
                        Text("Average shift span: ${s.averageShiftSpanSeconds?.let { "${it / 3600}h" } ?: "—"}")
                        Text("Busiest coworker: ${s.busiestCoworker?.displayName ?: "—"}")
                        Text("Off-hours calls: ${s.offHoursCallCount}")
                        Text("Holidays in range: ${s.holidayCount}")
                        Text("Non-working days in range: ${s.nonWorkingDayCount}")
                        s.perCoworker.values.forEach { stat ->
                            Text("${stat.displayName}: ${stat.callCount} calls, ${stat.totalTalkTimeSeconds / 60} min")
                        }
                    }
                } ?: Text("Loading…")
                HistoryFilter.OUTSIDE_HOURS -> LazyColumn {
                    items(state.offHoursCalls) { call ->
                        val time = Instant.ofEpochMilli(call.timestampMillis).atZone(ZoneId.systemDefault())
                        Text("${call.contactName ?: call.phoneNumberLast10} — $time")
                    }
                }
                else -> LazyColumn {
                    items(state.filteredDays) { day ->
                        Row {
                            Text("${day.date}: ${day.totalCalls} calls")
                            Button(onClick = { onOpenDay(day.date) }) { Text("View") }
                        }
                    }
                }
            }
        }
    }
}
```

This step only relocates Export CSV and adds the `AppScaffold`/back-button wrapper — the plain-text summary body is fully redesigned in Task 19.

- [ ] **Step 10: Build and verify**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/nav/ app/src/main/java/com/dailycallsreview/app/ui/home/HomeScreen.kt app/src/main/java/com/dailycallsreview/app/ui/history/HistoryScreen.kt app/src/main/java/com/dailycallsreview/app/ui/team/TeamSetupScreen.kt app/src/main/java/com/dailycallsreview/app/ui/settings/SettingsScreen.kt app/src/main/java/com/dailycallsreview/app/ui/daydetail/DayDetailScreen.kt app/src/main/java/com/dailycallsreview/app/ui/rangedetail/RangeDetailScreen.kt
git commit -m "feat: add navigation shell (top app bar + bottom nav) to every screen"
```

---

## Task 13: TeamSetupScreen UI redesign

**Files:**
- Modify: `app/src/main/java/com/dailycallsreview/app/ui/team/TeamSetupScreen.kt`

Implements: search across all three sections, Select all/Clear all (scoped to the currently visible/filtered set), Tagged section as its own fixed-height internally-scrolling box with an Edit mode (red minus buttons, hides the rest of the screen while active), a Suggested section (from Task 11's `suggestedContacts`), an alphabetical All Contacts section using `ToggleChip` instead of a checkbox, and an A–Z fast-scroll rail (hidden during search or edit mode, per spec §7.2/§8).

- [ ] **Step 1: Replace the full file**

Replace the full contents of `app/src/main/java/com/dailycallsreview/app/ui/team/TeamSetupScreen.kt` with:

```kotlin
package com.dailycallsreview.app.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.core.NameInitials
import com.dailycallsreview.app.core.SuggestedContact
import com.dailycallsreview.app.data.contacts.PickableContact
import com.dailycallsreview.app.ui.common.CoworkerAvatarBox
import com.dailycallsreview.app.ui.common.ToggleChip
import com.dailycallsreview.app.ui.nav.AppScaffold
import kotlinx.coroutines.launch

@Composable
fun TeamSetupScreen(app: DailyCallsReviewApplication, navController: NavHostController) {
    val viewModel: TeamSetupViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TeamSetupViewModel(app.contactsRepository, app.teamRepository, app.callLogRepository) }
        }
    )
    val allContacts by viewModel.allContacts.collectAsState()
    val taggedIds by viewModel.taggedContactIds.collectAsState()
    val suggested by viewModel.suggestedContacts.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val isEditing by viewModel.isEditingTagged.collectAsState()

    val taggedContacts = allContacts
        .filter { it.contactId in taggedIds }
        .filter { it.displayName.contains(query, ignoreCase = true) }
    val suggestedIds = suggested.map { it.contact.contactId }.toSet()
    val visibleSuggested = suggested.filter { it.contact.displayName.contains(query, ignoreCase = true) }
    val otherContacts = allContacts
        .filter { it.contactId !in taggedIds && it.contactId !in suggestedIds }
        .filter { it.displayName.contains(query, ignoreCase = true) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val showBrowseSections = !isEditing
    val showAzRail = showBrowseSections && query.isBlank() && otherContacts.isNotEmpty()

    // Item index of the first "All Contacts" row — used by the A-Z rail to scroll precisely.
    // Must mirror the exact item order built in the LazyColumn below.
    val allContactsStartIndex = 1 /* header */ + 1 /* search */ + 1 /* bulk actions */ +
        1 /* tagged section header */ + 1 /* tagged box */ +
        (if (visibleSuggested.isNotEmpty()) 1 + visibleSuggested.size else 0) +
        1 /* all-contacts header */

    AppScaffold(title = "Team Setup", navController = navController) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Team Setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${taggedIds.size} tagged",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                if (showBrowseSections) {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = viewModel::setSearchQuery,
                            placeholder = { Text("Search contacts…") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        )
                    }
                    item {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = "Select all",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable {
                                        viewModel.selectAll(visibleSuggested.map { it.contact } + otherContacts)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Clear all",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { viewModel.clearAll(taggedContacts) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "TAGGED (${taggedContacts.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isEditing) "Done" else "Edit",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { viewModel.setEditingTagged(!isEditing) }
                        )
                    }
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(96.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .verticalScroll(rememberScrollState())
                    ) {
                        taggedContacts.forEach { contact ->
                            TeamSetupRow(contact = contact) {
                                if (isEditing) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                            .clickable { viewModel.toggleTag(contact, true) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("−", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    ToggleChip(text = "Tagged ✓", selected = true, onClick = { viewModel.toggleTag(contact, true) })
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                if (showBrowseSections) {
                    if (visibleSuggested.isNotEmpty()) {
                        item {
                            Text(
                                text = "SUGGESTED — FROM CALL HISTORY",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(visibleSuggested, key = { "suggested_${it.contact.contactId}" }) { suggestion: SuggestedContact ->
                            TeamSetupRow(contact = suggestion.contact, subtitle = "${suggestion.callCountThisMonth} calls this month") {
                                ToggleChip(text = "+ Tag", selected = false, onClick = { viewModel.toggleTag(suggestion.contact, false) })
                            }
                        }
                    }
                    item {
                        Text(
                            text = "ALL CONTACTS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(otherContacts, key = { "all_${it.contactId}" }) { contact: PickableContact ->
                        TeamSetupRow(contact = contact) {
                            ToggleChip(text = "+ Tag", selected = false, onClick = { viewModel.toggleTag(contact, false) })
                        }
                    }
                }
            }

            if (showAzRail) {
                val presentLetters = otherContacts
                    .mapNotNull { it.displayName.firstOrNull()?.uppercaseChar() }
                    .distinct()
                Column(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
                ) {
                    presentLetters.forEach { letter ->
                        Text(
                            text = letter.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable {
                                    val targetIndex = otherContacts.indexOfFirst {
                                        it.displayName.firstOrNull()?.uppercaseChar() == letter
                                    }
                                    if (targetIndex >= 0) {
                                        coroutineScope.launch {
                                            listState.scrollToItem(allContactsStartIndex + targetIndex)
                                        }
                                    }
                                }
                                .padding(2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamSetupRow(
    contact: PickableContact,
    subtitle: String? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoworkerAvatarBox(key = contact.phoneNumbersLast10.firstOrNull() ?: contact.displayName, initials = NameInitials.of(contact.displayName))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(contact.displayName, style = MaterialTheme.typography.bodyMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        trailing()
    }
}
```

This references a new `CoworkerAvatarBox` shared composable (the circular initials avatar used inline by `CoworkerRow` in Task 5, now needed standalone here too) — extract it in Step 2 below rather than duplicating the avatar `Box` markup.

- [ ] **Step 2: Extract the avatar circle into a reusable `CoworkerAvatarBox`, use it from `CoworkerRow` too**

Create `app/src/main/java/com/dailycallsreview/app/ui/common/CoworkerAvatarBox.kt`:

```kotlin
package com.dailycallsreview.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dailycallsreview.app.core.CoworkerAvatarColors

@Composable
fun CoworkerAvatarBox(key: String, initials: String, size: Dp = 32.dp) {
    val colors = CoworkerAvatarColors.colorFor(key)
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(Color(colors.background)),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = Color(colors.text), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}
```

In `app/src/main/java/com/dailycallsreview/app/ui/common/CoworkerRow.kt`, replace the avatar block:

```kotlin
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(colors.background)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = NameInitials.of(stat.displayName),
                color = Color(colors.text),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
```

with:

```kotlin
        CoworkerAvatarBox(key = stat.phoneNumberLast10, initials = NameInitials.of(stat.displayName))
```

and remove the now-unused `colors` local (`val colors = CoworkerAvatarColors.colorFor(stat.phoneNumberLast10)`) and its now-unused imports (`CoworkerAvatarColors`, `CircleShape`, `Color`, `Box`, `Alignment` may still be used elsewhere in the same file for the volume bar — check each import before removing; `Box`/`Alignment` are still used by the relative-volume-bar markup below the avatar, so only remove `CoworkerAvatarColors` and `CircleShape`/`Color` if nothing else in the file uses them, which the volume-bar code does use `Color`-free `Modifier.background(MaterialTheme.colorScheme...)` calls — confirm by compiling in Step 3).

- [ ] **Step 3: Build and verify**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/team/TeamSetupScreen.kt app/src/main/java/com/dailycallsreview/app/ui/common/CoworkerAvatarBox.kt app/src/main/java/com/dailycallsreview/app/ui/common/CoworkerRow.kt
git commit -m "feat: redesign TeamSetupScreen (search, suggestions, bulk select, edit mode, A-Z rail)"
```

---

## Task 14: SettingsScreen UI redesign

**Files:**
- Modify: `app/src/main/java/com/dailycallsreview/app/ui/settings/SettingsScreen.kt`

Implements: working-hours as two tappable time cards (still opens the native `TimePickerDialog` per spec §7.3/§9 — no custom picker), working days as a row of `DayCircle`s, and a holiday add-row + trash-icon list. **Note:** `WorkSchedule.holidays` is `Set<LocalDate>` only — no label is stored/read back anywhere in the existing data model (`addHoliday(date, label)` writes a label, but nothing reads it back out). The exploratory mockup showed a label per holiday row; this task shows only the date, matching what the app actually has available — adding label read-back would be a data-model change outside this UI-only task's scope.

- [ ] **Step 1: Replace the full file**

Replace the full contents of `app/src/main/java/com/dailycallsreview/app/ui/settings/SettingsScreen.kt` with:

```kotlin
package com.dailycallsreview.app.ui.settings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.ui.common.DayCircle
import com.dailycallsreview.app.ui.common.SummaryCard
import com.dailycallsreview.app.ui.nav.AppScaffold
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

@Composable
fun SettingsScreen(app: DailyCallsReviewApplication, navController: NavHostController) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(app.settingsRepository) }
        }
    )
    val schedule by viewModel.schedule.collectAsState()
    val context = LocalContext.current

    AppScaffold(title = "Settings", navController = navController) { paddingValues ->
        schedule?.let { current ->
            var holidayLabel by remember { mutableStateOf("") }
            var holidayDate by remember { mutableStateOf(LocalDate.now()) }

            LazyColumn(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
                item {
                    Text(
                        "WORKING HOURS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                item {
                    SummaryCard(modifier = Modifier.padding(bottom = 16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TimeCard(
                                label = "START",
                                time = current.workStart,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    TimePickerDialog(context, { _, hour, minute ->
                                        viewModel.saveWorkHours(LocalTime.of(hour, minute), current.workEnd, current.workingDays)
                                    }, current.workStart.hour, current.workStart.minute, false).show()
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            TimeCard(
                                label = "END",
                                time = current.workEnd,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    TimePickerDialog(context, { _, hour, minute ->
                                        viewModel.saveWorkHours(current.workStart, LocalTime.of(hour, minute), current.workingDays)
                                    }, current.workEnd.hour, current.workEnd.minute, false).show()
                                }
                            )
                        }
                    }
                }
                item {
                    Text(
                        "WORKING DAYS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                item {
                    SummaryCard(modifier = Modifier.padding(bottom = 16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                            DayOfWeek.entries.forEach { day ->
                                DayCircle(
                                    label = day.name.take(1),
                                    active = day in current.workingDays,
                                    onClick = {
                                        val newDays = if (day in current.workingDays) current.workingDays - day else current.workingDays + day
                                        viewModel.saveWorkHours(current.workStart, current.workEnd, newDays)
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        "HOLIDAYS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                item {
                    SummaryCard(modifier = Modifier.padding(bottom = 10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = holidayDate.toString(),
                                modifier = Modifier
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                                    .then(
                                        Modifier.clickableDatePicker(context, holidayDate) { holidayDate = it }
                                    ),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = holidayLabel,
                                onValueChange = { holidayLabel = it },
                                placeholder = { Text("Label") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.addHoliday(holidayDate, holidayLabel)
                                holidayLabel = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("+ Add Holiday") }
                    }
                }
                items(current.holidays.sorted(), key = { it.toString() }) { date ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(date.toString(), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { viewModel.removeHoliday(date) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove holiday", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeCard(label: String, time: LocalTime, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = "$label\n${time.format(TIME_FORMAT)}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
            .padding(10.dp)
            .then(Modifier.clickableSimple(onClick))
    )
}
```

- [ ] **Step 2: Add the two small click-helper `Modifier` extensions this file uses**

Rather than importing `androidx.compose.foundation.clickable` plus a stateful `remember`-based `DatePickerDialog` trigger inline twice, add two tiny private extensions at the bottom of the same file, `app/src/main/java/com/dailycallsreview/app/ui/settings/SettingsScreen.kt`:

```kotlin
private fun Modifier.clickableSimple(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))

private fun Modifier.clickableDatePicker(
    context: android.content.Context,
    current: LocalDate,
    onPicked: (LocalDate) -> Unit
): Modifier = this.then(
    androidx.compose.foundation.clickable {
        DatePickerDialog(context, { _, year, month, day ->
            onPicked(LocalDate.of(year, month + 1, day))
        }, current.year, current.monthValue - 1, current.dayOfMonth).show()
    }
)
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/settings/SettingsScreen.kt
git commit -m "feat: redesign SettingsScreen (time cards, day circles, holiday list)"
```

---

## Task 15: Pure utilities — HistoryGrouping and OffHoursBreakdown

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/core/HistoryGrouping.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/core/OffHoursBreakdown.kt`
- Test: `app/src/test/java/com/dailycallsreview/app/core/HistoryGroupingTest.kt`
- Test: `app/src/test/java/com/dailycallsreview/app/core/OffHoursBreakdownTest.kt`

These power the History/Range Detail week subtotals, summary strip, busiest-day/month callout, Year-mode month rows, and the "BY COWORKER" off-hours breakdown (spec §6).

- [ ] **Step 1: Write the failing tests for `HistoryGrouping`**

Create `app/src/test/java/com/dailycallsreview/app/core/HistoryGroupingTest.kt`:

```kotlin
package com.dailycallsreview.app.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HistoryGroupingTest {

    private fun day(date: LocalDate, totalCalls: Int, totalTalkTimeSeconds: Long = 0L) = DailySummary(
        date = date,
        totalCalls = totalCalls,
        totalTalkTimeSeconds = totalTalkTimeSeconds,
        perCoworker = emptyMap(),
        firstCallTime = null,
        lastCallTime = null,
        shiftSpanSeconds = null,
        offHoursCallCount = 0,
        isHoliday = false,
        isNonWorkingDay = false
    )

    @Test
    fun `groupByIsoWeek groups days into their ISO week and sums totals`() {
        val days = listOf(
            day(LocalDate.of(2026, 9, 14), 5, 300L), // Monday, week 38
            day(LocalDate.of(2026, 9, 18), 3, 200L), // Friday, week 38
            day(LocalDate.of(2026, 9, 21), 4, 100L)  // Monday, week 39
        )

        val groups = HistoryGrouping.groupByIsoWeek(days)

        assertEquals(2, groups.size)
        assertEquals(8, groups[0].totalCalls)
        assertEquals(500L, groups[0].totalTalkTimeSeconds)
        assertEquals(4, groups[1].totalCalls)
    }

    @Test
    fun `computePeriodStats averages only over days that had calls`() {
        val days = listOf(
            day(LocalDate.of(2026, 9, 14), 10),
            day(LocalDate.of(2026, 9, 15), 0),
            day(LocalDate.of(2026, 9, 16), 6)
        )

        val stats = HistoryGrouping.computePeriodStats(days)

        assertEquals(16, stats.totalCalls)
        assertEquals(8.0, stats.avgCallsPerActiveDay, 0.001)
    }

    @Test
    fun `computePeriodStats busiestDay is null when every day has zero calls`() {
        val days = listOf(day(LocalDate.of(2026, 9, 14), 0), day(LocalDate.of(2026, 9, 15), 0))

        val stats = HistoryGrouping.computePeriodStats(days)

        assertEquals(null, stats.busiestDay)
        assertEquals(0.0, stats.avgCallsPerActiveDay, 0.001)
    }

    @Test
    fun `computePeriodStats busiestDay picks the day with the most calls`() {
        val busiest = day(LocalDate.of(2026, 9, 16), 12)
        val days = listOf(day(LocalDate.of(2026, 9, 14), 3), busiest, day(LocalDate.of(2026, 9, 15), 5))

        val stats = HistoryGrouping.computePeriodStats(days)

        assertEquals(busiest, stats.busiestDay)
    }

    @Test
    fun `groupByMonth groups days into calendar months sorted most recent first`() {
        val days = listOf(
            day(LocalDate.of(2026, 7, 10), 5),
            day(LocalDate.of(2026, 9, 1), 3),
            day(LocalDate.of(2026, 9, 30), 4)
        )

        val buckets = HistoryGrouping.groupByMonth(days)

        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 7)), buckets.map { it.month })
        assertEquals(7, buckets[0].totalCalls)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dailycallsreview.app.core.HistoryGroupingTest" --console=plain`
Expected: FAIL — `HistoryGrouping` unresolved reference

- [ ] **Step 3: Implement `HistoryGrouping`**

Create `app/src/main/java/com/dailycallsreview/app/core/HistoryGrouping.kt`:

```kotlin
package com.dailycallsreview.app.core

import java.time.YearMonth
import java.time.temporal.IsoFields

data class WeekGroup(
    val weekOfYear: Int,
    val days: List<DailySummary>,
    val totalCalls: Int,
    val totalTalkTimeSeconds: Long
)

data class PeriodStats(
    val totalCalls: Int,
    val totalTalkTimeSeconds: Long,
    val avgCallsPerActiveDay: Double,
    val busiestDay: DailySummary?
)

data class MonthBucket(val month: YearMonth, val totalCalls: Int, val totalTalkTimeSeconds: Long)

object HistoryGrouping {

    fun groupByIsoWeek(days: List<DailySummary>): List<WeekGroup> =
        days
            .groupBy { it.date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) }
            .toSortedMap()
            .map { (week, group) ->
                WeekGroup(
                    weekOfYear = week,
                    days = group,
                    totalCalls = group.sumOf { it.totalCalls },
                    totalTalkTimeSeconds = group.sumOf { it.totalTalkTimeSeconds }
                )
            }

    // avgCallsPerActiveDay divides by days-with-calls, not raw calendar days — matches how
    // RangeSummary.averageShiftSpanSeconds already excludes inactive days (spec section 8).
    fun computePeriodStats(days: List<DailySummary>): PeriodStats {
        val activeDays = days.filter { it.totalCalls > 0 }
        val totalCalls = days.sumOf { it.totalCalls }
        val totalTalk = days.sumOf { it.totalTalkTimeSeconds }
        val avg = if (activeDays.isEmpty()) 0.0 else totalCalls.toDouble() / activeDays.size
        val busiest = days.maxByOrNull { it.totalCalls }?.takeIf { it.totalCalls > 0 }
        return PeriodStats(totalCalls, totalTalk, avg, busiest)
    }

    fun groupByMonth(days: List<DailySummary>): List<MonthBucket> =
        days
            .groupBy { YearMonth.from(it.date) }
            .map { (month, group) -> MonthBucket(month, group.sumOf { it.totalCalls }, group.sumOf { it.totalTalkTimeSeconds }) }
            .sortedByDescending { it.month }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dailycallsreview.app.core.HistoryGroupingTest" --console=plain`
Expected: PASS (5 tests)

- [ ] **Step 5: Write the failing tests for `OffHoursBreakdown`**

Create `app/src/test/java/com/dailycallsreview/app/core/OffHoursBreakdownTest.kt`:

```kotlin
package com.dailycallsreview.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class OffHoursBreakdownTest {

    private fun call(number: String, name: String?) = CallRecord(
        phoneNumberLast10 = number,
        contactName = name,
        timestampMillis = 0L,
        durationSeconds = 30,
        type = CallType.ANSWERED
    )

    @Test
    fun `groups calls by coworker and counts them`() {
        val calls = listOf(
            call("1111111111", "Kiran"),
            call("1111111111", "Kiran"),
            call("2222222222", "Rahul")
        )

        val result = OffHoursBreakdown.groupByCoworker(calls)

        assertEquals(2, result.size)
        assertEquals("Kiran", result[0].displayName)
        assertEquals(2, result[0].callCount)
    }

    @Test
    fun `sorts by call count descending`() {
        val calls = listOf(call("1111111111", "A"), call("2222222222", "B"), call("2222222222", "B"))

        val result = OffHoursBreakdown.groupByCoworker(calls)

        assertEquals("B", result.first().displayName)
    }

    @Test
    fun `falls back to phone number when no contact name is present in any matching call`() {
        val calls = listOf(call("1111111111", null))

        val result = OffHoursBreakdown.groupByCoworker(calls)

        assertEquals("1111111111", result.single().displayName)
    }

    @Test
    fun `prefers the most recent non-null contact name`() {
        val calls = listOf(call("1111111111", "Old Name"), call("1111111111", null))

        val result = OffHoursBreakdown.groupByCoworker(calls)

        assertEquals("Old Name", result.single().displayName)
    }
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dailycallsreview.app.core.OffHoursBreakdownTest" --console=plain`
Expected: FAIL — `OffHoursBreakdown` unresolved reference

- [ ] **Step 7: Implement `OffHoursBreakdown`**

Create `app/src/main/java/com/dailycallsreview/app/core/OffHoursBreakdown.kt`:

```kotlin
package com.dailycallsreview.app.core

data class OffHoursCoworkerCount(val phoneNumberLast10: String, val displayName: String, val callCount: Int)

object OffHoursBreakdown {
    // displayName resolution mirrors CallAggregator.computeDailySummary: the most recent
    // non-null contactName seen for that number, falling back to the raw number.
    fun groupByCoworker(calls: List<CallRecord>): List<OffHoursCoworkerCount> =
        calls
            .groupBy { it.phoneNumberLast10 }
            .map { (number, group) ->
                val name = group.mapNotNull { it.contactName }.lastOrNull() ?: number
                OffHoursCoworkerCount(number, name, group.size)
            }
            .sortedByDescending { it.callCount }
}
```

- [ ] **Step 8: Run it to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dailycallsreview.app.core.OffHoursBreakdownTest" --console=plain`
Expected: PASS (4 tests)

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/core/HistoryGrouping.kt app/src/main/java/com/dailycallsreview/app/core/OffHoursBreakdown.kt app/src/test/java/com/dailycallsreview/app/core/HistoryGroupingTest.kt app/src/test/java/com/dailycallsreview/app/core/OffHoursBreakdownTest.kt
git commit -m "feat: add HistoryGrouping and OffHoursBreakdown pure utilities"
```

---

## Task 16: HistoryViewModel — add YEAR mode

**Files:**
- Modify: `app/src/main/java/com/dailycallsreview/app/ui/history/HistoryViewModel.kt`

Adding `YEAR` reuses the exact same day-by-day loop `loadRange` already runs for `MONTH`/`WEEK` — it only needs a new `rangeStart`/`rangeEnd` case (Jan 1 → Dec 31 of the anchor year). Filters (`ALL`/`OFF_DAYS`/`HOLIDAYS`/`OUTSIDE_HOURS`) already apply generically regardless of `viewMode`, so no other change is needed here — Year's month-row grouping happens in the UI layer via `HistoryGrouping.groupByMonth` (Task 15), consumed in Task 18.

- [ ] **Step 1: Add `YEAR` to the enum**

In `app/src/main/java/com/dailycallsreview/app/ui/history/HistoryViewModel.kt`, replace:

```kotlin
enum class HistoryViewMode { MONTH, WEEK }
```

with:

```kotlin
enum class HistoryViewMode { MONTH, WEEK, YEAR }
```

- [ ] **Step 2: Add the YEAR case to `loadRange`'s range computation**

In the same file, inside `private suspend fun loadRange(params: LoadParams)`, replace:

```kotlin
        val rangeStart = when (params.mode) {
            HistoryViewMode.MONTH -> params.anchor.withDayOfMonth(1)
            HistoryViewMode.WEEK -> params.anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        val rangeEnd = when (params.mode) {
            HistoryViewMode.MONTH -> params.anchor.withDayOfMonth(params.anchor.lengthOfMonth())
            HistoryViewMode.WEEK -> rangeStart.plusDays(6)
        }
```

with:

```kotlin
        val rangeStart = when (params.mode) {
            HistoryViewMode.MONTH -> params.anchor.withDayOfMonth(1)
            HistoryViewMode.WEEK -> params.anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            HistoryViewMode.YEAR -> params.anchor.withDayOfYear(1)
        }
        val rangeEnd = when (params.mode) {
            HistoryViewMode.MONTH -> params.anchor.withDayOfMonth(params.anchor.lengthOfMonth())
            HistoryViewMode.WEEK -> rangeStart.plusDays(6)
            HistoryViewMode.YEAR -> params.anchor.withDayOfYear(params.anchor.lengthOfYear())
        }
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`

Run: `./gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL` (no existing test exercises `HistoryViewModel` directly — it's `ContentProvider`/Android-`Flow` dependent like the rest of the ViewModels, consistent with the project's existing convention of not unit-testing that layer directly; this change is verified by compile plus Task 20's manual pass)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/history/HistoryViewModel.kt
git commit -m "feat: add YEAR view mode to HistoryViewModel"
```

---

## Task 17: Shared components — DayCard, WeekSubtotalHeader, OffHoursBreakdownList

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/core/DurationFormat.kt`
- Test: `app/src/test/java/com/dailycallsreview/app/core/DurationFormatTest.kt`
- Modify: `app/src/main/java/com/dailycallsreview/app/ui/common/DailySummaryCard.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/common/DayCard.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/common/WeekSubtotalHeader.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/common/OffHoursBreakdownList.kt`

These three are used by both `HistoryScreen` (Task 18) and `RangeDetailScreen` (Task 19), so they're built once here rather than duplicated.

- [ ] **Step 1: Write the failing test for the shared duration formatter**

Create `app/src/test/java/com/dailycallsreview/app/core/DurationFormatTest.kt`:

```kotlin
package com.dailycallsreview.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {

    @Test
    fun `formats hours and minutes`() {
        assertEquals("2h 5m", formatHoursMinutes(7500L))
    }

    @Test
    fun `formats zero seconds`() {
        assertEquals("0h 0m", formatHoursMinutes(0L))
    }

    @Test
    fun `formats less than an hour`() {
        assertEquals("0h 45m", formatHoursMinutes(2700L))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dailycallsreview.app.core.DurationFormatTest" --console=plain`
Expected: FAIL — `formatHoursMinutes` unresolved reference

- [ ] **Step 3: Implement it, then use it from `DailySummaryCard` too (removes the duplicate private copy)**

Create `app/src/main/java/com/dailycallsreview/app/core/DurationFormat.kt`:

```kotlin
package com.dailycallsreview.app.core

fun formatHoursMinutes(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return "${hours}h ${minutes}m"
}
```

In `app/src/main/java/com/dailycallsreview/app/ui/common/DailySummaryCard.kt`, remove the now-duplicate private function:

```kotlin
private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return "${hours}h ${minutes}m"
}
```

and replace its one call site:

```kotlin
                    text = "Shift span: ${summary.shiftSpanSeconds?.let { formatDuration(it) } ?: "—"}",
```

with:

```kotlin
                    text = "Shift span: ${summary.shiftSpanSeconds?.let { com.dailycallsreview.app.core.formatHoursMinutes(it) } ?: "—"}",
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dailycallsreview.app.core.DurationFormatTest" --console=plain`
Expected: PASS (3 tests)

- [ ] **Step 5: Create `DayCard`**

Create `app/src/main/java/com/dailycallsreview/app/ui/common/DayCard.kt`:

```kotlin
package com.dailycallsreview.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailycallsreview.app.core.DailySummary
import com.dailycallsreview.app.core.formatHoursMinutes
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

@Composable
fun DayCard(day: DailySummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isInactive = day.isHoliday || day.isNonWorkingDay
    val textColor = if (isInactive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = day.date.format(DATE_FORMAT),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            val subtitle = when {
                day.isHoliday -> "Holiday"
                day.isNonWorkingDay -> "Off day"
                day.firstCallTime != null && day.lastCallTime != null ->
                    "${day.firstCallTime.format(TIME_FORMAT)} – ${day.lastCallTime.format(TIME_FORMAT)}" +
                        (day.shiftSpanSeconds?.let { " · ${formatHoursMinutes(it)}" } ?: "")
                else -> "—"
            }
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (!isInactive) {
                Text(
                    text = "${day.totalCalls} calls",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (day.offHoursCallCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    WarningBadge("⚠ ${day.offHoursCallCount} off-hours")
                }
            } else {
                Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
```

- [ ] **Step 6: Create `WeekSubtotalHeader`**

Create `app/src/main/java/com/dailycallsreview/app/ui/common/WeekSubtotalHeader.kt`:

```kotlin
package com.dailycallsreview.app.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailycallsreview.app.core.WeekGroup
import com.dailycallsreview.app.core.formatHoursMinutes

@Composable
fun WeekSubtotalHeader(week: WeekGroup, modifier: Modifier = Modifier) {
    Text(
        text = "WEEK ${week.weekOfYear} · ${week.totalCalls} calls · ${formatHoursMinutes(week.totalTalkTimeSeconds)}",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}
```

- [ ] **Step 7: Create `OffHoursBreakdownList`**

Create `app/src/main/java/com/dailycallsreview/app/ui/common/OffHoursBreakdownList.kt`:

```kotlin
package com.dailycallsreview.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailycallsreview.app.core.CallRecord
import com.dailycallsreview.app.core.NameInitials
import com.dailycallsreview.app.core.OffHoursBreakdown
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CALL_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")

@Composable
fun OffHoursBreakdownList(calls: List<CallRecord>, modifier: Modifier = Modifier) {
    var selectedNumber by remember(calls) { mutableStateOf<String?>(null) }
    val breakdown = remember(calls) { OffHoursBreakdown.groupByCoworker(calls) }
    val visibleCalls = if (selectedNumber == null) calls else calls.filter { it.phoneNumberLast10 == selectedNumber }

    Column(modifier = modifier) {
        if (breakdown.size > 1) {
            Text(
                text = "BY COWORKER",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SummaryCard(contentPadding = 0.dp, modifier = Modifier.padding(bottom = 14.dp)) {
                breakdown.forEachIndexed { index, entry ->
                    val isSelected = selectedNumber == entry.phoneNumberLast10
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedNumber = if (isSelected) null else entry.phoneNumberLast10 }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoworkerAvatarBox(key = entry.phoneNumberLast10, initials = NameInitials.of(entry.displayName), size = 26.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = entry.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        Text(
                            text = "${entry.callCount} calls",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (index != breakdown.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        Text(
            text = "ALL OFF-HOURS CALLS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        SummaryCard(contentPadding = 0.dp) {
            visibleCalls.forEachIndexed { index, call ->
                val time = Instant.ofEpochMilli(call.timestampMillis).atZone(ZoneId.systemDefault())
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoworkerAvatarBox(
                            key = call.phoneNumberLast10,
                            initials = NameInitials.of(call.contactName ?: call.phoneNumberLast10),
                            size = 26.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(call.contactName ?: call.phoneNumberLast10, style = MaterialTheme.typography.bodyMedium)
                            Text(time.format(CALL_TIME_FORMAT), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("${call.durationSeconds / 60}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (index != visibleCalls.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
```

- [ ] **Step 8: Build and verify**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/core/DurationFormat.kt app/src/test/java/com/dailycallsreview/app/core/DurationFormatTest.kt app/src/main/java/com/dailycallsreview/app/ui/common/DailySummaryCard.kt app/src/main/java/com/dailycallsreview/app/ui/common/DayCard.kt app/src/main/java/com/dailycallsreview/app/ui/common/WeekSubtotalHeader.kt app/src/main/java/com/dailycallsreview/app/ui/common/OffHoursBreakdownList.kt
git commit -m "feat: add DayCard, WeekSubtotalHeader, OffHoursBreakdownList shared components"
```

---

## Task 18: HistoryScreen full redesign

**Files:**
- Modify: `app/src/main/java/com/dailycallsreview/app/ui/history/HistoryScreen.kt`

Assembles everything built so far: segmented Month/Week/Year/Custom control, period navigation, the summary strip (`HistoryGrouping.computePeriodStats`), week-subtotal headers in Month mode, restyled filter chips, `DayCard` list, Year mode's month rows (`HistoryGrouping.groupByMonth`, tap to drill into that month), and `OffHoursBreakdownList` for the Outside Hours filter — per spec §7.4.

- [ ] **Step 1: Replace the full file**

Replace the full contents of `app/src/main/java/com/dailycallsreview/app/ui/history/HistoryScreen.kt` with:

```kotlin
package com.dailycallsreview.app.ui.history

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.core.HistoryGrouping
import com.dailycallsreview.app.core.formatHoursMinutes
import com.dailycallsreview.app.ui.common.DayCard
import com.dailycallsreview.app.ui.common.OffHoursBreakdownList
import com.dailycallsreview.app.ui.common.StatTile
import com.dailycallsreview.app.ui.common.SummaryCard
import com.dailycallsreview.app.ui.common.ToggleChip
import com.dailycallsreview.app.ui.common.WeekSubtotalHeader
import com.dailycallsreview.app.ui.export.CsvExporter
import com.dailycallsreview.app.ui.nav.AppScaffold
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun HistoryScreen(
    app: DailyCallsReviewApplication,
    navController: NavHostController,
    onOpenDay: (LocalDate) -> Unit,
    onOpenRange: (LocalDate, LocalDate) -> Unit
) {
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HistoryViewModel(app.callLogRepository, app.teamRepository, app.settingsRepository) }
        }
    )
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    AppScaffold(
        title = "History",
        navController = navController,
        actions = {
            IconButton(onClick = {
                if (state.filter == HistoryFilter.OUTSIDE_HOURS) {
                    CsvExporter.exportCallsAndShare(context, "history_export.csv", state.offHoursCalls, ZoneId.systemDefault())
                } else {
                    CsvExporter.exportDailySummariesAndShare(context, "history_export.csv", state.days)
                }
            }) {
                Icon(Icons.Filled.Download, contentDescription = "Export CSV")
            }
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues).padding(horizontal = 16.dp)) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    listOf(
                        HistoryViewMode.MONTH to "Month",
                        HistoryViewMode.WEEK to "Week",
                        HistoryViewMode.YEAR to "Year"
                    ).forEach { (mode, label) ->
                        ToggleChip(
                            text = label,
                            selected = state.viewMode == mode,
                            onClick = { viewModel.setViewMode(mode) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    ToggleChip(
                        text = "Custom",
                        selected = false,
                        onClick = {
                            DatePickerDialog(context, { _, y1, m1, d1 ->
                                val start = LocalDate.of(y1, m1 + 1, d1)
                                DatePickerDialog(context, { _, y2, m2, d2 ->
                                    onOpenRange(start, LocalDate.of(y2, m2 + 1, d2))
                                }, start.year, start.monthValue - 1, start.dayOfMonth).show()
                            }, state.anchorDate.year, state.anchorDate.monthValue - 1, state.anchorDate.dayOfMonth).show()
                        }
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.setAnchorDate(shiftAnchor(state.viewMode, state.anchorDate, forward = false)) }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous")
                    }
                    Text(
                        text = periodLabel(state.viewMode, state.anchorDate),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { viewModel.setAnchorDate(shiftAnchor(state.viewMode, state.anchorDate, forward = true)) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next")
                    }
                }
            }
            item {
                val stats = HistoryGrouping.computePeriodStats(state.days)
                SummaryCard(modifier = Modifier.padding(bottom = 10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatTile(value = stats.totalCalls.toString(), label = "Calls", modifier = Modifier.weight(1f))
                        StatTile(value = formatHoursMinutes(stats.totalTalkTimeSeconds), label = "Talk time", modifier = Modifier.weight(1f))
                        StatTile(value = String.format("%.1f", stats.avgCallsPerActiveDay), label = "Avg/day", modifier = Modifier.weight(1f))
                    }
                    if (stats.busiestDay != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "🏆 Busiest: ${stats.busiestDay.date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))} (${stats.busiestDay.totalCalls} calls)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    HistoryFilter.entries.forEach { filterOption ->
                        ToggleChip(
                            text = filterLabel(filterOption),
                            selected = state.filter == filterOption,
                            onClick = { viewModel.setFilter(filterOption) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }

            if (state.filter == HistoryFilter.OUTSIDE_HOURS) {
                item { OffHoursBreakdownList(state.offHoursCalls) }
            } else if (state.viewMode == HistoryViewMode.YEAR) {
                val months = HistoryGrouping.groupByMonth(state.days)
                items(months, key = { it.month.toString() }) { bucket ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setViewMode(HistoryViewMode.MONTH)
                                viewModel.setAnchorDate(bucket.month.atDay(1))
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = bucket.month.format(DateTimeFormatter.ofPattern("MMMM")),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${bucket.totalCalls} calls",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(formatHoursMinutes(bucket.totalTalkTimeSeconds), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else if (state.viewMode == HistoryViewMode.MONTH) {
                val weekGroups = HistoryGrouping.groupByIsoWeek(state.days).reversed()
                weekGroups.forEach { week ->
                    item(key = "week_${week.weekOfYear}") { WeekSubtotalHeader(week) }
                    items(week.days.sortedByDescending { it.date }, key = { "day_${it.date}" }) { day ->
                        DayCard(day = day, onClick = { onOpenDay(day.date) })
                    }
                }
            } else {
                items(state.days.sortedByDescending { it.date }, key = { it.date.toString() }) { day ->
                    DayCard(day = day, onClick = { onOpenDay(day.date) })
                }
            }
        }
    }
}

private fun shiftAnchor(mode: HistoryViewMode, anchor: LocalDate, forward: Boolean): LocalDate {
    val sign = if (forward) 1L else -1L
    return when (mode) {
        HistoryViewMode.MONTH -> anchor.plusMonths(sign)
        HistoryViewMode.WEEK -> anchor.plusWeeks(sign)
        HistoryViewMode.YEAR -> anchor.plusYears(sign)
    }
}

private fun periodLabel(mode: HistoryViewMode, anchor: LocalDate): String = when (mode) {
    HistoryViewMode.MONTH -> anchor.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    HistoryViewMode.WEEK -> {
        val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        "${monday.format(DateTimeFormatter.ofPattern("MMM d"))} – ${monday.plusDays(6).format(DateTimeFormatter.ofPattern("MMM d"))}"
    }
    HistoryViewMode.YEAR -> anchor.year.toString()
}

private fun filterLabel(filter: HistoryFilter): String = when (filter) {
    HistoryFilter.ALL -> "All"
    HistoryFilter.OFF_DAYS -> "Off Days"
    HistoryFilter.HOLIDAYS -> "Holidays"
    HistoryFilter.OUTSIDE_HOURS -> "Outside Hours"
}
```

- [ ] **Step 2: Build and verify**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/history/HistoryScreen.kt
git commit -m "feat: redesign HistoryScreen (Year mode, summary strip, week subtotals, off-hours breakdown)"
```

---

## Task 19: RangeDetailScreen full redesign

**Files:**
- Modify: `app/src/main/java/com/dailycallsreview/app/ui/rangedetail/RangeDetailScreen.kt`

Replaces the plain-text `RangeSummary` dump with a `SummaryCard` (Total Calls, Total Talk Time, Avg Shift Span, Busiest Coworker `StatTile`s + holiday/off-day line + `WarningBadge`) and a `CoworkerRow` list, and reuses `DayCard` / `OffHoursBreakdownList` from Task 17 for the other filters — per spec §7.5.

- [ ] **Step 1: Replace the full file**

Replace the full contents of `app/src/main/java/com/dailycallsreview/app/ui/rangedetail/RangeDetailScreen.kt` with:

```kotlin
package com.dailycallsreview.app.ui.rangedetail

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.core.formatHoursMinutes
import com.dailycallsreview.app.ui.common.CoworkerRow
import com.dailycallsreview.app.ui.common.DayCard
import com.dailycallsreview.app.ui.common.OffHoursBreakdownList
import com.dailycallsreview.app.ui.common.StatTile
import com.dailycallsreview.app.ui.common.SummaryCard
import com.dailycallsreview.app.ui.common.ToggleChip
import com.dailycallsreview.app.ui.common.WarningBadge
import com.dailycallsreview.app.ui.export.CsvExporter
import com.dailycallsreview.app.ui.history.HistoryFilter
import com.dailycallsreview.app.ui.nav.AppScaffold
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TITLE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun RangeDetailScreen(
    app: DailyCallsReviewApplication,
    navController: NavHostController,
    startDate: LocalDate,
    endDate: LocalDate,
    onOpenDay: (LocalDate) -> Unit
) {
    val viewModel: RangeDetailViewModel = viewModel(
        key = "$startDate-$endDate",
        factory = viewModelFactory {
            initializer {
                RangeDetailViewModel(startDate, endDate, app.callLogRepository, app.teamRepository, app.settingsRepository)
            }
        }
    )
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    AppScaffold(
        title = "${startDate.format(TITLE_FORMAT)} – ${endDate.format(TITLE_FORMAT)}",
        navController = navController,
        showBackButton = true,
        actions = {
            IconButton(onClick = {
                when (state.filter) {
                    HistoryFilter.ALL -> state.summary?.let {
                        CsvExporter.exportRangeAndShare(context, "range_export.csv", it)
                    }
                    HistoryFilter.OUTSIDE_HOURS -> CsvExporter.exportCallsAndShare(
                        context, "range_export.csv", state.offHoursCalls, ZoneId.systemDefault()
                    )
                    else -> CsvExporter.exportDailySummariesAndShare(context, "range_export.csv", state.filteredDays)
                }
            }) {
                Icon(Icons.Filled.Download, contentDescription = "Export CSV")
            }
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues).padding(horizontal = 16.dp)) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    HistoryFilter.entries.forEach { filterOption ->
                        ToggleChip(
                            text = filterLabel(filterOption),
                            selected = state.filter == filterOption,
                            onClick = { viewModel.setFilter(filterOption) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }

            when (state.filter) {
                HistoryFilter.ALL -> {
                    val summary = state.summary
                    if (summary == null) {
                        item { Text("Loading…") }
                    } else {
                        item {
                            SummaryCard(modifier = Modifier.padding(bottom = 14.dp)) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    StatTile(value = summary.totalCalls.toString(), label = "Total calls", modifier = Modifier.weight(1f))
                                    StatTile(value = formatHoursMinutes(summary.totalTalkTimeSeconds), label = "Total talk time", modifier = Modifier.weight(1f))
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    StatTile(
                                        value = summary.averageShiftSpanSeconds?.let { formatHoursMinutes(it) } ?: "—",
                                        label = "Avg shift span",
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatTile(
                                        value = summary.busiestCoworker?.displayName ?: "—",
                                        label = "Busiest coworker",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "🎉 ${summary.holidayCount} holiday(s) · Off days: ${summary.nonWorkingDayCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (summary.offHoursCallCount > 0) {
                                        WarningBadge("⚠ ${summary.offHoursCallCount} outside hours")
                                    }
                                }
                            }
                        }
                        val sortedStats = summary.perCoworker.values.sortedByDescending { it.callCount }
                        if (sortedStats.isNotEmpty()) {
                            item {
                                Text(
                                    text = "PER COWORKER",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            item {
                                val maxCallCount = sortedStats.maxOf { it.callCount }
                                SummaryCard(contentPadding = 0.dp) {
                                    sortedStats.forEachIndexed { index, stat ->
                                        CoworkerRow(stat = stat, maxCallCount = maxCallCount)
                                        if (index != sortedStats.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
                HistoryFilter.OUTSIDE_HOURS -> item { OffHoursBreakdownList(state.offHoursCalls) }
                else -> items(state.filteredDays.sortedByDescending { it.date }, key = { it.date.toString() }) { day ->
                    DayCard(day = day, onClick = { onOpenDay(day.date) })
                }
            }
        }
    }
}

private fun filterLabel(filter: HistoryFilter): String = when (filter) {
    HistoryFilter.ALL -> "All"
    HistoryFilter.OFF_DAYS -> "Off Days"
    HistoryFilter.HOLIDAYS -> "Holidays"
    HistoryFilter.OUTSIDE_HOURS -> "Outside Hours"
}
```

- [ ] **Step 2: Build and verify**

Run: `./gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run the full unit test suite**

Run: `./gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL` — every test added across Tasks 3, 5, 10, 15, 17 plus the original `CallAggregatorTest`/`PhoneNumberNormalizerTest`/`WorkingDaysMaskTest` all pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/rangedetail/RangeDetailScreen.kt
git commit -m "feat: redesign RangeDetailScreen (SummaryCard, CoworkerRow, shared filter views)"
```

---

## Task 20: Manual end-to-end verification on a real device

**Files:** none (verification only)

No Compose UI test harness exists in this project (confirmed at plan-writing time — zero `androidx.compose.ui.test` usage anywhere), so this redesign is verified the same way the original 20-task build's Task 20 was: installed and driven on a real device against real call-log/contacts data, not synthetic/emulator data. Install the freshly built debug APK and work through this checklist, noting any deviation before moving to Task 21.

- [ ] **Step 1: Build and install**

Run: `./gradlew.bat :app:installDebug --console=plain`
Expected: `BUILD SUCCESSFUL`, app installs without error.

- [ ] **Step 2: Visual/branding pass**
  - Launcher icon shows the purple gradient with two white avatar circles and a phone badge (Task 6) — confirm it doesn't look clipped/cut off on the home screen.
  - Open the app: top app bar shows "Daily Calls Review" + Refresh icon on Home; bottom nav shows Home/History/Team/Settings with the current tab highlighted.
  - Toggle the phone's system dark mode: every screen should switch palettes (dark background `#14151C`, dark surfaces `#1E2030`, lighter purple `#8B6CFF` primary) without any screen staying stuck in the wrong theme or showing unreadable (e.g. white-on-white) text.

- [ ] **Step 3: Home screen**
  - Stat tiles (Calls/Talk Time/First Call/Last Call) show correct values matching today's actual tagged-coworker call history.
  - Off-hours warning badge appears only when today has calls outside configured working hours.
  - Per-coworker list is sorted busiest-first, each row's avatar color is visually distinct and consistent.
  - Tap Refresh — values update after making/receiving a real call.
  - Tap each bottom nav tab (History/Team/Settings) — confirm each opens directly, and tapping Home from any of them returns without a back-stack pile-up (repeatedly switching tabs shouldn't grow the back stack — verify system back from any tab goes to app exit, not through every previously-visited tab).

- [ ] **Step 4: Team Setup screen**
  - Search box filters Tagged, Suggested, and All Contacts sections live, case-insensitively.
  - "Suggested" section shows real untagged contacts you've actually called this calendar month, ranked by call count, with an accurate "N calls this month" label; confirm it's empty (not broken) on the 1st of a month or if you haven't called anyone untagged yet.
  - Tap "+ Tag" on a Suggested or All Contacts row — it moves to the Tagged section (fixed-height box, scrolls internally once it overflows ~3 rows) and disappears from Suggested/All Contacts.
  - Tap "Edit" next to Tagged — Suggested/All Contacts/search/bulk-action rows hide; each tagged row shows a red minus button; tapping it untags and the row disappears; tap "Done" to exit edit mode and confirm the rest of the screen reappears.
  - "Select all" tags every currently-visible (search-filtered) untagged contact; "Clear all" untags every currently-visible tagged contact. Test both with and without an active search query to confirm the "visible-only" scoping (spec §8).
  - With search blank, the A–Z rail appears on the right edge; tapping a letter scrolls straight to that letter's first contact in All Contacts. Typing anything into search hides the rail.

- [ ] **Step 5: Settings screen**
  - Tapping either time card opens the native Android time picker; picking a new time updates that card's displayed value immediately.
  - Tapping a day circle toggles it between filled (working day) and gray (non-working day) and persists across leaving/returning to the screen.
  - Adding a holiday (date + label + "+ Add Holiday") adds a new row to the list below with a working trash icon that removes it.
  - Rapidly tap multiple day circles in quick succession — confirm no edit is lost (this app previously had a real rapid-edit data-loss bug here; the fix is unrelated to this redesign but the UI must not reintroduce it).

- [ ] **Step 6: History screen**
  - Month/Week/Year segmented control switches correctly; period label and ‹ › navigation update to the right month/week/year.
  - Summary strip (Calls/Talk Time/Avg per day) and the busiest-day callout show numbers consistent with manually counting the visible day cards.
  - In Month view, week-subtotal headers appear between ISO weeks with correct sums; days render most-recent-first within each week.
  - Off Days/Holidays filters restyle correctly (grayed-out day cards where applicable).
  - Outside Hours filter shows the "BY COWORKER" breakdown with correct per-person counts, and the flat call list below; tapping a coworker narrows the call list to just their calls, tapping again (or switching away and back to the filter) clears it.
  - Year mode shows a yearly summary strip and 12 month rows (empty months included, sorted most-recent-first); tapping a month switches to Month view anchored on that month.
  - "Custom range" still opens the two-date native picker flow and navigates to Range Detail.
  - Export CSV (now a top-app-bar icon) still produces a correct file for both the day-list and Outside-Hours-call-list cases.

- [ ] **Step 7: Range Detail and Day Detail**
  - Open Range Detail via History's Custom range — back arrow in the app bar returns to History; title shows the date range.
  - SummaryCard shows Total Calls/Talk Time/Avg Shift Span/Busiest Coworker correctly, plus the holiday/off-day line and off-hours badge when applicable.
  - Per-coworker list matches Home's visual style.
  - Switching Range Detail's filter chips shows the same DayCard list / off-hours breakdown as History.
  - Open Day Detail (tap any day card) — back arrow works, content matches Home's `DailySummaryCard` styling exactly.

- [ ] **Step 8: Regression checks (must be unaffected by this redesign)**
  - Revoke and re-grant call log/contacts permissions from system settings — confirm the permission gate still works exactly as before (this redesign didn't touch `PermissionsGate`/`RequiredPermissions`).
  - Confirm the home-screen widget still shows today's stats and updates after a manual refresh (this redesign doesn't touch `TodayWidget.kt` at all — Glance widgets can't render the new Compose UI components, so verify it still works, not that it changed).
  - Make a real call to a tagged coworker mid-session (app open, then backgrounded) — confirm totals update correctly on return, matching the original app's proven behavior.

- [ ] **Step 9: Record results**

Append a "Results" section to the bottom of this plan file (`docs/superpowers/plans/2026-09-19-ui-redesign.md`) summarizing what was verified, the device/Android version used, and any deviations found — matching how the original 20-task plan recorded its Task 20 results.

---

## Task 21: Final holistic review and finish the branch

**Files:** none (review + branch completion only)

- [ ] **Step 1: Run the full test suite one more time**

Run: `./gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Request a code-quality review of the whole branch**

Use the `superpowers:requesting-code-review` skill (or dispatch a `superpowers:code-reviewer` subagent) comparing the branch tip against the commit before Task 1, looking specifically for: any screen that silently regressed a behavior locked in by the original spec (phone-number matching, MISSED+REJECTED counted/BLOCKED excluded, filters scoping to the visible range, CSV export correctness), any duplicated logic that should have used one of this plan's shared components/utilities instead, and any place the "avatar color key" or "A–Z rail" deviations noted in this plan's header were implemented inconsistently between screens.

- [ ] **Step 3: Fix any findings, re-run tests**

Run: `./gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Finish the branch**

Use the `superpowers:finishing-a-development-branch` skill to present merge/PR/keep/discard options and carry out whichever the user picks.

---
