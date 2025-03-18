package com.trimble.ttm.routemanifest.ui.fragments

import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.marginStart
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TIMELINE
import com.trimble.ttm.commons.utils.DateUtil.checkIfDeviceTimeFormatIs24HourFormatOrNot
import com.trimble.ttm.commons.utils.traceBeginSection
import com.trimble.ttm.commons.utils.traceEndSection
import com.trimble.ttm.formlibrary.customViews.setErrorState
import com.trimble.ttm.formlibrary.customViews.setNoState
import com.trimble.ttm.formlibrary.customViews.setProgressState
import com.trimble.ttm.formlibrary.utils.UiUtil.convertDpToPixel
import com.trimble.ttm.formlibrary.utils.isNull
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.databinding.FragmentTimelineBinding
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.TRIP_START_TIME_IN_MILLIS_KEY
import com.trimble.ttm.routemanifest.model.DispatchActiveState
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.utils.TIMELINE_VIEW_COUNT
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.viewmodel.DispatchDetailViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.max

private const val STOP_HIGHLIGHTED_ICON_ID_MULTIPLIER = 50

class TimelineFragment : Fragment() {

    private val viewModel: DispatchDetailViewModel by sharedViewModel()
    private val dataStoreManager: DataStoreManager by inject()
    private val dateTimeFormat: SimpleDateFormat
        get() = DateFormat.getTimeFormat(activity) as SimpleDateFormat
    private val screenHeight: Int
        get() = max(
            Resources.getSystem().displayMetrics.widthPixels,
            Resources.getSystem().displayMetrics.heightPixels
        )
    private var totalHours = 0
    private var numberOfHours = 0
    private var stopIndicatorIconSize = 0
    private var stopIndicatorIconSizeHalf = 0
    private var stopIndicatorHighlightedIconSize = 0
    private var stopIndicatorHighlightedIconSizeHalf = 0
    private var verticalLineWidth = 0
    private var verticalLineWidthHalf = 0
    private var previousDotMargin = 0
    private var calendarToRetrieveTime = Calendar.getInstance()
    private var initialTimeCalendar = Calendar.getInstance()
    private var hourWidthInPixel = 0
    private var minuteWidthMultiplierInPixel = 0f
    private var currentStopHighlightIconId = -100
    private var currentStopIconId = -10
    private var timeCardViewWidth = 0
    private var timeCardViewHeight = 0
    private val stopIconFocusRectLeftOffset = -70
    private val stopIconFocusRectRightOffset = 70
    private var index = 1
    private var isTimeStringShouldBeDisplayed = false


    private lateinit var fragmentTimelineBinding: FragmentTimelineBinding
    private val logTag = "TimelineFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.logLifecycle(logTag, "$logTag onCreateView")
        traceBeginSection("TIMELINESCREENLOADINGTIME")
        // Inflate the layout for this fragment
        fragmentTimelineBinding = FragmentTimelineBinding.inflate(inflater, container, false)
        isTimeStringShouldBeDisplayed = true

        return fragmentTimelineBinding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.logLifecycle(logTag, "$logTag onViewCreated")

        if (viewModel.dispatchActiveStateFlow.value == DispatchActiveState.PREVIEWING){
            fragmentTimelineBinding.progressErrorView.setErrorState(getString(R.string.timeline_for_active_trip_only))
            return
        }
        fragmentTimelineBinding.progressErrorView.setProgressState(getString(R.string.calculating_eta_timeline))

        fragmentTimelineBinding.myScrollHolder.visibility = View.GONE

        val horizontalScrollView = view.findViewById<HorizontalScrollView>(R.id.myScrollHolder)
        val parentLayout = horizontalScrollView.findViewById<ConstraintLayout>(R.id.myViewHolder)
        val startMargin = context?.let { convertDpToPixel(50f, it).toInt() } ?: 0
        val timeTextWidth = context?.let { convertDpToPixel(100f, it).toInt() } ?: 0
        val timeTextHeight = context?.let { convertDpToPixel(40f, it).toInt() } ?: 0

