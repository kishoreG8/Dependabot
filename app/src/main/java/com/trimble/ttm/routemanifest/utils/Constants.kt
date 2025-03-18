package com.trimble.ttm.routemanifest.utils

const val SELECTED_STOP_ID = "stopID"
const val CURRENT_STOP_INDEX = "currentIndex"
const val DISPATCH_BLOB_COLLECTION = "DispatchBlob"
const val VEHICLES_COLLECTION = "Vehicles"
const val DISPATCH_QUERY_LIMIT = 100L
const val RECEIVED_DISPATCH_SET_LIMIT = 50
const val DISPATCH_ID_TO_RENDER = "dispatchIdToRender"

const val IS_DRIVER_IN_IMESSAGE_REPLY_FORM = "IsDriverInImessageReplyForm"

const val PAYLOAD = "Payload"
const val ADDED = "added"
const val REMOVED = "removed"
const val INVALID_STOP_INDEX = "invalid_index"
const val STOP_COUNT_CHANGE_LISTEN_DELAY = 5000L
const val STOP_NAME_FOR_FORM = "stop_name_for_form"
const val COLLECTION_NAME_TRIP_END = "tripEnd"
const val COLLECTION_NAME_TRIP_START = "tripStart"
const val COLLECTION_NAME_VEHICLES = "vehicles"
const val COLLECTION_NAME_DISPATCH_EVENT = "dispatchEvent"
const val COLLECTION_NAME_STOP_EVENTS = "stopEvents"
const val NEXT_STOP_MESSAGE_ID = 32
const val SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID = 33
const val SAVED_STATE = "saved_state"
const val FORM_COUNT_FOR_STOP = "formCountOfStops"

const val ISO_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
const val TRIP_START_TIME_VALIDATION_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'.000Z'"

const val GMT_DATE_TIME_FORMAT = "EEE MMM dd HH:mm:ss zzzz yyyy"

const val FILL_FORMS_MESSAGE_ID = 34
const val TRUE = true
const val FALSE = false
const val ISCOMPLETED = "IsCompleted"
const val ISREADY = "IsReady"
const val DISPATCH_DELETION_FCM_KEY = "isDispatchDeleted"
const val IS_DISPATCH_DELETED = "IsDispatchDeleted"
const val DISPATCH_DELETED_TIME = "DispatchDeletedTime"
const val SCHEDULE_TIME_FOR_DISPATCH_VISIBILITY: Long = 1000 * 60 //1 Minute
const val IS_TRIP_COMPLETION_POPUP_SHOWN = "isTripCompletionPopupShown"
const val IS_ACTIVE_DISPATCH = "IsActiveDispatch"

const val TOAST_DEBOUNCE_TIME = 1000L

// TODO store keys securely
const val CONSUMER_KEY = "cbae6254-8dc0-4d6c-8dcf-058fa9239fea"
const val PROD_CONSUMER_KEY = "ebd2ee6f-1596-4bb0-8550-965d8fe3e3fd"

const val TRIP_POSITIVE_ACTION_TIMEOUT_IN_SECONDS = 15
const val TRIP_POSITIVE_ACTION_TIMEOUT_IN_MILLISECONDS = 15000L
const val TRIP_POSITIVE_ACTION_COUNTDOWN_INTERVAL_IN_MILLISECONDS = 1000L
const val FLAVOR_DEV = "dev"
const val FLAVOR_QA = "qa"
const val FLAVOR_STG = "stg"
const val FLAVOR_PROD = "prod"
const val LAUNCHER_PACKAGE_NAME_PREFIX = "com.trimble.ttm.applauncher"
const val DISPATCHER_FORM_VALUES = "DispatcherFormValues"
const val DISPATCH_DESC_CHAR_LENGTH = 280
const val START_INDEX = 0
const val END_INDEX = 281

//Trip panel message priority
const val TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY_FOR_CURRENT_STOP = 0
const val TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY = 1
const val TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY = 2
const val TRIP_PANEL_SELECT_STOP_MSG_PRIORITY = 3
const val TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY = 4
const val INVALID_PRIORITY = -1

