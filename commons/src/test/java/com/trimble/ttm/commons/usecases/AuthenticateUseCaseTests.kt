package com.trimble.ttm.commons.usecases

import com.google.firebase.auth.FirebaseAuth
import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.AuthResult
import com.trimble.ttm.commons.model.DeviceFcmToken
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.repo.DeviceAuthRepo
import com.trimble.ttm.commons.repo.FCMDeviceTokenRepository
import com.trimble.ttm.commons.repo.FeatureFlagCacheRepo
import com.trimble.ttm.commons.repo.FirebaseAuthRepo
import com.trimble.ttm.commons.repo.ManagedConfigurationRepo
import com.trimble.ttm.commons.usecase.AuthenticateUseCase
import com.trimble.ttm.commons.utils.AUTH_DEVICE_ERROR
import com.trimble.ttm.commons.utils.AUTH_SERVER_ERROR
import com.trimble.ttm.commons.utils.AUTH_SUCCESS
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.INTENT_CATEGORY_LAUNCHER
import com.trimble.ttm.commons.utils.TRIP_INFO_WIDGET
import com.trimble.ttm.commons.utils.WORKFLOW_SHORTCUT_USE_COUNT
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticateUseCaseTests {

    @RelaxedMockK
    private lateinit var firebaseAuth: FirebaseAuth

    @RelaxedMockK
    private lateinit var deviceAuthRepo: DeviceAuthRepo

    @RelaxedMockK
    private lateinit var firebaseAuthRepo: FirebaseAuthRepo

    @RelaxedMockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator

    @RelaxedMockK
    private lateinit var featureFlagCacheRepo: FeatureFlagCacheRepo

    @RelaxedMockK
    private lateinit var firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder

    private lateinit var fcmDeviceTokenRepository: FCMDeviceTokenRepository
    private lateinit var authUsecase: AuthenticateUseCase
    private lateinit var managedConfigurationRepo: ManagedConfigurationRepo

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        fcmDeviceTokenRepository = mockk()
        managedConfigurationRepo = mockk()

        authUsecase = AuthenticateUseCase(
            deviceAuthRepo,
            firebaseAuthRepo,
            fcmDeviceTokenRepository,
            featureFlagCacheRepo,
            firebaseAnalyticEventRecorder = firebaseAnalyticEventRecorder,
            managedConfigurationRepo = managedConfigurationRepo,
            appModuleCommunicator = appModuleCommunicator,
            firebaseAuth = firebaseAuth
        )
        mockkObject(Log)
        every {
            Log.n(any(), any(), any())
        } returns Unit
    }

    @Test
    fun `verify non empty device token is returned`() = runTest {

        coEvery { deviceAuthRepo.getDeviceToken(any()) } returns "c1234ds78&^%%"

        assertEquals(authUsecase.getDeviceAccessToken("abc"), "c1234ds78&^%%")
    }

    @Test
    fun `verify empty device token is returned`() = runTest {

        coEvery { deviceAuthRepo.getDeviceToken(any()) } returns null

        assertTrue(authUsecase.getDeviceAccessToken("abc").isEmpty())
    }

    @Test
    fun `verify non empty firebase token is returned`() = runTest {

        coEvery { firebaseAuthRepo.getFireBaseToken(any()) } returns "c1234ds78&^%%"

        assertEquals(authUsecase.getFireBaseToken("abc"), "c1234ds78&^%%")
    }

    @Test
    fun `verify empty firebase token is returned`() = runTest {

        coEvery { firebaseAuthRepo.getFireBaseToken(any()) } returns null

        assertTrue(authUsecase.getDeviceAccessToken("abc").isEmpty())
    }

    @Test
    fun `authenticate firestore runs`(): Unit = runTest {
        coEvery { firebaseAuthRepo.authenticateFirestore(any()) } returns AuthResult(true, null)

        authUsecase.authenticateFirestore("fasdf")
    }

    @Test
    fun `register Device Specific Token To FireStore when old truckNumber is empty`() = runTest {

        coEvery { authUsecase.getAppModuleCommunicator() } returns appModuleCommunicator
        coEvery { fcmDeviceTokenRepository.storeFCMTokenInFirestore(any(), any()) } returns true
        coEvery { fcmDeviceTokenRepository.deleteFCMTokenFromFirestore(any()) } returns true

        authUsecase.registerDeviceSpecificTokenToFireStore(
            "123",
            "4234",
            "SDFRTYUUGHGF",
            EMPTY_STRING,
            EMPTY_STRING
        )

        coVerify(exactly = 1) {
            fcmDeviceTokenRepository.deleteFCMTokenFromFirestore(any())
        }
        coVerify(exactly = 1) {
            fcmDeviceTokenRepository.storeFCMTokenInFirestore(any(), any())
        }
        coVerify(exactly = 1) {
            appModuleCommunicator.setFCMTokenFirestoreStatusInDataStore(any())
        }
    }

    @Test
    fun `unregister Device Specific Token for vehicleId from FireStore`() = runTest {

        coEvery { fcmDeviceTokenRepository.deleteFCMTokenFromFirestore(any()) } returns true
        authUsecase.unRegisterDeviceSpecificTokenFromFireStore("123", "32")
    }

    @Test
    fun `verify if consumerKey empty and is getDeviceAccessToken called once`() = runTest {
        val consumerKey = EMPTY_STRING
        coEvery { deviceAuthRepo.getDeviceToken(consumerKey) } returns EMPTY_STRING
        assertEquals(
            AUTH_DEVICE_ERROR, authUsecase.doAuthentication(consumerKey).message
        )
        coVerify(exactly = 1) {
            authUsecase.getDeviceAccessToken(consumerKey)
        }

        coVerify(exactly = 0) {
            authUsecase.getFireBaseToken(any())
        }
    }

    @Test
    fun `verify if consumerKey is NOT empty and is getDeviceAccessToken and getFireBaseToken called once`() =
        runTest {
            val consumerKey = "testData"
            coEvery { deviceAuthRepo.getDeviceToken(consumerKey) } returns "testData"
            coEvery { firebaseAuthRepo.getFireBaseToken(consumerKey) } returns EMPTY_STRING
            assertEquals(
                AUTH_SERVER_ERROR, authUsecase.doAuthentication(consumerKey).message
            )
            coVerify(exactly = 1) {
                authUsecase.getDeviceAccessToken(consumerKey)
            }

            coVerify(exactly = 1) {
                authUsecase.getFireBaseToken(any())
            }

            coVerify(exactly = 0) {
                authUsecase.authenticateFirestore(any())
            }

        }

    @Test
    fun `verify if consumerKey and getDeviceAccessToken,getFireBaseToken are NOT empty then authenticateFirestore called once`() =
        runTest {
            val consumerKey = "testData"
            coEvery { deviceAuthRepo.getDeviceToken(consumerKey) } returns "testDeviceToken"
            coEvery { firebaseAuthRepo.getFireBaseToken("testDeviceToken") } returns "testFirebaseToken"
            coEvery { firebaseAuthRepo.authenticateFirestore("testFirebaseToken") } returns null

            assertEquals(
                AUTH_SERVER_ERROR, authUsecase.doAuthentication(consumerKey).message
            )
            coVerify(exactly = 1) {
                authUsecase.getDeviceAccessToken(consumerKey)
            }

            coVerify(exactly = 1) {
                authUsecase.getFireBaseToken(any())
            }

            coVerify(exactly = 1) {
                authUsecase.authenticateFirestore(any())
            }
        }

    @Test
    fun `verify all tokens are got and firestore authenticated - SUCCESS`() = runTest {
        val consumerKey = "testData"
        coEvery { deviceAuthRepo.getDeviceToken(consumerKey) } returns "testDeviceToken"
        coEvery { firebaseAuthRepo.getFireBaseToken("testDeviceToken") } returns "testFirebaseToken"
        coEvery { firebaseAuthRepo.authenticateFirestore("testFirebaseToken") } returns AuthResult(
            true,
            null
        )
        coEvery { firebaseAuth.currentUser?.uid } returns "abcdef"

        assertEquals(
            AUTH_SUCCESS, authUsecase.doAuthentication(consumerKey).message
        )
    }

    @Test
    fun `verify all tokens are got and firestore authentication - FAILED`() = runTest {
        val consumerKey = "testData"
        coEvery { deviceAuthRepo.getDeviceToken(consumerKey) } returns "testDeviceToken"
        coEvery { firebaseAuthRepo.getFireBaseToken("testDeviceToken") } returns "testFirebaseToken"
        coEvery { firebaseAuthRepo.authenticateFirestore("testFirebaseToken") } returns AuthResult(
            false,
            null
        )

        assertEquals(
            AUTH_SERVER_ERROR, authUsecase.doAuthentication(consumerKey).message
        )
    }


    @Test
    fun `verify fetchDeviceSpecificToken call`() = runTest {    //NOSONAR
        coEvery {
            authUsecase.fetchFCMDeviceToken()
        } returns "272727bhh"
        authUsecase.fetchFCMDeviceToken().let {
            Assert.assertEquals("272727bhh", it)
        }
    }

    @Test
    fun `verify save device specific fcm token call`() = runTest {
        coEvery { authUsecase.getAppModuleCommunicator() } returns appModuleCommunicator
        coEvery {
            fcmDeviceTokenRepository.deleteFCMTokenFromFirestore(any())
        } returns true

        coEvery {
            fcmDeviceTokenRepository.storeFCMTokenInFirestore(any(), any())
        } returns true
        authUsecase.registerDeviceSpecificTokenToFireStore(
            "123",
            "4234",
            "SDFRTYUUGHGF",
            EMPTY_STRING,
            EMPTY_STRING
        )
        coVerify(exactly = 1) {
            fcmDeviceTokenRepository.storeFCMTokenInFirestore(any(), any())
        }
        coVerify(exactly = 1) {
            appModuleCommunicator.setFCMTokenFirestoreStatusInDataStore(any())
        }
    }

    @Test
    fun `verify delete old fcm token for given vehicle`() = runTest {
        coEvery {
            fcmDeviceTokenRepository.deleteFCMTokenFromFirestore(any())
        } returns true
        authUsecase.unRegisterDeviceSpecificTokenFromFireStore("123", "4234")
        coVerify(exactly = 1) {
            fcmDeviceTokenRepository.deleteFCMTokenFromFirestore(any())
        }
    }

    @Test
    fun `verify delete old fcm token for both current and old vehicle and new fcm token is stored`() =
        runTest {
            coEvery { authUsecase.getAppModuleCommunicator() } returns appModuleCommunicator
            coEvery {
                fcmDeviceTokenRepository.deleteFCMTokenFromFirestore(any())
            } returns true
            coEvery {
                fcmDeviceTokenRepository.storeFCMTokenInFirestore(any(), any())
            } returns true
            authUsecase.registerDeviceSpecificTokenToFireStore(
                "123",
                "4234",
                "SDERTYGKJ898GFYTU89",
                "oldVehicleTruckNumber",
                EMPTY_STRING
            )
            coVerify(exactly = 2) {
                fcmDeviceTokenRepository.deleteFCMTokenFromFirestore(any())
            }
            coVerify(exactly = 1) {
                fcmDeviceTokenRepository.storeFCMTokenInFirestore(any(), any())
            }
            coVerify(exactly = 1) {
                appModuleCommunicator.setFCMTokenFirestoreStatusInDataStore(any())
            }
        }

    @Test
    fun `verify delete old fcm token for both current vehicle and new fcm token is stored`() =
        runTest {
            coEvery { authUsecase.getAppModuleCommunicator() } returns appModuleCommunicator
            coEvery {
                fcmDeviceTokenRepository.deleteFCMTokenFromFirestore(any())
            } returns true
            coEvery {
                fcmDeviceTokenRepository.storeFCMTokenInFirestore(any(), any())
            } returns true
            authUsecase.registerDeviceSpecificTokenToFireStore(
                "123",
                "4234",
                "SDERTYGKJ898GFYTU89",
                EMPTY_STRING,
                EMPTY_STRING
            )
            coVerify(exactly = 1) {
                fcmDeviceTokenRepository.deleteFCMTokenFromFirestore(any())
            }
            coVerify(exactly = 1) {
                fcmDeviceTokenRepository.storeFCMTokenInFirestore(any(), any())
            }
            coVerify(exactly = 1) {
                appModuleCommunicator.setFCMTokenFirestoreStatusInDataStore(any())
            }
        }

    @Test
    fun `verify FCM token is not registered if Vehicle Number is invalid`() = runTest {
        authUsecase.fetchAndRegisterFcmDeviceSpecificToken("123", "", "")

        coVerify(exactly = 0) {
            fcmDeviceTokenRepository.storeFCMTokenInFirestore(any(), any())
        }
        coVerify(exactly = 0) {
            appModuleCommunicator.setFCMTokenFirestoreStatusInDataStore(any())
        }
    }

    @Test
    fun `verify FCM token is not registered if CID is invalid`() = runTest {

        authUsecase.fetchAndRegisterFcmDeviceSpecificToken("", "123", "")

        coVerify(exactly = 0) {
            fcmDeviceTokenRepository.storeFCMTokenInFirestore(any(), any())
        }
        coVerify(exactly = 0) {
            appModuleCommunicator.setFCMTokenFirestoreStatusInDataStore(any())
        }
    }

    @Test
    fun `verify FCM token is not registered if Firebase Authentication is not completed`() = runTest {
        coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns false
        authUsecase.fetchAndRegisterFcmDeviceSpecificToken("123", "456", "")

        coVerify(exactly = 0) {
            fcmDeviceTokenRepository.storeFCMTokenInFirestore(any(), any())
        }
        coVerify(exactly = 0) {
            appModuleCommunicator.setFCMTokenFirestoreStatusInDataStore(any())
        }
    }

    @Test
    fun `verify isAuthenticationComplete() - IS_FIREBASE_AUTH_SUCCESS_KEY is false`() = runTest {
        coEvery { authUsecase.getAppModuleCommunicator() } returns appModuleCommunicator
        coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns false
        coEvery { appModuleCommunicator.getFCMTokenFirestoreStatus() } returns true
        assertFalse(authUsecase.getAuthenticationProcessResult().isAuthenticationComplete())
    }

    @Test
    fun `verify isAuthenticationComplete() - IS_FCM_DEVICE_TOKEN_STORED_IN_FIRESTORE is false`() =
        runTest {
            coEvery { authUsecase.getAppModuleCommunicator() } returns appModuleCommunicator
            coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns true
            coEvery { appModuleCommunicator.getFCMTokenFirestoreStatus() } returns false
            assertFalse(authUsecase.getAuthenticationProcessResult().isAuthenticationComplete())
        }

    @Test
    fun `verify isAuthenticationComplete() - IS_FCM_DEVICE_TOKEN_STORED_IN_FIRESTORE and IS_FIREBASE_AUTH_SUCCESS_KEY is true`() =
        runTest {
            coEvery { authUsecase.getAppModuleCommunicator() } returns appModuleCommunicator
            coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns true
            coEvery { appModuleCommunicator.getFCMTokenFirestoreStatus() } returns true
            assertTrue(authUsecase.getAuthenticationProcessResult().isAuthenticationComplete())
        }

    @Test
    fun `test handleAuthenticationProcess when authentication complete(both Firestore and FCM)`() =
        runTest {
            val onAuthenticationComplete = mockk<() -> Unit>(relaxed = true)
            val doAuthentication = mockk<() -> Unit>(relaxed = true)
            val onAuthenticationFailed = mockk<() -> Unit>(relaxed = true)
            val onNoInternet = mockk<() -> Unit>(relaxed = true)

            coEvery { authUsecase.getAppModuleCommunicator() } returns appModuleCommunicator
            coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns true
            coEvery { appModuleCommunicator.getFCMTokenFirestoreStatus() } returns true
            every { managedConfigurationRepo.fetchManagedConfigDataFromServer("") } just runs

            authUsecase.handleAuthenticationProcess(
                "",
                true,
                onAuthenticationComplete,
                doAuthentication,
                onAuthenticationFailed,
                onNoInternet
            )
            verify(exactly = 1) {
                managedConfigurationRepo.fetchManagedConfigDataFromServer("")
                onAuthenticationComplete()
            }
            verify(exactly = 0) {
                doAuthentication()
            }
            verify(exactly = 0) {
                onAuthenticationFailed()
            }
            verify(exactly = 0) {
                onNoInternet()
            }
        }

    @Test
    fun `test handleAuthenticationProcess when firestore authentication is not completed and no internet available`() =
        runTest {
            val onAuthenticationComplete = mockk<() -> Unit>(relaxed = true)
            val doAuthentication = mockk<() -> Unit>(relaxed = true)
            val onAuthenticationFailed = mockk<() -> Unit>(relaxed = true)
            val onNoInternet = mockk<() -> Unit>(relaxed = true)

            coEvery { authUsecase.getAppModuleCommunicator() } returns appModuleCommunicator
            coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns false
            coEvery { appModuleCommunicator.getFCMTokenFirestoreStatus() } returns false

            authUsecase.handleAuthenticationProcess(
                "",
                false,
                onAuthenticationComplete,
                doAuthentication,
                onAuthenticationFailed,
                onNoInternet
            )
            verify(exactly = 0) {
                onAuthenticationComplete()
            }
            verify(exactly = 0) {
                doAuthentication()
            }
            verify(exactly = 0) {
                onAuthenticationFailed()
            }
            verify(exactly = 1) {
                onNoInternet()
            }
        }

    @Test
    fun `test handleAuthenticationProcess when authentication completed(both Firestore and FCM) and no internet available`() =
        runTest {
            val onAuthenticationComplete = mockk<() -> Unit>(relaxed = true)
            val doAuthentication = mockk<() -> Unit>(relaxed = true)
            val onAuthenticationFailed = mockk<() -> Unit>(relaxed = true)
            val onNoInternet = mockk<() -> Unit>(relaxed = true)

            coEvery { authUsecase.getAppModuleCommunicator() } returns appModuleCommunicator
            coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns true
            coEvery { appModuleCommunicator.getFCMTokenFirestoreStatus() } returns true
            every { managedConfigurationRepo.fetchManagedConfigDataFromServer("") } just runs

            authUsecase.handleAuthenticationProcess(
                "",
                false,
                onAuthenticationComplete,
                doAuthentication,
                onAuthenticationFailed,
                onNoInternet
            )
            verify(exactly = 1) {
                managedConfigurationRepo.fetchManagedConfigDataFromServer("")
                onAuthenticationComplete()
            }
            verify(exactly = 0) {
                doAuthentication()
            }
            verify(exactly = 0) {
                onAuthenticationFailed()
            }
            verify(exactly = 0) {
                onNoInternet()
            }
        }

    @Test
    fun `test handleAuthenticationProcess when firebase authentication is not completed and internet is available`() =
        runTest {
            val onAuthenticationComplete = mockk<() -> Unit>(relaxed = true)
            val doAuthentication = mockk<() -> Unit>(relaxed = true)
            val onAuthenticationFailed = mockk<() -> Unit>(relaxed = true)
            val onNoInternet = mockk<() -> Unit>(relaxed = true)

            coEvery { authUsecase.getAppModuleCommunicator() } returns appModuleCommunicator
            coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns false
            coEvery { appModuleCommunicator.getFCMTokenFirestoreStatus() } returns false

            authUsecase.handleAuthenticationProcess(
                "",
                true,
                onAuthenticationComplete,
                doAuthentication,
                onAuthenticationFailed,
                onNoInternet
            )

            verify(exactly = 0) {
                onAuthenticationComplete()
            }
            verify(exactly = 1) {
                doAuthentication()
            }
            verify(exactly = 0) {
                onAuthenticationFailed()
            }
            verify(exactly = 0) {
                onNoInternet()
            }
        }

    @Test
    fun `test handleAuthenticationProcess when firebase authentication is completed but FCM not saved and internet available`() =
        runTest {
            val onAuthenticationComplete = mockk<() -> Unit>(relaxed = true)
            val doAuthentication = mockk<() -> Unit>(relaxed = true)
            val onAuthenticationFailed = mockk<() -> Unit>(relaxed = true)
            val onNoInternet = mockk<() -> Unit>(relaxed = true)

            coEvery { authUsecase.getAppModuleCommunicator() } returns appModuleCommunicator
            coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns true
            coEvery { appModuleCommunicator.getFCMTokenFirestoreStatus() } returns false

            authUsecase.handleAuthenticationProcess(
                "",
                true,
                onAuthenticationComplete,
                doAuthentication,
                onAuthenticationFailed,
                onNoInternet
            )

            verify(exactly = 1) {
                onAuthenticationFailed()
            }
            verify(exactly = 0) {
                onAuthenticationComplete()
            }
            verify(exactly = 0) {
                onNoInternet()
            }
            verify(exactly = 0) {
                doAuthentication()
            }
        }

    @Test
    fun `test handleAuthenticationProcess when firebase authentication is completed but FCM not saved and internet not available`() =
        runTest {
            val onAuthenticationComplete = mockk<() -> Unit>(relaxed = true)
            val doAuthentication = mockk<() -> Unit>(relaxed = true)
            val onAuthenticationFailed = mockk<() -> Unit>(relaxed = true)
            val onNoInternet = mockk<() -> Unit>(relaxed = true)

            coEvery { authUsecase.getAppModuleCommunicator() } returns appModuleCommunicator
            coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns true
            coEvery { appModuleCommunicator.getFCMTokenFirestoreStatus() } returns false

            authUsecase.handleAuthenticationProcess(
                "",
                false,
                onAuthenticationComplete,
                doAuthentication,
                onAuthenticationFailed,
                onNoInternet
            )

            verify(exactly = 1) {
                onAuthenticationFailed()
            }
            verify(exactly = 0) {
                onAuthenticationComplete()
            }
            verify(exactly = 0) {
                onNoInternet()
            }
            verify(exactly = 0) {
                doAuthentication()
            }
        }

    @Test
    fun `test checkForFCMTokenSavedState invokes onAuthenticationComplete once FCM token is saved`() =
        runTest {
            val onAuthenticationComplete = mockk<() -> Unit>(relaxed = true)
            val onAuthenticationFailed = mockk<() -> Unit>(relaxed = true)

            coEvery { authUsecase.getAppModuleCommunicator() } returns appModuleCommunicator
            coEvery { appModuleCommunicator.getFCMTokenFirestoreStatus() } returns true

            authUsecase.checkForFCMTokenSavedState(
                "",
                onAuthenticationComplete,
                onAuthenticationFailed
            )

            verify(exactly = 1) {
                onAuthenticationComplete()
            }
            verify(exactly = 0) {
                onAuthenticationFailed()
            }
        }

    @Test
    fun `test checkForFCMTokenSavedState invokes onAuthenticationFailed when FCM token is not saved`() =
        runTest {
            val onAuthenticationComplete = mockk<() -> Unit>(relaxed = true)
            val onAuthenticationFailed = mockk<() -> Unit>(relaxed = true)

            coEvery { authUsecase.getAppModuleCommunicator() } returns appModuleCommunicator
            coEvery { appModuleCommunicator.getFCMTokenFirestoreStatus() } returns false

            authUsecase.checkForFCMTokenSavedState(
                "",
                onAuthenticationComplete,
                onAuthenticationFailed
            )

            verify(exactly = 0) {
                onAuthenticationComplete()
            }
            verify(exactly = 1) {
                onAuthenticationFailed()
            }
        }

    @Test
    fun `check if the event gets recorded when the workflow shortcut icon is clicked`() {
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(any()) } just runs
        authUsecase.recordShortcutClickEvent(
            WORKFLOW_SHORTCUT_USE_COUNT,
            "ShortcutIcon",
            setOf(INTENT_CATEGORY_LAUNCHER)
        )
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(any())
        }
    }

    @Test
    fun `check if the event gets recorded when trip info widget is clicked`() {
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(any()) } just runs
        authUsecase.recordShortcutClickEvent(
            WORKFLOW_SHORTCUT_USE_COUNT, TRIP_INFO_WIDGET, setOf(
                INTENT_CATEGORY_LAUNCHER
            )
        )
        verify(exactly = 0) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(any())
        }
    }

    @Test
    fun `check if the event gets recorded when authenticate activity open via other options is clicked`() {
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(any()) } just runs
        authUsecase.recordShortcutClickEvent(WORKFLOW_SHORTCUT_USE_COUNT, "FormLibrary", setOf())
        verify(exactly = 0) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(any())
        }
    }

    val customerId = "123"
    val truckNumber = "abc"
    val deviceFcmToken = "awbcdes"
    val fcmToken = "abcde"
    private fun groupingCommonMethodMockCalls(
    ) {
        coEvery { fcmDeviceTokenRepository.fetchFCMToken() } returns EMPTY_STRING
        coEvery { fcmDeviceTokenRepository.deleteFCMTokenFromFirestore(any()) } returns true
        coEvery { fcmDeviceTokenRepository.storeFCMTokenInFirestore(any(), any()) } returns true
        coEvery { authUsecase.getAppModuleCommunicator() } returns appModuleCommunicator
        coEvery { appModuleCommunicator.setFCMTokenFirestoreStatusInDataStore(any()) } just runs

    }

    @Test
    fun `check if FCM token gets updated when FCM is empty in Firestore`() = runTest {
        groupingCommonMethodMockCalls()
        coEvery { fcmDeviceTokenRepository.fetchFCMTokenFromFirestore(any()) } returns EMPTY_STRING
        assertTrue(authUsecase.checkFcmInSyncWithFireStore(customerId, truckNumber, deviceFcmToken))
    }

    @Test
    fun `FCM token is updated when it doesnt match device FCM token`() = runTest {
        groupingCommonMethodMockCalls()
        coEvery { fcmDeviceTokenRepository.fetchFCMTokenFromFirestore(any()) } returns "abcde"
        assertTrue(authUsecase.checkFcmInSyncWithFireStore(customerId, truckNumber, deviceFcmToken))
    }

    @Test
    fun `FCM token is NOT updated when it matches device FCM token`() = runTest {
        groupingCommonMethodMockCalls()
        coEvery { fcmDeviceTokenRepository.fetchFCMTokenFromFirestore(any()) } returns deviceFcmToken
        assertTrue(authUsecase.checkFcmInSyncWithFireStore(customerId, truckNumber, deviceFcmToken))
        verify(exactly = 0) { Log.n(any(), any(), any()) }
    }

    @Test
    fun `FCM token is NOT updated when Error is returned from firestore`() = runTest {
        groupingCommonMethodMockCalls()
        coEvery { fcmDeviceTokenRepository.fetchFCMTokenFromFirestore(any()) } returns "Error"
        assertTrue(
            authUsecase.checkFcmInSyncWithFireStore(
                customerId,
                truckNumber,
                deviceFcmToken
            )
        )
    }

    @Test
    fun `Exception thrown while fetching token from firestore`() = runTest {
        groupingCommonMethodMockCalls()
        every {
            Log.w(any(), any(), any())
        } returns Unit
        coEvery { fcmDeviceTokenRepository.fetchFCMTokenFromFirestore(any()) } throws Exception()
        assertFalse(
            authUsecase.checkFcmInSyncWithFireStore(
                customerId,
                truckNumber,
                deviceFcmToken
            )
        )
        verify(exactly = 1) {
            Log.w(any(), any(), any())
        }
    }

    @Test
    fun `no need to update FCM token for the vehicle if token retrieved from cache is identical of the device`() =
        runTest {
            val deviceFcmToken = DeviceFcmToken(fcmToken, truckNumber)
            every { Log.n(any(), any(), any()) } returns Unit
            coEvery { authUsecase.getFcmTokenDataFromDatastore() } returns deviceFcmToken
            groupingCommonMethodMockCalls()

            assertTrue(authUsecase.checkFcmInSyncWithCache(customerId, truckNumber, fcmToken))
            verify(exactly = 0) { Log.n(any(), any(), any()) }
        }

    @Test
    fun `do nothing if fcm token from cache and truck number are empty `() =
        runTest {
            val deviceFcmToken = DeviceFcmToken(EMPTY_STRING, EMPTY_STRING)
            every { Log.n(any(), any(), any()) } returns Unit
            coEvery { authUsecase.getFcmTokenDataFromDatastore() } returns deviceFcmToken
            groupingCommonMethodMockCalls()

            assertTrue(authUsecase.checkFcmInSyncWithCache(customerId, truckNumber, fcmToken))
            verify(exactly = 0) { Log.n(any(), any(), any()) }
        }

    @Test
    fun `check FCM token gets updated when vehicleID in Datastore is not sync with current vehicleID`() =
        runTest {
            val deviceFcmToken = DeviceFcmToken(fcmToken, "bca")
            coEvery { authUsecase.getFcmTokenDataFromDatastore() } returns deviceFcmToken
            groupingCommonMethodMockCalls()

            assertTrue(authUsecase.checkFcmInSyncWithCache(customerId, truckNumber, fcmToken))
        }

    @Test
    fun `check FCM token gets updated when FCM token in Datastore is not sync with current device FCM token`() =
        runTest {
            val deviceFcmToken = DeviceFcmToken("qwsad", truckNumber)
            coEvery { authUsecase.getFcmTokenDataFromDatastore() } returns deviceFcmToken
            groupingCommonMethodMockCalls()

            assertTrue(authUsecase.checkFcmInSyncWithCache(customerId, truckNumber, fcmToken))
        }
    
    @Test
    fun `while fetching fcm token from data store - exception thrown`() =
        runTest {
            every {
                Log.w(any(), any(), any())
            } returns Unit
            coEvery { authUsecase.getFcmTokenDataFromDatastore() } throws Exception()
            groupingCommonMethodMockCalls()

            assertFalse(authUsecase.checkFcmInSyncWithCache(customerId, truckNumber, fcmToken))
            verify(exactly = 1) {
                Log.w(any(), any(), any())
            }
        }

    @After
    fun after() {
        unmockkAll()
    }

}
