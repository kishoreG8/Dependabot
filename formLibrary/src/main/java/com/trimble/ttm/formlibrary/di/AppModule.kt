package com.trimble.ttm.formlibrary.di

import android.content.Context
import androidx.room.Room
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.repo.FCMDeviceTokenRepository
import com.trimble.ttm.commons.repo.FCMDeviceTokenRepositoryImpl
import com.trimble.ttm.commons.repo.FirebaseAuthRepo
import com.trimble.ttm.commons.repo.FirebaseAuthRepoImpl
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.formlibrary.BuildConfig
import com.trimble.ttm.formlibrary.dataLayer.MandatoryInspectionDao
import com.trimble.ttm.formlibrary.dataLayer.MandatoryInspectionRelatedDatabase
import com.trimble.ttm.formlibrary.dataSource.CloudFunctionFormsDataSource
import com.trimble.ttm.formlibrary.dataSource.FirestoreFormsDataSource
import com.trimble.ttm.formlibrary.dataSource.IFormsDataSource
import com.trimble.ttm.formlibrary.manager.ITtsManager
import com.trimble.ttm.formlibrary.manager.ITtsPlayerManager
import com.trimble.ttm.formlibrary.manager.MessagesManagerImpl
import com.trimble.ttm.formlibrary.manager.TtsManagerImpl
import com.trimble.ttm.formlibrary.manager.TtsPlayerManagerImpl
import com.trimble.ttm.formlibrary.model.LocalDataSource
import com.trimble.ttm.formlibrary.model.LocalDataSourceImpl
import com.trimble.ttm.formlibrary.repo.CacheGroupsRepo
import com.trimble.ttm.formlibrary.repo.CacheGroupsRepoImpl
import com.trimble.ttm.formlibrary.repo.ContactsRepository
import com.trimble.ttm.formlibrary.repo.ContactsRepositoryImpl
import com.trimble.ttm.formlibrary.repo.DraftRepo
import com.trimble.ttm.formlibrary.repo.DraftRepoImpl
import com.trimble.ttm.formlibrary.repo.DraftingRepo
import com.trimble.ttm.formlibrary.repo.DraftingRepoImpl
import com.trimble.ttm.formlibrary.repo.EDVIRFormRepoImpl
import com.trimble.ttm.formlibrary.repo.EDVIRInspectionsRepo
import com.trimble.ttm.formlibrary.repo.EDVIRInspectionsRepoImpl
import com.trimble.ttm.formlibrary.repo.FormLibraryRepo
import com.trimble.ttm.formlibrary.repo.FormLibraryRepoImpl
import com.trimble.ttm.formlibrary.repo.FormsRepo
import com.trimble.ttm.formlibrary.repo.FormsRepoImpl
import com.trimble.ttm.formlibrary.repo.ImageHandler
import com.trimble.ttm.formlibrary.repo.ImageHandlerImpl
import com.trimble.ttm.formlibrary.repo.InboxRepo
import com.trimble.ttm.formlibrary.repo.InboxRepoImpl
import com.trimble.ttm.formlibrary.repo.InspectionExposeRepo
import com.trimble.ttm.formlibrary.repo.InspectionExposeRepoImpl
import com.trimble.ttm.formlibrary.repo.LocalRepo
import com.trimble.ttm.formlibrary.repo.LocalRepoImpl
import com.trimble.ttm.formlibrary.repo.MessageConfirmationRepo
import com.trimble.ttm.formlibrary.repo.MessageConfirmationRepoImpl
import com.trimble.ttm.formlibrary.repo.MessageFormRepo
import com.trimble.ttm.formlibrary.repo.MessageFormRepoImpl
import com.trimble.ttm.formlibrary.repo.SentRepo
import com.trimble.ttm.formlibrary.repo.SentRepoImpl
import com.trimble.ttm.formlibrary.repo.TrashRepo
import com.trimble.ttm.formlibrary.repo.TrashRepoImpl
import com.trimble.ttm.formlibrary.repo.cawb.CustomWorkFlowFormResponseSaveRepo
import com.trimble.ttm.formlibrary.repo.cawb.CustomWorkFlowFormResponseSaveRepoImpl
import com.trimble.ttm.formlibrary.usecases.CacheGroupsUseCase
import com.trimble.ttm.formlibrary.usecases.ContactsUseCase
import com.trimble.ttm.formlibrary.usecases.CustomWorkflowFormHandleUseCase
import com.trimble.ttm.formlibrary.usecases.DraftUseCase
import com.trimble.ttm.formlibrary.usecases.DraftingUseCase
import com.trimble.ttm.formlibrary.usecases.EDVIRFormUseCase
import com.trimble.ttm.formlibrary.usecases.EDVIRInspectionsUseCase
import com.trimble.ttm.formlibrary.usecases.FirebaseCurrentUserTokenFetchUseCase
import com.trimble.ttm.formlibrary.usecases.FormLibraryUseCase
import com.trimble.ttm.formlibrary.usecases.FormRenderUseCase
import com.trimble.ttm.formlibrary.usecases.ImageHandlerUseCase
import com.trimble.ttm.formlibrary.usecases.InboxUseCase
import com.trimble.ttm.formlibrary.usecases.MessageConfirmationUseCase
import com.trimble.ttm.formlibrary.usecases.MessageFormUseCase
import com.trimble.ttm.formlibrary.usecases.SentUseCase
import com.trimble.ttm.formlibrary.usecases.TrashUseCase
import com.trimble.ttm.formlibrary.usecases.UpdateInspectionInformationUseCase
import com.trimble.ttm.formlibrary.viewmodel.ContactListViewModel
import com.trimble.ttm.formlibrary.viewmodel.CustomSignatureDialogViewModel
import com.trimble.ttm.formlibrary.viewmodel.DraftViewModel
import com.trimble.ttm.formlibrary.viewmodel.DraftingViewModel
import com.trimble.ttm.formlibrary.viewmodel.EDVIRFormViewModel
import com.trimble.ttm.formlibrary.viewmodel.EDVIRInspectionsViewModel
import com.trimble.ttm.formlibrary.viewmodel.FormLibraryViewModel
import com.trimble.ttm.formlibrary.viewmodel.ImportImageViewModel
import com.trimble.ttm.formlibrary.viewmodel.InboxViewModel
import com.trimble.ttm.formlibrary.viewmodel.MessageFormViewModel
import com.trimble.ttm.formlibrary.viewmodel.MessagingViewModel
import com.trimble.ttm.formlibrary.viewmodel.PreviewImageViewModel
import com.trimble.ttm.formlibrary.viewmodel.SentViewModel
import com.trimble.ttm.formlibrary.viewmodel.TrashViewModel
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

