package com.trimble.ttm.routemanifest.di

import android.os.Bundle
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.repo.BackboneRepository
import com.trimble.ttm.commons.repo.BackboneRepositoryImpl
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.viewModel.SignatureDialogViewModel
import com.trimble.ttm.formlibrary.repo.EDVIRInspectionsRepo
import com.trimble.ttm.formlibrary.repo.EDVIRInspectionsRepoImpl
import com.trimble.ttm.formlibrary.repo.MessageFormRepo
import com.trimble.ttm.formlibrary.repo.MessageFormRepoImpl
import com.trimble.ttm.formlibrary.viewmodel.AuthenticationViewModel
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.managers.ResourceStringsManager
import com.trimble.ttm.routemanifest.managers.ServiceManager
import com.trimble.ttm.routemanifest.repo.ArrivalReasonEventRepo
import com.trimble.ttm.routemanifest.repo.ArrivalReasonEventRepoImpl
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepoImpl
import com.trimble.ttm.routemanifest.repo.FireStoreCacheRepository
import com.trimble.ttm.routemanifest.repo.FireStoreCacheRepositoryImpl
import com.trimble.ttm.routemanifest.repo.FormsRepository
import com.trimble.ttm.routemanifest.repo.FormsRepositoryImpl
import com.trimble.ttm.routemanifest.repo.SendDispatchDataRepo
import com.trimble.ttm.routemanifest.repo.SendDispatchDataRepoImpl
import com.trimble.ttm.routemanifest.repo.TripMobileOriginatedEventsRepo
import com.trimble.ttm.routemanifest.repo.TripMobileOriginatedEventsRepoImpl
import com.trimble.ttm.routemanifest.repo.TripPanelEventRepo
import com.trimble.ttm.routemanifest.repo.TripPanelEventRepoImpl
import com.trimble.ttm.routemanifest.usecases.ArrivalReasonUsecase
import com.trimble.ttm.routemanifest.usecases.ArriveTriggerDataStoreKeyManipulationUseCase
import com.trimble.ttm.routemanifest.usecases.DispatchBaseUseCase
import com.trimble.ttm.routemanifest.usecases.DispatchListUseCase
import com.trimble.ttm.routemanifest.usecases.DispatchStopsUseCase
import com.trimble.ttm.routemanifest.usecases.DispatchValidationUseCase
import com.trimble.ttm.routemanifest.usecases.DockModeUseCase
import com.trimble.ttm.routemanifest.usecases.EDVIRSettingsCacheUseCase
import com.trimble.ttm.routemanifest.usecases.FetchDispatchStopsAndActionsUseCase
import com.trimble.ttm.routemanifest.usecases.FormUseCase
import com.trimble.ttm.routemanifest.usecases.ICancelNotificationHelper
import com.trimble.ttm.routemanifest.usecases.LateNotificationUseCase
import com.trimble.ttm.routemanifest.usecases.NotificationQueueUseCase
import com.trimble.ttm.routemanifest.usecases.RemoveExpiredTripPanelMessageUseCase
import com.trimble.ttm.routemanifest.usecases.RouteETACalculationUseCase
import com.trimble.ttm.routemanifest.usecases.SendBroadCastUseCase
import com.trimble.ttm.routemanifest.usecases.SendDispatchDataUseCase
import com.trimble.ttm.routemanifest.usecases.StopDetentionWarningUseCase
import com.trimble.ttm.routemanifest.usecases.TripCacheUseCase
import com.trimble.ttm.routemanifest.usecases.TripCompletionUseCase
import com.trimble.ttm.routemanifest.usecases.TripInfoWidgetUseCase
import com.trimble.ttm.routemanifest.usecases.TripPanelActionHandleUseCase
import com.trimble.ttm.routemanifest.usecases.TripPanelUseCase
import com.trimble.ttm.routemanifest.usecases.TripStartUseCase
import com.trimble.ttm.routemanifest.usecases.WorkFlowApplicationUseCase
import com.trimble.ttm.routemanifest.usecases.WorkflowAppNotificationUseCase
import com.trimble.ttm.routemanifest.viewmodel.DispatchDetailViewModel
import com.trimble.ttm.routemanifest.viewmodel.DispatchListViewModel
import com.trimble.ttm.routemanifest.viewmodel.FormViewModel
import com.trimble.ttm.routemanifest.viewmodel.StopDetailViewModel
import com.trimble.ttm.routemanifest.viewmodel.TripPanelPositiveActionTransitionScreenViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import javax.inject.Qualifier