        stopIndicatorIconSize = context?.let { convertDpToPixel(25f, it).toInt() } ?: 0
        stopIndicatorIconSizeHalf = context?.let { convertDpToPixel(12.5f, it).toInt() } ?: 0
        stopIndicatorHighlightedIconSize = context?.let { convertDpToPixel(60f, it).toInt() } ?: 0
        stopIndicatorHighlightedIconSizeHalf = context?.let { convertDpToPixel(30f, it).toInt() } ?: 0
        verticalLineWidth = context?.let { convertDpToPixel(2f, it).toInt() } ?: 0
        verticalLineWidthHalf = context?.let { convertDpToPixel(1f, it).toInt() } ?: 0
        hourWidthInPixel = 2 * 50.toDp()
        minuteWidthMultiplierInPixel = hourWidthInPixel / 60f
        timeCardViewWidth = 120.toDp()
        timeCardViewHeight = 40.toDp()

        viewModel.rearrangedStops.observe(viewLifecycleOwner) { unmodifiedStopDetailList ->
            if (unmodifiedStopDetailList.isNotEmpty()) {
                val stopDetailList = unmodifiedStopDetailList.toMutableList()
                stopDetailList.sortBy { it.stopid }
                val timelineStopDetailFragment = TimelineStopDetailFragment()
                Log.d("${TIMELINE}Check", stopDetailList.toString())
                // If StopDetail object has ETA then draw timeline view
                stopDetailList.firstOrNull()?.let { stopDetail ->
                    stopDetail.EstimatedArrivalTime?.let {
                        fragmentTimelineBinding.progressErrorView.setNoState()
                        fragmentTimelineBinding.myScrollHolder.visibility = VISIBLE
                        traceEndSection("TIMELINESCREENLOADINGTIME")

                        /*Create a stop detail object and set current time
                        as estimated arrival time and add as first item to the
                        stop detail collection to indicate the trip start time*/
                        StopDetail().apply {
                            lifecycleScope.launch(CoroutineName(TIMELINE)) {
                                if (dataStoreManager.containsKey(TRIP_START_TIME_IN_MILLIS_KEY)) {
                                    val tripStartTimeCalendar = Calendar.getInstance()
                                    tripStartTimeCalendar.timeInMillis = dataStoreManager.getValue(
                                        TRIP_START_TIME_IN_MILLIS_KEY,
                                        Calendar.getInstance().timeInMillis
                                    )
                                    EstimatedArrivalTime = tripStartTimeCalendar
                                } else {
                                    EstimatedArrivalTime = Calendar.getInstance()
                                }
                                stopDetailList.add(0, this@apply)
                            }
                        }

                        val firstStopDetail = stopDetailList[0]
                        val lastStopDetail = stopDetailList[stopDetailList.size - 1]
                        val diffInHrs = getDiffInHrs(
                            lastStopDetail.EstimatedArrivalTime!!.timeInMillis,
                            firstStopDetail.EstimatedArrivalTime!!.timeInMillis
                        )

                        numberOfHours = diffInHrs + 12
                        totalHours = (numberOfHours) * 2
                        initialTimeCalendar =
                            firstStopDetail.EstimatedArrivalTime!!.clone() as Calendar
                        initialTimeCalendar.add(Calendar.HOUR, -1)
                        initialTimeCalendar.set(Calendar.MINUTE, 0)
                        calendarToRetrieveTime =
                            firstStopDetail.EstimatedArrivalTime!!.clone() as Calendar
                        calendarToRetrieveTime.add(Calendar.HOUR, -1)

                        addVerticalLines(
                            totalHours,
                            startMargin,
                            parentLayout,
                            timeTextHeight
                        )

                        if(isTimeStringShouldBeDisplayed) {
                            addTimeStringAtBottomOfVerticalLine(
                                parentLayout,
                                timeTextWidth,
                                timeTextHeight
                            )

                            viewModel.getTimeString(
                                calendarToRetrieveTime = calendarToRetrieveTime,
                                dateTimeFormat = dateTimeFormat,
                                tag = "${TIMELINE}GetTimeString",
                                numberOfHours = numberOfHours,
                                is24HourTimeFormat = checkIfDeviceTimeFormatIs24HourFormatOrNot(
                                    activity
                                )
                            )

                            isTimeStringShouldBeDisplayed = false
                        }

                        fragmentTimelineBinding.dotLayout.removeAllViews()
                        fragmentTimelineBinding.timeCardLayout.removeAllViews()

                        addStopIndicatorIcon(parentLayout, stopDetailList)

                        observeCurrentStop()

                        childFragmentManager.beginTransaction().also { fragmentTransaction ->
                            fragmentTransaction.replace(
                                R.id.card_fragment_container,
                                timelineStopDetailFragment,
                                "TimelineStopDetail"
                            )
                            fragmentTransaction.commit()
                        }
                    }
                        ?: fragmentTimelineBinding.progressErrorView.setErrorState(getString(R.string.timeline_not_displayed))
                }
            } else {
                childFragmentManager.findFragmentByTag("TimelineStopDetail")?.also {
                    childFragmentManager.beginTransaction().remove(it).commit()
                }
                fragmentTimelineBinding.progressErrorView.setErrorState(getString(R.string.timeline_not_displayed))
            }
        }

