# Daily Calls Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Daily Calls Review Android app per `docs/superpowers/specs/2026-09-16-daily-calls-review-design.md` — auto-read call log, track tagged coworkers, daily/weekly/monthly/range summaries, off-hours flagging, CSV export, home screen widget.

**Architecture:** Kotlin + Jetpack Compose, MVVM. Call data is never duplicated locally — every screen queries `CallLog.Calls` on demand with a date-bounded selection. Room stores only tagged contacts, settings, and holidays. Aggregation logic (`CallAggregator`) is pure/testable, independent of Android framework classes.

**Tech Stack:** Kotlin 1.9.24, AGP 8.5.2, Jetpack Compose (BOM 2024.06.00), Room 2.6.1 (KSP), Navigation-Compose 2.7.7, WorkManager 2.9.1, Jetpack Glance 1.1.1, JUnit 4, minSdk 26 (chosen so `java.time` is available without desugaring), compileSdk/targetSdk 34.

**Prerequisites:** Android Studio (or Android SDK cmdline-tools + JDK 17) must be installed on the machine that builds/runs this project — this plan's code cannot be compiled or run from a shell without the Android SDK. Steps that say "Run: `./gradlew ...`" assume that environment.

**Package:** `com.dailycallsreview.app`. **Project root:** repo root (this file's repo).

---

## Task 1: Project Scaffolding

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/java/com/dailycallsreview/app/MainActivity.kt`

- [x] **Step 1: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "DailyCallsReview"
include(":app")
```

- [x] **Step 2: Create root `build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
```

- [x] **Step 3: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
kotlin.code.style=official
```

- [x] **Step 4: Create `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.dailycallsreview.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dailycallsreview.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

- [x] **Step 5: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.READ_CALL_LOG" />
    <uses-permission android:name="android.permission.READ_CONTACTS" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.DailyCallsReview">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.DailyCallsReview">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
```

- [x] **Step 6: Create `app/src/main/res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Daily Calls Review</string>
</resources>
```

- [x] **Step 7: Create `app/src/main/res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.DailyCallsReview" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [x] **Step 8: Create an Android-appropriate `.gitignore` at the project root**

```gitignore
*.iml
.gradle/
/local.properties
/.idea/
.DS_Store
/build/
/captures/
.externalNativeBuild/
.cxx/
local.properties
*.apk
```

`local.properties` holds a machine-specific Android SDK path and must never be committed — every subsequent task that runs Gradle relies on this being excluded from version control from the very start.

- [x] **Step 9: Create a placeholder `MainActivity.kt`**

```kotlin
package com.dailycallsreview.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text("Daily Calls Review")
                }
            }
        }
    }
}
```

This placeholder is replaced in Task 18 once the real navigation shell exists — its only purpose here is to let the project compile end-to-end.

- [x] **Step 10: Generate the Gradle wrapper and verify the project compiles**

Run: `gradle wrapper --gradle-version 8.7` (or open the project once in Android Studio, which generates the wrapper automatically), then:

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [x] **Step 11: Commit**

```bash
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res app/src/main/java gradle gradlew gradlew.bat
git commit -m "chore: scaffold Android project"
```

---

## Task 2: Core Domain Models

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/core/CallType.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/core/CallRecord.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/core/WorkSchedule.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/core/CoworkerStat.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/core/DailySummary.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/core/RangeSummary.kt`

These are plain data classes with no logic, so there is nothing to TDD here — they exist to give the aggregator (Task 4) and repositories concrete types to work with.

- [x] **Step 1: Create `CallType.kt`**

```kotlin
package com.dailycallsreview.app.core

enum class CallType {
    ANSWERED,
    MISSED,
    REJECTED
}
```

- [x] **Step 2: Create `CallRecord.kt`**

```kotlin
package com.dailycallsreview.app.core

data class CallRecord(
    val phoneNumberLast10: String,
    val contactName: String?,
    val timestampMillis: Long,
    val durationSeconds: Int,
    val type: CallType
)
```

- [x] **Step 3: Create `WorkSchedule.kt`**

```kotlin
package com.dailycallsreview.app.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class WorkSchedule(
    val workStart: LocalTime,
    val workEnd: LocalTime,
    val workingDays: Set<DayOfWeek>,
    val holidays: Set<LocalDate>
)
```

- [x] **Step 4: Create `CoworkerStat.kt`**

```kotlin
package com.dailycallsreview.app.core

data class CoworkerStat(
    val phoneNumberLast10: String,
    val displayName: String,
    val callCount: Int,
    val totalTalkTimeSeconds: Long
)
```

- [x] **Step 5: Create `DailySummary.kt`**

```kotlin
package com.dailycallsreview.app.core

import java.time.LocalDate
import java.time.LocalDateTime

data class DailySummary(
    val date: LocalDate,
    val totalCalls: Int,
    val totalTalkTimeSeconds: Long,
    val perCoworker: Map<String, CoworkerStat>,
    val firstCallTime: LocalDateTime?,
    val lastCallTime: LocalDateTime?,
    val shiftSpanSeconds: Long?,
    val offHoursCallCount: Int,
    val isHoliday: Boolean,
    val isNonWorkingDay: Boolean
)
```

- [x] **Step 6: Create `RangeSummary.kt`**

```kotlin
package com.dailycallsreview.app.core

import java.time.LocalDate

data class RangeSummary(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalCalls: Int,
    val totalTalkTimeSeconds: Long,
    val perCoworker: Map<String, CoworkerStat>,
    val averageShiftSpanSeconds: Long?,
    val busiestCoworker: CoworkerStat?,
    val offHoursCallCount: Int,
    val holidayCount: Int,
    val nonWorkingDayCount: Int
)
```

- [x] **Step 7: Verify the project still compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [x] **Step 8: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/core
git commit -m "feat: add core domain models"
```

---

## Task 3: Phone Number Normalizer (TDD)

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/core/PhoneNumberNormalizer.kt`
- Test: `app/src/test/java/com/dailycallsreview/app/core/PhoneNumberNormalizerTest.kt`

- [x] **Step 1: Write the failing test**

```kotlin
package com.dailycallsreview.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNumberNormalizerTest {

    @Test
    fun `strips plus and country code to last 10 digits`() {
        assertEquals("9876543210", PhoneNumberNormalizer.last10Digits("+91 98765 43210"))
    }

    @Test
    fun `strips dashes and spaces`() {
        assertEquals("9876543210", PhoneNumberNormalizer.last10Digits("987-654-3210"))
    }

    @Test
    fun `number shorter than 10 digits is returned as is`() {
        assertEquals("12345", PhoneNumberNormalizer.last10Digits("12345"))
    }

    @Test
    fun `already normalized number is unchanged`() {
        assertEquals("9876543210", PhoneNumberNormalizer.last10Digits("9876543210"))
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.dailycallsreview.app.core.PhoneNumberNormalizerTest"`
Expected: FAIL — `PhoneNumberNormalizer` is unresolved.

- [x] **Step 3: Write the implementation**

```kotlin
package com.dailycallsreview.app.core

object PhoneNumberNormalizer {
    fun last10Digits(rawNumber: String): String {
        val digitsOnly = rawNumber.filter { it.isDigit() }
        return if (digitsOnly.length <= 10) digitsOnly else digitsOnly.takeLast(10)
    }
}
```

- [x] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.dailycallsreview.app.core.PhoneNumberNormalizerTest"`
Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/core/PhoneNumberNormalizer.kt app/src/test/java/com/dailycallsreview/app/core/PhoneNumberNormalizerTest.kt
git commit -m "feat: add phone number normalizer"
```

---

## Task 4: Call Aggregator (TDD)

This is the heart of the app — every screen's numbers come from these two pure functions. `computeDailySummary` turns a day's `CallRecord`s into a `DailySummary`; `computeRangeSummary` composes multiple `DailySummary`s into a `RangeSummary` (so range logic is never duplicated).

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/core/CallAggregator.kt`
- Test: `app/src/test/java/com/dailycallsreview/app/core/CallAggregatorTest.kt`

- [x] **Step 1: Write the failing tests**

```kotlin
package com.dailycallsreview.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class CallAggregatorTest {

    private val zone = ZoneOffset.UTC
    private val schedule = WorkSchedule(
        workStart = LocalTime.of(9, 0),
        workEnd = LocalTime.of(18, 0),
        workingDays = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
        ),
        holidays = setOf(LocalDate.of(2026, 1, 26))
    )

    private fun millisAt(date: LocalDate, time: LocalTime): Long =
        LocalDateTime.of(date, time).toInstant(zone).toEpochMilli()

    private fun record(
        date: LocalDate,
        time: LocalTime,
        number: String = "9876543210",
        name: String? = "Asha",
        durationSeconds: Int = 120,
        type: CallType = CallType.ANSWERED
    ) = CallRecord(
        phoneNumberLast10 = number,
        contactName = name,
        timestampMillis = millisAt(date, time),
        durationSeconds = durationSeconds,
        type = type
    )

    @Test
    fun `call before working hours is off-hours`() {
        val monday = LocalDate.of(2026, 3, 2)
        val call = record(monday, LocalTime.of(7, 0))
        assertTrue(CallAggregator.isOffHours(call, schedule, zone))
    }

    @Test
    fun `call after working hours is off-hours`() {
        val monday = LocalDate.of(2026, 3, 2)
        val call = record(monday, LocalTime.of(19, 30))
        assertTrue(CallAggregator.isOffHours(call, schedule, zone))
    }

    @Test
    fun `call within working hours on a working day is not off-hours`() {
        val monday = LocalDate.of(2026, 3, 2)
        val call = record(monday, LocalTime.of(11, 0))
        assertTrue(!CallAggregator.isOffHours(call, schedule, zone))
    }

    @Test
    fun `call on a non-working weekday is off-hours even within the time window`() {
        val sunday = LocalDate.of(2026, 3, 1)
        val call = record(sunday, LocalTime.of(11, 0))
        assertTrue(CallAggregator.isOffHours(call, schedule, zone))
    }

    @Test
    fun `call on a holiday is off-hours even on a normally-working day within the time window`() {
        val holiday = LocalDate.of(2026, 1, 26)
        val call = record(holiday, LocalTime.of(11, 0))
        assertTrue(CallAggregator.isOffHours(call, schedule, zone))
    }

    @Test
    fun `daily summary with no calls has null first, last and shift span`() {
        val monday = LocalDate.of(2026, 3, 2)
        val summary = CallAggregator.computeDailySummary(monday, emptyList(), schedule, zone)
        assertEquals(0, summary.totalCalls)
        assertNull(summary.firstCallTime)
        assertNull(summary.lastCallTime)
        assertNull(summary.shiftSpanSeconds)
    }

    @Test
    fun `daily summary with a single call has a zero shift span, not null`() {
        val monday = LocalDate.of(2026, 3, 2)
        val call = record(monday, LocalTime.of(10, 0))
        val summary = CallAggregator.computeDailySummary(monday, listOf(call), schedule, zone)
        assertEquals(1, summary.totalCalls)
        assertEquals(0L, summary.shiftSpanSeconds)
    }

    @Test
    fun `daily summary computes shift span from first to last call and per-coworker breakdown`() {
        val monday = LocalDate.of(2026, 3, 2)
        val calls = listOf(
            record(monday, LocalTime.of(9, 2), number = "9876543210", name = "Asha", durationSeconds = 300),
            record(monday, LocalTime.of(12, 0), number = "9000000000", name = "Ravi", durationSeconds = 180),
            record(monday, LocalTime.of(17, 47), number = "9876543210", name = "Asha", durationSeconds = 60)
        )
        val summary = CallAggregator.computeDailySummary(monday, calls, schedule, zone)

        assertEquals(3, summary.totalCalls)
        assertEquals(540L, summary.totalTalkTimeSeconds)
        assertEquals(LocalTime.of(9, 2), summary.firstCallTime?.toLocalTime())
        assertEquals(LocalTime.of(17, 47), summary.lastCallTime?.toLocalTime())
        assertEquals(2, summary.perCoworker.size)
        assertEquals(2, summary.perCoworker["9876543210"]?.callCount)
        assertEquals(360L, summary.perCoworker["9876543210"]?.totalTalkTimeSeconds)
    }

    @Test
    fun `daily summary counts off-hours calls separately while including them in the total`() {
        val monday = LocalDate.of(2026, 3, 2)
        val calls = listOf(
            record(monday, LocalTime.of(10, 0)),
            record(monday, LocalTime.of(20, 0))
        )
        val summary = CallAggregator.computeDailySummary(monday, calls, schedule, zone)
        assertEquals(2, summary.totalCalls)
        assertEquals(1, summary.offHoursCallCount)
    }

    @Test
    fun `daily summary display name prefers the most recent non-null contact name over a later null`() {
        val monday = LocalDate.of(2026, 3, 2)
        val calls = listOf(
            record(monday, LocalTime.of(9, 0), number = "9876543210", name = "Asha"),
            record(monday, LocalTime.of(17, 0), number = "9876543210", name = null)
        )
        val summary = CallAggregator.computeDailySummary(monday, calls, schedule, zone)
        assertEquals("Asha", summary.perCoworker["9876543210"]?.displayName)
    }

    @Test
    fun `range summary keeps a known display name instead of letting a later no-name day blank it out`() {
        val day1 = LocalDate.of(2026, 3, 2)
        val day2 = LocalDate.of(2026, 3, 3)

        val summary1 = CallAggregator.computeDailySummary(
            day1,
            listOf(record(day1, LocalTime.of(9, 0), number = "9876543210", name = "Asha")),
            schedule,
            zone
        )
        val summary2 = CallAggregator.computeDailySummary(
            day2,
            listOf(record(day2, LocalTime.of(9, 0), number = "9876543210", name = null)),
            schedule,
            zone
        )

        val range = CallAggregator.computeRangeSummary(listOf(summary1, summary2))

        assertEquals("Asha", range.perCoworker["9876543210"]?.displayName)
    }

    @Test
    fun `range summary merges per-coworker stats, averages shift span across days that had calls, and finds the busiest coworker`() {
        val day1 = LocalDate.of(2026, 3, 2)
        val day2 = LocalDate.of(2026, 3, 3)
        val day3 = LocalDate.of(2026, 3, 4)

        val summary1 = CallAggregator.computeDailySummary(
            day1,
            listOf(
                record(day1, LocalTime.of(9, 0), number = "9876543210", name = "Asha"),
                record(day1, LocalTime.of(17, 0), number = "9876543210", name = "Asha")
            ),
            schedule,
            zone
        )
        val summary2 = CallAggregator.computeDailySummary(
            day2,
            listOf(record(day2, LocalTime.of(10, 0), number = "9000000000", name = "Ravi")),
            schedule,
            zone
        )
        val summary3 = CallAggregator.computeDailySummary(day3, emptyList(), schedule, zone)

        val range = CallAggregator.computeRangeSummary(listOf(summary1, summary2, summary3))

        assertEquals(3, range.totalCalls)
        assertEquals(2, range.perCoworker.size)
        assertEquals("Asha", range.busiestCoworker?.displayName)
        // summary1 spans 09:00->17:00 = 28800s, summary2 is a single call = 0s, summary3 has no calls (excluded)
        assertEquals(14400L, range.averageShiftSpanSeconds)
    }
}
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.dailycallsreview.app.core.CallAggregatorTest"`
Expected: FAIL — `CallAggregator` is unresolved.

- [x] **Step 3: Write the implementation**

```kotlin
package com.dailycallsreview.app.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object CallAggregator {

    /**
     * A call is off-hours if it falls on a holiday, on a non-working day of week, or outside
     * the `[workStart, workEnd]` window — [WorkSchedule.workStart] and [WorkSchedule.workEnd]
     * are inclusive boundaries, so a call exactly at either edge counts as within hours.
     */
    fun isOffHours(record: CallRecord, schedule: WorkSchedule, zone: ZoneId): Boolean {
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(record.timestampMillis), zone)
        val isHoliday = dateTime.toLocalDate() in schedule.holidays
        val isNonWorkingDay = dateTime.dayOfWeek !in schedule.workingDays
        val isOutsideHours = dateTime.toLocalTime() < schedule.workStart || dateTime.toLocalTime() > schedule.workEnd
        return isHoliday || isNonWorkingDay || isOutsideHours
    }

    /**
     * Aggregates one day's [CallRecord]s into a [DailySummary]. `shiftSpanSeconds` is `null`
     * only when there were no calls that day; a single call yields `0`, not `null`. Each
     * coworker's `displayName` prefers the most recent non-null `contactName` seen that day for
     * that number, falling back to the raw number only if none of the day's calls had a name.
     */
    fun computeDailySummary(
        date: LocalDate,
        records: List<CallRecord>,
        schedule: WorkSchedule,
        zone: ZoneId
    ): DailySummary {
        val sortedRecords = records.sortedBy { it.timestampMillis }

        val perCoworker = sortedRecords
            .groupBy { it.phoneNumberLast10 }
            .mapValues { (number, calls) ->
                CoworkerStat(
                    phoneNumberLast10 = number,
                    displayName = calls.mapNotNull { it.contactName }.lastOrNull() ?: number,
                    callCount = calls.size,
                    totalTalkTimeSeconds = calls.sumOf { it.durationSeconds.toLong() }
                )
            }

        val firstCallTime = sortedRecords.firstOrNull()?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it.timestampMillis), zone)
        }
        val lastCallTime = sortedRecords.lastOrNull()?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it.timestampMillis), zone)
        }
        val shiftSpanSeconds = if (firstCallTime != null && lastCallTime != null) {
            Duration.between(firstCallTime, lastCallTime).seconds
        } else null

        return DailySummary(
            date = date,
            totalCalls = sortedRecords.size,
            totalTalkTimeSeconds = sortedRecords.sumOf { it.durationSeconds.toLong() },
            perCoworker = perCoworker,
            firstCallTime = firstCallTime,
            lastCallTime = lastCallTime,
            shiftSpanSeconds = shiftSpanSeconds,
            offHoursCallCount = sortedRecords.count { isOffHours(it, schedule, zone) },
            isHoliday = date in schedule.holidays,
            isNonWorkingDay = date.dayOfWeek !in schedule.workingDays
        )
    }

    /**
     * Composes multiple [DailySummary]s into a [RangeSummary]. `averageShiftSpanSeconds` is
     * `null` only when no day in the range had any calls; it otherwise averages over days that
     * had calls, excluding call-free days entirely. A merged coworker's `displayName` keeps an
     * already-known real name rather than letting a later no-name day overwrite it with a raw
     * number fallback. `busiestCoworker` orders by `callCount`, breaking ties by
     * `totalTalkTimeSeconds`.
     */
    fun computeRangeSummary(dailySummaries: List<DailySummary>): RangeSummary {
        require(dailySummaries.isNotEmpty()) { "Cannot summarize an empty range" }

        val sortedDays = dailySummaries.sortedBy { it.date }
        val mergedCoworkers = mutableMapOf<String, CoworkerStat>()
        for (day in sortedDays) {
            for ((number, stat) in day.perCoworker) {
                val existing = mergedCoworkers[number]
                mergedCoworkers[number] = if (existing == null) {
                    stat
                } else {
                    val resolvedDisplayName = when {
                        existing.displayName != existing.phoneNumberLast10 && stat.displayName == stat.phoneNumberLast10 -> existing.displayName
                        else -> stat.displayName
                    }
                    existing.copy(
                        displayName = resolvedDisplayName,
                        callCount = existing.callCount + stat.callCount,
                        totalTalkTimeSeconds = existing.totalTalkTimeSeconds + stat.totalTalkTimeSeconds
                    )
                }
            }
        }

        val spans = sortedDays.mapNotNull { it.shiftSpanSeconds }
        val averageShiftSpanSeconds = if (spans.isEmpty()) null else spans.sum() / spans.size

        val busiestCoworker = mergedCoworkers.values.maxWithOrNull(
            compareBy({ it.callCount }, { it.totalTalkTimeSeconds })
        )

        return RangeSummary(
            startDate = sortedDays.first().date,
            endDate = sortedDays.last().date,
            totalCalls = sortedDays.sumOf { it.totalCalls },
            totalTalkTimeSeconds = sortedDays.sumOf { it.totalTalkTimeSeconds },
            perCoworker = mergedCoworkers,
            averageShiftSpanSeconds = averageShiftSpanSeconds,
            busiestCoworker = busiestCoworker,
            offHoursCallCount = sortedDays.sumOf { it.offHoursCallCount },
            holidayCount = sortedDays.count { it.isHoliday },
            nonWorkingDayCount = sortedDays.count { it.isNonWorkingDay }
        )
    }
}
```

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.dailycallsreview.app.core.CallAggregatorTest"`
Expected: `BUILD SUCCESSFUL`, 12 tests passed. (Corrected from an earlier miscount of 9 — the test file genuinely has 12 `@Test` methods: the original 10 plus 2 display-name regression tests added after code review caught a real bug — see the display-name resolution behavior documented in Step 3's KDoc.)

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/core/CallAggregator.kt app/src/test/java/com/dailycallsreview/app/core/CallAggregatorTest.kt
git commit -m "feat: add call aggregator with off-hours flagging and range summary"
```

---

## Task 5: Room — Tagged Contacts

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/data/db/TaggedContact.kt`
- Test: `app/src/androidTest/java/com/dailycallsreview/app/data/db/TaggedContactDaoTest.kt`

- [x] **Step 1: Create the entity and DAO**

```kotlin
package com.dailycallsreview.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tagged_contacts")
data class TaggedContactEntity(
    @PrimaryKey val phoneNumberLast10: String,
    val contactId: Long,
    val displayName: String
)

@Dao
interface TaggedContactDao {
    @Query("SELECT * FROM tagged_contacts ORDER BY displayName ASC")
    fun observeAll(): Flow<List<TaggedContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<TaggedContactEntity>)

    @Query("DELETE FROM tagged_contacts WHERE contactId = :contactId")
    suspend fun deleteByContactId(contactId: Long)

    @Query("SELECT * FROM tagged_contacts")
    suspend fun getAllOnce(): List<TaggedContactEntity>
}
```

Note: this file has no `AppDatabase` reference yet — that's assembled in Task 7 once all three entities exist. This DAO can't be instantiated standalone, so its test (Step 2) references a minimal single-entity database declared inline in the test file, matching the pattern used in Room's own docs for isolated DAO tests.

- [x] **Step 2: Write the instrumented test**

```kotlin
package com.dailycallsreview.app.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@Database(entities = [TaggedContactEntity::class], version = 1, exportSchema = false)
abstract class TaggedContactTestDatabase : RoomDatabase() {
    abstract fun taggedContactDao(): TaggedContactDao
}

@RunWith(AndroidJUnit4::class)
class TaggedContactDaoTest {
    private lateinit var db: TaggedContactTestDatabase
    private lateinit var dao: TaggedContactDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TaggedContactTestDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.taggedContactDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndObserveTaggedContacts() = runBlocking {
        dao.insertAll(
            listOf(
                TaggedContactEntity("9876543210", 1L, "Asha"),
                TaggedContactEntity("9123456780", 1L, "Asha")
            )
        )
        val result = dao.observeAll().first()
        assertEquals(2, result.size)
        assertEquals("Asha", result[0].displayName)
    }

    @Test
    fun deleteByContactIdRemovesAllNumbersForThatContact() = runBlocking {
        dao.insertAll(
            listOf(
                TaggedContactEntity("9876543210", 1L, "Asha"),
                TaggedContactEntity("9123456780", 1L, "Asha"),
                TaggedContactEntity("9000000000", 2L, "Ravi")
            )
        )
        dao.deleteByContactId(1L)
        val result = dao.observeAll().first()
        assertEquals(1, result.size)
        assertEquals("Ravi", result[0].displayName)
    }
}
```

- [x] **Step 3: Run the instrumented test on a device or emulator**

Run: `./gradlew connectedDebugAndroidTest --tests "com.dailycallsreview.app.data.db.TaggedContactDaoTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests passed. (Requires a connected device or running emulator.)

- [x] **Step 4: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/data/db/TaggedContact.kt app/src/androidTest/java/com/dailycallsreview/app/data/db/TaggedContactDaoTest.kt
git commit -m "feat: add tagged contact Room entity and DAO"
```

---

## Task 6: Room — Holidays

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/data/db/Holiday.kt`
- Test: `app/src/androidTest/java/com/dailycallsreview/app/data/db/HolidayDaoTest.kt`

- [x] **Step 1: Create the entity and DAO**

```kotlin
package com.dailycallsreview.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "holidays")
data class HolidayEntity(
    @PrimaryKey val date: String, // ISO-8601 yyyy-MM-dd
    val label: String
)

@Dao
interface HolidayDao {
    @Query("SELECT * FROM holidays ORDER BY date ASC")
    fun observeAll(): Flow<List<HolidayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(holiday: HolidayEntity)

    @Query("DELETE FROM holidays WHERE date = :date")
    suspend fun deleteByDate(date: String)
}
```

- [x] **Step 2: Write the instrumented test**

```kotlin
package com.dailycallsreview.app.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@Database(entities = [HolidayEntity::class], version = 1, exportSchema = false)
abstract class HolidayTestDatabase : RoomDatabase() {
    abstract fun holidayDao(): HolidayDao
}

@RunWith(AndroidJUnit4::class)
class HolidayDaoTest {
    private lateinit var db: HolidayTestDatabase
    private lateinit var dao: HolidayDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HolidayTestDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.holidayDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndObserveHolidaysOrderedByDate() = runBlocking {
        dao.insert(HolidayEntity("2026-12-25", "Christmas"))
        dao.insert(HolidayEntity("2026-01-26", "Republic Day"))
        val result = dao.observeAll().first()
        assertEquals(2, result.size)
        assertEquals("2026-01-26", result[0].date)
    }

    @Test
    fun deleteByDateRemovesOnlyThatHoliday() = runBlocking {
        dao.insert(HolidayEntity("2026-12-25", "Christmas"))
        dao.insert(HolidayEntity("2026-01-26", "Republic Day"))
        dao.deleteByDate("2026-12-25")
        val result = dao.observeAll().first()
        assertEquals(1, result.size)
        assertEquals("Republic Day", result[0].label)
    }
}
```

- [x] **Step 3: Run the instrumented test**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dailycallsreview.app.data.db.HolidayDaoTest` (the `--tests` flag is not recognized by `connectedDebugAndroidTest` on this AGP version — use `-Pandroid.testInstrumentationRunnerArguments.class=...` instead to filter to one class)
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [x] **Step 4: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/data/db/Holiday.kt app/src/androidTest/java/com/dailycallsreview/app/data/db/HolidayDaoTest.kt
git commit -m "feat: add holiday Room entity and DAO"
```

---

## Task 7: Room — App Settings, Working Days Mask, and the Assembled Database

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/data/db/WorkingDaysMask.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/data/db/AppSettings.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/data/db/AppDatabase.kt`
- Test: `app/src/test/java/com/dailycallsreview/app/data/db/WorkingDaysMaskTest.kt`
- Test: `app/src/androidTest/java/com/dailycallsreview/app/data/db/AppSettingsDaoTest.kt`

`WorkingDaysMask` converts between a `Set<DayOfWeek>` (used everywhere in the domain layer) and a single `Int` bitmask (stored in Room) — this is pure logic, so it gets a plain JVM unit test.

- [x] **Step 1: Write the failing test for `WorkingDaysMask`**

```kotlin
package com.dailycallsreview.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class WorkingDaysMaskTest {

    @Test
    fun `round-trips a Monday-to-Saturday set`() {
        val days = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
        )
        val mask = WorkingDaysMask.fromSet(days)
        assertEquals(days, WorkingDaysMask.toSet(mask))
    }

    @Test
    fun `round-trips an empty set`() {
        assertEquals(emptySet<DayOfWeek>(), WorkingDaysMask.toSet(WorkingDaysMask.fromSet(emptySet())))
    }

    @Test
    fun `round-trips a single day`() {
        val days = setOf(DayOfWeek.SUNDAY)
        assertEquals(days, WorkingDaysMask.toSet(WorkingDaysMask.fromSet(days)))
    }

    @Test
    fun `default mask is Monday through Saturday`() {
        val expected = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
        )
        assertEquals(expected, WorkingDaysMask.toSet(WorkingDaysMask.DEFAULT_MON_TO_SAT))
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.dailycallsreview.app.data.db.WorkingDaysMaskTest"`
Expected: FAIL — `WorkingDaysMask` is unresolved.

- [x] **Step 3: Write `WorkingDaysMask.kt`**

```kotlin
package com.dailycallsreview.app.data.db

import java.time.DayOfWeek

object WorkingDaysMask {
    private val ORDER = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    )

    fun toSet(mask: Int): Set<DayOfWeek> =
        ORDER.filterIndexed { index, _ -> (mask shr index) and 1 == 1 }.toSet()

    fun fromSet(days: Set<DayOfWeek>): Int =
        ORDER.foldIndexed(0) { index, acc, day ->
            if (day in days) acc or (1 shl index) else acc
        }

    val DEFAULT_MON_TO_SAT: Int = fromSet(ORDER.take(6).toSet())
}
```

- [x] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.dailycallsreview.app.data.db.WorkingDaysMaskTest"`
Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [x] **Step 5: Create `AppSettings.kt` (entity + DAO)**

```kotlin
package com.dailycallsreview.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val workStartMinutes: Int,
    val workEndMinutes: Int,
    val workingDaysMask: Int
)

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 0")
    fun observe(): Flow<AppSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: AppSettingsEntity)
}
```

- [x] **Step 6: Write the instrumented test for `AppSettingsDao`**

```kotlin
package com.dailycallsreview.app.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@Database(entities = [AppSettingsEntity::class], version = 1, exportSchema = false)
abstract class AppSettingsTestDatabase : RoomDatabase() {
    abstract fun appSettingsDao(): AppSettingsDao
}

@RunWith(AndroidJUnit4::class)
class AppSettingsDaoTest {
    private lateinit var db: AppSettingsTestDatabase
    private lateinit var dao: AppSettingsDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppSettingsTestDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.appSettingsDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun observeReturnsNullBeforeAnySettingsAreSaved() = runBlocking {
        assertNull(dao.observe().first())
    }

    @Test
    fun upsertReplacesThePreviousSingleRow() = runBlocking {
        dao.upsert(AppSettingsEntity(workStartMinutes = 540, workEndMinutes = 1080, workingDaysMask = 63))
        dao.upsert(AppSettingsEntity(workStartMinutes = 480, workEndMinutes = 1020, workingDaysMask = 31))
        val result = dao.observe().first()
        assertEquals(480, result?.workStartMinutes)
        assertEquals(31, result?.workingDaysMask)
    }
}
```

- [x] **Step 7: Run the instrumented test**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dailycallsreview.app.data.db.AppSettingsDaoTest` (the `--tests` flag is not recognized by `connectedDebugAndroidTest` on this AGP version — use `-Pandroid.testInstrumentationRunnerArguments.class=...` instead to filter to one class)
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [x] **Step 8: Create `AppDatabase.kt`, assembling all three entities**

```kotlin
package com.dailycallsreview.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TaggedContactEntity::class, HolidayEntity::class, AppSettingsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taggedContactDao(): TaggedContactDao
    abstract fun holidayDao(): HolidayDao
    abstract fun appSettingsDao(): AppSettingsDao
}
```

- [x] **Step 9: Verify the project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [x] **Step 10: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/data/db app/src/test/java/com/dailycallsreview/app/data/db/WorkingDaysMaskTest.kt app/src/androidTest/java/com/dailycallsreview/app/data/db/AppSettingsDaoTest.kt
git commit -m "feat: add app settings entity/DAO, working days bitmask, and assembled AppDatabase"
```

---

## Task 8: Call Log Repository

Queries `CallLog.Calls` directly, always with a date-bounded `WHERE` clause (per the spec's architecture decision — never a full-history scan), filtered to the tagged phone numbers passed in. `BLOCKED_TYPE` calls are dropped entirely; `MISSED_TYPE` and `REJECTED_TYPE` are kept per the spec.

This talks to a real `ContentProvider` and real device call history, so per the spec's Testing Approach it is verified manually (Task 20), not unit tested here.

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/data/calllog/CallLogRepository.kt`

- [x] **Step 1: Write `CallLogRepository.kt`**

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

    // suspend + Dispatchers.IO: this does a ContentResolver query and a full
    // cursor loop, which is blocking I/O. Every caller (Home, Task 14; later
    // History/Day/Range Detail, Tasks 15-16) invokes this from a coroutine, so
    // pushing it off Dispatchers.Main.immediate here keeps it out of all of
    // them for free instead of each screen having to remember to do it.
    suspend fun getCallsBetween(startMillis: Long, endMillis: Long, taggedNumbers: Set<String>): List<CallRecord> =
        withContext(Dispatchers.IO) {
            if (taggedNumbers.isEmpty()) return@withContext emptyList()

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
                    if (normalized !in taggedNumbers) continue

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
            records
        }
}
```

- [x] **Step 2: Verify the project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [x] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/data/calllog/CallLogRepository.kt
git commit -m "feat: add date-bounded call log repository"
```

**Post-review fix (surfaced during Task 14 review):** `getCallsBetween` was originally a plain synchronous `fun`. Since `HomeViewModel` (Task 14) called it from inside `viewModelScope.launch`, the blocking `ContentResolver` query ran on `Dispatchers.Main.immediate`. Fixed by making it `suspend` and wrapping the body in `withContext(Dispatchers.IO)`, as reflected in the code block above. No call sites needed changes since the only caller was already inside a coroutine.

---

## Task 9: Contacts Repository

Reads all phone contacts with their numbers (so Team Setup, Task 12, can list them), grouping every number under its contact — this is what makes "tagging a contact tags all its numbers" (spec requirement) possible.

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/data/contacts/ContactsRepository.kt`

- [x] **Step 1: Write `ContactsRepository.kt`**

```kotlin
package com.dailycallsreview.app.data.contacts

import android.content.Context
import android.provider.ContactsContract
import com.dailycallsreview.app.core.PhoneNumberNormalizer

data class PickableContact(
    val contactId: Long,
    val displayName: String,
    val phoneNumbersLast10: List<String>
)

class ContactsRepository(private val context: Context) {

    fun getAllContactsWithPhoneNumbers(): List<PickableContact> {
        val numbersByContact = mutableMapOf<Long, MutableList<String>>()
        val namesByContact = mutableMapOf<Long, String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex) ?: continue
                val rawNumber = cursor.getString(numberIndex) ?: continue
                val normalized = PhoneNumberNormalizer.last10Digits(rawNumber)

                namesByContact[contactId] = name
                numbersByContact.getOrPut(contactId) { mutableListOf() }.add(normalized)
            }
        }

        return numbersByContact.map { (contactId, numbers) ->
            PickableContact(
                contactId = contactId,
                displayName = namesByContact[contactId].orEmpty(),
                phoneNumbersLast10 = numbers.distinct()
            )
        }.sortedBy { it.displayName }
    }
}
```

- [x] **Step 2: Verify the project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [x] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/data/contacts/ContactsRepository.kt
git commit -m "feat: add contacts repository grouping numbers by contact"
```

---

## Task 10: Team and Settings Repositories

Thin wrappers around the DAOs from Tasks 5–7, giving the UI layer domain-shaped APIs (`WorkSchedule`, `Set<String>` of tagged numbers) instead of raw entities. No dedicated tests here — the logic is a direct pass-through already covered by the DAO instrumented tests (Tasks 5–7) and by `CallAggregator`'s tests (Task 4), consistent with the spec's Testing Approach.

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/data/TeamRepository.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/data/SettingsRepository.kt`

- [x] **Step 1: Write `TeamRepository.kt`**

```kotlin
package com.dailycallsreview.app.data

import com.dailycallsreview.app.data.contacts.PickableContact
import com.dailycallsreview.app.data.db.TaggedContactDao
import com.dailycallsreview.app.data.db.TaggedContactEntity
import kotlinx.coroutines.flow.Flow

class TeamRepository(private val dao: TaggedContactDao) {

    fun observeTaggedContacts(): Flow<List<TaggedContactEntity>> = dao.observeAll()

    suspend fun tagContact(contact: PickableContact) {
        dao.insertAll(
            contact.phoneNumbersLast10.map { number ->
                TaggedContactEntity(
                    phoneNumberLast10 = number,
                    contactId = contact.contactId,
                    displayName = contact.displayName
                )
            }
        )
    }

    suspend fun untagContact(contactId: Long) {
        dao.deleteByContactId(contactId)
    }

    suspend fun getTaggedNumbersOnce(): Set<String> =
        dao.getAllOnce().map { it.phoneNumberLast10 }.toSet()
}
```

- [x] **Step 2: Write `SettingsRepository.kt`**

```kotlin
package com.dailycallsreview.app.data

import com.dailycallsreview.app.core.WorkSchedule
import com.dailycallsreview.app.data.db.AppSettingsDao
import com.dailycallsreview.app.data.db.AppSettingsEntity
import com.dailycallsreview.app.data.db.HolidayDao
import com.dailycallsreview.app.data.db.HolidayEntity
import com.dailycallsreview.app.data.db.WorkingDaysMask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private val DEFAULT_SETTINGS = AppSettingsEntity(
    workStartMinutes = 9 * 60,
    workEndMinutes = 18 * 60,
    workingDaysMask = WorkingDaysMask.DEFAULT_MON_TO_SAT
)

class SettingsRepository(
    private val settingsDao: AppSettingsDao,
    private val holidayDao: HolidayDao
) {
    private val writeMutex = Mutex()

    fun observeWorkSchedule(): Flow<WorkSchedule> =
        combine(settingsDao.observe(), holidayDao.observeAll()) { settingsEntity, holidayEntities ->
            val settings = settingsEntity ?: DEFAULT_SETTINGS
            WorkSchedule(
                workStart = LocalTime.ofSecondOfDay(settings.workStartMinutes * 60L),
                workEnd = LocalTime.ofSecondOfDay(settings.workEndMinutes * 60L),
                workingDays = WorkingDaysMask.toSet(settings.workingDaysMask),
                holidays = holidayEntities.map { LocalDate.parse(it.date) }.toSet()
            )
        }

    suspend fun saveWorkSchedule(workStart: LocalTime, workEnd: LocalTime, workingDays: Set<DayOfWeek>) {
        // Serialized: concurrent callers (e.g. two rapid checkbox toggles in
        // SettingsScreen, Task 13) can't have their upserts reordered by
        // Room's executor pool and silently overwrite one another.
        writeMutex.withLock {
            settingsDao.upsert(
                AppSettingsEntity(
                    workStartMinutes = workStart.toSecondOfDay() / 60,
                    workEndMinutes = workEnd.toSecondOfDay() / 60,
                    workingDaysMask = WorkingDaysMask.fromSet(workingDays)
                )
            )
        }
    }

    suspend fun addHoliday(date: LocalDate, label: String) {
        holidayDao.insert(HolidayEntity(date.toString(), label))
    }

    suspend fun removeHoliday(date: LocalDate) {
        holidayDao.deleteByDate(date.toString())
    }
}
```

- [x] **Step 3: Verify the project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [x] **Step 4: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/data/TeamRepository.kt app/src/main/java/com/dailycallsreview/app/data/SettingsRepository.kt
git commit -m "feat: add team and settings repositories"
```

---

## Task 11: Application Class and Permissions Gate

`DailyCallsReviewApplication` is the app's manual dependency-injection root (no DI framework — this app is small enough that a lazy-singleton `Application` subclass is simpler and matches YAGNI). `PermissionsGate` is a composable wrapper that blocks its content until `READ_CALL_LOG` and `READ_CONTACTS` are both granted, per the spec's Permissions section.

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/DailyCallsReviewApplication.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/permissions/PermissionsGate.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [x] **Step 1: Write `DailyCallsReviewApplication.kt`**

```kotlin
package com.dailycallsreview.app

import android.app.Application
import androidx.room.Room
import com.dailycallsreview.app.data.SettingsRepository
import com.dailycallsreview.app.data.TeamRepository
import com.dailycallsreview.app.data.calllog.CallLogRepository
import com.dailycallsreview.app.data.contacts.ContactsRepository
import com.dailycallsreview.app.data.db.AppDatabase

class DailyCallsReviewApplication : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "daily-calls-review.db").build()
    }

    val teamRepository: TeamRepository by lazy { TeamRepository(database.taggedContactDao()) }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(database.appSettingsDao(), database.holidayDao())
    }
    val callLogRepository: CallLogRepository by lazy { CallLogRepository(applicationContext) }
    val contactsRepository: ContactsRepository by lazy { ContactsRepository(applicationContext) }
}
```

- [x] **Step 2: Write `PermissionsGate.kt`**

```kotlin
package com.dailycallsreview.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.READ_CALL_LOG,
    Manifest.permission.READ_CONTACTS
)

