@file:OptIn(ExperimentalMaterial3Api::class)

package com.trimble.ttm.formlibrary.ui.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.trimble.ttm.commons.composable.commonComposables.LoadingScreen
import com.trimble.ttm.commons.composable.commonComposables.ProgressErrorComposable
import com.trimble.ttm.commons.composable.commonComposables.ScreenContentState
import com.trimble.ttm.commons.composable.uiutils.styles.charCoalBlack
import com.trimble.ttm.commons.composable.uiutils.styles.charCoalPurple
import com.trimble.ttm.commons.composable.uiutils.styles.goldenRod
import com.trimble.ttm.commons.composable.uiutils.styles.grey
import com.trimble.ttm.commons.composable.uiutils.styles.lightGrey
import com.trimble.ttm.commons.composable.uiutils.styles.red
import com.trimble.ttm.commons.composable.uiutils.styles.steelBlue
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.fontSize20Sp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.heightSize100Dp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.heightSize200Dp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.heightSize32Dp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.heightSize35Dp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.heightSize40Dp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.heightSize48Dp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.heightSize4Dp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.heightSize8Dp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.paddingSize12Dp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.paddingSize16Dp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.paddingSize1Dp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.paddingSize4Dp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.paddingSize8Dp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.widthSize200Dp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.widthSize300Dp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.widthSize8Dp
import com.trimble.ttm.commons.composable.uiutils.styles.transparent
import com.trimble.ttm.commons.composable.uiutils.styles.white
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.AuthenticationState
import com.trimble.ttm.commons.ui.BasePermissionActivity
import com.trimble.ttm.commons.utils.AUTH_DEVICE_ERROR
import com.trimble.ttm.commons.utils.AUTH_SERVER_ERROR
import com.trimble.ttm.commons.utils.FORM_RESPONSE_PATH
import com.trimble.ttm.commons.utils.SHOULD_NOT_RETURN_TO_FORM_LIST
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.model.Favourite
import com.trimble.ttm.formlibrary.model.HotKeys
import com.trimble.ttm.formlibrary.ui.activities.ui.theme.MaineFormsWorkflowAppTheme
import com.trimble.ttm.formlibrary.ui.activities.ui.theme.typography
import com.trimble.ttm.formlibrary.utils.ARROW
import com.trimble.ttm.formlibrary.utils.BACK
import com.trimble.ttm.formlibrary.utils.COMPLETE_FORM_DETAIL
import com.trimble.ttm.formlibrary.utils.DETAILS
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FAVOURITE
import com.trimble.ttm.formlibrary.utils.FORMS
import com.trimble.ttm.formlibrary.utils.FORMS_SHORTCUT_USE_COUNT
import com.trimble.ttm.formlibrary.utils.FORM_DRAFT_RESPONSE_PATH
import com.trimble.ttm.formlibrary.utils.FORM_GROUP_TAB_INDEX
import com.trimble.ttm.formlibrary.utils.HOTKEYS
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_DRAFT_RESPONSE_COLLECTION
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_RESPONSE_COLLECTION
import com.trimble.ttm.formlibrary.utils.LOADING
import com.trimble.ttm.formlibrary.utils.SEARCH
import com.trimble.ttm.formlibrary.utils.TOGGLE_VIEW
import com.trimble.ttm.formlibrary.utils.ext.openActivityIfItsAlreadyInBackStack
import com.trimble.ttm.formlibrary.viewmodel.FormLibraryViewModel
import com.trimble.ttm.formlibrary.viewmodel.Forms
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import me.drakeet.support.toast.ToastCompat
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date

class FormLibraryActivity : BasePermissionActivity(), KoinComponent {

    private val tag = "FormLibraryActivity"
    private val formLibraryViewModel: FormLibraryViewModel by viewModel()
    private val formDataStoreManager: FormDataStoreManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.logLifecycle(tag, "$tag onCreate")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        formLibraryViewModel.changeSelectedTab(
            intent.extras?.getString(FORM_GROUP_TAB_INDEX) ?: FORMS
        )
        // This pair will hold the functions to get the value from forms and hotKeys to launch the form activity.
        // First data : It is a function that will be called when the user clicks the forms item.
        // Second data : It is a function that will be called when the user clicks the hotkeys item.
        val launchFormActivityRelatedPair = Pair<(Forms, String, String) -> Unit, (HotKeys, String, String) -> Unit>({ formItem, customerId, vehicleId ->
            getValuesFromFormsToLaunchFormActivity(
                formItem,
                customerId,
                vehicleId
            )
        }, { hotKeyItem, customerId, vehicleId ->
            getValuesFromHotkeysToLaunchFormActivity(
                hotKeyItem,
                customerId,
                vehicleId
            )
        })
        setContent {
            MaineFormsWorkflowAppTheme {
                FormAndHotKeysScreen(
                    formLibraryViewModel = formLibraryViewModel,
                    launchFormActivityRelatedPair = launchFormActivityRelatedPair
                )
            }
        }
        formLibraryViewModel.recordShortCutIconClickEvent(FORMS_SHORTCUT_USE_COUNT, intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.logLifecycle(tag, "$tag onNewIntent")
        formLibraryViewModel.changeSelectedTab(
            intent.extras?.getString(FORM_GROUP_TAB_INDEX) ?: FORMS
        )
        formLibraryViewModel.recordShortCutIconClickEvent(FORMS_SHORTCUT_USE_COUNT, intent)
    }

