package com.khosravi.devin.present.domain

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.client.getLogPassword
import com.khosravi.devin.present.data.AppPref
import com.khosravi.devin.present.present.ClientLoginDialogHost
import javax.inject.Inject

class ClientLoginInteractor @Inject constructor(private val appPref: AppPref) {

    fun onClientSelect(activity: AppCompatActivity, clientData: ClientData, onNext: (Boolean) -> Unit) {
        handleOpeningNextActivityRequest(clientData, { password ->
            ClientLoginDialogHost(activity).show(
                correctPassword = password,
                onCorrectPassword = { enteredPassword ->
                    appPref.apply {
                        resetLastWrongPasswordCount(clientData.id)
                        saveConfirmedPassword(clientData.id, enteredPassword)
                    }
                    onNext(true)
                },
                onWrongPassword = {
                    val wrongCount = appPref.increaseLastWrongPasswordCount(clientData.id)
                    if (wrongCount == VALUE_MAX_WRONG_PASSWORD_TRY) {
                        onNext(false)
                        true
                    } else {
                        false
                    }
                }
            )
        }, onNext)
    }

    private fun handleOpeningNextActivityRequest(
        clientData: ClientData,
        onNeedPassword: (password: String) -> Unit,
        onNext: (Boolean) -> Unit
    ) {
        val password = clientData.getLogPassword()
        if (password != null) {
            val confirmedPassword = appPref.getLastConfirmedPassword(clientData.id)
            if (confirmedPassword == password) {
                onNext(true)
                return
            } else {
                //we need validation.
                val wrongPasswordCount = appPref.getLastWrongPasswordCountWithConstrainCheck(clientData.id)
                if (wrongPasswordCount == VALUE_MAX_WRONG_PASSWORD_TRY) {
                    onNext(false)
                    return
                }
                onNeedPassword.invoke(password)
            }
        } else {
            onNext(true)
        }
    }

    fun showManyTryPasswordToast(activity: AppCompatActivity) {
        Toast.makeText(activity, "Too many wrong password try", Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val VALUE_MAX_WRONG_PASSWORD_TRY = 4
    }

}