private fun hasAllPermissions(context: Context): Boolean =
    REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@Composable
fun PermissionsGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAllPermissions(context)) }

    // Re-check on every resume: permissions can be granted or revoked from
    // outside the app (system Settings, or an OS auto-reset of an unused
    // permission) while this composable's state would otherwise go stale.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = hasAllPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.all { it }
    }

    if (granted) {
        content()
    } else {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Daily Calls Review needs access to your call log and contacts to track " +
                    "calls with your tagged coworkers. No data ever leaves your device."
            )
            Button(onClick = { launcher.launch(REQUIRED_PERMISSIONS) }) {
                Text("Grant access")
            }
        }
    }
}
```

`LocalLifecycleOwner` resolves from `androidx.compose.ui.platform` given this project's existing
Compose/`lifecycle-runtime-ktx` dependencies — no new Gradle dependency needed. (If it ever fails
to resolve, `androidx.lifecycle.compose.LocalLifecycleOwner` is the alternative import, which
requires adding `androidx.lifecycle:lifecycle-runtime-compose` to `app/build.gradle.kts`.)

- [x] **Step 3: Register the Application class in the manifest**

Modify `app/src/main/AndroidManifest.xml` — add `android:name=".DailyCallsReviewApplication"` to the `<application>` tag:

```xml
<application
    android:name=".DailyCallsReviewApplication"
    android:allowBackup="true"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:theme="@style/Theme.DailyCallsReview">