    override fun onStart() {
        //This will navigate driver to the form screen, if there is any pending form.
        openActivityIfItsAlreadyInBackStack(FormLibraryFormActivity::class.java.name)
        formLibraryViewModel.checkForUpdateFromServerIfActivityIsResumingFromBackgroundAfterLongTime()
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(tag, "$tag onResume")
        lifecycleScope.launch(formLibraryViewModel.dispatchers.main() + CoroutineName(tag)) {
            formDataStoreManager.setValue(FormDataStoreManager.IS_DRAFT_VIEW, false)
        }
    }

    override suspend fun onAllPermissionsGranted() {
        // Ignore
    }

    private fun getValuesFromFormsToLaunchFormActivity(
        formItem: Forms,
        customerId: String,
        vehicleId: String
    ) {
        with(formItem.formDef) {
            launchFormActivity(
                formId = formid.toString(),
                formName = name,
                formClass = formClass.toString(),
                customerId = customerId,
                vehicleId = vehicleId
            )
        }
    }

    private fun getValuesFromHotkeysToLaunchFormActivity(
        hotKeyItem: HotKeys,
        customerId: String,
        vehicleId: String
    ) {
        with(hotKeyItem) {
            launchFormActivity(
                formId = formId.toString(),
                formName = formName,
                formClass = formClass.toString(),
                customerId = customerId,
                vehicleId = vehicleId
            )
        }
    }