const val FIRESTORE_FORM_DATA_SOURCE = "firestore_form_data_source"
const val CLOUD_FUNCTION_FORM_DATA_SOURCE = "cloudFunction_form_data_source"

var coroutineScopeModule = module {
    factory {CoroutineScope(get<DispatcherProvider>().io())}
}

val formLibraryViewModelModule = module {
    viewModel { EDVIRInspectionsViewModel(androidApplication(), get(), get()) }
    viewModel {
        EDVIRFormViewModel(
            eDVIRFormUseCase = get(),
            authenticateUseCase = get(),
            updateInspectionInformationUseCase = get(),
            messageFormUseCase = get(),
            formDataStoreManager = get(),
            appModuleCommunicator = get(),
            application = androidApplication()
        )
    }
    viewModel {
        InboxViewModel(
            application = androidApplication(),
            inboxUseCase = get(),
            messageConfirmationUseCase = get()
        )
    }
    viewModel {
        TrashViewModel(
            application = androidApplication(),
            trashUseCase = get(),
            appModuleCommunicator = get(),
            dispatcherProvider = get(),
            firebaseCurrentUserTokenFetchUseCase = get()
        )
    }
    viewModel {
        MessageFormViewModel(
            application = androidApplication(), messageFormUseCase = get(),
            draftUseCase = get(), firebaseCurrentUserTokenFetchUseCase = get(),
            formRenderUseCase = get(), coroutineDispatcherProvider = get(),
            deepLinkUseCase = get(),
            formFieldDataUseCase = get(),
            formDataStoreManager = get()
        )
    }
    viewModel {
        FormLibraryViewModel(
            androidApplication(),
            formLibraryUseCase = get(),
            authenticateUseCase = get(),
            dispatchers = get(),
            appModuleCommunicator = get(),
            formDataStoreManager = get(),
            edvirFormUseCase = get()
        )
    }
    viewModel { ContactListViewModel(androidApplication(), get(), get()) }
    viewModel { SentViewModel(androidApplication(), get(), get(), get(), get()) }
    viewModel { DraftViewModel(androidApplication(), get(), get(), get()) }
    viewModel {
        MessagingViewModel(
            application = get(),
            sentUseCase = get(),
            draftUseCase = get(),
            inboxUseCase = get(),
            trashUseCase = get(),
            authenticateUseCase = get(),
            formLibraryUseCase = get(),
            edvirFormUseCase = get(),
            formDataStoreManager = get(),
            appModuleCommunicator = get()
        )
    }
    viewModel { DraftingViewModel(get(), get(), get()) }
    viewModel { CustomSignatureDialogViewModel() }
    viewModel { PreviewImageViewModel(get()) }
    viewModel { ImportImageViewModel(get()) }
}