```

- [x] **Step 4: Verify the project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/DailyCallsReviewApplication.kt app/src/main/java/com/dailycallsreview/app/ui/permissions/PermissionsGate.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add application DI root and permissions gate"
```

---

## Task 12: Team Setup Screen

Lets the user tag/untag contacts as coworkers. Tagging a contact tags all of its phone numbers (spec requirement) — `TeamRepository.tagContact` (Task 10) already does this.

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/ui/team/TeamSetupViewModel.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/team/TeamSetupScreen.kt`

- [x] **Step 1: Write `TeamSetupViewModel.kt`**

```kotlin
package com.dailycallsreview.app.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailycallsreview.app.data.TeamRepository
import com.dailycallsreview.app.data.contacts.ContactsRepository
import com.dailycallsreview.app.data.contacts.PickableContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TeamSetupViewModel(
    private val contactsRepository: ContactsRepository,
    private val teamRepository: TeamRepository
) : ViewModel() {

    private val _allContacts = MutableStateFlow<List<PickableContact>>(emptyList())
    val allContacts: StateFlow<List<PickableContact>> = _allContacts.asStateFlow()

    val taggedContactIds: StateFlow<Set<Long>> = teamRepository.observeTaggedContacts()
        .map { entities -> entities.map { it.contactId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        _allContacts.value = contactsRepository.getAllContactsWithPhoneNumbers()
    }

    fun toggleTag(contact: PickableContact, currentlyTagged: Boolean) {
        viewModelScope.launch {
            if (currentlyTagged) {
                teamRepository.untagContact(contact.contactId)
            } else {
                teamRepository.tagContact(contact)
            }
        }
    }
}
```

- [x] **Step 2: Write `TeamSetupScreen.kt`**

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
import com.dailycallsreview.app.DailyCallsReviewApplication

@Composable
fun TeamSetupScreen(app: DailyCallsReviewApplication) {
    val viewModel: TeamSetupViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TeamSetupViewModel(app.contactsRepository, app.teamRepository) }
        }
    )
    val contacts by viewModel.allContacts.collectAsState()
    val taggedIds by viewModel.taggedContactIds.collectAsState()

    LazyColumn {
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
```

- [x] **Step 3: Verify the project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [x] **Step 4: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/team
git commit -m "feat: add team setup screen"
```

---

## Task 13: Settings Screen

Lets the user set working hours (via `TimePickerDialog`), toggle each weekday as working/off, and manage the manual holiday list (via `DatePickerDialog`) — all per the spec's Settings section.

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/settings/SettingsScreen.kt`

- [x] **Step 1: Write `SettingsViewModel.kt`**

```kotlin
package com.dailycallsreview.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailycallsreview.app.core.WorkSchedule
import com.dailycallsreview.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // This screen is the only writer of settings, so once loaded, local state is authoritative.
    // Edits update _schedule synchronously so rapid successive edits correctly accumulate off
    // each other; persistence to the DB is fire-and-forget and never read back into this state,
    // since a stale echo could otherwise overwrite an edit made after that write was issued.
    //
    // An earlier version derived `schedule` via `observeWorkSchedule().stateIn(...)` and had
    // SettingsScreen compute each edit inline off that DB-backed value. That looked correct but
    // wasn't: two rapid edits issued before either DB write completes both read the SAME stale
    // `current` snapshot (nothing had re-emitted yet), so the second edit's inline computation
    // didn't include the first edit — the first write was clobbered, not merged. Holding the
    // accumulating state here, updated synchronously per edit, closes that gap.
    private val _schedule = MutableStateFlow<WorkSchedule?>(null)
    val schedule: StateFlow<WorkSchedule?> = _schedule.asStateFlow()

    init {
        viewModelScope.launch {
            _schedule.value = settingsRepository.observeWorkSchedule().first()
        }
    }

    fun saveWorkHours(start: LocalTime, end: LocalTime, workingDays: Set<DayOfWeek>) {
        _schedule.value = _schedule.value?.copy(workStart = start, workEnd = end, workingDays = workingDays)
        viewModelScope.launch { settingsRepository.saveWorkSchedule(start, end, workingDays) }
    }

    fun addHoliday(date: LocalDate, label: String) {
        _schedule.value = _schedule.value?.let { it.copy(holidays = it.holidays + date) }
        viewModelScope.launch { settingsRepository.addHoliday(date, label) }
    }

    fun removeHoliday(date: LocalDate) {
        _schedule.value = _schedule.value?.let { it.copy(holidays = it.holidays - date) }
        viewModelScope.launch { settingsRepository.removeHoliday(date) }
    }
}
```

- [x] **Step 2: Write `SettingsScreen.kt`**

```kotlin
package com.dailycallsreview.app.ui.settings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dailycallsreview.app.DailyCallsReviewApplication
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun SettingsScreen(app: DailyCallsReviewApplication) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(app.settingsRepository) }
        }
    )
    val schedule by viewModel.schedule.collectAsState()
    val context = LocalContext.current

    // No local draft mirrors for workStart/workEnd/workingDays: every edit
    // saves immediately, so there is no unsaved state to protect, and
    // mirroring `current` into remember(current)-keyed vars is actively
    // harmful — when an async write's result echoes back through
    // observeWorkSchedule() before a second, still-in-flight edit's write
    // completes, the re-key snaps all three vars back to the stale value
    // and silently drops the second edit. Read straight from `current` for
    // display and compute each new value inline at the point of the edit.
    schedule?.let { current ->
        var holidayLabel by remember { mutableStateOf("") }
        var holidayDate by remember { mutableStateOf(LocalDate.now()) }

        LazyColumn(modifier = Modifier.padding(16.dp)) {
            item {
                Button(onClick = {
                    TimePickerDialog(context, { _, hour, minute ->
                        val newStart = LocalTime.of(hour, minute)
                        viewModel.saveWorkHours(newStart, current.workEnd, current.workingDays)
                    }, current.workStart.hour, current.workStart.minute, false).show()
                }) { Text("Work start: ${current.workStart}") }
            }
            item {
                Button(onClick = {
                    TimePickerDialog(context, { _, hour, minute ->
                        val newEnd = LocalTime.of(hour, minute)
                        viewModel.saveWorkHours(current.workStart, newEnd, current.workingDays)
                    }, current.workEnd.hour, current.workEnd.minute, false).show()
                }) { Text("Work end: ${current.workEnd}") }
            }
            items(DayOfWeek.entries) { day ->
                Row {
                    Checkbox(
                        checked = day in current.workingDays,
                        onCheckedChange = { checked ->
                            val newDays = if (checked) current.workingDays + day else current.workingDays - day
                            viewModel.saveWorkHours(current.workStart, current.workEnd, newDays)
                        }
                    )
                    Text(day.name)
                }
            }
            item {
                Row {
                    Button(onClick = {
                        DatePickerDialog(context, { _, year, month, day ->
                            holidayDate = LocalDate.of(year, month + 1, day)
                        }, holidayDate.year, holidayDate.monthValue - 1, holidayDate.dayOfMonth).show()
                    }) { Text("Date: $holidayDate") }
                    TextField(
                        value = holidayLabel,
                        onValueChange = { holidayLabel = it },
                        label = { Text("Label") }
                    )
                    Button(onClick = {
                        viewModel.addHoliday(holidayDate, holidayLabel)
                        holidayLabel = ""
                    }) { Text("Add holiday") }
                }
            }
            items(current.holidays.sorted(), key = { it.toString() }) { date ->
                Row {
                    Text(date.toString())
                    Button(onClick = { viewModel.removeHoliday(date) }) { Text("Remove") }
                }
            }
        }
    }
}
```

`SettingsRepository.saveWorkSchedule` (Task 10) wraps its write in a `Mutex` so that concurrent
calls from rapid edits here can't be reordered by Room's executor pool and silently overwrite one
another — see the updated code block in Task 10, Step 2.

- [x] **Step 3: Verify the project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [x] **Step 4: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/settings
git commit -m "feat: add settings screen for work hours, working days, and holidays"
```

---

## Task 14: Home Screen (Today's Summary)

`DailySummaryCard` is a shared composable — it's reused as-is by Day Detail (Task 16), so the layout only has to be built once.

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/ui/common/DailySummaryCard.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/home/HomeScreen.kt`

- [x] **Step 1: Write `DailySummaryCard.kt`**

```kotlin
package com.dailycallsreview.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailycallsreview.app.core.DailySummary
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

@Composable
fun DailySummaryCard(summary: DailySummary) {
    Card(modifier = Modifier.padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Date: ${summary.date}")
            Text("Total calls: ${summary.totalCalls}")
            Text("Total talk time: ${summary.totalTalkTimeSeconds / 60} min")
            Text("First call: ${summary.firstCallTime?.format(TIME_FORMAT) ?: "—"}")
            Text("Last call: ${summary.lastCallTime?.format(TIME_FORMAT) ?: "—"}")
            Text("Shift span: ${summary.shiftSpanSeconds?.let { formatDuration(it) } ?: "—"}")
            if (summary.offHoursCallCount > 0) {
                Text("⚠ ${summary.offHoursCallCount} call(s) outside working hours")
            }
            if (summary.isHoliday) Text("🎉 Holiday")
            if (summary.isNonWorkingDay) Text("Off day")
            Text("Per coworker:")
            summary.perCoworker.values.forEach { stat ->
                Text("  ${stat.displayName}: ${stat.callCount} calls, ${stat.totalTalkTimeSeconds / 60} min")
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

- [x] **Step 2: Write `HomeViewModel.kt`**

```kotlin
package com.dailycallsreview.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailycallsreview.app.core.CallAggregator
import com.dailycallsreview.app.core.DailySummary
import com.dailycallsreview.app.core.WorkSchedule
import com.dailycallsreview.app.data.SettingsRepository
import com.dailycallsreview.app.data.TeamRepository
import com.dailycallsreview.app.data.calllog.CallLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class HomeViewModel(
    private val callLogRepository: CallLogRepository,
    private val teamRepository: TeamRepository,
    private val settingsRepository: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)
    private val _todaySummary = MutableStateFlow<DailySummary?>(null)
    val todaySummary: StateFlow<DailySummary?> = _todaySummary.asStateFlow()

    init {
        viewModelScope.launch {
            combine(settingsRepository.observeWorkSchedule(), refreshTrigger) { schedule, _ -> schedule }
                .collectLatest { schedule -> refresh(schedule) }
        }
    }

    private suspend fun refresh(schedule: WorkSchedule) {
        val today = LocalDate.now(zone)
        val startMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val taggedNumbers = teamRepository.getTaggedNumbersOnce()
        val records = callLogRepository.getCallsBetween(startMillis, endMillis, taggedNumbers)
        _todaySummary.value = CallAggregator.computeDailySummary(today, records, schedule, zone)
    }

    fun refreshNow() {
        refreshTrigger.value += 1
    }
}
```

- [x] **Step 3: Write `HomeScreen.kt`**

```kotlin
package com.dailycallsreview.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.ui.common.DailySummaryCard

