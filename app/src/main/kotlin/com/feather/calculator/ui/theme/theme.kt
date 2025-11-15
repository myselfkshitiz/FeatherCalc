package com.feather.calculator.ui.theme

import android.app.Application

import com.google.android.material.color.DynamicColors

class theme : Application() {

    override fun onCreate() {
        super.onCreate()

        DynamicColors.applyToActivitiesIfAvailable(this)

       
    }
}