val formLibraryRepoModule = module {
    fun provideCoroutineScope(appModuleCommunicator: AppModuleCommunicator): CoroutineScope {
        return appModuleCommunicator.getAppModuleApplicationScope()
    }
    single<InboxRepo> { InboxRepoImpl(get(), get()) }
    single<TrashRepo> { TrashRepoImpl(get()) }
    single<MessageFormRepo> {
        MessageFormRepoImpl(
            appModuleCommunicator = get(),
            dispatcherProvider = get()
        )
    }
    single<FormLibraryRepo> { FormLibraryRepoImpl(get(), get()) }
    single<ContactsRepository> { ContactsRepositoryImpl(get()) }
    single { EDVIRFormRepoImpl(get(named(FIRESTORE_FORM_DATA_SOURCE)), get(named(CLOUD_FUNCTION_FORM_DATA_SOURCE))) }
    single<EDVIRInspectionsRepo> { EDVIRInspectionsRepoImpl() }
    single<FCMDeviceTokenRepository> { FCMDeviceTokenRepositoryImpl(get()) }
    single<LocalRepo> { LocalRepoImpl(get()) }
    single<SentRepo> { SentRepoImpl(get(), get()) }
    single<DraftRepo> { DraftRepoImpl(get()) }
            factory<MessageConfirmationRepo> { MessageConfirmationRepoImpl(get()) }
    single<InspectionExposeRepo> { InspectionExposeRepoImpl(get()) }
    factory<CacheGroupsRepo> { CacheGroupsRepoImpl(get(), get(), get()) }
    single<DraftingRepo> {
        DraftingRepoImpl(
            get(),
            provideCoroutineScope(get())
        )
    }
    single<FirebaseAuthRepo> { FirebaseAuthRepoImpl() }
    single<CustomWorkFlowFormResponseSaveRepo> { CustomWorkFlowFormResponseSaveRepoImpl(get()) }
    factory<FormsRepo> { FormsRepoImpl(get(named(FIRESTORE_FORM_DATA_SOURCE)),get(named(
        CLOUD_FUNCTION_FORM_DATA_SOURCE))) }
}