@Composable
fun HomeScreen(
    app: DailyCallsReviewApplication,
    onOpenHistory: () -> Unit,
    onOpenTeamSetup: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(app.callLogRepository, app.teamRepository, app.settingsRepository)
            }
        }
    )
    val summary by viewModel.todaySummary.collectAsState()

    Column {
        HomeActionBar(
            onOpenHistory = onOpenHistory,
            onOpenTeamSetup = onOpenTeamSetup,
            onOpenSettings = onOpenSettings,
            onRefresh = viewModel::refreshNow
        )
        summary?.let { DailySummaryCard(it) } ?: Text("Loading today's calls…")
    }
}

@Composable
private fun HomeActionBar(
    onOpenHistory: () -> Unit,
    onOpenTeamSetup: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    Row {
        Button(onClick = onOpenHistory) { Text("History") }
        Button(onClick = onOpenTeamSetup) { Text("Team") }
        Button(onClick = onOpenSettings) { Text("Settings") }
        Button(onClick = onRefresh) { Text("Refresh") }
    }
}
```

- [x] **Step 4: Verify the project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/common app/src/main/java/com/dailycallsreview/app/ui/home
git commit -m "feat: add home screen with today's daily summary"
```

---

## Task 15: History Screen (Month/Week + Filters)

Implements the spec's month/week toggle and the single-select filter chips (All / Off Days / Holidays / Outside Working Hours), with filters applying to whichever range is currently visible — not just "the month" (per the spec revision from the design review). The `OUTSIDE_HOURS` filter switches the list from days to individual off-hours calls, per spec.

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/ui/history/HistoryViewModel.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/history/HistoryScreen.kt`

- [x] **Step 1: Write `HistoryViewModel.kt`**

```kotlin
package com.dailycallsreview.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailycallsreview.app.core.CallAggregator
import com.dailycallsreview.app.core.CallRecord
import com.dailycallsreview.app.core.DailySummary
import com.dailycallsreview.app.core.WorkSchedule
import com.dailycallsreview.app.data.SettingsRepository
import com.dailycallsreview.app.data.TeamRepository
import com.dailycallsreview.app.data.calllog.CallLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class HistoryViewMode { MONTH, WEEK }
enum class HistoryFilter { ALL, OFF_DAYS, HOLIDAYS, OUTSIDE_HOURS }