    private fun launchFormActivity(
        formId: String,
        formName: String,
        formClass: String,
        customerId: String,
        vehicleId: String
    ) {
        val activityIntent = intent
        val context = this
        if(vehicleId.isNotEmpty() && customerId.isNotEmpty()) {
            val intent = Intent(context, FormLibraryFormActivity::class.java).apply {
                putExtra(
                    FORM_RESPONSE_PATH,
                    "$INBOX_FORM_RESPONSE_COLLECTION/$customerId/$vehicleId"
                )
                putExtra(
                    FORM_DRAFT_RESPONSE_PATH,
                    "$INBOX_FORM_DRAFT_RESPONSE_COLLECTION/$customerId/$vehicleId"
                )
                with(ArrayList<String>()) {
                    add(customerId)
                    add(formId)
                    add(formName)
                    add(formClass)
                    putStringArrayListExtra(COMPLETE_FORM_DETAIL, this)
                }
            }
            //When form library opened from inbox or dispatch screen, finish the activity - feature expectation
            if(activityIntent.getBooleanExtra(SHOULD_NOT_RETURN_TO_FORM_LIST, false)) {
                finish()
            }
            startActivity(context, intent, null)
        } else {
            ToastCompat.makeText(
                context,
                context.getString(R.string.vehicle_id_or_cid_not_found),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(tag, "$tag onStop")
        formLibraryViewModel.updateExpiryTimeForFormFetchFromServer(Date().time)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.logLifecycle(tag, "$tag onDestroy")
    }
}

@Composable
fun FormAndHotKeysScreen(
    formLibraryViewModel: FormLibraryViewModel,
    launchFormActivityRelatedPair: Pair<(Forms, String, String) -> Unit, (HotKeys, String, String) -> Unit>
) {
    val authenticationState by formLibraryViewModel.composeAuthenticationState.observeAsState()
    val internetConnectionStatus by formLibraryViewModel.listenToNetworkConnectivityChange()
        .collectAsState(
            initial = formLibraryViewModel.isActiveInternetAvailable()
        )
    val context = LocalContext.current
    LaunchedEffect(key1 = true) {
       formLibraryViewModel.handleAuthenticationProcessForComposable("FormLibraryActivity")
    }
    Column(modifier = Modifier.background(color = charCoalPurple)) {
        when(authenticationState) {
            AuthenticationState.Loading -> {
                LoadingScreen(progressText = context.getString(R.string.authenticate_progress_text), show = true)
            }
            AuthenticationState.FirestoreAuthenticationSuccess -> {
                LoadingScreen(progressText = EMPTY_STRING, show = false)
            }
            AuthenticationState.Error(AUTH_DEVICE_ERROR) -> {
                LoadingScreen(progressText = context.getString(R.string.device_authentication_failure), show = false)
            }
            AuthenticationState.Error(AUTH_SERVER_ERROR) -> {
                LoadingScreen(progressText = context.getString(R.string.firestore_authentication_failure), show = false)
            }
            AuthenticationState.Error(context.getString(R.string.no_internet_authentication_failed)) -> {
                LoadingScreen(progressText = context.getString(R.string.no_internet_authentication_failed), show = false)
            }
            else -> {
                // Show error message
            }
        }
        if(authenticationState is AuthenticationState.Error && internetConnectionStatus) {
            formLibraryViewModel.handleAuthenticationProcessForComposable("FormLibraryActivityAfterInternetAvailable")
        }
        if(authenticationState == AuthenticationState.FirestoreAuthenticationSuccess) {
            val screenState by formLibraryViewModel.formsAndHotKeysScreenContentState.collectAsState()
            val isHotKeysAvailable by formLibraryViewModel.isHotKeysAvailable.observeAsState()
            Column(modifier = Modifier.background(color = charCoalPurple)) {
                LaunchedEffect(key1 = true) {
                    formLibraryViewModel.startForegroundService()
                    formLibraryViewModel.checkEDVIRAvailabilityAndUpdateHamburgerMenuVisibility()
                    formLibraryViewModel.observeInternetConnectivityAndCacheFormsIfRequired()
                    formLibraryViewModel.canShowHotKeysTab()
                }
                ProgressErrorComposable(screenContentState = screenState)
            }
            if(screenState == ScreenContentState.Success()){
                ParentComposableForHotkeysAndForms(formLibraryViewModel, isHotKeysAvailable = isHotKeysAvailable ?: false, launchFormActivityRelatedPair = launchFormActivityRelatedPair)
            }
        }
    }
}

@Composable
fun ParentComposableForHotkeysAndForms(
    formLibraryViewModel: FormLibraryViewModel,
    isHotKeysAvailable: Boolean,
    launchFormActivityRelatedPair: Pair<(Forms, String, String) -> Unit, (HotKeys, String, String) -> Unit>
) {
    val selectedTab by formLibraryViewModel.selectedTabIndex.collectAsState()
    val hotKeys by formLibraryViewModel.hotKeys.collectAsState()
    val forms by formLibraryViewModel.formList.collectAsState()
    var customerId by rememberSaveable { mutableStateOf(EMPTY_STRING) }
    var vehicleId by rememberSaveable { mutableStateOf(EMPTY_STRING) }
    val tabList = formLibraryViewModel.getTabListBasedOnHotKeys(isHotKeysAvailable)
    LaunchedEffect(key1 = true) {
        formLibraryViewModel.getDriverOriginatedForms()
        formLibraryViewModel.getHotkeys()
        customerId = formLibraryViewModel.appModuleCommunicator.doGetCid()
        vehicleId = formLibraryViewModel.appModuleCommunicator.doGetTruckNumber()
    }
    // Favorite forms are implemented for future use but are not currently utilized.
    // They can be removed in the future if deemed unnecessary.
    // For now, they can remain as they are.
    // formLibraryViewModel.getFavouriteForms()
    // val favoriteForms by formLibraryViewModel.favouriteForms.collectAsState()
    var searchText by rememberSaveable { mutableStateOf(EMPTY_STRING) }
    var isFocused by remember { mutableStateOf(false) }
    val displaySearch by formLibraryViewModel.canDisplaySearch.collectAsState()
    formLibraryViewModel.canDisplaySearch(selectedTab = selectedTab)

    Scaffold(
        topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(charCoalPurple)
                        .height(heightSize100Dp)
                        .padding(
                            top = WindowInsets.systemBars
                                .asPaddingValues()
                                .calculateTopPadding()
                        ), // Apply padding only to the top
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackButton()
                    Spacer(modifier = Modifier.width(widthSize8Dp))
                    TabLayout(
                        tabs = tabList,
                        selectedIndex = tabList.indexOf(selectedTab),
                        onTabSelected = {
                            formLibraryViewModel.changeSelectedTab(tabList[it])
                            formLibraryViewModel.canDisplaySearch(tabList[it])
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    SearchFormsTextField(
                        searchText = searchText,
                        isFocused = isFocused,
                        onSearchTextChange = { searchText = it },
                        onFocusChange = { isFocused = it },
                        displaySearch = displaySearch
                    )
                }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LaunchedEffect(selectedTab) {
                searchText = EMPTY_STRING
            }

            val formsAndHotKeysTabRowHeight = heightSize100Dp
            val topAndBottomPaddingOfContainerInDp = heightSize35Dp
            // Calculate the total available height for the content by subtracting the heights of the TabRow,
            // top and bottom padding.
            val totalAvailableHeight =
                LocalConfiguration.current.screenHeightDp.dp - formsAndHotKeysTabRowHeight - topAndBottomPaddingOfContainerInDp
            val customerAndVehicleIdPair = Pair(customerId, vehicleId)
            when (selectedTab) {
                HOTKEYS ->
                    HotKeysScreen(
                        availableHeight = totalAvailableHeight,
                        items = hotKeys,
                        customerIdAndVehicleIdPair = customerAndVehicleIdPair,
                        getGridCellsCountBasedOnItemsSize = { itemCount ->
                            formLibraryViewModel.getGridCellsCountBasedOnItemsSize(
                                itemCount
                            )
                        },
                        isHotKeysFetchInProgress = formLibraryViewModel.hotKeysScreenState.collectAsState().value,
                        getValuesFromHotkeysToLaunchFormActivity = launchFormActivityRelatedPair.second,
                        getMaxLinesForHotKeys = { hotKeysItemCount -> formLibraryViewModel.getMaxLinesForHotKeys(hotKeysItemCount) }
                    )

                FORMS -> {
                    // This pair will hold the data of whether the form fetch pagination is in progress and whether the last form has been received.
                    // First data : It is used to determine whether the form fetch pagination is in progress.
                    // Second data : It is used to determine whether the last form has been received.
                    val formsPaginationRelatedPair = Pair(
                        formLibraryViewModel.isPaginationLoading.collectAsState().value,
                        formLibraryViewModel.isLastFormReceived.observeAsState(false).value
                    )
                    // This pair will hold the data of whether the forms should be displayed in list view or card view and the function to change the view.
                    // First data : It is used to determine whether the forms should be displayed in list view or card view related .
                    // Second data : It is a function that will be called when the user clicks the toggle button.
                    val formsListOrCardViewRelatedPair = Pair(
                        formLibraryViewModel.isListView.collectAsState().value
                    ) { isListView: Boolean -> formLibraryViewModel.changeListView(isListView) }
                    // This pair will hold the data of the functions related to the favourites.
                    // First data : It is a data that holds the removeFavourite function.
                    // Second data : It is a data that holds the updateFavourite function.
                    val favouritesFunctionsRelatedPair =
                        Pair<(String) -> Unit, (Favourite) -> Unit>(
                            { formId -> formLibraryViewModel.removeFavourite(formId) },
                            { favourite -> formLibraryViewModel.updateFavourite(favourite) }
                        )
                    // This pair will hold the data of the functions related to the form search and pagination.
                    // First data : It is a function that will be called when the user searches for a form.
                    // Second data : It is a function that will be called when the user scrolls to the end of the list to fetch more forms.
                    val formSearchAndPaginationFunctionsRelatedPair =
                        Pair<() -> List<Forms>, suspend () -> Unit>(
                            {
                                formLibraryViewModel.filterFormsBasedOnSearchText(
                                    forms,
                                    searchText
                                )
                            },
                            { formLibraryViewModel.fetchFormsWithPagination() }
                        )
                    FormsScreen(
                        customerIdAndVehicleIdPair = customerAndVehicleIdPair,
                        formsPaginationRelatedPair = formsPaginationRelatedPair,
                        formsListOrCardViewRelatedPair = formsListOrCardViewRelatedPair,
                        favouritesFunctionsRelatedPair = favouritesFunctionsRelatedPair,
                        isFormFetchInProgress = formLibraryViewModel.formsScreenState.collectAsState().value,
                        formSearchAndPaginationFunctionsRelatedPair = formSearchAndPaginationFunctionsRelatedPair,
                        getValuesFromFormsToLaunchFormActivity = launchFormActivityRelatedPair.first
                    )
                }
                //2 -> FavouriteItemList(favoriteForms, formLibraryViewModel)
            }
        }
    }
}

@Composable
fun BackButton() {
    val context = LocalContext.current
    IconButton(onClick = {
        (context as Activity).finish()
    }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = BACK,
            tint = white
        )
    }
}

@Composable
fun SearchFormsTextField(
    searchText: String,
    isFocused: Boolean,
    onSearchTextChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    displaySearch: Boolean
) {
    if (displaySearch) {
        TextField(
            value = searchText,
            onValueChange = { onSearchTextChange(it) },
            placeholder = {
                if (!isFocused) {
                    Text(
                        text = stringResource(R.string.search),
                        color = grey
                    )
                }
            },
            modifier = Modifier
                .width(widthSize300Dp)
                .padding(paddingSize8Dp)
                .onFocusChanged {
                    onFocusChange(isFocused)
                },
            shape = CircleShape,
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = SEARCH,
                    modifier = Modifier.padding(paddingSize8Dp),
                    tint = grey
                )
            },
            singleLine = true,
            textStyle = TextStyle(color = white),
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = charCoalBlack,
                focusedIndicatorColor = transparent,
                unfocusedIndicatorColor = transparent
            )
        )
    }
}