var repoModule = module {
    single<DispatchFirestoreRepo> { DispatchFirestoreRepoImpl(get()) }
    single<FormsRepository> { FormsRepositoryImpl(scope = CoroutineScope(Dispatchers.IO)) }
    single<BackboneRepository> { BackboneRepositoryImpl(androidContext(), get()) }
    single<TripMobileOriginatedEventsRepo> { TripMobileOriginatedEventsRepoImpl() }
    single<ArrivalReasonEventRepo> { ArrivalReasonEventRepoImpl( appModuleCommunicator = get()) }
    single<TripPanelEventRepo> { TripPanelEventRepoImpl() }
    single<EDVIRInspectionsRepo> { EDVIRInspectionsRepoImpl() }
    single<SendDispatchDataRepo> { SendDispatchDataRepoImpl() }
    single<FireStoreCacheRepository> { FireStoreCacheRepositoryImpl() }
    single<MessageFormRepo> {
        MessageFormRepoImpl(
            appModuleCommunicator = get(),
            dispatcherProvider = get()
        )
    }
}

var dataSourceModule = module {
    single { NotificationQueueUseCase(get()) }
    factory { ResourceStringsManager(androidContext()) }
}

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class UseCaseScope

val appModule = module {
    single<CoroutineScope>(named("UseCaseScope")) {
        CoroutineScope(Dispatchers.IO)
    }
    single<StateFlow<Bundle?>> {
        (androidContext() as WorkflowApplication).routeResultBundleFlow
    }
}