//Trip panel icon file name without extension
const val TRIP_PANEL_DID_YOU_ARRIVE_MSG_ICON_FILE_NAME = "trip_panel_did_you_arrive_icon"
const val TRIP_PANEL_COMPLETE_FORM_MSG_ICON_FILE_NAME = "trip_panel_form_message_icon"
const val TRIP_PANEL_SELECT_STOP_MSG_ICON_FILE_NAME = "trip_panel_select_stop_icon"
const val TRIP_PANEL_MILES_AWAY_MSG_ICON_FILE_NAME = "trip_panel_next_stop_distance_icon"

const val INCOMING_ARRIVED_TRIGGER = "arrived_geofence_trigger"

const val DRIVER_FORM_HASH = 5501L

const val DEFAULT_DEPART_RADIUS_IN_FEET = 26400 // 5 miles
const val DEFAULT_ARRIVED_RADIUS_IN_FEET = 2640 // 1/2 miles
const val DEFAULT_RADIUS_FOR_ACTION_NOT_FOUND_IN_TRIP_XML = -1
const val ARRIVED_RADIUS_RELATIVE_TO_DEPART_RADIUS_IN_PERCENTAGE = 0.6
const val DEFAULT_DETENTION_WARNING_RADIUS_IN_FEET = 600
const val INTENT_ACTION_MAP_SERVICE_STATUS_EVENT = "com.trimble.ttm.MAP_SERVICE_STATUS_EVENT"
const val KEY_MAP_SERVICE_STATUS_EVENT = "map_service_status_event"
const val MAP_SERVICE_BOUND = "map_service_bound"
const val APP_UPDATE_TRACKER_TAG = "APP-UPDATE-TRACKER"
const val BOOT_COMPLETE_TRACKER_TAG = "BOOT-COMPLETE-TRACKER"

const val ETA_RETRIEVAL_FOR_STOP_STOPDETAILVIEWMODEL = "ETA_Retrieval_for_stop_StopDetailViewModel"

const val SAVE_FORM_DATA_FORMVIEWMODEL = "SaveFormData_FormViewModel"
const val GET_FORM_DATA_FORMVIEWMODEL = "GetFormData_FormViewModel"
const val GET_ACTIONS_FOR_STOP_DISPATCHBASEVIEWMODEL = "GetActionsForStop_DispatchBaseViewModel"

//AppLauncher CPIK instance Constants
const val ADD_GEOFENCE = "com.trimble.ttm.addGeofence"
const val ONLY_ROUTE = "com.trimble.ttm.onlyRoute"
const val ONLY_ROUTE_AND_GEOFENCE = "com.trimble.ttm.onlyRouteAndGeofence"
const val REMOVE_ALL_GEOFENCE = "com.trimble.ttm.removeAllGeofence"
const val REMOVE_GEOFENCE = "com.trimble.ttm.removeGeofence"
const val CLEAR_ROUTE = "com.trimble.ttm.clearRoute"
const val DISPATCH_COMPLETE = "com.trimble.ttm.dispatchComplete"
const val CPIK_GEOFENCE_EVENT = "com.trimble.ttm.cpikGeofenceEvent"
const val CPIK_ROUTE_COMPUTATION_RESULT = "com.trimble.ttm.cpikRouteComputationResult"
const val STOPDETAIL_LIST = "com.trimble.ttm.StopDetailList"
const val ROUTE_CALCULATION_RESULT_STATE = "com.trimble.ttm.RouteCalculationResultState"
const val ROUTE_CALCULATION_RESULT_ERROR = "com.trimble.ttm.RouteCalculationResultError"
const val ROUTE_CALCULATION_RESULT_STOPDETAIL_LIST =
    "com.trimble.ttm.RouteCalculationResultStopDetailList"
const val ROUTE_CALCULATION_RESULT_TOTAL_HOUR = "com.trimble.ttm.RouteCalculationResultStopDetailList.total.hour"
const val ROUTE_CALCULATION_RESULT_TOTAL_DISTANCE = "com.trimble.ttm.RouteCalculationResultStopDetailList.total.distance"
const val ROUTE_COMPUTATION_RESPONSE_TO_CLIENT_KEY =
    "com.trimble.ttm.RouteComputationResponseToClient"
