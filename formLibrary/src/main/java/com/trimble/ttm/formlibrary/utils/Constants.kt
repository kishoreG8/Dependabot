package com.trimble.ttm.formlibrary.utils

//generic fields
const val SIGNATURE_RESULT_RECEIVER = "signature_result_receiver"
const val SIGNATURE_RESULT_BYTE_ARRAY = "signature_result"
const val IS_FORM_FIELD_DRIVER_EDITABLE = 1
const val NUMERIC_THOUSAND_SEPARATOR_PATTERN = "###,###.##"
const val FREE_FORM_FORM_CLASS = 1
const val BARCODE_RESULT_RECEIVER = "barcode_result_receiver"
const val BARCODE_RESULT_STRING_ARRAY = "barcode_result"
const val CAMERA_PERMISSION_REQUEST_CODE = 121
const val MEDIA_LAUNCH_ACTION = "multimedialaunchaction"
const val IMAGE_REFERENCE_RESULT = "ImageReferenceResult"
const val ENCODED_IMAGE = "encodedImage"
const val COMMA = ","
const val NEWLINE = "\n"
const val MAX_CHAR = 60
const val ZERO = 0
const val FULL_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
const val READABLE_DATE_FORMAT = "MMM dd"
const val READABLE_DATE_TIME_FORMAT = "MMM dd - HH:mm:ss"
const val EMPTY_STRING = ""
const val SCREEN = "screen"
const val INTENT_CLASS_NAME = "INTENT_CLASS_NAME"
const val IS_FOR_NEW_MESSAGE = "IS_FOR_NEW_MESSAGE"
const val IS_FOR_NEW_TRIP = "IS_FOR_NEW_TRIP"
const val LAUNCH_MODE = "LAUNCH_MODE"
const val LINE_FEED = "&#x0A;"
const val SIGNATURE_WIDTH_DP = 420F

//Dtf fields
const val LOOP_START = "Loopstart"
const val LOOP_END = "loopend"
const val BRANCH_TARGET = "branch_target"
const val BRANCH_TO = "branch"

//collection fields
const val FORM_ID_KEY = "formId"
const val FORM_CLASS_KEY = "formClass"
const val FORMS_COLLECTION = "Forms"
const val FREE_FORMS_COLLECTION = "FreeForms"
const val FORMS_LIST_COLLECTION = "FormList"
const val FORM_FIELD_COLLECTION = "FormField"
const val FORM_CHOICES_COLLECTION = "FormChoices"
const val RECIPIENT_USERNAME = "RecipientUserNames"
const val USERNAME = "username"
const val EMAIL = "email"
const val ACTIVE = "active"
const val DRAFTED_USERS = "DraftedUsers"
const val IS_FROM_DRAFTED_FORM = "IsFromDraftedForm"
const val SENT_USERS = "SentUsers"
const val IS_FROM_SENT_FORM = "IsFromSentForm"
const val INBOX_MSG_CONFIRMATION_COLLECTION = "InboxMessageConfirmation"

//Json Parse and collection fields
const val INBOX_COLLECTION = "Inbox"
const val TRASH_COLLECTION = "Trash"
const val ASN_INBOX = "Asn"
const val PAYLOAD = "Payload"
const val RECIPIENTS_FIELD = "Recipients"
const val HAS_PRE_DEFINED_RECIPIENTS = "HasPredefinedRecipients"
const val FORM_ID = "FormId"
const val FORM_CLASS = "FormClass"
const val FORM_NAME = "FormName"
const val REPLY_FORM_ID = "ReplyFormId"
const val REPLY_FORM_CLASS = "ReplyFormClass"
const val REPLY_FORM_NAME = "ReplyFormName"
const val REPLY_ACTION_TYPE="ReplyActionType"
const val MESSAGE_CONTENT = "MessageContent"
const val PFM_TIMESTAMP_SETTING_REGEX = "\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}"
const val PFM_MESSAGE_TIMESTAMP_FORMAT = "MM/dd HH:mm"
const val FORM_FIELD = "FormField"
const val FQUESTION = "FQuestion"
const val MESSAGE = "Message"
const val FORM_UI_RESPONSE = "FormUiResponse"
const val COMES_FROM_DETAIL = "comes_from_detail"
const val IS_READ = "IsRead"
const val IS_DELIVERED = "IsDelivered"
const val MARK_READ = "mark_read"
const val CONFIRMED = "C"
const val DELIVERED = "D"
const val MARK_DELIVERED = "mark_delivered"
const val INBOX_FORM_RESPONSE_COLLECTION = "PFMFormResponses"
const val INBOX_FORM_DRAFT_RESPONSE_COLLECTION = "PFMFormDraftResponses"
const val SKIPPED = "skipped"
const val SHOULD_OPEN_MESSAGE_DETAIL_FOR_TRASH = "ShouldOpenForTrash"
const val IS_REPLY_WITH_SAME = "IsReplyWithSame"
const val CAN_DRIVER_REPLY= "CanDriverReply"
const val ROW_DATE = "RowDate"
const val DSN = "dsn"
const val CONFIRMATION_DATE_TIME = "confirmationDateTime"
const val STATUS = "status"

