package com.scenicroute.ui.mydrives

import com.google.common.truth.Truth.assertThat
import com.scenicroute.auth.AuthRepository
import com.scenicroute.auth.AuthState
import com.scenicroute.data.db.entities.DriveEntity
import com.scenicroute.data.repo.DriveRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyDrivesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun drive(
        id: String,
        startedAt: Long,
        distanceM: Int,
        durationS: Int,
    ) = DriveEntity(
        id = id,
        ownerUid = "u",
        title = "drive-$id",
        status = "DRAFT",
        visibility = "PRIVATE",
        startLat = 0.0,
        startLng = 0.0,
        startedAt = startedAt,
        distanceM = distanceM,
        durationS = durationS,
        syncState = "LOCAL",
    )

    /**
     * StateFlow with WhileSubscribed only collects upstream when subscribed. Spin a hot
     * collector on the test scope so values flow, then read .value.
     */
    private fun <T> TestScope.collectHot(flow: StateFlow<T>): Job =
        launch { flow.collect { /* drain */ } }

    @Test fun default_sort_is_newest_first() = runTest(dispatcher) {
        val drives = listOf(
            drive("a", startedAt = 100, distanceM = 5_000, durationS = 600),
            drive("b", startedAt = 300, distanceM = 1_000, durationS = 100),
            drive("c", startedAt = 200, distanceM = 9_000, durationS = 1200),
        )
        val vm = newVm(drives)
        val job = collectHot(vm.drives)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.drives.value.map { it.id }).containsExactly("b", "c", "a").inOrder()
        job.cancel()
    }

    @Test fun setSort_distance_desc() = runTest(dispatcher) {
        val drives = listOf(
            drive("a", startedAt = 100, distanceM = 5_000, durationS = 600),
            drive("b", startedAt = 300, distanceM = 1_000, durationS = 100),
            drive("c", startedAt = 200, distanceM = 9_000, durationS = 1200),
        )
        val vm = newVm(drives)
        val job = collectHot(vm.drives)
        vm.setSort(DriveSort.DISTANCE_DESC)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.drives.value.map { it.id }).containsExactly("c", "a", "b").inOrder()
        job.cancel()
    }

    @Test fun setSort_duration_desc() = runTest(dispatcher) {
        val drives = listOf(
            drive("a", startedAt = 100, distanceM = 5_000, durationS = 600),
            drive("b", startedAt = 300, distanceM = 1_000, durationS = 100),
            drive("c", startedAt = 200, distanceM = 9_000, durationS = 1200),
        )
        val vm = newVm(drives)
        val job = collectHot(vm.drives)
        vm.setSort(DriveSort.DURATION_DESC)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.drives.value.map { it.id }).containsExactly("c", "a", "b").inOrder()
        job.cancel()
    }

    @Test fun setSort_date_asc() = runTest(dispatcher) {
        val drives = listOf(
            drive("a", startedAt = 100, distanceM = 5_000, durationS = 600),
            drive("b", startedAt = 300, distanceM = 1_000, durationS = 100),
            drive("c", startedAt = 200, distanceM = 9_000, durationS = 1200),
        )
        val vm = newVm(drives)
        val job = collectHot(vm.drives)
        vm.setSort(DriveSort.DATE_ASC)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.drives.value.map { it.id }).containsExactly("a", "c", "b").inOrder()
        job.cancel()
    }

    @Test fun signed_out_yields_empty_list() = runTest(dispatcher) {
        val auth = mockk<AuthRepository>()
        every { auth.authState } returns flowOf(AuthState.Anonymous)
        val driveRepo = mockk<DriveRepository>()
        every { driveRepo.observeDrives(any()) } returns flowOf(emptyList())
        every { driveRepo.observeTrashedCount(any()) } returns flowOf(0)
        val vm = MyDrivesViewModel(auth, driveRepo)
        val job = collectHot(vm.drives)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.drives.value).isEmpty()
        job.cancel()
    }

    private fun newVm(drives: List<DriveEntity>): MyDrivesViewModel {
        val auth = mockk<AuthRepository>()
        every { auth.authState } returns MutableStateFlow(
            AuthState.SignedIn(
                uid = "u",
                email = "u@example.com",
                displayName = "u",
                photoUrl = null,
                isEmailVerified = true,
            ),
        )
        val driveRepo = mockk<DriveRepository>()
        every { driveRepo.observeDrives("u") } returns flowOf(drives)
        every { driveRepo.observeTrashedCount("u") } returns flowOf(0)
        return MyDrivesViewModel(auth, driveRepo)
    }
}