        viewModel.dispatchDetailError.observe(viewLifecycleOwner) {
            fragmentTimelineBinding.progressErrorView.setErrorState(getString(R.string.timeline_not_displayed))
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.logLifecycle(logTag, "$logTag onDestroyView")
    }

    override fun onResume() {
        super.onResume()
        viewModel.logNewEventWithDefaultParameters(
            eventName = TIMELINE_VIEW_COUNT
        )
    }

    //endregion

    // region private functions

    private fun addVerticalLines(
        totalHours: Int,
        startMargin: Int,
        parentLayout: View,
        timeTextHeight: Int
    ) {
        for (index in 1..totalHours) {
            View(context).let { verticalLineView ->
                // Assign index as id
                verticalLineView.id = index
                verticalLineView.setBackgroundResource(R.color.color_white)

                ViewGroup.MarginLayoutParams(verticalLineWidth, screenHeight)
                    .also { marginLayoutParams ->
                        marginLayoutParams.marginStart = index * startMargin
                        // Draw lines in odd position until time view
                        if (index % 2 != 0) marginLayoutParams.bottomMargin = timeTextHeight
                        verticalLineView.layoutParams = marginLayoutParams
                        parentLayout.findViewById<RelativeLayout>(R.id.timelineLayout)
                            .addView(verticalLineView)
                    }
            }
        }
    }

    private fun addTimeStringAtBottomOfVerticalLine(
        parentLayout: View,
        timeTextWidth: Int,
        timeTextHeight: Int
    ) {
        viewModel.verticalLineTimeString.observe(viewLifecycleOwner) { timeString ->
            TextView(context).let { timeTextView ->
                timeTextView.text = timeString
                timeTextView.gravity = Gravity.CENTER
                timeTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                timeTextView.setTextColor(Color.WHITE)
                ViewGroup.MarginLayoutParams(timeTextWidth, timeTextHeight)
                    .also { marginLayoutParams ->
                        marginLayoutParams.marginStart = (index - 1) * (hourWidthInPixel)
                        timeTextView.layoutParams = marginLayoutParams
                        parentLayout.findViewById<RelativeLayout>(R.id.timeLayout)
                            .addView(timeTextView)
                    }
                index++
            }
        }
    }

    private fun addStopIndicatorIcon(
        parentLayout: ConstraintLayout,
        stopDetailList: List<StopDetail>
    ) {
        stopDetailList.forEachIndexed { index, stopDetail ->
            val hrsAndMinsPair =
                Utils.getDiffInHrsAndMinsRemAsPair(
                    initialTimeCalendar,
                    if (stopDetail.EstimatedArrivalTime.isNull())
                        Utils.getCalendarFromDate(
                            Utils.getLocalDate(stopDetail.completedTime)!!
                        )
                    else
                        stopDetail.EstimatedArrivalTime!!

                )
            val startMargin =
                hrsAndMinsPair.first * (hourWidthInPixel) + hrsAndMinsPair.second * minuteWidthMultiplierInPixel
            addDotToLayout(
                index + 1,
                parentLayout,
                startMargin,
                stopDetailList
            )
        }
    }

