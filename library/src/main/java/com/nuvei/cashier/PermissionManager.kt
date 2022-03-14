package com.nuvei.cashier

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.Context.MODE_PRIVATE
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/*
* Callback on various cases on checking permission
*
* 1.  Below M, runtime permission not needed. In that case "Granted" would be called.
*
* 2.  Above M, if the permission is being asked first time "Ask" would be called.
*
* 3.  Above M, if the permission is previously asked but not granted, "Ask"
*     would be called.
*
* 4.  Above M, if the permission is disabled by device policy or the user checked "Never ask again"
*     check box on previous request permission, "Denied" would be called.
* */

object PermissionManager {
    private const val preferencesFileName = "PermissionStatus"
    private var completion: ((Status) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val permission = intent.extras?.getSerializable(AskPermissionsActivity.Keys.Permission.value) as? Permission ?: return
            markPermissionAskedOnce(context, permission)

            completion?.invoke(
                when (intent.action) {
                    AskPermissionsActivity.Broadcasts.PermissionGranted.value -> Status.Granted
                    else  -> Status.Denied
                }
            )
        }
    }

    enum class Permission(val value: String) {
        Camera(Manifest.permission.CAMERA)
    }

    enum class Status {
        Unknown,
        Granted,
        Denied,
        Ask
    }

    fun checkPermission(
        context: Context,
        permission: Permission,
        completion: (Status) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Runtime permissions are not supported
            completion(Status.Unknown)
            return
        }

        if (checkSelfPermission(context, permission.value) == PermissionChecker.PERMISSION_GRANTED) {
            completion(Status.Granted)
            return
        }

        if ((context as Activity).shouldShowRequestPermissionRationale(permission.value)) {
            completion(if (hasNeverAskCheckbox()) Status.Ask else Status.Denied)
            return
        }

        if (didAskPermissionOnce(context, permission)) {
            // This means "Never ask again" checked
            completion(Status.Denied)
        } else {
            completion(Status.Ask)
        }
    }

    fun askPermission(context: Context, permission: Permission, completion: (Status) -> Unit) {
        this.completion = completion

        val intent = Intent(context, AskPermissionsActivity::class.java)

        val filter = IntentFilter()
        filter.addAction(AskPermissionsActivity.Broadcasts.PermissionGranted.value)
        filter.addAction(AskPermissionsActivity.Broadcasts.PermissionDenied.value)

        LocalBroadcastManager.getInstance(context).registerReceiver(
            receiver,
            filter
        )

        intent.putExtra(AskPermissionsActivity.Keys.Permission.value, permission)

        context.startActivity(intent)
    }

    private fun hasNeverAskCheckbox(): Boolean {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)
    }

    private fun markPermissionAskedOnce(context: Context, permission: Permission) {
        getStorage(context)
            .edit()
            .putBoolean(permission.name, true)
            .apply()
    }

    private fun didAskPermissionOnce(context: Context, permission: Permission): Boolean {
        return if (hasNeverAskCheckbox()) getStorage(context).getBoolean(permission.name, false) else false
    }

    private fun getStorage(context: Context): SharedPreferences {
        return context.getSharedPreferences(
            preferencesFileName,
            MODE_PRIVATE
        )
    }
}

class AskPermissionsActivity: AppCompatActivity() {
    enum class Keys(val value: String) {
        Permission("permissionKey")
    }

    enum class Broadcasts(val value: String) {
        PermissionGranted("AskPermissionsActivity.permissionGranted"),
        PermissionDenied("AskPermissionsActivity.permissionDenied")
    }

    private val permissionRequestCode = 0
    private var permission: PermissionManager.Permission? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permission = intent.getSerializableExtra(Keys.Permission.value) as? PermissionManager.Permission ?: return
        ActivityCompat.requestPermissions(
            this,
            arrayOf(permission!!.value),
            permissionRequestCode
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val permission = permission ?: return

        if (requestCode != permissionRequestCode) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            return
        }

        val intent = Intent()
        val broadcastManager = LocalBroadcastManager.getInstance(this)

        intent.putExtra(Keys.Permission.value, permission)
        if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            intent.action = Broadcasts.PermissionGranted.value
            broadcastManager.sendBroadcast(intent)
        } else {
            intent.action = Broadcasts.PermissionDenied.value
            broadcastManager.sendBroadcast(intent)
        }

        finish()
    }
}
