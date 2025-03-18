package com.trimble.ttm.commons.utils


const val INBOX_COLLECTION_READ_DELAY = 3000L

const val FEATURE_FLAGS = "FeatureFlags"

//FCM Constants
const val FCM_TOKENS_COLLECTION = "FcmTokens"
const val FCM_TOKEN_RECHECK = "FcmTokenRecheck"

const val AUTH_SUCCESS = "AUTH_SUCCESS"
const val AUTH_DEVICE_ERROR = "AUTH_DEVICE_ERROR"
const val AUTH_SERVER_ERROR = "AUTH_SERVER_ERROR"

const val EMPTY_STRING = ""
const val APP_NAME="Instinct-RouteManifest"
const val DAMN = "damn"
const val ASN = "asn"
const val ZERO = 0
const val DEEP_LINK_PARAMETER_ONE = 1
const val DEEP_LINK_PARAMETER_THREE = 3
const val FREE_FORM_FORM_CLASS = 1

const val SIGNATURE_WIDTH_DP = 420F
const val NEWLINE = "\n"


const val REQUIRED_TEXT = " (Required) "

const val FORM_NOT_A_VALID_NUMBER = "*Entered field can not be empty and should be a valid number"
const val DOT = "."
const val VALUE_IS_NOT_IN_RANGE = "*Entered field is not in the expected range min-max  "
const val DECIMAL_DIGITS_ARE_NOT_ALLOWED = "*Decimal Digits are not allowed"
const val DECIMAL_RANGE_EXCEEDS = "*Decimal digit range is exceeding"
const val FIELD_CAN_NOT_BE_EMPTY = "Cannot be empty"
const val READ_ONLY_VIEW_ALPHA = 0.8f

const val DRIVER_FORM_ID = "DriverFormId"
const val DISPATCH_FORM_PATH_SAVED = "DispatchFormPathSaved"
const val UNCOMPLETED_DISPATCH_FORM_PATH = "UncompletedDispatchFormPath"
const val DISPATCH_FORM_SAVE_PATH = "DispatchFormSavePath"
const val IS_SYNC_DATA_TO_QUEUE_KEY = "isSyncDataToQueue"
const val FORM_DATA_KEY = "formData"
const val CAN_SHOW_CANCEL = "can_show_cancel"
const val IS_ACTION_RESPONSE_SENT_TO_SERVER = "isActionResponseSentToServer"
const val FORM_RESPONSE_PATH = "form_response_path"
const val formActivityIntentAction = "intent.action.formactivity"
const val composeFormActivityIntentAction = "intent.action.composeformactivity"
const val IS_SECOND_FORM_KEY = "isSecondFormKey"
const val IMESSAGE_REPLY_FORM_DEF = "ImessageReplyFormDef"
const val IS_FROM_TRIP_PANEL = "is_from_trip_panel"
const val IS_FROM_DRAFT = "is_from_draft"

const val TWENTY_FOUR_HOUR_TIME_PATTERN = "HH:mm"
const val TWELVE_HOUR_TIME_PATTERN = "hh:mm a"
const val UTC = "UTC"

const val CID = "CID"
const val OBC_ID = "OBCID"
const val VEHICLE_ID = "VehicleId"
const val CID_VEHICLE_ID = "CID_VehicleId"
const val DURATION = "Duration"
const val INTENT_CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"
const val TRIP_INFO_WIDGET = "TripInfoWidget"
const val WORKFLOW_SHORTCUT_USE_COUNT = "WorkFlowShortCutUseCount"
const val ZERO_TIME_DURATION = "0 hours 0 minutes 0 seconds"
const val CUSTOM_DATE_FIELD = "CUSTOM_DATE_FIELD"
const val CUSTOM_TIME_FIELD = "CUSTOM_TIME_FIELD"
const val CUSTOM_DATE_TIME_FIELD = "CUSTOM_DATE_TIME_FIELD"
const val CUSTOM_BARCODE_FIELD = "CUSTOM_BARCODE_FIELD"

const val ENCODED_IMAGE_COLLECTION = "EncodedFormImages"
const val IMG_VIEW_ID = "imgViewId"
const val IMG_STOP_ID = "imgStopId"
const val IMG_UNIQUE_ID = "imgDocId"
const val IMG_UNIQUE_IDENTIFIER = "imgUniqueId"

const val SIGN_VIEW_ID = "signViewId"
const val SIGN_STOP_ID = "signStopId"

const val BARCODE_VIEW_ID = "barcodeViewId"

//Notification related constants
const val WORKFLOWS_CHANNEL_ID = "1111"
const val NEW_DISPATCH_NOTIFICATION_ID = 2000
const val NEW_MESSAGE_NOTIFICATION_ID = 3000

const val UTC_TIME_ZONE_ID = "UTC"

//FormFieldTypes
const val FREETEXT_KEY = "freeText"
const val LATLNG_KEY = "latlong"
const val LOCATION_KEY = "location"
const val ODOMETER_KEY = "odometer"
const val BARCODE_KEY = "barcode"
const val SIGNATURE_KEY = "signature"
const val IMAGE_REFERENCE_KEY = "imageRef"
const val IMG_REFERENCE_KEY = "imageReferenceKey"
const val MULTIPLECHOICE_KEY = "multipleChoice"

