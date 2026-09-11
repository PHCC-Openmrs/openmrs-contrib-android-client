package org.openmrs.mobile.utilities

import com.github.mikephil.charting.components.AxisBase
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mock

class DayAxisValueFormatterTest{

    val value1 : Float = 0.5F
    val value2 : Float = 1.2F
    val ax = object : AxisBase(){}

    @Test
    fun correctFormatInputString_returnsFormattedValue(){
        val result1 = DayAxisValueFormatter(arrayListOf("01/04/2020", "01/04/2021")).getFormattedValue(value1, ax)
        val result2 = DayAxisValueFormatter(arrayListOf("01/04/2020", "01/04/2021")).getFormattedValue(value2, ax)
        print(result1)
        print(result2)
        assertEquals("Apr 1, '20", result1)
        assertEquals("Apr 1, '21", result2)
    }

    /**
     * A dash-separated date matches none of the supported formats. DateUtils used to parse it
     * leniently into an unrelated date; it now rejects it outright, so the formatter fails
     * instead of charting a silently wrong label.
     */
    @Test(expected = NullPointerException::class)
    fun wrongFormatOfInputString_isRejected(){
        DayAxisValueFormatter(arrayListOf("01-04-2020", "01-04-2021")).getFormattedValue(value1, ax)
    }

    @Test(expected = NullPointerException::class)
    fun invalidFormat_throwsNPE(){
        val result = DayAxisValueFormatter(arrayListOf("01042020", "01042021")).getFormattedValue(value1, ax)
        print(result)
    }
}