    private fun addTimeLayout(
        startMargin: Float,
        parentLayout: View,
        timeStr: String
    ) {
        context?.let { context ->
            CardView(context).let {
                it.cardElevation = convertDpToPixel(3f, context)
                it.radius = convertDpToPixel(20f, context)
                it.setCardBackgroundColor(ContextCompat.getColor(context, R.color.timeLabelBgColor))
                val marginLayoutParams =
                    ViewGroup.MarginLayoutParams(timeCardViewWidth, timeCardViewHeight)
                marginLayoutParams.marginStart = startMargin.toInt() - 60.toDp()

                it.layoutParams = marginLayoutParams

                val tv = TextView(context)
                tv.text = timeStr
                tv.gravity = Gravity.CENTER
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                tv.setTextColor(Color.WHITE)

                it.addView(tv)
                parentLayout.findViewById<RelativeLayout>(R.id.timeCardLayout).addView(it)
            }
        }
    }

    private fun addDotToLayout(
        index: Int,
        parentLayout: View,
        startMargin: Float,
        stopDetailList: List<StopDetail>
    ) {
        val currentStartMargin: Int = startMargin.toInt() - stopIndicatorIconSizeHalf

        Button(this.context).let { stopIndicatorButton ->
            var iconSize = stopIndicatorIconSize
            if (index == 1) {
                stopIndicatorButton.setBackgroundResource(R.mipmap.trip_start_icon)
                iconSize = stopIndicatorHighlightedIconSize
            } else {
                stopIndicatorButton.id = index // Don't set id for first dot button
                stopIndicatorButton.setBackgroundResource(R.drawable.ic_stop_indicator)
            }

            stopIndicatorButton.setOnClickListener { stopIndicatorButtonView ->
                if (stopIndicatorButtonView.id > 1)
                    viewModel.selectStop(stopIndicatorButtonView.id - 2)
            }

            ViewGroup.MarginLayoutParams(iconSize, iconSize)
                .also { marginLayoutParams ->
                    marginLayoutParams.marginStart = currentStartMargin
                    stopIndicatorButton.layoutParams = marginLayoutParams
                }

            parentLayout.findViewById<RelativeLayout>(R.id.dotLayout).addView(stopIndicatorButton)
            alignViewToCenterInRelativeLayout(stopIndicatorButton)
        }

        when (index) {
            in 0..1 ->
                previousDotMargin = startMargin.toInt() + stopIndicatorIconSizeHalf + 4.toDp()
            /*Draw time view for the first stop.As we have added trip start time in the collection
            as first stop, index will be 2*/
            2 -> addTimeLayout(
                startMargin,
                parentLayout,
                dateTimeFormat.format(stopDetailList[1].EstimatedArrivalTime?.time!!)
            )
            // Draw time view for the last stop
            stopDetailList.size -> addTimeLayout(
                startMargin,
                parentLayout,
                dateTimeFormat.format(stopDetailList.lastOrNull()?.EstimatedArrivalTime?.time!!)
            )
        }

        View(this.context).let { horizontalLineView ->
            horizontalLineView.setBackgroundColor(Color.WHITE)

            ViewGroup.MarginLayoutParams(
                (currentStartMargin - previousDotMargin - 4.toDp()),
                2.toDp()
            ).also { marginLayoutParams ->
                marginLayoutParams.marginStart = previousDotMargin
                marginLayoutParams.topMargin = stopIndicatorIconSizeHalf
                horizontalLineView.layoutParams = marginLayoutParams
            }

            parentLayout.findViewById<RelativeLayout>(R.id.dotLayout).addView(horizontalLineView)
            alignViewToCenterInRelativeLayout(horizontalLineView)
        }

        previousDotMargin = currentStartMargin + stopIndicatorIconSize + 4.toDp()
    }