data class HistoryUiState(
    val viewMode: HistoryViewMode = HistoryViewMode.MONTH,
    val filter: HistoryFilter = HistoryFilter.ALL,
    val anchorDate: LocalDate = LocalDate.now(),
    val days: List<DailySummary> = emptyList(),
    val offHoursCalls: List<CallRecord> = emptyList()
)

private data class LoadParams(
    val mode: HistoryViewMode,
    val filter: HistoryFilter,
    val anchor: LocalDate,
    val schedule: WorkSchedule
)

class HistoryViewModel(
    private val callLogRepository: CallLogRepository,
    private val teamRepository: TeamRepository,
    private val settingsRepository: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val viewMode = MutableStateFlow(HistoryViewMode.MONTH)
    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val anchorDate = MutableStateFlow(LocalDate.now(zone))

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                viewMode, filter, anchorDate, settingsRepository.observeWorkSchedule()
            ) { mode, currentFilter, anchor, schedule ->
                LoadParams(mode, currentFilter, anchor, schedule)
            }.collectLatest { params -> loadRange(params) }
        }
    }

    fun setViewMode(mode: HistoryViewMode) { viewMode.value = mode }
    fun setFilter(newFilter: HistoryFilter) { filter.value = newFilter }
    fun setAnchorDate(date: LocalDate) { anchorDate.value = date }

    private suspend fun loadRange(params: LoadParams) {
        val rangeStart = when (params.mode) {
            HistoryViewMode.MONTH -> params.anchor.withDayOfMonth(1)
            HistoryViewMode.WEEK -> params.anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        val rangeEnd = when (params.mode) {
            HistoryViewMode.MONTH -> params.anchor.withDayOfMonth(params.anchor.lengthOfMonth())
            HistoryViewMode.WEEK -> rangeStart.plusDays(6)
        }

        val taggedNumbers = teamRepository.getTaggedNumbersOnce()
        val startMillis = rangeStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = rangeEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val records = callLogRepository.getCallsBetween(startMillis, endMillis, taggedNumbers)
        val recordsByDate = records.groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }

        val days = generateSequence(rangeStart) { it.plusDays(1) }
            .takeWhile { !it.isAfter(rangeEnd) }
            .map { date -> CallAggregator.computeDailySummary(date, recordsByDate[date].orEmpty(), params.schedule, zone) }
            .toList()

        val offHoursCalls = records
            .filter { CallAggregator.isOffHours(it, params.schedule, zone) }
            .sortedBy { it.timestampMillis }

        val filteredDays = when (params.filter) {
            HistoryFilter.ALL -> days
            HistoryFilter.OFF_DAYS -> days.filter { it.isNonWorkingDay }
            HistoryFilter.HOLIDAYS -> days.filter { it.isHoliday }
            HistoryFilter.OUTSIDE_HOURS -> days
        }

        _uiState.value = HistoryUiState(
            viewMode = params.mode,
            filter = params.filter,
            anchorDate = params.anchor,
            days = filteredDays,
            offHoursCalls = if (params.filter == HistoryFilter.OUTSIDE_HOURS) offHoursCalls else emptyList()
        )
    }
}
```

- [x] **Step 2: Write `HistoryScreen.kt`**

```kotlin
package com.dailycallsreview.app.ui.history

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.ui.export.CsvExporter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HistoryScreen(
    app: DailyCallsReviewApplication,
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

    Column {
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
            Button(onClick = {
                if (state.filter == HistoryFilter.OUTSIDE_HOURS) {
                    CsvExporter.exportCallsAndShare(context, "history_export.csv", state.offHoursCalls, ZoneId.systemDefault())
                } else {
                    CsvExporter.exportDailySummariesAndShare(context, "history_export.csv", state.days)
                }
            }) { Text("Export CSV") }
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
```

Note: `HistoryScreen.kt` references `CsvExporter`, which is written in Task 17. This is fine — Task 17 is completed before Task 18 (final navigation wiring) touches this screen's compiled output, and the project as a whole isn't required to compile again until Task 17 adds the missing file. If executing tasks strictly in order and verifying compilation after every task, skip the "verify compiles" step below for this task and instead verify after Task 17.

- [x] **Step 3: Commit (do not run a compile check yet — `CsvExporter` doesn't exist until Task 17)**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/history
git commit -m "feat: add history screen with month/week toggle and filters"
```

---

## Task 16: Day Detail and Range Detail Screens

Day Detail reuses `DailySummaryCard` (Task 14) as-is. Range Detail composes `CallAggregator.computeRangeSummary` (Task 4) over the per-day summaries in the selected range — no new aggregation logic here, just wiring.

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/ui/daydetail/DayDetailViewModel.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/daydetail/DayDetailScreen.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/rangedetail/RangeDetailViewModel.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/ui/rangedetail/RangeDetailScreen.kt`

- [x] **Step 1: Write `DayDetailViewModel.kt`**

```kotlin
package com.dailycallsreview.app.ui.daydetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailycallsreview.app.core.CallAggregator
import com.dailycallsreview.app.core.DailySummary
import com.dailycallsreview.app.data.SettingsRepository
import com.dailycallsreview.app.data.TeamRepository
import com.dailycallsreview.app.data.calllog.CallLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class DayDetailViewModel(
    private val date: LocalDate,
    private val callLogRepository: CallLogRepository,
    private val teamRepository: TeamRepository,
    private val settingsRepository: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _summary = MutableStateFlow<DailySummary?>(null)
    val summary: StateFlow<DailySummary?> = _summary.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeWorkSchedule().collectLatest { schedule ->
                val taggedNumbers = teamRepository.getTaggedNumbersOnce()
                val startMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val endMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val records = callLogRepository.getCallsBetween(startMillis, endMillis, taggedNumbers)
                _summary.value = CallAggregator.computeDailySummary(date, records, schedule, zone)
            }
        }
    }
}
```

- [x] **Step 2: Write `DayDetailScreen.kt`**

```kotlin
package com.dailycallsreview.app.ui.daydetail

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.ui.common.DailySummaryCard
import java.time.LocalDate