var useCaseModule = module {
    fun provideCoroutineScope(appModuleCommunicator: AppModuleCommunicator): CoroutineScope {
        return appModuleCommunicator.getAppModuleApplicationScope()
    }
    factory {
        FetchDispatchStopsAndActionsUseCase(dispatchFirestoreRepo = get())
    }
    factory {
        SendDispatchDataUseCase(
            fetchDispatchStopsAndActionsUseCase = get(),
            sendDispatchDataRepo = get(),
            localDataSourceRepo = get(),
            sendWorkflowEventsToAppUseCase = get()
        )
    }
    factory {
        FormUseCase(
            formsRepository = get(),
            encodedImageRefUseCase = get(),
            dispatchFormUseCase = get(),
            appModuleCommunicator = get(),
            firebaseAnalyticEventRecorder = get(),
            localDataSourceRepo = get(),
            formFieldDataUseCase = get(),
            dispatcherFormValuesUseCase = get(),
            fetchDispatchStopsAndActionsUseCase = get(),
            messageFormRepo = get()
        )
    }
    factory {
        DispatchBaseUseCase(
            appModuleCommunicator = get(),
            fetchDispatchStopsAndActionsUseCase = get(),
            dispatchRepo = get(),
            firebaseAnalyticEventRecorder = get(),
            localDataSourceRepo = get()
        )
    }
    factory {
        DispatchListUseCase(
            dispatchFirestoreRepo = get(),
            featureFlagCacheRepo = get(),
            localDataSourceRepo = get(),
            tripPanelUseCase = get(),
            formDataStoreManager = get()
        )
    }
    single {
        RouteETACalculationUseCase(
            scope = get(named("UseCaseScope")),
            routeResultBundleFlow = get(),
            tripPanelRepo = get(),
            dispatchFirestoreRepo = get(),
            sendDispatchDataUseCase = get(),
            localDataSourceRepo = get(),
            tripInfoWidgetUseCase = get(),
            fetchDispatchStopsAndActionsUseCase = get(),
            dataStoreManager = get(),
            sendWorkflowEventsToAppUseCase = get()
        )
    }
    factory {
        TripCacheUseCase(
            fireStoreCacheRepository = get(),
            dataStoreManager = get(),
            workflowAppNotificationUseCase = get(),
            fetchDispatchStopsAndActionsUseCase = get(),
            dispatchListUseCase = get(),
            dispatchFirestoreRepo = get(),
            appModuleCommunicator = get()
        )
    }
    factory {
        EDVIRSettingsCacheUseCase(
            fireStoreCacheRepository = get(),
            messageConfirmationUseCase = get(),
            appModuleCommunicator = get()
        )
    }
    factory {
        TripCompletionUseCase(
            dispatchFirestoreRepo = get(),
            tripMobileOriginatedEventsRepo = get(),
            formUseCase = get(),
            draftUseCase = get(),
            dispatchStopsUseCase = get(),
            sendDispatchDataUseCase = get(),
            stopDetentionWarningUseCase = get(),
            tripPanelUseCase = get(),
            backboneUseCase = get(),
            localDataSourceRepo = get(),
            tripInfoWidgetUseCase = get(),
            sendWorkflowEventsToAppUseCase = get(),
            managedConfigurationRepo = get(),
            dispatchListUseCase = get(),
            coroutineDispatcherProvider = get()
        )
    }
    factory{
        WorkFlowApplicationUseCase(
            applicationScope = provideCoroutineScope(
                get()
            ),
            serviceManager = get(),
            coroutineDispatcherProvider = get(),
            backboneUseCase = get(),
            cacheGroupsUseCase = get(),
            appModuleCommunicator = get(),
            edvirSettingsCacheUseCase = get(),
            notificationQueueUseCase = get(),
            tripCacheUseCase = get(),
            workflowAppNotificationUseCase = get()
        )
    }
    factory { DockModeUseCase(get(), get()) }
    factory { BackboneUseCase(get()) }
    factory { (coroutineScope: CoroutineScope) ->
        RemoveExpiredTripPanelMessageUseCase(
            coroutineScope,
            get(),
            get(),
            get()
        )
    }
    factory {
        LateNotificationUseCase(
            scope = CoroutineScope(Dispatchers.IO),
            backboneUseCase = get(),
            dispatchFirestoreRepo = get(),
            tripMobileOriginatedEventsRepo = get(),
            arrivalReasonEventRepo = get()
        )
    }
    single {
        WorkflowAppNotificationUseCase(
            application = androidApplication(),
            dataStoreManager = get(),
            messageConfirmationUseCase = get(),
            appModuleCommunicator = get(),
            sendWorkflowEventsToAppUseCase = get(),
            dispatchStopsUseCase = get(),
            backboneUseCase = get(),
            dispatchListUseCase = get(),
            tripCompletionUseCase = get()
        )
    } bind ICancelNotificationHelper::class // Do not change the scope , since it holds the stop removal notification data
    factory {
        ArriveTriggerDataStoreKeyManipulationUseCase(
            localDataSourceRepo = get()
        )
    }
    single {
        TripPanelUseCase(
            tripPanelEventRepo = get(),
            backboneUseCase = get(),
            resourceStringsManager = get(),
            sendBroadCastUseCase = get(),
            localDataSourceRepo = get(),
            dispatchStopsUseCase = get(),
            appModuleCommunicator = get(),
            context = androidContext(),
            arriveTriggerDataStoreKeyManipulationUseCase = get(),
            fetchDispatchStopsAndActionsUseCase = get()
        )
    }
    single { SendBroadCastUseCase(androidContext()) }
    single {
        StopDetentionWarningUseCase(
            applicationInstance = androidApplication(),
            appModuleCommunicator = get(),
            dataStoreManager = get(),
            dispatcherProvider = get(),
            fetchDispatchStopsAndActionsUseCase = get(),
            backboneUseCase = get()
        )
    }
    single {
        DispatchStopsUseCase(
            formsRepository = get(),
            dispatchFirestoreRepo = get(),
            dispatchProvider = get(),
            stopDetentionWarningUseCase = get(),
            routeETACalculationUseCase = get(),
            formUseCase = get(),
            featureFlagGateKeeper = get(),
            sendWorkflowEventsToAppUseCase = get(),
            deepLinkUseCase = get(),
            arriveTriggerDataStoreKeyManipulationUseCase = get(),
            dataStoreManager = get(),
            fetchDispatchStopsAndActionsUseCase = get()
        )
    }
    single{
        ArrivalReasonUsecase(
            backboneUseCase = get(),
            localDataSourceRepo = get(),
            arrivalReasonEventRepo = get(),
            appModuleCommunicator = get(),
            dispatchFirestoreRepo = get(),
            managedConfigurationRepo = get()
        )
    }

    factory {
        ServiceManager(
            dataStoreManager = get(),
            appModuleCommunicator = get(),
            tripCompletionUseCase = get(),
            tripPanelUseCase = get(),
            tripPanelEventsRepo = get(),
            formDataStoreManager = get(),
            featureFlagCacheRepo = get(),
            authenticateUseCase = get(),
            edvirFormUseCase = get(),
            dispatchStopsUseCase = get(),
            backboneUseCase = get()
            )
    }
    factory {
        DispatchValidationUseCase(
            get(),
            provideCoroutineScope(
                get()
            )
        )
    }
    factory {
        TripInfoWidgetUseCase(
            context = androidContext(),
            localDataSourceRepo = get(),
            sendBroadCastUseCase = get(),
            coroutineScope = CoroutineScope(Dispatchers.IO),
            coroutineDispatcherProvider = get()
        )
    }
    factory {
        TripPanelActionHandleUseCase(
            tripPanelUseCase = get(),
            sendDispatchDataUseCase = get(),
            appModuleCommunicator = get(),
            dispatchStopsUseCase = get(),
            routeETACalculationUseCase = get(),
            arriveTriggerDataStoreKeyManipulationUseCase = get(),
            remoteConfigGatekeeper = get(),
            coroutineScope = CoroutineScope(Dispatchers.IO),
            backboneUseCase = get(),
            context = androidContext()
        )
    }

    factory {
        TripStartUseCase(
            coroutineDispatcherProvider = get(),
            dataStoreManager = get(),
            fetchDispatchStopsAndActionsUseCase = get(),
            dispatchBaseUseCase = get(),
            dispatchStopsUseCase = get(),
            lateNotificationUseCase = get(),
            backboneUseCase = get(),
            tripPanelUseCase = get(),
            sendWorkflowEventsToAppUseCase = get(),
            sendDispatchDataUseCase = get(),
            routeETACalculationUseCase = get(),
            serviceManager = get(),
            appModuleCommunicator = get(),
            context = androidContext()
        )
    }
}