@Composable
fun TabLayout(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabRowWidth = heightSize200Dp * tabs.size
    Row(
        modifier = Modifier
            .width(tabRowWidth)
            .background(charCoalPurple),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedIndex,
            containerColor = charCoalPurple,
            contentColor = transparent,
            indicator = { tabPositions ->
                if(selectedIndex in tabPositions.indices) {
                    Box(
                        Modifier
                            .tabIndicatorOffset(tabPositions[selectedIndex])
                            .background(goldenRod)
                            .fillMaxHeight(0.1f)
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    text = { Text(text = title, color = white, fontSize = fontSize20Sp) },
                    selected = selectedIndex == index,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier
                        .width(widthSize200Dp)
                        .background(if (selectedIndex == index) transparent else charCoalPurple)
                )
            }
        }
    }
}


@Composable
fun FormsScreen(
    customerIdAndVehicleIdPair: Pair<String, String>,
    formsPaginationRelatedPair: Pair<Boolean, Boolean>,
    formsListOrCardViewRelatedPair: Pair<Boolean, (Boolean) -> Unit>,
    favouritesFunctionsRelatedPair: Pair<(String) -> Unit, (Favourite) -> Unit>,
    isFormFetchInProgress : ScreenContentState,
    formSearchAndPaginationFunctionsRelatedPair: Pair<() -> List<Forms>, suspend () -> Unit>,
    getValuesFromFormsToLaunchFormActivity : (Forms, String, String) -> Unit
    ) {
    Column(modifier = Modifier
        .background(color = charCoalBlack)
        .padding(paddingSize16Dp)
    ) {
        ProgressErrorComposable(screenContentState = isFormFetchInProgress)
        if (isFormFetchInProgress == ScreenContentState.Success()) {
            DisplayForms(
                customerIdAndVehicleIdPair = customerIdAndVehicleIdPair,
                formsPaginationRelatedPair = formsPaginationRelatedPair,
                formsListOrCardViewRelatedPair = formsListOrCardViewRelatedPair,
                favouritesFunctionsRelatedPair = favouritesFunctionsRelatedPair,
                formSearchAndPaginationFunctionalityRelatedPair = formSearchAndPaginationFunctionsRelatedPair,
                getValuesFromFormsToLaunchFormActivity = getValuesFromFormsToLaunchFormActivity
            )
        }
    }
}

