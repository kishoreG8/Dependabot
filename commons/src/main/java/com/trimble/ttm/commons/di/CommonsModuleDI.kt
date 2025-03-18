package com.trimble.ttm.commons.di

import android.annotation.SuppressLint
import com.google.firebase.analytics.FirebaseAnalytics
import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.datasource.ManagedConfigurationDataSource
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.repo.DeviceAuthRepo
import com.trimble.ttm.commons.repo.DeviceAuthRepoImpl
import com.trimble.ttm.commons.repo.DispatchFormRepo
import com.trimble.ttm.commons.repo.DispatchFormRepoImpl
import com.trimble.ttm.commons.repo.DispatcherFormValuesRepo
import com.trimble.ttm.commons.repo.DispatcherFormValuesRepoImpl
import com.trimble.ttm.commons.repo.EncodedImageRefRepo
import com.trimble.ttm.commons.repo.EncodedImageRefRepoImpl
import com.trimble.ttm.commons.repo.FCMDeviceTokenHandler
import com.trimble.ttm.commons.repo.FCMDeviceTokenRepository
import com.trimble.ttm.commons.repo.FCMDeviceTokenRepositoryImpl
import com.trimble.ttm.commons.repo.FeatureFlagCacheRepo
import com.trimble.ttm.commons.repo.FeatureFlagCacheRepoImpl
import com.trimble.ttm.commons.repo.FirebaseAuthRepo
import com.trimble.ttm.commons.repo.FirebaseAuthRepoImpl
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.commons.repo.LocalDataSourceRepoImpl
import com.trimble.ttm.commons.repo.ManagedConfigurationRepo
import com.trimble.ttm.commons.repo.ManagedConfigurationRepoImpl
import com.trimble.ttm.commons.repo.VehicleDriverMappingRepo
import com.trimble.ttm.commons.repo.VehicleDriverMappingRepoImpl
import com.trimble.ttm.commons.usecase.AuthenticateUseCase
import com.trimble.ttm.commons.usecase.DeepLinkUseCase
import com.trimble.ttm.commons.usecase.DispatchFormUseCase
import com.trimble.ttm.commons.usecase.DispatcherFormValuesUseCase
import com.trimble.ttm.commons.usecase.EncodedImageRefUseCase
import com.trimble.ttm.commons.usecase.FormFieldDataUseCase
import com.trimble.ttm.commons.usecase.SendWorkflowEventsToAppUseCase
import com.trimble.ttm.commons.usecase.VehicleDriverMappingUseCase
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.FeatureFlagGateKeeper
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appContextModule = module {
    //single { androidContext() } // Provide the application context
    single { androidApplication() as AppModuleCommunicator }
}

val commonUseCaseModule = module {
    factory {
        AuthenticateUseCase(
            deviceAuthRepo = get(),
            firebaseAuthRepo = get(),
            fcmDeviceTokenRepository = get(),
            featureFlagCacheRepo = get(),
            firebaseAnalyticEventRecorder = get(),
            managedConfigurationRepo = get(),
            appModuleCommunicator = get()
        )
    }
    factory { EncodedImageRefUseCase(encodedImageRefRepo = get()) }
    factory {
        DispatchFormUseCase(
            applicationInstance = androidApplication(), dispatchFormRepo = get(),
            formFieldDataBuilderUseCase = get()
        )
    }
    factory {
        SendWorkflowEventsToAppUseCase(
            androidContext(),
            get(),
            CoroutineScope(Dispatchers.IO)
        )
    }
    factory { DeepLinkUseCase(managedConfigurationRepo = get()) }
    factory {
        FormFieldDataUseCase(
            appModuleCommunicator = get(),
            encodedImageRefUseCase = get(),
            backboneUseCase = get()
        )
    }
    factory { DispatcherFormValuesUseCase(dispatcherFormValuesRepo = get()) }
    factory { VehicleDriverMappingUseCase(backboneUseCase = get(), vehicleDriverMappingRepository = get()) }
}

val commonRepoModule = module {
    single<FCMDeviceTokenRepository> { FCMDeviceTokenRepositoryImpl(get()) }
    single<DeviceAuthRepo> { DeviceAuthRepoImpl(androidContext()) }
    single<FirebaseAuthRepo> { FirebaseAuthRepoImpl() }
    single<FeatureFlagCacheRepo> { FeatureFlagCacheRepoImpl() }
    factory<EncodedImageRefRepo> { EncodedImageRefRepoImpl(androidContext()) }
    factory<DispatchFormRepo> { DispatchFormRepoImpl() }
    single<ManagedConfigurationRepo> { ManagedConfigurationRepoImpl(get()) }
    single<DispatcherFormValuesRepo> { DispatcherFormValuesRepoImpl() }
    single<LocalDataSourceRepo> {
        LocalDataSourceRepoImpl(
            dataStoreManager = get(),
            formDataStoreManager = get(),
            appModuleCommunicator = get()
        )
    }
    factory<VehicleDriverMappingRepo> { VehicleDriverMappingRepoImpl() }
}

/**
 * Module that provides Firebase dependencies to the application.
 */
val commonFireBaseModule = module {
    single { FCMDeviceTokenHandler() }
    single<FeatureGatekeeper> {
        FeatureFlagGateKeeper()
    }
}

@SuppressLint("MissingPermission")
val commonFirebaseAnalyticsModule = module {
    single {
        FirebaseAnalyticEventRecorder(
            FirebaseAnalytics.getInstance(androidContext()),
            get(),
            get()
        )
    }
}

val commonDispatcherProviderModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
}

val commonDataSourceModule = module {
    single { DataStoreManager(androidContext()) }
    single { FormDataStoreManager(androidContext()) }
    factory { ManagedConfigurationDataSource(androidContext()) }
}