const val TTS_WIDGET = "TTSWidget"
const val TTS_VOLUME_LEVEL = 100
const val TTS_UTTERANCE_ID = "forms_utterance"
const val TTS_FROM="From"
const val TTS_DATE="Date"
const val TTS_FORMNAME="Form Name"
const val NULL="null"
const val DVIR_START="dvir_start"

//FormLibrary fields
const val V_UNIT_COLLECTION = "VUnits"
const val DSN_QUERY_PARAM = "Payload.Dsn"
const val VID_FIELD = "Payload.Vid"
const val CID_FIELD = "Payload.Cid"
const val GROUP_UNITS_COLLECTION = "VehicleGroups"
const val ALL_VEHICLES_GROUP_ID = 0
const val GID_FIELD = "GroupIdList"
const val GROUP_FORMS_COLLECTION = "GroupForms"
const val FORM_LIST_FIELD = "FormIdList"
const val FORM_DRAFT_RESPONSE_PATH = "form_draft_response_path"
const val COMPLETE_FORM_DETAIL = "completeFormDetail"
const val SELECTED_USERS_KEY = "selected_user_ids"
const val GROUP_USERS_COLLECTION = "GroupUsers"
const val ADDR_BOOK_FIELD = "addrBook"
const val UID_FIELD_KEY = "uid"
const val USERS_COLLECTION = "Users"
const val USER_LIST_COLLECTION = "UserList"
const val USER_LIST_FIELD = "UserIdList"
const val PAYLOAD_UID = "Payload.UID"
const val PAYLOAD_EMAIL = "Payload.Email"
const val WHERE_IN_QUERY_CHUNK_MAX_COUNT = 10
const val LAST_MODIFIED = "LastModified"
const val END_REACHED = "EndReached"
const val IS_HOME_PRESSED = "isHomeButtonPressed"

const val defaultValue = -1L

//FormLibraryCaching Fields
const val FORM_LIB_CACHE_FROM_AUTH = "FormLibraryCacheFromAuthentication"
const val USER_IDS = "FormLibraryCacheFromAuthentication"
const val FORM_LIB_CACHE_WORKER_TAG = "FormLibraryCacheWorker"

//Pagination fields
const val MESSAGE_COUNT_PER_PAGE = 20L
const val FORM_COUNT_PER_PAGE = 50

//View alpha manipulation fields
const val REDUCED_ALPHA = 0.3f
const val NORMAL_ALPHA = 1.0f

//View alpha for read-only form components
const val READ_ONLY_VIEW_ALPHA = 0.8f

//Messaging menu
const val MESSAGES_MENU_TAB_INDEX = "messages_menu_tab_index"
const val INBOX_INDEX = 0
const val SENT_INDEX = 1
const val DRAFT_INDEX = 2
const val TRASH_INDEX = 3
const val INVALID_INDEX = -1