@Composable
fun DayDetailScreen(app: DailyCallsReviewApplication, date: LocalDate) {
    val viewModel: DayDetailViewModel = viewModel(
        key = date.toString(),
        factory = viewModelFactory {
            initializer {
                DayDetailViewModel(date, app.callLogRepository, app.teamRepository, app.settingsRepository)
            }
        }
    )
    val summary by viewModel.summary.collectAsState()
    summary?.let { DailySummaryCard(it) } ?: Text("Loading…")
}
```

- [x] **Step 3: Write `RangeDetailViewModel.kt`**

Per the spec, filters (All / Off Days / Holidays / Outside Working Hours) apply here too, not just in History — so this reuses the `HistoryFilter` enum from Task 15 rather than duplicating it, and exposes both the aggregate `RangeSummary` rollup (shown under the `ALL` filter) and the filtered day/call lists (shown under the other filters), matching how `HistoryViewModel` works.

```kotlin
package com.dailycallsreview.app.ui.rangedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailycallsreview.app.core.CallAggregator
import com.dailycallsreview.app.core.CallRecord
import com.dailycallsreview.app.core.DailySummary
import com.dailycallsreview.app.core.RangeSummary
import com.dailycallsreview.app.data.SettingsRepository
import com.dailycallsreview.app.data.TeamRepository
import com.dailycallsreview.app.data.calllog.CallLogRepository
import com.dailycallsreview.app.ui.history.HistoryFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class RangeDetailUiState(
    val filter: HistoryFilter = HistoryFilter.ALL,
    val summary: RangeSummary? = null,
    val filteredDays: List<DailySummary> = emptyList(),
    val offHoursCalls: List<CallRecord> = emptyList()
)