const val ROUTE_CALCULATION_RESPONSE = "com.trimble.ttm.RouteCalculationResponse"
const val KEY_DISPATCH_STOPS_GEO_COORDINATES = "dispatch_stops_geo_coordinates"
const val KEY_REDRAW_COPILOT_ROUTE = "com.trimble.ttm.RedrawCopilotRoute"
const val KEY_STOP_INFO_LIST = "stop-info-list"
const val KEY_NAVIGATION_EVENT = "navigation_event"
const val KEY_EVENT_MESSAGE = "event_message"
const val ROUTE_CALCULATION_START = "route_calculation_start"
const val ROUTE_CALCULATION_COMPLETE = "route_calculation_complete"
const val ROUTE_CALCULATION_FAILED = "route_calculation_failed"
const val KEY_GEOFENCE_NAME = "geofence_name"
const val KEY_GEOFENCE_EVENT = "geofence_event"
const val KEY_GEOFENCE_TIME = "geofence_time"
const val APPROACH = "Approach"
const val ARRIVED = "Arrived"
const val DEPART = "Depart"
const val KEY_GEOFENCE_TRIGGER = "geofence_trigger"
const val KEY_IS_NEW_LAUNCHER = "is_new_launcher"
const val CPIK_EVENT_TYPE_KEY = "applauncher-cpik-instance-result-to-routemanifest"

const val GEOFENCE_NAME = "geofence-name"

const val INVALID_ACTION_ID = -1

const val TRIP_SELECT_INDEX = 0
const val STOP_LIST_INDEX = 0
const val TIMELINE_INDEX = 1

//TO LAUNCH DRIVING NAVIGATION SCREEN
//https://docs.google.com/document/d/1M3o6i8M_hVDlehzrxk7JFtMarfZwjKgbavg1aRBtBcs/edit?usp=sharing
const val LAUNCH_SCREEN_INTENT = "com.trimble.ttm.applauncher.LAUNCH_SCREEN"
const val SCREEN_TYPE_KEY="SCREEN_TYPE"
const val DRIVING_SUB_SCREEN_TYPE_KEY="SUB_SCREEN_TYPE"
const val DRIVING_SCREEN_TYPE=1
const val DRIVING_SUB_SCREEN_NAVIGATION_OPTION_TYPE=3
const val ADDRESS_NOT_AVAILABLE="Address Not Available"


//TTS CONSTANTS
//this value is used to put the periodic time repetition. Can't be less than 15 min
// https://developer.android.com/topic/libraries/architecture/workmanager/how-to/define-work
const val REPETITION_TIME : Long = 15
const val FCM_TOKEN_RECHECK_INTERVAL : Long = 30


const val DEFAULT_TRIP_PANEL_MESSAGE_TIMEOUT_IN_SECONDS = 0
const val INVALID_TRIP_PANEL_MESSAGE_ID  = -1
const val IS_DELETED_STOP = "Payload.Deleted"
const val INT_MAX : Long = 2147483647
var APP_LAUNCHER_MAP_PERFORMANCE_FIX_VERSION_CODE: Long = INT_MAX //Replace this value once MAPP-8661 is merged to master in AL repo. it will be updated from firestore even if we don't update here.
const val APP_LAUNCHER_TRIP_PANEL_ACTION_PENDING_INTENT_BUILD_VERSION_CODE = 15514L

const val DETENTION_WARNING_TEXT = "DETENTION_WARNING_TEXT"
const val DETENTION_WARNING_STOP_ID = "DETENTION_WARNING_STOP_ID"

const val TRIP_CREATION_DATE = "TripCreationDate"
const val FOURTEEN_DAYS_IN_MILLISECONDS = 1209600000

const val LISTEN_GEOFENCE_EVENT = "applauncher-routemanifest_listen_geofence_event"
const val WORKFLOW_SERVICE_INTENT_ACTION_KEY = "workflow-service-intent-action"
const val EVENTS_PROCESSING_FOREGROUND_SERVICE_INTENT_ACTION = "com.trimble.ttm.routemanifest.service.StartEventsProcessingForegroundService"
const val DEFAULT_ROUTE_CALC_REQ_DEBOUNCE_THRESHOLD = 1000L
const val COPILOT_ROUTE_CLEARING_TIME_SECONDS = 1000L
const val COPILOT_ROUTE_CALC_RETRY_DELAY = 2000L
const val ROUTE_CALCULATION_MAX_RETRY_COUNT = 5