const val FORM_RESPONSE_INBOX_VALUE = "noMailboxSpecified"
const val BACKBONE_ERROR_INT_VALUE = -1
const val BACKBONE_ERROR_VALUE = -1.0
const val MULTIPLE_CHOICE_FIELD_TYPE = 2

//Workflow Events Communication
const val WORKFLOW_EVENT_DATA = "WorkflowEventData"
const val WORKFLOW_EVENTS_COMMUNICATION_SERVICE_INTENT_ACTION = "com.trimble.ttm.workfloweventscommunication.service.StartWorkflowEventsCommunicationService"
const val ON_POSITIVE_BUTTON_CLICKED_CALLER = "onPositiveButtonClicked"
const val ON_MESSAGE_DISMISSED_CALLER = "onMessageDismissed"
const val DO_ON_ARRIVAL_CALLER = "doOnArrival"
const val APPROACH_GEOFENCE_CALLER = "ApproachGeofence"
const val DEPART_GEOFENCE_CALLER = "DepartGeofence"
const val SEND_TRIP_START_EVENT_CALLER = "sendTripStartEventToThirdPartyApps"
const val RUN_ON_TRIP_END_CALLER = "runOnTripEnd"
const val ON_BACKGROUND_NEGATIVE_GUF_CALLER = "onBackgroundNegativeGufCaller"

// DeepLink Trigger
const val ARRIVAL = "Arrival"
const val FORM_SUBMISSION = "Form Submission"


val CUSTOMER_ID_KEY = "customer id"
val FORM_ID_KEY = "form id"
const val ERROR_TAG = "Error"

// TTL constants
const val VALUE = "value"
const val EXPIRE_AT = "ExpireAt"
const val UNDERSCORE = "_"
const val DISPATCHID = "dispatchId"
const val STOPID = "stopId"
const val ACTION = "action"

/* Index values to fetch form data results from the Jobs, this is used in [FormFieldDataUseCase]
[FORM_TEMPLATE_INDEX] - To fetch the needed Form Template from Firestore
[UI_FORM_RESPONSE_INDEX] - To fetch the saved/Drafted Form Responses from the Firestore
[DEFAULT_VALUES_INDEX] - To fetch the Default values from the Dispatch collection with the particular stop action
*/
const val FORM_TEMPLATE_INDEX = 0
const val UI_FORM_RESPONSE_INDEX = 1
const val DEFAULT_VALUES_INDEX = 2
const val FORMRESULT_SIZE_INCLUDING_FORMDEF_FORMRESPONSE_DEFVALUE = 3
const val FORMRESULT_SIZE_INCLUDING_FORMDEF_FORMRESPONSE = 2


const val DISPATCH_COLLECTION = "Dispatches"
const val STOPS = "Stops"
const val ACTIONS = "Actions"
const val DISPATCHER_FORM_VALUES = "DispatcherFormValues"

const val INBOX_COLLECTION = "Inbox"
const val PAYLOAD_DRIVER_FORM_ID = "Payload.DriverFormid"
const val PAYLOAD_FORCED_FORM_ID = "Payload.ForcedFormId"

const val DRAFT_KEY = "Draft"
const val SENT_KEY = "Sent"

const val CID_JSON_KEY = "cid"
const val VEHICLE_NUMBER_JSON_KEY = "vehicleNumber"
const val VID_JSON_KEY = "vid"
const val CREATE_DATE_JSON_KEY = "createDate"

const val DEFAULT_LATITUDE = 0.0
const val DEFAULT_LONGITUDE = 0.0
//When formLibrary called from Detail Screen / Dispatch form screen, this flag variable should be set to finish the FormLibrary Activity
const val SHOULD_NOT_RETURN_TO_FORM_LIST = "shouldNotReturnToFormList"

const val ARRIVAL_REASON_COLLECTION = "arrivalReason"
const val CIRCULAR = "CIRCULAR"
const val POLYGON = "POLYGON"
const val STORAGE = "storage"
const val MAX_IMAGE_SIZE = 1024 * 1024L
const val IMAGE_UPLOAD_PERIODIC_WORKER = "ImageUploadPeriodicWorker"
const val IMAGE_UPLOAD_ONE_TIME_WORKER = "ImageUploadOneTimeWorker"
const val IMAGE_DELETE_ONE_TIME_WORKER = "ImageDeleteOneTimeWorker"
const val UNIQUE_WORK_NAME_KEY = "uniqueWorkName"
const val IMAGE_NAMES_KEY = "imageIds"
const val IS_DRAFT_KEY = "isDraft"
const val SHOULD_DELETE_FROM_STORAGE_KEY = "shouldDeleteFromServer"
const val IMAGE_HANDLER = "ImageHandler"
const val ENCODED_IMAGE_REF_REPO = "EncodedImageRefRepo"
const val IMAGES = "images"
const val STORAGE_TO_BE_UPLOADED = "storageToBeUploaded"
const val STORAGE_THUMBNAIL = "storageThumbnail"
const val STORAGE_DRAFT = "storageDraft"
const val COMPRESSION_QUALITY_95 = 95
const val COMPRESSION_QUALITY_100 = 100
const val THUMBNAIL_SIZE = 100
const val TEMP_IMAGE = "image"
const val JPG = "jpg"
const val DOT_JPG = ".jpg"
const val UNDERSCORE_THUMBNAIL = "_thumbnail"
const val GOOGLE_URL = "https://www.google.com"
