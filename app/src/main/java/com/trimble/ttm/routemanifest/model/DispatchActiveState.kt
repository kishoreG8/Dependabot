package com.trimble.ttm.routemanifest.model

enum class DispatchActiveState {
    /**
     * The currently selected trip is active. In this state:
     * (1) on the top bar of the home screen, the button and label are gone;
     * (2) in the stop list, the navigate shortcut icons are shown as normal; and
     * (3) on the stop detail screen, the action buttons are shown as normal.
     */
    ACTIVE,

    /**
     * The currently selected trip is inactive, but a different trip is active. In this state:
     * (1) on the top bar of the home screen, the PREVIEW ONLY label is shown;
     * (2) in the stop list, the navigate shortcut icons are gone; and
     * (3) on the stop detail screen, the PREVIEW ONLY label replaces the action buttons.
     */
    PREVIEWING,

    /**
     * There is no currently active trip. In this state:
     * (1) on the top bar of the home screen, the START TRIP button is shown;
     * (2) in the stop list, the navigate shortcut icons are gone; and
     * (3) on the stop detail screen, the PREVIEW ONLY label replaces the action buttons.
     */
    NO_TRIP_ACTIVE
}