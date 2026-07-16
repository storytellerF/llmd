package com.storytellerf.llmd

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class LlmdIpcAuthorizationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callerPackage = intent.getStringExtra(LlmdIpcAuthorization.EXTRA_CALLER_PACKAGE).orEmpty()
        if (callerPackage.isBlank()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val callerLabel = LlmdIpcAuthorization.callerLabel(this, callerPackage)
        val callerDigest = LlmdIpcAuthorization.displayDigest(this, callerPackage)
        setContentView(buildContent(callerPackage, callerLabel, callerDigest))
    }

    private fun buildContent(
        callerPackage: String,
        callerLabel: String,
        callerDigest: String,
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(PADDING, PADDING, PADDING, PADDING)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )

            addView(
                TextView(context).apply {
                    text = "授权 llmd IPC"
                    textSize = TITLE_TEXT_SIZE_SP
                },
            )
            addView(
                TextView(context).apply {
                    text = "允许 $callerLabel 使用本机 llmd 服务？\n\n包名：$callerPackage\n签名：$callerDigest"
                    textSize = BODY_TEXT_SIZE_SP
                    setPadding(0, PADDING / 2, 0, PADDING / 2)
                },
            )
            addView(
                Button(context).apply {
                    text = "允许"
                    setOnClickListener {
                        val authorized = LlmdIpcAuthorization.authorizePackage(context, callerPackage)
                        setResult(if (authorized) RESULT_OK else RESULT_CANCELED)
                        finish()
                    }
                },
            )
            addView(
                Button(context).apply {
                    text = "取消"
                    setOnClickListener {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                },
            )
        }

    private companion object {
        const val PADDING = 48
        const val TITLE_TEXT_SIZE_SP = 22f
        const val BODY_TEXT_SIZE_SP = 16f
    }
}