//EDVIR Inspection
const val EDVIR_COLLECTION_ID = "EDVIR"
const val EDVIR_ENABLED_SETTINGS_DOCUMENT_ID = "Enabled"
const val EDVIR_MANDATORY_SETTINGS_DOCUMENT_ID = "Mandatory"
const val EDVIR_PRE_TRIP_SETTINGS_DOCUMENT_ID = "PreTrip"
const val EDVIR_POST_TRIP_SETTINGS_DOCUMENT_ID = "PostTrip"
const val EDVIR_INTER_TRIP_SETTINGS_DOCUMENT_ID = "InterTrip"
const val EDVIR_DOT_TRIP_SETTINGS_DOCUMENT_ID = "DOTTrip"
const val EDVIR_INVALID_INT_VALUE = -1
const val INVALID_FORM_CLASS = -1
const val MOBILE_ORIGINATED_DOCUMENT_ID = "MobileOriginated"
const val FORM_RESPONSES = "FormResponses"
const val CREATED_AT = "createdAt"
const val MANDATORY_EDVIR_INTENT_ACTION = "com.trimble.ttm.formsandworkflow.EDVIR_INSPECTION"
const val MANDATORY_EDVIR_INSPECTION_RESULT_INTENT_ACTION =
    "com.trimble.ttm.formsandworkflow.EDVIR_INSPECTION_RESULT"
const val INSPECTION_TYPE = "inspectionType"
const val DRIVER_NAME_KEY = "driverName"
const val INSPECTION_FORM_ID_KEY = "form_id"
const val INSPECTION_FORM_CLASS_KEY = "form_class"
const val INSPECTION_CREATED_AT_KEY = "inspection_created_at"
const val IS_INSPECTION_FORM_VIEW_ONLY_KEY = "is_inspection_form_view_only"
const val DRIVER_ACTION = "driver_action"
const val SOURCE_ACTIVITY_KEY = "source_activity"
const val DRIVER_ID = "driver_id"
const val DRIVER_NAME = "driver_name"
const val VEHICLE_DSN = "vehicle_dsn"
const val DRIVER_ACTION_LOGIN = "login"
const val DRIVER_ACTION_LOGOUT = "logout"
const val ANNOTATION_INVALID_REGEX = "@[^a-zA-Z0-9!@#\$%^&*()_\\-+\\=’{}\\[\\]:;?/“”\\\\., ]+”"
const val INVALID_FORM_ID = -1
const val EFS_ADD_ANNOTATION = "biz.iseinc.efleetsuite.ADD_ANNOTATION"
const val EFS_ADD_ANNOTATION_SUCCESSFULL = "biz.iseinc.efleetsuite.ADD_ANNOTATION_SUCCESSFUL"
const val EFS_DRIVER_NOT_SIGNED_IN =
    "biz.iseinc.efleetsuite.ADD_ANNOTATION_FAILED_DRIVER_NOT_SIGNED_IN"
const val EFS_FAILED_ANNOTATION_INVALID =
    "biz.iseinc.efleetsuite.ADD_ANNOTATION_FAILED_ANNOTATION_INVALID"
const val EFS_FAILED_ANNOTATION_LOG_EVENT_CHANGED =
    "biz.iseinc.efleetsuite.ADD_ANNOTATION_FAILED_LOG_EVENT_CHANGED"
const val EFS_DRIVER_ID_KEY = "driverID"
const val EFS_ANNOTATION_TEXT_KEY = "annotationText"
const val EDVIR_INSPECTION_STATUS = "edvir_inspection_status"
const val EDVIR_INSPECTION_STATUS_FAILURE = "INSPECTION_FAILURE"
const val EDVIR_INSPECTION_STATUS_SUCCESS = "INSPECTION_SUCCESS"
const val EDVIR_INSPECTION_STATUS_MESSAGE = "edvir_inspection_status_message"

//EDVIR Performance SDK constants
const val GET_INSPECTION_HISTORY_EDVIRINSPECTIONVIEWMODEL =
    "GetInspectionHistory_EdvirInspectionViewModel"
const val FETCH_FORM_TO_BE_RENDERED_EDVIRFORMVIEWMODEL = "FetchFormToBeRendered_EdvirFormViewModel"
const val SAVE_EDVIRFORM_DATA_EDVIRFORMVIEWMODEL = "SaveEdvirFormData_EdvirFormViewModel"
const val GET_FORM_ID_FOR_INSPECTION_EDVIRFORMVIEWMODEL =
    "GetFormIdForInspection_EdvirFormViewModel"
const val GET_EDVIR_FORM_RESPONSE_EDVIRFORMVIEWMODEL = "GetEdvirFormResponse_EdvirFormViewModel"

//Dispatch
const val NOTIFICATION_DISPATCH_DATA = "notification_dispatch"