@Composable
fun DisplayForms(
    customerIdAndVehicleIdPair: Pair<String, String>,
    formsPaginationRelatedPair: Pair<Boolean, Boolean>,
    formsListOrCardViewRelatedPair: Pair<Boolean, (Boolean) -> Unit>,
    favouritesFunctionsRelatedPair: Pair<(String) -> Unit, (Favourite) -> Unit>,
    formSearchAndPaginationFunctionalityRelatedPair: Pair<() -> List<Forms>, suspend () -> Unit>,
    getValuesFromFormsToLaunchFormActivity: (Forms, String, String) -> Unit
) {
    val isListView = formsListOrCardViewRelatedPair.first
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(charCoalPurple)
            .height(heightSize48Dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { formsListOrCardViewRelatedPair.second(isListView) }) {
            Icon(
                imageVector = if (isListView) Icons.Filled.ViewModule else Icons.AutoMirrored.Filled.ViewList,
                contentDescription = TOGGLE_VIEW,
                tint = white
            )
        }
    }
    if (isListView) {
        ItemsList(
            customerIdAndVehicleIdPair = customerIdAndVehicleIdPair,
            isPaginationLoading = formsPaginationRelatedPair.first,
            isLastFormReached = formsPaginationRelatedPair.second,
            favouritesFunctionsRelatedPair = favouritesFunctionsRelatedPair,
            formSearchAndPaginationFunctionalityRelatedPair = formSearchAndPaginationFunctionalityRelatedPair,
            getValuesFromFormsToLaunchFormActivity = getValuesFromFormsToLaunchFormActivity
        )
    } else {
        ItemsCard(
            customerIdAndVehicleIdPair = customerIdAndVehicleIdPair,
            isPaginationLoading = formsPaginationRelatedPair.first,
            isLastFormReached = formsPaginationRelatedPair.second,
            favouritesFunctionsRelatedPair = favouritesFunctionsRelatedPair,
            formSearchAndPaginationFunctionalityRelatedPair = formSearchAndPaginationFunctionalityRelatedPair,
            getValuesFromFormsToLaunchFormActivity = getValuesFromFormsToLaunchFormActivity
        )
    }
}

