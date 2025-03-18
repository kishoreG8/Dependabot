package com.trimble.ttm.routemanifest.utils

import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class SendDispatchUtilTest {

    private val directoryPath = "C:\\Dispatch"
    private val jarFileName = "route-manifest-1.1-SNAPSHOT.jar"


    @Test
    fun sendNormalSequenceDispatch(){
        //executeJarInDirectory(directoryPath = directoryPath,jarFileName = jarFileName, dispatchFileName = "NormalTripWith7SequenceStops.txt", numberOfTrips = 12)
    }

    @Test
    fun sendAutoStartSequenceDispatch(){
       //executeJarInDirectory(directoryPath = directoryPath,jarFileName = jarFileName, dispatchFileName = "AutoStartTripWith77SequenceStops.txt")
    }


    @Test
    fun sendNormalFreeFloatDispatch(){
        //executeJarInDirectory(directoryPath = directoryPath,jarFileName = jarFileName, dispatchFileName = "NormalTripWith7FreeFloatStops.txt")
    }

    @Test
    fun sendAutoStartFreeFloatDispatch(){
        //executeJarInDirectory(directoryPath = directoryPath,jarFileName = jarFileName, dispatchFileName = "AutoStartTripWith7FreeFloatStops.txt")
    }


    private fun executeJarInDirectory(directoryPath: String, jarFileName: String, dispatchFileName:String, numberOfTrips:Int = 1) {
        val file = File(directoryPath, jarFileName)
        val dispatchFile = File(directoryPath,dispatchFileName)
        val processBuilder = ProcessBuilder("java", "-jar", file.absolutePath, "staging" ,dispatchFile.absolutePath, "$numberOfTrips")
        processBuilder.directory(File(directoryPath))
        val process = processBuilder.start()
        val output = StringBuilder()
        val reader = BufferedReader(InputStreamReader(process.errorStream))

        var line: String? = reader.readLine()
        while (line != null) {
            output.append(line).append(System.lineSeparator())
            line = reader.readLine()
        }

        process.waitFor()

        println(output)
    }


}