    private fun alignViewToCenterInRelativeLayout(it: View) {
        // Align the dot button to center after adding it
        // to the parent(RelativeLayout)
        val viewLayoutParams = it.layoutParams as RelativeLayout.LayoutParams
        viewLayoutParams.addRule(RelativeLayout.CENTER_VERTICAL)
        it.layoutParams = viewLayoutParams
    }

    private fun observeCurrentStop() {
        viewModel.currentStop.observe(
            viewLifecycleOwner
        ) { currentStop ->
            lifecycleScope.launch {
                viewModel.rearrangedStops.value?.let { stopList ->
                    val currentStopIndex = stopList.indexOf(currentStop)
                    removeHighlightedStopIcon()
                    addHighlightedStopIcon(currentStopIndex)
                }
                // for each currentStop value, we need to obtain a formatted date.
                // throught dataBinding with the val eTAForStop we update the view.
                // the val eTAForStop is updated use this method bellow (checkETAForStop)
                viewModel.checkETAForStop(
                    currentStop.stopid
                )
            }
        }
    }

    private fun removeHighlightedStopIcon() {
        fragmentTimelineBinding.dotLayout.let {
            if (currentStopHighlightIconId > 0) {
                it.removeView(it.findViewById(currentStopHighlightIconId))
            }
            if (currentStopIconId > 0) {
                it.findViewById<View>(currentStopIconId)?.let { currentStopView ->
                    currentStopView.visibility = VISIBLE
                }
            }
        }
    }

    private fun addHighlightedStopIcon(currentStopIndex: Int) {
        fragmentTimelineBinding.dotLayout.let {
            currentStopIconId = currentStopIndex + 2
            it.findViewById<View>(currentStopIconId)
                ?.let { stopIcon ->
                    if (stopIcon is Button) {
                        stopIcon.visibility = INVISIBLE// To prevent flickering
                        // Stop highlight icon size set to 60dp
                        val marginLayoutParams = ViewGroup.MarginLayoutParams(
                            stopIndicatorHighlightedIconSize,
                            stopIndicatorHighlightedIconSize
                        )
                        /* Subtracting stopIndicatorIconSizeHalf(25dp) + 5dp = 30dp to set the start
                        margin to left of vertical line so that the icon will be placed equally
                        to left and right which constitutes to total size of 60dp*/
                        marginLayoutParams.marginStart =
                            stopIcon.marginStart - stopIndicatorIconSizeHalf - 5.toDp()
                        Button(this.context).let { stopHighlightIcon ->
                            // Adding 1 to index on since dots are added in even position in dotLayout
                            // Multiplying by 50 to set unique id to the button
                            stopHighlightIcon.id =
                                (currentStopIndex + 1) * STOP_HIGHLIGHTED_ICON_ID_MULTIPLIER
                            currentStopHighlightIconId = stopHighlightIcon.id
                            stopHighlightIcon.layoutParams = marginLayoutParams
                            stopHighlightIcon.setBackgroundResource(R.drawable.ic_timeline_stop_highlight_find)
                            it.addView(stopHighlightIcon)
                            alignViewToCenterInRelativeLayout(stopHighlightIcon)

                            // Scroll to current stop view based on the bottom stop selection.
                            Rect(
                                stopIconFocusRectLeftOffset,
                                0,
                                stopIcon.getWidth() + stopIconFocusRectRightOffset,
                                stopIcon.getHeight()
                            ).apply {
                                stopIcon.requestRectangleOnScreen(this, false)
                            }
                        }
                    }
                }
        }
    }

    private fun Int.toDp(): Int = context?.let { context ->
        (this * context.resources.displayMetrics.density).toInt()
    } ?: 0

    private fun getDiffInHrs(endTimeInMillis: Long, startTimeInMillis: Long): Int {
        val seconds = (endTimeInMillis - startTimeInMillis) / 1000
        return ceil(seconds / 3600f).toInt()
    }
}