class RangeDetailViewModel(
    startDate: LocalDate,
    endDate: LocalDate,
    private val callLogRepository: CallLogRepository,
    private val teamRepository: TeamRepository,
    private val settingsRepository: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    // Normalized defensively: callers (e.g. History's custom-range date pickers) don't validate
    // that endDate >= startDate, and an inverted range would make generateSequence/takeWhile
    // below yield an empty list, which CallAggregator.computeRangeSummary rejects via `require`.
    private val rangeStart = minOf(startDate, endDate)
    private val rangeEnd = maxOf(startDate, endDate)

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    private val _uiState = MutableStateFlow(RangeDetailUiState())
    val uiState: StateFlow<RangeDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(filter, settingsRepository.observeWorkSchedule()) { currentFilter, schedule ->
                currentFilter to schedule
            }.collectLatest { (currentFilter, schedule) ->
                val taggedNumbers = teamRepository.getTaggedNumbersOnce()
                val startMillis = rangeStart.atStartOfDay(zone).toInstant().toEpochMilli()
                val endMillis = rangeEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val records = callLogRepository.getCallsBetween(startMillis, endMillis, taggedNumbers)
                val recordsByDate = records.groupBy {
                    Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate()
                }
                val dailySummaries = generateSequence(rangeStart) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(rangeEnd) }
                    .map { date -> CallAggregator.computeDailySummary(date, recordsByDate[date].orEmpty(), schedule, zone) }
                    .toList()

                val offHoursCalls = records
                    .filter { CallAggregator.isOffHours(it, schedule, zone) }
                    .sortedBy { it.timestampMillis }

                val filteredDays = when (currentFilter) {
                    HistoryFilter.ALL -> dailySummaries
                    HistoryFilter.OFF_DAYS -> dailySummaries.filter { it.isNonWorkingDay }
                    HistoryFilter.HOLIDAYS -> dailySummaries.filter { it.isHoliday }
                    HistoryFilter.OUTSIDE_HOURS -> dailySummaries
                }

                _uiState.value = RangeDetailUiState(
                    filter = currentFilter,
                    summary = CallAggregator.computeRangeSummary(dailySummaries),
                    filteredDays = filteredDays,
                    offHoursCalls = if (currentFilter == HistoryFilter.OUTSIDE_HOURS) offHoursCalls else emptyList()
                )
            }
        }
    }

    fun setFilter(newFilter: HistoryFilter) { filter.value = newFilter }
}
```

- [x] **Step 4: Write `RangeDetailScreen.kt`**

Under the `ALL` filter this shows the aggregate rollup (as before); under `OUTSIDE_HOURS` it shows individual calls; under the remaining filters it shows the matching day list (reusing the `Day Detail` navigation, same as History). Export also respects whichever filter is active, per spec.

```kotlin
package com.dailycallsreview.app.ui.rangedetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.ui.export.CsvExporter
import com.dailycallsreview.app.ui.history.HistoryFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun RangeDetailScreen(
    app: DailyCallsReviewApplication,
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

    Column {
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

        Button(onClick = {
            when (state.filter) {
                HistoryFilter.ALL -> state.summary?.let {
                    CsvExporter.exportRangeAndShare(context, "range_export.csv", it)
                }
                HistoryFilter.OUTSIDE_HOURS -> CsvExporter.exportCallsAndShare(
                    context, "range_export.csv", state.offHoursCalls, ZoneId.systemDefault()
                )
                else -> CsvExporter.exportDailySummariesAndShare(context, "range_export.csv", state.filteredDays)
            }
        }) { Text("Export CSV") }
    }
}
```

- [x] **Step 5: Commit (compile check deferred to Task 17, same reason as Task 15)**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/daydetail app/src/main/java/com/dailycallsreview/app/ui/rangedetail
git commit -m "feat: add day detail and range detail screens"
```

---

## Task 17: CSV Export

Provides three writers matching the three shapes of data History/Range Detail can show on screen (day list, individual off-hours calls, range rollup) — per the spec's "export respects exactly what's on screen" requirement. Needs a `FileProvider` to share the generated file via Android's share sheet, since apps can't hand another app a raw file path.

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/ui/export/CsvExporter.kt`
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [x] **Step 1: Write `CsvExporter.kt`**

```kotlin
package com.dailycallsreview.app.ui.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.dailycallsreview.app.core.CallRecord
import com.dailycallsreview.app.core.DailySummary
import com.dailycallsreview.app.core.RangeSummary
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExporter {

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

    fun exportDailySummariesAndShare(context: Context, fileName: String, summaries: List<DailySummary>) {
        val file = newExportFile(context, fileName)
        FileWriter(file).use { writer ->
            writer.appendLine(
                "Date,Total Calls,Total Talk Time (min),First Call,Last Call,Shift Span,Off-Hours Calls,Holiday,Non-Working Day"
            )
            for (summary in summaries) {
                writer.appendLine(
                    listOf(
                        summary.date.toString(),
                        summary.totalCalls.toString(),
                        (summary.totalTalkTimeSeconds / 60).toString(),
                        summary.firstCallTime?.format(TIME_FORMAT) ?: "",
                        summary.lastCallTime?.format(TIME_FORMAT) ?: "",
                        summary.shiftSpanSeconds?.let { formatDuration(it) } ?: "",
                        summary.offHoursCallCount.toString(),
                        summary.isHoliday.toString(),
                        summary.isNonWorkingDay.toString()
                    ).joinToString(",")
                )
            }
        }
        shareFile(context, file)
    }

    fun exportCallsAndShare(context: Context, fileName: String, calls: List<CallRecord>, zone: ZoneId) {
        val file = newExportFile(context, fileName)
        FileWriter(file).use { writer ->
            writer.appendLine("Date,Time,Contact,Duration (sec),Type")
            for (call in calls) {
                val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(call.timestampMillis), zone)
                writer.appendLine(
                    listOf(
                        dateTime.toLocalDate().toString(),
                        dateTime.toLocalTime().format(TIME_FORMAT),
                        csvField(call.contactName ?: call.phoneNumberLast10),
                        call.durationSeconds.toString(),
                        call.type.name
                    ).joinToString(",")
                )
            }
        }
        shareFile(context, file)
    }

    fun exportRangeAndShare(context: Context, fileName: String, summary: RangeSummary) {
        val file = newExportFile(context, fileName)
        FileWriter(file).use { writer ->
            writer.appendLine(
                listOf("Range", "${summary.startDate} to ${summary.endDate}").joinToString(",")
            )
            writer.appendLine(listOf("Total Calls", summary.totalCalls.toString()).joinToString(","))
            writer.appendLine(
                listOf("Total Talk Time (min)", (summary.totalTalkTimeSeconds / 60).toString()).joinToString(",")
            )
            writer.appendLine(
                listOf(
                    "Average Shift Span",
                    summary.averageShiftSpanSeconds?.let { formatDuration(it) } ?: ""
                ).joinToString(",")
            )
            writer.appendLine(
                listOf("Busiest Coworker", csvField(summary.busiestCoworker?.displayName ?: "")).joinToString(",")
            )
            writer.appendLine(listOf("Off-Hours Calls", summary.offHoursCallCount.toString()).joinToString(","))
            writer.appendLine(listOf("Holidays In Range", summary.holidayCount.toString()).joinToString(","))
            writer.appendLine(
                listOf("Non-Working Days In Range", summary.nonWorkingDayCount.toString()).joinToString(",")
            )
            writer.appendLine()
            writer.appendLine("Coworker,Calls,Talk Time (min)")
            for (stat in summary.perCoworker.values) {
                writer.appendLine(
                    listOf(
                        csvField(stat.displayName),
                        stat.callCount.toString(),
                        (stat.totalTalkTimeSeconds / 60).toString()
                    ).joinToString(",")
                )
            }
        }
        shareFile(context, file)
    }

    private fun newExportFile(context: Context, fileName: String): File {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(exportsDir, fileName)
    }

    private fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Daily Calls Review"))
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return "${hours}h ${minutes}m"
    }

    private fun csvField(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}
```

- [x] **Step 2: Create `app/src/main/res/xml/file_paths.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="exports" path="exports/" />
</paths>
```

- [x] **Step 3: Register the `FileProvider` in the manifest**

Modify `app/src/main/AndroidManifest.xml` — add inside `<application>`, after the `<activity>` block:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

- [x] **Step 4: Verify the whole project compiles (this resolves the `CsvExporter` references left open in Tasks 15–16)**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/export app/src/main/res/xml/file_paths.xml app/src/main/AndroidManifest.xml
git commit -m "feat: add CSV export with share-sheet integration"
```