@Composable
fun FavouriteItemList(
    favouriteItems: Set<Favourite>
) {
    LazyColumn {
        items(favouriteItems.toList()) { favouriteItem ->
            FavouriteItemRow(
                favouriteItem
            )
        }
    }
}

@Composable
fun FavouriteItemRow(item: Favourite) {

    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(charCoalPurple)
            .padding(paddingSize16Dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = item.formName, color = white)
        }
        Row {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = FAVOURITE,
                    tint = red
                )
            }
            IconButton(onClick = {
                val intent = Intent(context, FormLibraryFormActivity::class.java).apply {
                    with(ArrayList<String>()) {
                        add(item.cid)
                        add(item.formId)
                        add(item.formName)
                        add(item.formClass)
                        putStringArrayListExtra(COMPLETE_FORM_DETAIL, this)
                    }
                }
                startActivity(context, intent, null)
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = DETAILS
                )
            }
        }
    }
}

@Composable
fun ItemsCard(
    customerIdAndVehicleIdPair: Pair<String, String>,
    isPaginationLoading: Boolean,
    isLastFormReached: Boolean,
    favouritesFunctionsRelatedPair: Pair<(String) -> Unit, (Favourite) -> Unit>,
    formSearchAndPaginationFunctionalityRelatedPair: Pair<() -> List<Forms>, suspend () -> Unit>,
    getValuesFromFormsToLaunchFormActivity: (Forms, String, String) -> Unit
) {
    // Define the number of columns in the grid as 4.
    val formsGridColumns = 4
    // Calculate the width of each form card by subtracting a fixed padding (heightSize32Dp) from the screen width,
    // and then dividing the result by the number of columns (formsGridColumns).
    val formsCardWidth =
        (LocalConfiguration.current.screenWidthDp.dp - heightSize32Dp) / formsGridColumns
    // Calculate the total available height for the content by subtracting the heights of the top and bottom padding,
    // the height of the TabRow, and the height of the toggle button.
    val totalAvailableHeight =
        LocalConfiguration.current.screenHeightDp.dp - heightSize100Dp - heightSize40Dp
    val filteredItems = formSearchAndPaginationFunctionalityRelatedPair.first()
    LazyVerticalGrid(
        columns = GridCells.Fixed(formsGridColumns),
        modifier = Modifier
            .fillMaxSize()
            .background(charCoalBlack)
    ) {
        // This pair will hold the total height and the width of the form card.
        val totalHeightAndFormsCardWidthRelatedPair = Pair(totalAvailableHeight, formsCardWidth)
        items(filteredItems) { formItem ->
            FormItemInCard(
                totalHeightAndFormsCardWidthRelatedPair = totalHeightAndFormsCardWidthRelatedPair,
                item = formItem,
                customerIdAndVehicleIdPair = customerIdAndVehicleIdPair,
                removeFavourite = favouritesFunctionsRelatedPair.first,
                updateFavourite = favouritesFunctionsRelatedPair.second,
                getValuesFromFormsToLaunchFormActivity = getValuesFromFormsToLaunchFormActivity
            )
        }
        if (isLastFormReached.not()) {
            item {
                FormsPaginationScreen(isPaginationLoading = isPaginationLoading, fetchFormWithPagination = formSearchAndPaginationFunctionalityRelatedPair.second)
            }
        }
    }
}