const val FORM_RESPONSE_TYPE = "FormResponseType"
const val FORM_LIBRARY_RESPONSE_TYPE = "FormLibrary"
const val INBOX_FREE_FORM_RESPONSE_TYPE = "InboxNewMessage"
const val INBOX_FORM_RESPONSE_TYPE = "InboxReplyForm"
const val DISPATCH_FORM_RESPONSE_TYPE = "Dispatch"
const val FLAVOR_DEV = "dev"
const val FLAVOR_QA = "qa"
const val FLAVOR_STG = "stg"
const val FLAVOR_PROD = "prod"

const val CORPORATE_FREE_FORM_ID_DEV_AND_PROD = 3481
const val CORPORATE_FREE_FORM_ID_QA_AND_STAGE = 5294

const val QUERY_FORM_IN_USE_BITS = "inUseBits"
const val QUERY_DRIVER_ORIGINATED_FORM = "driverOriginate"
const val IS_BLU_USE_CHECKED_FORM = 4

const val NO_CONTACTS = "No Contacts"
const val PRE_DEFINED_RECIPS = "Predefined Recipients"

const val HOST_REGION = "us-central1"
const val PROJECT_ID_DEV = "forms-workflow-dev-ce68c3dd"
const val PROJECT_ID_QA = "forms-workflow-qa-53f11fe5"
const val PROJECT_ID_STG = "forms-workflow-stg-55a487ab"
const val PROJECT_ID_PROD = "forms-workflow-prod-7f1153bb"
const val BASE_URL_PREFIX = "https://"
const val BASE_URL_SUFFIX = ".cloudfunctions.net"
const val HYPHEN_SEPARATOR = "-"
const val CONTENT_TYPE = "Content-type"
const val CONTENT_TYPE_JSON = "application/json"
const val BEARER = "Bearer"
const val APP_CHECK_AUTHORIZATION = "X-Firebase-AppCheck"

const val DEBOUNCE_INTERVAL = 300L

const val dispatchListImplicitIntentAction = "intent.action.dispatchlist"
const val dispatchDetailImplicitIntentAction = "intent.action.dispatchdetail"


const val FORM_LIBRARY_UPDATION_CHECK_TIME_INTERVAL: Long = 15*60*1000L //15 mins
const val INBOX_MESSAGE_CONFIRMATION_FIRESTORE_READ_TIME_INTERVAL: Long = 1000 * 60 * 2 // 2 mins
const val PFM_TIMESTAMP_LENGTH=11

const val NEW_FORM_VIEW_COUNT = "NewFormViewCount"
const val FORMS_SHORTCUT_USE_COUNT = "FormsShortcutUseCount"
const val INBOX_SCREEN_TIME = "InboxScreenTime"
const val TRASH_SCREEN_TIME = "TrashScreenTime"
const val SENT_SCREEN_TIME = "SentScreenTime"
const val DRAFT_SCREEN_TIME = "DraftScreenTime"
const val INBOX_SHORTCUT_USE_COUNT = "InboxShortCutUseCount"

const val EDVIR_MSG_CONFIRMATION_COLLECTION = "EDVIRMessageConfirmation"
const val EDVIR_MOBILE_ORIGINATED_MSG_COLLECTION = "ConfirmedMessages"

const val NO_REPLY_ACTION = 0
const val REPLY_WITH_SAME = 1
const val REPLY_WITH_NEW = 2
const val FORMS  = "FORMS"
const val HOTKEYS = "HOTKEYS"
const val HOTKEYS_COLLECTION_NAME = "HotKeys"
const val HOT_KEYS_MENU_INDEX = 2
const val FORM_GROUP_TAB_INDEX = "form_group_tab_index"
const val BACK = "Back"
const val SEARCH = "Search"
const val TOGGLE_VIEW = "ToggleView"
const val FAVOURITE = "Favourite"
const val DETAILS = "Details"
const val ARROW = "Arrow"
const val HOTKEYS_DESCRIPTION_COLLECTION_NAME = "hotkeysUnits"
const val LOADING = "Loading..."
const val FAILED_TO_FETCH_HOTKEYS = "Failed to fetch hot keys"
const val TOOLBAR_HEIGHT = 60.0f
const val KMS_TO_FEET = 3280.84