---

## Task 18: Navigation and App Shell (Final Wiring)

Wires every screen from Tasks 12–16 together via Navigation-Compose, and replaces the Task 1 placeholder `MainActivity` with the real shell: `PermissionsGate` wrapping `AppNavGraph`.

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/ui/nav/NavGraph.kt`
- Modify: `app/src/main/java/com/dailycallsreview/app/MainActivity.kt`

- [ ] **Step 1: Write `NavGraph.kt`**

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
            HomeScreen(
                app = app,
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenTeamSetup = { navController.navigate(Routes.TEAM_SETUP) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                app = app,
                onOpenDay = { date -> navController.navigate(Routes.dayDetail(date)) },
                onOpenRange = { start, end -> navController.navigate(Routes.rangeDetail(start, end)) }
            )
        }
        composable(Routes.TEAM_SETUP) {
            TeamSetupScreen(app = app)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(app = app)
        }
        composable(
            route = Routes.DAY_DETAIL,
            arguments = listOf(navArgument("date") { type = NavType.StringType })
        ) { backStackEntry ->
            val date = LocalDate.parse(backStackEntry.arguments?.getString("date"))
            DayDetailScreen(app = app, date = date)
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
                startDate = start,
                endDate = end,
                onOpenDay = { date -> navController.navigate(Routes.dayDetail(date)) }
            )
        }
    }
}
```

- [ ] **Step 2: Replace `MainActivity.kt` with the real app shell**

```kotlin
package com.dailycallsreview.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dailycallsreview.app.ui.nav.AppNavGraph
import com.dailycallsreview.app.ui.permissions.PermissionsGate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DailyCallsReviewApplication
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PermissionsGate {
                        AppNavGraph(app = app)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Verify the full project compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests from Tasks 3, 4, and 7 pass (17 tests total: 4 + 9 + 4).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/ui/nav app/src/main/java/com/dailycallsreview/app/MainActivity.kt
git commit -m "feat: wire navigation graph and replace placeholder MainActivity"
```

---

## Task 19: Home Screen Widget

The widget itself queries live (same repositories, same `CallAggregator`) every time it's asked to render — there's no separate cached snapshot to keep in sync. A `WorkManager` periodic job (15-minute minimum, per the platform) is the only thing that triggers a re-render on a timer; per the spec's Known Limitations, the widget shows a neutral placeholder before setup is complete (no permissions or no tagged coworkers) rather than a blank/error state.

**Files:**
- Create: `app/src/main/java/com/dailycallsreview/app/widget/TodayWidget.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/widget/TodayWidgetReceiver.kt`
- Create: `app/src/main/java/com/dailycallsreview/app/widget/TodayWidgetWorker.kt`
- Create: `app/src/main/res/xml/today_widget_info.xml`
- Create: `app/src/main/res/layout/glance_default_loading_layout.xml`
- Modify: `app/src/main/java/com/dailycallsreview/app/DailyCallsReviewApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Write `TodayWidget.kt`**

```kotlin
package com.dailycallsreview.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.unit.dp
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.MainActivity
import com.dailycallsreview.app.core.CallAggregator
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as DailyCallsReviewApplication
        val taggedNumbers = app.teamRepository.getTaggedNumbersOnce()

        if (taggedNumbers.isEmpty()) {
            provideContent {
                Column(modifier = GlanceModifier.padding(12.dp).clickable(actionStartActivity<MainActivity>())) {
                    Text("Tag coworkers in the app to see today's stats here")
                }
            }
            return
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val schedule = app.settingsRepository.observeWorkSchedule().first()
        val startMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val records = app.callLogRepository.getCallsBetween(startMillis, endMillis, taggedNumbers)
        val summary = CallAggregator.computeDailySummary(today, records, schedule, zone)

        provideContent {
            Column(modifier = GlanceModifier.padding(12.dp).clickable(actionStartActivity<MainActivity>())) {
                Text("Today: ${summary.totalCalls} calls")
                Text("${summary.totalTalkTimeSeconds / 60} min")
            }
        }
    }
}
```

- [ ] **Step 2: Write `TodayWidgetReceiver.kt`**

```kotlin
package com.dailycallsreview.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
```

- [ ] **Step 3: Write `TodayWidgetWorker.kt`**

```kotlin
package com.dailycallsreview.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class TodayWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        TodayWidget().updateAll(applicationContext)
        return Result.success()
    }
}
```

- [ ] **Step 4: Create `app/src/main/res/layout/glance_default_loading_layout.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

- [ ] **Step 5: Create `app/src/main/res/xml/today_widget_info.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="100dp"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen" />
```

`updatePeriodMillis="0"` is intentional — the widget's own refresh cycle is driven entirely by the `WorkManager` job (Step 7), not by the OS's built-in (and less reliable, minimum-30-minute) AppWidget update mechanism.

- [ ] **Step 6: Register the widget receiver in the manifest**

Modify `app/src/main/AndroidManifest.xml` — add inside `<application>`, after the `FileProvider` block from Task 17:

```xml
<receiver
    android:name=".widget.TodayWidgetReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/today_widget_info" />
</receiver>
```

- [ ] **Step 7: Modify `DailyCallsReviewApplication.kt` to enqueue the periodic refresh**

```kotlin
package com.dailycallsreview.app

import android.app.Application
import androidx.room.Room
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dailycallsreview.app.data.SettingsRepository
import com.dailycallsreview.app.data.TeamRepository
import com.dailycallsreview.app.data.calllog.CallLogRepository
import com.dailycallsreview.app.data.contacts.ContactsRepository
import com.dailycallsreview.app.data.db.AppDatabase
import com.dailycallsreview.app.widget.TodayWidgetWorker
import java.util.concurrent.TimeUnit

class DailyCallsReviewApplication : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "daily-calls-review.db").build()
    }

    val teamRepository: TeamRepository by lazy { TeamRepository(database.taggedContactDao()) }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(database.appSettingsDao(), database.holidayDao())
    }
    val callLogRepository: CallLogRepository by lazy { CallLogRepository(applicationContext) }
    val contactsRepository: ContactsRepository by lazy { ContactsRepository(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        val request = PeriodicWorkRequestBuilder<TodayWidgetWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "today_widget_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
```

- [ ] **Step 8: Verify the full project compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/dailycallsreview/app/widget app/src/main/res/xml/today_widget_info.xml app/src/main/res/layout/glance_default_loading_layout.xml app/src/main/java/com/dailycallsreview/app/DailyCallsReviewApplication.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add home screen widget with periodic refresh"
```

---

## Task 20: Manual End-to-End Verification

Per the spec's Testing Approach, `CallLog`/`Contacts` content provider behavior and the widget can't be fully unit tested — this final task is a manual checklist on a real device or emulator with the Android SDK.

**Files:** none (verification only)

- [ ] **Step 1: Install the app**

Run: `./gradlew installDebug`
Expected: app installs and launches, showing the permissions rationale screen (no permissions granted yet).

- [ ] **Step 2: Grant permissions and verify the empty state clears**

Tap "Grant access", allow both Call Log and Contacts. Expected: Home screen appears (likely showing zero calls, since no coworkers are tagged yet).

- [ ] **Step 3: Insert fake call log entries for testing**

Real calls are slow to generate manually — use `adb` to insert synthetic call log rows instead:

```bash
adb shell content insert --uri content://call_log/calls \
  --bind number:s:9876543210 --bind date:l:$(date +%s000) \
  --bind duration:i:120 --bind type:i:2 --bind name:s:"Test Coworker"
```

(`type:i:2` = `OUTGOING_TYPE`. Repeat with `type:i:1` for `INCOMING_TYPE`, `type:i:3` for `MISSED_TYPE`, varying `number` and `date` to build a realistic multi-day, multi-contact history.)

- [ ] **Step 4: Tag a coworker**

Open Team, find a contact whose number matches one of the inserted call log entries (or add that number to a real contact first), tag it. Expected: checkbox stays checked after leaving and returning to the screen.

- [ ] **Step 5: Verify Home screen totals**

Return to Home, tap Refresh. Expected: total calls, total talk time, first/last call time, and shift span match the inserted test data; per-coworker breakdown lists the tagged contact.

- [ ] **Step 6: Verify Settings changes affect off-hours flagging**

Set working hours narrower than one of the test calls' time (e.g. 9 AM–5 PM when a test call was inserted at 8 PM). Return to Home. Expected: that call is now counted in the off-hours badge.

- [ ] **Step 7: Verify History month/week toggle and filters**

Open History. Expected: Month view lists every day of the current month; Week view narrows to the current Mon–Sun; tapping a day opens Day Detail with matching totals. Apply each filter (Off Days, Holidays, Outside Working Hours) and confirm the list narrows correctly — Outside Working Hours should show individual calls, not days.

- [ ] **Step 8: Verify Range Detail**

Tap "Custom range", pick a start and end date spanning several of the inserted test calls. Expected: Range Detail shows aggregated totals, a non-blank average shift span, and a busiest coworker matching the test data.

- [ ] **Step 9: Verify CSV export**

Tap "Export CSV" from History (in each filter mode) and from Range Detail. Expected: Android's share sheet opens; sharing to a file manager or emailing it to yourself produces a valid CSV, openable in a spreadsheet app, with rows matching what was on screen.

- [ ] **Step 10: Verify the home screen widget**

Long-press the device home screen, add the "Daily Calls Review" widget. Expected: widget shows today's call count and total time (or the "tag coworkers" placeholder if none are tagged); tapping it opens the app. Force the periodic worker to run immediately rather than waiting 15 minutes:

```bash
adb shell cmd jobscheduler run -f com.dailycallsreview.app <JOB_ID>
```

(Find `<JOB_ID>` via `adb shell dumpsys jobscheduler | grep -A 2 com.dailycallsreview.app` if it isn't obvious from the WorkManager-assigned job list.) Insert another fake call via Step 3's command, force the job, and confirm the widget's count updates without reopening the app.

- [ ] **Step 11: Verify permission revocation**

In Android Settings, revoke Call Log or Contacts permission for the app, then reopen it. Expected: the permissions rationale screen reappears (no crash, no silent empty state).

This completes the implementation. All 20 tasks together deliver every requirement in the design spec.
