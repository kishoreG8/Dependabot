package com.trimble.ttm.routemanifest.utils

import android.app.Application
import com.trimble.ttm.formlibrary.utils.FormUtils
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

class FormUtilsTest {

    @RelaxedMockK
    private lateinit var application: Application

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        startKoin {
            androidContext(application)
        }
    }

    @Test
    fun `check if the given form is dtf or not if it has uppercase strings`() {    //NOSONAR
        val userFdlScript1 =
            "form_header_info(\"DTF ISSUE 010 Load Zone\",\"010 Load Zone\",can_send,can_send)\n" +
                    "ISSUBJECT(FIELD_TEXT(\"Load Number\",\"Load Number\",not_editable,optional))\n" +
                    "//* Loop for VIN numbers\n" +
                    "LOOPSTART(12)\n" +
                    "FIELD_TEXT(\"VIN#\",\"VIN\",optional,optional)\n" +
                    "FIELD_TEXT(\"Color\",\"Color\",optional,optional)\n" +
                    "FIELD_TEXT(\"Model\",\"Model\",optional,optional)\n" +
                    "FIELD_TEXT(\"Dest\",\"Destination\",optional,optional)\n" +
                    "//* Loop for Damage codes\n" +
                    "LOOPSTART(5)\n" +
                    "FIELD_MULTIPLE_CHOICE(\"More codes?\",\"Asks if driver has any more damage codes\",required,not_editable,\"No\"(\"No_more_codes\"),\"Yes\")\n" +
                    "FIELD_TEXT(\"Code\",\"Damage Code\",optional,optional)\n" +
                    "LOOPEND\n" +
                    "//* End Loop for Damage Codes\n" +
                    "BRANCH_TARGET(\"No_more_codes\")\n" +
                    "FIELD_TEXT(\"VIN Approval Code\",\"VIN Approval Code\",optional,optional)\n" +
                    "FIELD_TEXT(\"Exception Approval Code\",\"Exception Approval Code\",optional,optional)\n" +
                    "\n" +
                    "FIELD_MULTIPLE_CHOICE(\"More VINs?\",\"Asks if driver has any more VINs\",required,not_editable,\"No\"(\"16\"),\"Yes\")\n" +
                    "LOOPEND\n" +
                    "//* End Loop for VIN numbers\n" +
                    "BRANCH_TARGET(\"16\")\n" +
                    "//*\n" +
                    "FIELD_AUTO_LATLONG\n" +
                    "FIELD_AUTO_ODOMETER\n" +
                    "FIELD_AUTO_LOCATION"
        val userFdlScript2 =
            "ISSUBJECT(FIELD_TEXT(\"Load Number\",\"Load Number\",not_editable,optional))"

        assertEquals(true, FormUtils.isDecisionTreeForm(userFdlScript1))
        assertEquals(false, FormUtils.isDecisionTreeForm(userFdlScript2))
    }

    @Test
    fun `check if the given form is dtf or not if it has lowercase strings`() {    //NOSONAR
        val userFdlScript1 =
            "form_header_info(\"DTF FORM Testing\",\"Use to enter information at consignee. PACOS only.\",can_send,can_send_force_urgent)\n" +
                    "field_text(\"ORDER NUMBER\",\"\",not_editable,optional)\n" +
                    "field_multiple_choice(\"DO YOU NEED TO POST-PONE UNLOADING?\",\"\",required,optional,\"Yes\"(\"End\"),\"No\")\n" +
                    "field_multiple_choice(\"DID YOU HOOK A TRAILER?\",\"\",required,optional,\"Yes\",\"No\"(\"LIVELOADTRAILER\"))\n" +
                    "field_text(\"TRAILER DROPPED:\",\"\",required,optional)\n" +
                    "field_text(\"TRAILER HOOKED:\",\"\",required,optional)\n" +
                    "branch_to(\"BILLS\")\n" +
                    "branch_target(\"LIVELOADTRAILER\")\n" +
                    "field_text(\"LIVE UNLOAD TRAILER:\",\"\",required,optional)\n" +
                    "branch_target(\"BILLS\")\n" +
                    "field_multiple_choice(\"WERE THE BILLS SIGNED CLEAN?\",\"\",required,optional,\"Yes\"(\"EMPTY\"),\"No\")\n" +
                    "field_multiple_choice(\"ALL PRODUCT REJECTED?\",\"\",required,optional,\"Yes\"(\"Call\"),\"No\")\n" +
                    "loopstart(25)\n" +
                    "field_text(\"ITEM NUMBER\",\"Driver indicates item\",required,optional)\n" +
                    "field_multiple_choice(\"OVER, SHORT, DAMAGE?\",\"\",required,optional,\"Over\",\"Short\",\"Damage\")\n" +
                    "field_multiple_choice(\"MORE ITEMS?\",\"\",required,optional,\"No\"(\"Call\"),\"Yes\")\n" +
                    "loopend\n" +
                    "branch_target(\"EMPTY\")\n" +
                    "field_multiple_choice(\"TRAILER EMPTY?\",\"\",required,optional,\"Yes\",\"No\"(\"ETA\"))\n" +
                    "field_multiple_choice(\"ARE YOU AVAILABLE FOR ANOTHER LOAD?\",\"\",required,optional,\"Yes\"(\"End\"),\"No\")\n" +
                    "field_date_and_time(\"WHAT IS YOUR PTA?\",\"\",optional,optional)\n" +
                    "branch_to(\"End\")\n" +
                    "branch_target(\"ETA\")\n" +
                    "field_date_and_time(\"ETA TO NEXT STOP DATE/TIME:\",\"\",required,optional)\n" +
                    "field_multiple_choice(\"TRAILER SEALED AND LOCKED?\",\"\",required,optional,\"Yes\",\"No\")\n" +
                    "branch_to(\"End\")\n" +
                    "branch_target(\"Call\")\n" +
                    "field_multiple_choice(\"CALL DISPATCH PRIOR TO MOVING\",\"\",required,optional,\"OK\")\n" +
                    "branch_target(\"End\")\n" +
                    "field_text(\"ANY FINAL REMARKS?\",\"\",optional,optional)\n" +
                    "field_text(\"EVENT\",\"\",not_editable,optional)\n" +
                    "field_text(\"EVENT DESC\",\"\",not_editable,optional)\n" +
                    "field_text(\"COMPANY ID\",\"\",not_editable,optional)\n" +
                    "field_text(\"COMPANY NAME\",\"\",not_editable,optional)\n" +
                    "field_number(\"STOP#\",\"\",not_editable,optional,-999999999999,999999999999,0,\"\",0,left-justify)\n" +
                    "field_number(\"SEGMENT#\",\"\",not_editable,optional,-999999999999,999999999999,0,\"\",0,left-justify)\n" +
                    "field_auto_location\n" +
                    "field_auto_latlong\n" +
                    "field_auto_datetime\n"
        val userFdlScript2 =
            "issubject(field_text(\"load number\",\"load number\",not_editable,optional))"

        assertEquals(true, FormUtils.isDecisionTreeForm(userFdlScript1))
        assertEquals(false, FormUtils.isDecisionTreeForm(userFdlScript2))
    }


    @After
    fun after() {
        stopKoin()
        unmockkAll()
    }
}