val formLibraryDataSourceModule = module {
    single<LocalDataSource> { LocalDataSourceImpl(get()) }
    single<ITtsManager> { TtsManagerImpl(androidContext()) }
    single<ITtsPlayerManager> { TtsPlayerManagerImpl(get()) }
    single {
        MessagesManagerImpl(
            appModuleCommunicator = get(),
            messageFormUseCase = get(),
            inboxUseCase = get(),
            context = androidContext()
        )
    }
    factory<IFormsDataSource>(named(FIRESTORE_FORM_DATA_SOURCE)) { FirestoreFormsDataSource()  }
    factory<IFormsDataSource>(named(CLOUD_FUNCTION_FORM_DATA_SOURCE)) { CloudFunctionFormsDataSource()  }
    factory<ImageHandler> { ImageHandlerImpl(androidContext()) }
}

val formLibraryUseCaseModule = module {
    factory {
        InboxUseCase(
            inboxRepo = get(),
            firebaseAnalyticEventRecorder = get(),
            messageConfirmationUseCase = get()
        )
    }
    factory { TrashUseCase(trashRepo = get(), firebaseAnalyticEventRecorder = get()) }
    factory {
        MessageFormUseCase(
            encodedImageRefUseCase = get(),
            dispatchFormUseCase = get(),
            messageFormRepo = get(),
            formFieldDataUseCase = get(),
            dispatcherFormValuesUseCase = get()
        )
    }
    factory {
        FormLibraryUseCase(
            formLibraryRepo = get(),
            cacheGroupsUseCase = get(),
            appModuleCommunicator = get(),
            firebaseAnalyticEventRecorder = get()
        )
    }
    factory { ContactsUseCase(get(), get()) }
    factory { EDVIRInspectionsUseCase(get()) }
    factory {
        EDVIRFormUseCase(
            eDVIRFormRepository = get(), eDVIRInspectionsRepo = get(),
            firebaseAuthRepo = get(), appModuleCommunicator = get(),
            formFieldDataUseCase = get(),
            messageConfirmationUseCase = get()
        )
    }
    factory { UpdateInspectionInformationUseCase(get(), get(), androidApplication() as AppModuleCommunicator) }
    factory {
        SentUseCase(
            sentRepo = get(),
            appModuleCommunicator = get(),
            firebaseAnalyticEventRecorder = get()
        )
    }
    factory {
        MessageConfirmationUseCase(
            messageConfirmationRepo = get(),
            coroutineScope = get()
        )
    }
    factory {
        DraftUseCase(
            draftRepo = get(),
            appModuleCommunicator = get(),
            featureFlagGateKeeper = get(),
            firebaseAnalyticEventRecorder = get()
        )
    }
    factory { CacheGroupsUseCase(get()) }
    factory { FirebaseCurrentUserTokenFetchUseCase() }
    factory { DraftingUseCase(get(), get(), get(),get(), get()) }
    factory { FormRenderUseCase() }
    factory { CustomWorkflowFormHandleUseCase(get()) }
    factory { ImageHandlerUseCase(get()) }
}

val roomDataBaseModule = module {

    fun provideDatabase(context: Context): MandatoryInspectionRelatedDatabase {
        return Room.databaseBuilder(
            context,
            MandatoryInspectionRelatedDatabase::class.java,
            "MandatoryInspectionRelatedDatabase"
        ).fallbackToDestructiveMigration().build()
    }

    fun provideMandatoryInspectionDao(database: MandatoryInspectionRelatedDatabase): MandatoryInspectionDao {
        return database.mandatoryInspectionDao
    }

    single { provideDatabase(androidContext()) }
    single { provideMandatoryInspectionDao(get()) }
}

var networkModule = module {
    factory { provideOkHttpClient() }
    single { provideRetrofit(get()) }
}

fun provideOkHttpClient(): OkHttpClient {
    val clientBuilder = OkHttpClient.Builder()
    if (BuildConfig.DEBUG) {
        val logInter = HttpLoggingInterceptor()
        logInter.level = HttpLoggingInterceptor.Level.BODY
        clientBuilder.addInterceptor(logInter)
    }
    return clientBuilder.connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .build()
}


fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
    return Retrofit.Builder().baseUrl("http://localhost/").client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create()).build()
}