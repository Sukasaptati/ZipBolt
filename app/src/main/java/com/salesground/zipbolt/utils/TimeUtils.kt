package com.salesground.zipbolt.utils

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

val dateFormat = SimpleDateFormat("d MMMM, yyyy", Locale.UK)

fun Long.parseDate(): String {
    return dateFormat.format(this)
}

private fun String.splitDate() : List<String>{
    return split(" ", ignoreCase = true)
}

fun String.customizeDate() : String{
    var day : String = this
    val splitDate = splitDate()
    val presentDate = System.currentTimeMillis().parseDate().splitDate()
    // check if the month and year is the same
    if(splitDate[1] == presentDate[1] && splitDate[2] == presentDate[2]){
        when(presentDate[0].toInt() - splitDate[0].toInt()){
            0 -> day = "Today"
            1 -> day = "Yesterday"
            2 -> day = "Two days ago"
        }
    }
    return day
}

