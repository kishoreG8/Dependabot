package com.trimble.ttm.commons.logger

enum class FeatureLogTags {
    ROUTE_CALCULATION_RESULT, APPLICATION_CLASS_ON_CREATE, FEATURE_FLAG_TAG
}

const val SERVICE="ForegroundService"
const val SERVICE_NOTIFICATION="NotificationService"
const val SERVICE_MANAGER="ServiceManager"
const val ACTIVITY = "Act"
const val FRAGMENT = "Frag"
const val VIEW_MODEL = "VM"
const val USE_CASE = "UC"
const val REPO="Repo"
const val WIDGET="Widget"
const val NOTIFICATION="Notification"

//Authentication
const val DEVICE_AUTH="Auth"
const val DEVICE_FCM="FCM"
const val STATUS="Status"
const val APP_CHECK = "AppCheck"

//Trips
const val TRIP="Trip"
const val TRIP_WIDGET = "TripWidget"
const val TRIP_COMPLETE = "TripCompleteUpdate"
const val TRIP_COMPLETE_FORM_PENDING = "TripCompleteFormPending"
const val TRIP_STOP_COUNT = "TripStopCount"
const val TRIP_IS_READY = "TripReady"
const val TRIP_ACTIVE_CHECK = "TripActiveIDCheck"
const val TRIP_STOP_LIST = "TripStopList"
const val TRIP_PFM_EVENT = "TripPFMEvent"
const val TRIP_COUNT = "TripCount"
const val TRIP_FORM = "TripOpenForm"
const val TRIP_FIRST_STOP_CURRENT_STOP = "TripStartCurrentStop"
const val TRIP_ID = "TripId"
const val TRIP_CURRENT_STOP_ID = "TripCurrentStopID"
const val TRIP_EDIT = "TripEdit"
const val TRIP_LIST = "TripList"
const val TRIP_STOP_REMOVAL_WORKER = "TripStopRemovalWorker"
const val TRIP_MAP_REQUEST = "TripMapRequest"
const val TRIP_UNCOMPLETED_FORMS="TripUncompletedForms"
const val TRIP_STOP_ACTION_LATE_NOTIFICATION = "TripStopActionLateNotification"
const val TIMELINE = "TimeLine"
const val TRIP_START_CALL = "TRIP_START"
const val AUTO_TRIP_START = "AutoTripStart"
const val TRIP_START_INSIDE_APP = "TripStartInsideApp"
const val GET_ALL_DISPATCHES = "GetAllDispatches"
const val LISTEN_ALL_DISPATCHES = "ListenAllDispatches"
const val MANUAL_TRIP_START = "ManualTripStart"
const val AUTO_TRIP_END = "AutoTripEnd"
const val TRIP_VALIDATION = "TripSingleCheck"
const val GEOFENCE_EVENT_PROCESS = "GeoFenceEventProcess"
const val TRIP_STOP_AUTO_ARRIVAL = "TripStopAutoArrival"
const val TRIP_STOP_MANUAL_ARRIVAL = "TripStopManualArrival"
const val TRIP_DRAFT_FORM_DELETE="TripDraftFormDelete"
const val TRIP_FORM_CRUD="TripFormCRUD"
const val STOP_ACTION_EVENTS="StopActionEvents"
const val MESSAGE_CONFIRMATION = "MessageConfirmation"
const val DISPATCH_BLOB = "DispatchBlob"
const val TRIP_LOAD_VIEW="TripLoadView"
const val TRIP_STOP_LIST_ADAPTER = "TripStopListAdapter"

const val TRIP_ONE_MINUTE_DELAY="TripOneMinuteDelay"
const val TRIP_PANEL = "TripPanel"

//Stops
const val STOP="Stop"