@Composable
private fun FormItemInCard(
    totalHeightAndFormsCardWidthRelatedPair: Pair<Dp, Dp>,
    item: Forms,
    customerIdAndVehicleIdPair: Pair<String, String>,
    removeFavourite: (String) -> Unit,
    updateFavourite: (Favourite) -> Unit,
    getValuesFromFormsToLaunchFormActivity: (Forms, String, String) -> Unit
) {
    // Calculate the height of each form card by dividing the total height by 4.
    val formsCardHeight = totalHeightAndFormsCardWidthRelatedPair.first / 4
    Card(
        modifier = Modifier
            .padding(paddingSize4Dp)
            .width(totalHeightAndFormsCardWidthRelatedPair.second)
            .height(formsCardHeight)
            .clickable {
                getValuesFromFormsToLaunchFormActivity(
                    item,
                    customerIdAndVehicleIdPair.first,
                    customerIdAndVehicleIdPair.second
                )
            },
        elevation = CardDefaults.cardElevation(paddingSize4Dp),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = charCoalPurple)
    ) {
        Column(
            modifier = Modifier
                .padding(paddingSize8Dp)
                .fillMaxHeight(),
        ) {
            Text(
                text = item.formDef.name,
                color = white,
                fontWeight = FontWeight.Bold,
                fontSize = typography.titleLarge.fontSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier
                .height(heightSize8Dp)
                .weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = {
                    if (item.isFavourite) {
                        //item.isFavourite = false
                        removeFavourite(
                            item.formDef.formid.toString()
                        )
                    } else {
                        //item.isFavourite = true
                        updateFavourite(
                            Favourite(
                                item.formDef.formid.toString(),
                                item.formDef.name,
                                item.formDef.cid.toString(),
                                item.formDef.formClass.toString()
                            )
                        )
                    }
                }, modifier = Modifier.size(0.dp)) {
                    Icon(
                        imageVector = if (item.isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = FAVOURITE,
                        tint = red
                    )
                }
                IconButton(onClick = {
                    getValuesFromFormsToLaunchFormActivity(
                        item,
                        customerIdAndVehicleIdPair.first,
                        customerIdAndVehicleIdPair.second
                    )
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = ARROW,
                        tint = white
                    )
                }
            }
        }
    }
}

@Composable
fun ItemsList(
    customerIdAndVehicleIdPair: Pair<String, String>,
    isPaginationLoading: Boolean,
    isLastFormReached: Boolean,
    favouritesFunctionsRelatedPair : Pair<(String) -> Unit, (Favourite) -> Unit>,
    formSearchAndPaginationFunctionalityRelatedPair :  Pair<() -> List<Forms>, suspend () -> Unit>,
    getValuesFromFormsToLaunchFormActivity : (Forms, String, String) -> Unit
) {
    val filteredItems = formSearchAndPaginationFunctionalityRelatedPair.first()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(filteredItems.toMutableList()) { item ->
            FormItemInList(
                item = item,
                customerIdAndVehicleIdPair = customerIdAndVehicleIdPair,
                removeFavourite = favouritesFunctionsRelatedPair.first,
                updateFavourite = favouritesFunctionsRelatedPair.second,
                getValuesFromFormsToLaunchFormActivity = getValuesFromFormsToLaunchFormActivity
            )
            HorizontalDivider(color = lightGrey)
        }
        if (isLastFormReached.not()) {
            item {
                FormsPaginationScreen(isPaginationLoading = isPaginationLoading, fetchFormWithPagination = formSearchAndPaginationFunctionalityRelatedPair.second)
            }
        }
    }
}

@Composable
private fun FormItemInList(
    item: Forms,
    customerIdAndVehicleIdPair: Pair<String, String>,
    removeFavourite: (String) -> Unit,
    updateFavourite: (Favourite) -> Unit,
    getValuesFromFormsToLaunchFormActivity : (Forms, String, String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(charCoalPurple)
            .padding(16.dp)
            .clickable {
                getValuesFromFormsToLaunchFormActivity(
                    item,
                    customerIdAndVehicleIdPair.first,
                    customerIdAndVehicleIdPair.second
                )
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.formDef.name, color = white, fontSize = typography.titleLarge.fontSize)
        }
        Row {
            IconButton(onClick = {
                if (item.isFavourite) {
                    // item.isFavourite = false
                    removeFavourite(
                        item.formDef.formid.toString()
                    )
                } else {
                    //item.isFavourite = true
                    updateFavourite(
                        Favourite(
                            item.formDef.formid.toString(),
                            item.formDef.name,
                            item.formDef.cid.toString(),
                            item.formDef.formClass.toString()
                        )
                    )
                }
            }, modifier = Modifier.size(0.dp)) {
                Icon(
                    imageVector = if (item.isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = FAVOURITE,
                    tint = if (item.isFavourite) red else grey
                )
            }
            IconButton(onClick = {
                getValuesFromFormsToLaunchFormActivity(
                    item,
                    customerIdAndVehicleIdPair.first,
                    customerIdAndVehicleIdPair.second
                )
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = DETAILS,
                    tint = white
                )
            }
        }
    }
}

@Composable
private fun FormsPaginationScreen(isPaginationLoading: Boolean, fetchFormWithPagination: suspend () -> Unit) {
    if (isPaginationLoading) {
        LoadingScreen(progressText = LOADING, show = true)
    } else {
        LaunchedEffect(key1 = Unit) {
            fetchFormWithPagination()
        }
    }
}