var viewModelModule = module {
    viewModel {
        AuthenticationViewModel(
            eDVIRFormUseCase = get(),
            authenticateUseCase = get(),
            formDataStoreManager = get(),
            application = androidApplication()
        )
    }
    viewModel {
        DispatchListViewModel(
            application = androidApplication(),
            dispatchListUseCase = get(),
            backboneUseCase = get(),
            appModuleCommunicator = get(),
            dispatchValidationUseCase = get(),
            tripStartUseCase = get(),
            formLibraryUseCase = get(),
            coroutineDispatcherProvider = get()
        )
    }
    viewModel {
        DispatchDetailViewModel(
            applicationInstance = androidApplication(),
            routeETACalculationUseCase = get(),
            tripCompletionUseCase = get(),
            dataStoreManager = get(),
            formDataStoreManager = get(),
            dispatchBaseUseCase = get(),
            dispatchStopsUseCase = get(),
            sendDispatchDataUseCase = get(),
            tripPanelUseCase = get(),
            dispatchValidationUseCase = get(),
            coroutineDispatcher = get(),
            stopDetentionWarningUseCase = get(),
            backboneUseCase = get(),
            lateNotificationUseCase = get(),
            sendWorkflowEventsToAppUseCase = get(),
            fetchDispatchStopsAndActionsUseCase = get(),
            tripStartUseCase = get(),
            formLibraryUseCase = get()
        )
    }
    viewModel {
        FormViewModel(
            application = androidApplication(),
            formUseCase = get(),
            appModuleCommunicator = get(),
            tripPanelUseCase = get(),
            draftingUseCase = get(),
            draftUseCase = get(),
            dispatcherProvider = get(),
            deepLinkUseCase = get(),
            formFieldDataUseCase = get()
        )
    }
    viewModel {
        StopDetailViewModel(
            applicationInstance = get(),
            routeETACalculationUseCase = get(),
            tripCompletionUseCase = get(),
            dataStoreManager = get(),
            formDataStoreManager = get(),
            dispatchBaseUseCase = get(),
            dispatchStopsUseCase = get(),
            sendDispatchDataUseCase = get(),
            tripPanelUseCase = get(),
            dispatcherProvider = get(),
            stopDetentionWarningUseCase = get(),
            dispatchValidationUseCase = get(),
            fetchDispatchStopsAndActionsUseCase = get(),
            formViewModel = get(),
            backboneUseCase = get(),
            tripStartUseCase = get()
        )
    }
    viewModel {
        SignatureDialogViewModel()
    }
    viewModel { TripPanelPositiveActionTransitionScreenViewModel(tripPanelActionHandleUseCase = get()) }
}