//Messages
const val INBOX="Inbox"
const val INBOX_LIST_UI_ON_BIND = "InboxListOnBind"
const val INBOX_LIST="InboxList"
const val INBOX_LIST_DIFF_UTIL="DiffUtil"
const val MESSAGE_DELETE="DeleteMsg"
const val MESSAGE_DETAIL="MsgDetail"
const val ACKNOWLEDGMENT="Ack"
const val RECIPIENT="Recipient"
const val IMAGE="EncodedImage"
const val TRASH_LIST_UI_ON_BIND = "TrashListOnBind"
const val TRASH = "Trash"
const val TRASH_LIST="TrashList"

//TTS
const val TTS="TTS"
//Forms

//Barcode Compose
const val BARCODE_RESULTS="BarcodeResults"
const val BARCODE_MODULE_AVAILABILITY="BarcodeModuleAvailability"
const val BARCODE_MODULE_DOWNLOAD="BarcodeModuleDownload"
//EDVIR

//Managed Config
const val MANAGED_CONFIG = "ManagedConfig"
const val DEEP_LINK = "DeepLink"

//Workflow Events Communication
const val DRIVER_WORKFLOW_EVENTS_COMMUNICATION = "DriverWorkflowEventsCommunication"

const val ARRIVAL_PROMPT = "ArrivalPrompt"
const val ARRIVAL_PROMPT_PANEL = "ArrivalPromptPanel"
const val SHOW_ARRIVAL_DIALOG="ShowArrivalDialogApp"
const val DID_YOU_ARRIVE_DATASTORE_KEY_MANIPULATION = "DidYouArriveTriggerDataStoreKeyManipulation"

const val NEGATIVE_GUF_BACKGROUND_TIMER = "NegativeGufBackgroundTimer"
const val ARRIVAL_DIALOG_YES_CLICK = "arriveDialogYesClick"
const val ARRIVAL_DIALOG_NO_CLICK = "arriveDialogNoClick"

const val FORM_DATA_RESPONSE = "formDataResponse"
const val FREE_FORM_DATA_RESPONSE = "FreeFormDataResponse"
const val INSPECTION_FORM_DATA_RESPONSE = "InspectionFormDataResponse"

const val INSPECTION_FLOW = "InspectionFlow"

//Caller tags for saveToDraft event
const val SAVE_TO_DRAFTS_BUTTON_CLICK = "SaveToDraftsButtonClick"
const val ON_STOP_CALLBACK = "OnStopCallback"
const val BACK_BUTTON_CLICK = "BackButtonClick"
const val DRIVER_AND_REPLY_MESSAGE_NAVIGATION = "DriverAndReplyMessageNavigation"
const val FORM_DEF_VALUES = "FormDefValues"
const val INBOX_MESSAGE_DEF_VALUES = "InboxMessageDefaultValues"

const val TRIP_CACHING = "TripCaching"
const val TRIP_PREVIEWING = "TripPreview"
const val DISPATCH_FORM_SAVE = "DispatchFormSave"
const val DISPATCH_FORM_DRAFT = "DispatchFormDraft"
const val ACTION_COMPLETION = "ActionCompletion"

//Arrival
const val MANUAL_ARRIVAL_STOP = "ManualArrivalOfStop"
const val AUTO_TRIP_START_BACKGROUND = "AutoTripStartBackground"
const val AUTO_TRIP_START_CALLER_PUSH_NOTIFICATION = "AutoTripStartCallerPushNotification"
const val AUTO_TRIP_START_CALLER_FOREGROUND_SERVICE = "AutoTripStartCallerForegroundService"
const val AUTO_TRIP_START_CALLER_TRIP_END = "AutoTripStartCallerTripEnd"
const val FAVOURITES = "Favourites"

const val ARRIVAL_REASON = "ArrivalReason"

//Internet Check
const val INTERNET_CONNECTIVITY = "InternetConnectivity"

const val CHANGE_USER = "ChangeUser"

const val KEY = "key"
const val DISPATCH_LIFECYCLE = "DispatchLifecycle"