@Composable
fun HotKeysScreen(
    availableHeight: Dp,
    items: Set<HotKeys>,
    customerIdAndVehicleIdPair: Pair<String, String>,
    getGridCellsCountBasedOnItemsSize: (itemCount: Int) -> Int,
    isHotKeysFetchInProgress: ScreenContentState,
    getValuesFromHotkeysToLaunchFormActivity: (HotKeys, String, String) -> Unit,
    getMaxLinesForHotKeys : (Int) -> Int
) {

    Column(
        modifier = Modifier
            .background(color = charCoalBlack)
            .padding(paddingSize12Dp)
    ) {
        ProgressErrorComposable(screenContentState = isHotKeysFetchInProgress)
        if (isHotKeysFetchInProgress == ScreenContentState.Success()) {
            DisplayHotKeys(
                availableHeight = availableHeight,
                items = items,
                customerIdAndVehicleIdPair = customerIdAndVehicleIdPair,
                getGridCellsCountBasedOnItemsSize = getGridCellsCountBasedOnItemsSize,
                getValuesFromHotkeysToLaunchFormActivity = getValuesFromHotkeysToLaunchFormActivity,
                getMaxLinesForHotKeys = getMaxLinesForHotKeys
            )
        }
    }
}

@Composable
private fun DisplayHotKeys(
    availableHeight: Dp,
    items: Set<HotKeys>,
    customerIdAndVehicleIdPair: Pair<String, String>,
    getGridCellsCountBasedOnItemsSize: (itemCount: Int) -> Int,
    getValuesFromHotkeysToLaunchFormActivity: (HotKeys, String, String) -> Unit,
    getMaxLinesForHotKeys: (Int) -> Int
) {
    val hotKeysItemCount = items.size
    // Calculate the width of each hotkey card as one-fourth of the screen width.
    val hotKeyCardWidth = LocalConfiguration.current.screenWidthDp.dp / 4
    // Calculate the height of each hotkey card based on the number of hotkeys:
    // If there are 8 or fewer hotkeys, set the height to half of the available height.
    // If there are 9 to 12 hotkeys, set the height to one-third of the available height.
    // If there are more than 12 hotkeys, set the height to one-fourth of the available height.
    val hotKeyCardHeight =
        if (hotKeysItemCount <= 8) availableHeight / 2 else if (hotKeysItemCount in 9..12) availableHeight / 3 else availableHeight / 4
    // Calculate the font size for the hotkey title and form name based on the number of hotkeys:
    // If there are 4 or fewer hotkeys, use the titleMedium font size for the title and the headlineSmall font size for the form name.
    // If there are more than 4 hotkeys, use the titleMedium font size for the title and the bodyLarge font size for the form name.
    val hotkeysTitleFontTypography : TextUnit
    val hotkeysFormNameFontTypography : TextUnit
    if(hotKeysItemCount <= 4){
        hotkeysTitleFontTypography = typography.titleMedium.fontSize
        hotkeysFormNameFontTypography =  typography.headlineSmall.fontSize
    } else {
        hotkeysTitleFontTypography = typography.titleMedium.fontSize
        hotkeysFormNameFontTypography = typography.bodyLarge.fontSize
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(
            getGridCellsCountBasedOnItemsSize(hotKeysItemCount)
        ),
        modifier = Modifier
            .fillMaxSize()
            .background(charCoalBlack),
    ) {
        items(hotKeysItemCount) { index ->
            Card(
                modifier = Modifier
                    .padding(paddingSize8Dp)
                    .width(hotKeyCardWidth)
                    .height(hotKeyCardHeight)
                    .background(steelBlue)
                    .border(paddingSize1Dp, steelBlue),
                elevation = CardDefaults.cardElevation(paddingSize4Dp),
                shape = RectangleShape,
                colors = CardDefaults.cardColors(
                    containerColor = steelBlue
                ),
                onClick = {
                    getValuesFromHotkeysToLaunchFormActivity(
                        items.elementAt(index),
                        customerIdAndVehicleIdPair.first,
                        customerIdAndVehicleIdPair.second
                    )
                }
            ) {
                Column(
                    modifier = Modifier.padding(paddingSize16Dp),
                ) {
                    Text(
                        text = items.elementAt(index).hotKeysDescription.description,
                        color = white,
                        fontWeight = FontWeight.Bold,
                        fontSize = hotkeysTitleFontTypography
                    )
                    Spacer(modifier = Modifier.height(heightSize4Dp))
                    Text(
                        text = items.elementAt(index).formName,
                        color = white,
                        fontSize = hotkeysFormNameFontTypography,
                        maxLines = getMaxLinesForHotKeys(hotKeysItemCount),
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}