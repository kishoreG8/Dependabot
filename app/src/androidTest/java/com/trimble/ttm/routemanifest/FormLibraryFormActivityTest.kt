package com.trimble.ttm.routemanifest

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.formlibrary.ui.activities.FormLibraryFormActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

private fun lastLifeCycleTransition(activity: Activity): Stage {
    return ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(activity)
}

@RunWith(AndroidJUnit4::class)
@LargeTest
class FormLibraryFormActivityTest {

    @get:Rule val activityScenarioRule = ActivityScenarioRule(FormLibraryFormActivity::class.java)
    private lateinit var formActivity: ActivityScenario<FormLibraryFormActivity>
    private lateinit var formLibraryFormActivity: FormLibraryFormActivity
    private var formField1 = FormField(1)
    private var formField2 = FormField(2, qtype = FormFieldType.LOOP_END.ordinal)

    @Before
    fun setUp() {
        formActivity = activityScenarioRule.scenario.onActivity {
            formLibraryFormActivity = it
            formLibraryFormActivity.formTemplate.formFieldsList.add(formField1)
            formLibraryFormActivity.formTemplate.formFieldsList.add(formField2)
        }
    }

    @Test
    @Throws(Exception::class)
    fun activityShouldBeResumedAutomatically() {
        //given

        //when
        activityScenarioRule
            .scenario
            .onActivity { activity: Activity ->
                //then
                assertEquals(lastLifeCycleTransition(activity), Stage.RESUMED)
            }
    }

    @After
    fun tearDown() = activityScenarioRule.scenario.close()

}