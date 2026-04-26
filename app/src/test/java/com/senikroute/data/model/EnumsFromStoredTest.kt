package com.senikroute.data.model

import com.google.common.truth.Truth.assertThat
import com.senikroute.data.profile.ProfileVisibility
import org.junit.Test

class EnumsFromStoredTest {

    @Test fun visibility_known_values() {
        assertThat(Visibility.fromStored("PRIVATE")).isEqualTo(Visibility.PRIVATE)
        assertThat(Visibility.fromStored("UNLISTED")).isEqualTo(Visibility.UNLISTED)
        assertThat(Visibility.fromStored("PUBLIC")).isEqualTo(Visibility.PUBLIC)
    }

    @Test fun visibility_unknown_falls_back_to_private() {
        assertThat(Visibility.fromStored("garbage")).isEqualTo(Visibility.PRIVATE)
        assertThat(Visibility.fromStored("")).isEqualTo(Visibility.PRIVATE)
        assertThat(Visibility.fromStored("public")).isEqualTo(Visibility.PRIVATE) // case-sensitive
    }

    @Test fun driveStatus_known_values() {
        assertThat(DriveStatus.fromStored("RECORDING")).isEqualTo(DriveStatus.RECORDING)
        assertThat(DriveStatus.fromStored("DRAFT")).isEqualTo(DriveStatus.DRAFT)
        assertThat(DriveStatus.fromStored("PUBLISHED")).isEqualTo(DriveStatus.PUBLISHED)
    }

    @Test fun driveStatus_unknown_falls_back_to_draft() {
        assertThat(DriveStatus.fromStored("nope")).isEqualTo(DriveStatus.DRAFT)
    }

    @Test fun syncState_known_values() {
        assertThat(SyncState.fromStored("LOCAL")).isEqualTo(SyncState.LOCAL)
        assertThat(SyncState.fromStored("SYNCING")).isEqualTo(SyncState.SYNCING)
        assertThat(SyncState.fromStored("SYNCED")).isEqualTo(SyncState.SYNCED)
        assertThat(SyncState.fromStored("DIRTY")).isEqualTo(SyncState.DIRTY)
    }

    @Test fun syncState_unknown_falls_back_to_local() {
        assertThat(SyncState.fromStored("nope")).isEqualTo(SyncState.LOCAL)
    }

    @Test fun profileVisibility_known_values_case_insensitive() {
        assertThat(ProfileVisibility.fromStored("PRIVATE")).isEqualTo(ProfileVisibility.PRIVATE)
        assertThat(ProfileVisibility.fromStored("private")).isEqualTo(ProfileVisibility.PRIVATE)
        assertThat(ProfileVisibility.fromStored("PUBLIC")).isEqualTo(ProfileVisibility.PUBLIC)
        assertThat(ProfileVisibility.fromStored("public")).isEqualTo(ProfileVisibility.PUBLIC)
    }

    @Test fun profileVisibility_unknown_or_null_defaults_to_private() {
        assertThat(ProfileVisibility.fromStored(null)).isEqualTo(ProfileVisibility.PRIVATE)
        assertThat(ProfileVisibility.fromStored("nope")).isEqualTo(ProfileVisibility.PRIVATE)
    }
}