// Google Analytics Event Names
const val AUTO_ARRIVED = "AutoArrived"
const val MANUAL_ARRIVED = "ManualArrived"
const val AUTO_DEPARTED = "AutoDeparted"
const val MANUAL_DEPARTED = "ManualDeparted"
const val STOP_DETAIL_SCREEN_TIME = "StopDetailScreenTime"
const val STOP_LIST_SCREEN_TIME = "StopListScreenTime"
const val TIMELINE_VIEW_COUNT = "TimeLineViewCount"
const val TIME_TAKEN_FROM_ARRIVAL_TO_DEPARTURE = "TimeTakenFromArrivalToDeparture"
const val TIME_TAKEN_FROM_ARRIVAL_TO_FORM_SUBMISSION = "TimeTakenForArrivalToFormSubmission"

const val DISPATCH_ID_WORKER_KEY = "DispatchIdWorkerKey"
const val CUSTOMER_ID_WORKER_KEY = "CidWorkerKey"
const val VEHICLE_ID_WORKER_KEY = "VehicleIdWorkerKey"
const val TRIP_START_EVENT_REASON_WORKER_KEY = "TripStartEventReasonWorkerKey"
const val AUTO_START_CALLER_WORKER_KEY = "AutoStartCallerWorkerKey"
const val DISPATCH_NAME_WORKER_KEY = "DispatchName"
const val AUTO_TRIP_START_WORKER_KEY = "AutoTripStartWorker"
const val CHECK_WITH_FIRESTORE_FCM_WORKER_KEY = "CheckFcmInDb"
const val TRUCK_NUMBER = "TruckNumber"

// Events, Reason Codes, mileType(gps or ecm) in PFM
const val MILE_TYPE_GPS = "gps"
const val TRIP_START_EVENT_REASON_TYPE = "TripStartEventReasonType"
const val NEGATIVE_GUF_TIMEOUT = "NegativeGufTimeout"
const val NEGATIVE_GUF_CONFIRMED = "NegativeGufConfirmed"
const val REQUIRED_GUF_CONFIRMED = "RequiredGufConfirmed"

const val TRIP_PANEL_MESSAGE_ID_KEY = "TripPanelMessageIdKey"
const val TRIP_PANEL_AUTO_DISMISS_KEY = "TripPanelAutoDismissKey"
const val TRIP_PANEL_MESSAGE_PRIORITY_KEY = "TripPanelMessagePriorityKey"

const val DISPATCH_BLOB_REF = "DispatchBlobDocRef"
const val BLOB_JSON_KEY = "blob"
const val APPID_JSON_KEY = "appId"
const val HOSTID_JSON_KEY = "hostId"

//Arrival reasons
const val ARRIVAL_ACTION_STATUS = "arrivalActionStatus"
const val ARRIVAL_ACTION_STATUS_TIME = "arrivalActionStatusTime"
const val ARRIVAL_ACTION_STATUS_LOCATION = "arrivalActionStatusLocation"
const val DISTANCE_TO_ARRIVAL_ACTION_STATUS_LOCATION = "distanceToArrivalActionStatusLocation"
const val DISTANCE_TO_ARRIVAL_LOCATION = "distanceToArrivalLocation"
const val ARRIVAL_TIME = "arrivalTime"
const val ARRIVAL_LOCATION = "arrivalLocation"
const val ARRIVAL_TYPE = "arrivalType"
const val INSIDE_GEOFENCE_AT_ARRIVAL = "insideGeofenceAtArrival"
const val INSIDE_GEOFENCE_AT_ARRIVAL_ACTION_STATUS = "insideGeofenceAtArrivalActionStatus"
const val STOP_LOCATION = "stopLocation"
const val GEOFENCE_TYPE = "geofenceType"
const val ETA = "eta"
const val SEQUENCED = "sequenced"
const val DRIVERID = "driverId"
const val ARRIVAL_REASON_DETAILS = "arrivalReasonDetails"
