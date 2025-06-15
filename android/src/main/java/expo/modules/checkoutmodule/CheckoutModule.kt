package expo.modules.checkoutmodule

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner

import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

import com.google.android.material.bottomsheet.BottomSheetDialog

import com.checkout.components.core.CheckoutComponentsFactory
import com.checkout.components.interfaces.Environment
import com.checkout.components.interfaces.api.CheckoutComponents
import com.checkout.components.interfaces.component.CheckoutComponentConfiguration
import com.checkout.components.interfaces.component.ComponentCallback
import com.checkout.components.interfaces.error.CheckoutError
import com.checkout.components.interfaces.model.ComponentName
import com.checkout.components.interfaces.model.PaymentMethodName
import com.checkout.components.interfaces.model.PaymentSessionResponse
import com.checkout.components.wallet.wrapper.GooglePayFlowCoordinator
import com.google.android.material.bottomsheet.BottomSheetBehavior

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.*

class CheckoutModule : Module() {
    var publicKey: String = ""
    var environment: Environment = Environment.SANDBOX
    private var bottomSheetDialog: BottomSheetDialog? = null

    private var paymentSessionID: String = ""
    private var paymentSessionToken: String = ""
    private var paymentSessionSecret: String = ""

    private fun closeBottomSheet() {
        val dialog = bottomSheetDialog
        dialog?.dismiss()
        bottomSheetDialog = null
    }

    lateinit private var checkoutComponents: CheckoutComponents

    private fun handleActivityResult(resultCode: Int, data: String) {
        this.checkoutComponents?.handleActivityResult(resultCode, data)
    }


    override fun definition() = ModuleDefinition {
        Name("CheckoutModule")

        Events("onSuccess", "onFail")

        AsyncFunction("setCredentials") { data: Map<String, Any?> ->
            val publicKey = data["publicKey"] as? String ?: return@AsyncFunction
            val environmentString = (data["environment"] as? String)?.lowercase() ?: "sandbox"

            val environment = when (environmentString) {
                "sandbox" -> Environment.SANDBOX
                "production" -> Environment.PRODUCTION
                else -> Environment.SANDBOX
            }

            this@CheckoutModule.publicKey = publicKey
            this@CheckoutModule.environment = environment

            Log.d("setCredentials", "publicKey: $publicKey")
            Log.d("setCredentials", "environment: $environment")
        }

        AsyncFunction("initializeCheckout") { paymentSession: Map<String, Any?> ->
            val paymentSessionId = paymentSession["id"] as? String ?: return@AsyncFunction
            val paymentSessionToken =
                paymentSession["payment_session_token"] as? String ?: return@AsyncFunction
            val paymentSessionSecret =
                paymentSession["payment_session_secret"] as? String ?: return@AsyncFunction

            this@CheckoutModule.paymentSessionID = paymentSessionId
            this@CheckoutModule.paymentSessionToken = paymentSessionToken
            this@CheckoutModule.paymentSessionSecret = paymentSessionSecret
        }

        AsyncFunction("renderFlow") {
            Log.d(
                "FlowModule",
                "startPaymentSession invoked with ID: $this@CheckoutModule.paymentSessionID"
            )

            val activity = appContext.currentActivity
            if (activity == null) {
                Log.e("FlowModule", "No current activity available.")
                return@AsyncFunction null
            }

            val customComponentCallback = ComponentCallback(
                onReady = { component ->
                    Log.d("flow component", "onReady ${component.name}")
                },
                onSubmit = { component ->
                    Log.d("flow component", "onSubmit ${component.name}")
                    closeBottomSheet()
                },
                onSuccess = { component, paymentID ->
                    Log.d("flow component", "payment success ${component.name}, ID: $paymentID")
                    this@CheckoutModule.sendEvent("onSuccess");
                },
                onError = { component, checkoutError ->
                    Log.e(
                        "flow component",
                        "Error: ${checkoutError.message}, Code: ${checkoutError.code}"
                    )
                    this@CheckoutModule.sendEvent("onFail");
                }
            )

            val context = activity as Context
            Log.d("FlowModule", "Context: $context")
            activity.runOnUiThread {
                val coordinator = GooglePayFlowCoordinator(
                    context = activity,
                    handleActivityResult = { resultCode, data ->
                        this@CheckoutModule.handleActivityResult(resultCode, data)
                    }
                )

                val flowCoordinators = mapOf(PaymentMethodName.GooglePay to coordinator)

                val configuration = CheckoutComponentConfiguration(
                    context = context,
                    paymentSession = PaymentSessionResponse(
                        id = this@CheckoutModule.paymentSessionID,
                        paymentSessionToken = this@CheckoutModule.paymentSessionToken,
                        paymentSessionSecret = this@CheckoutModule.paymentSessionSecret
                    ),
                    componentCallback = customComponentCallback,
                    publicKey = this@CheckoutModule.publicKey,
                    environment = this@CheckoutModule.environment,
                    flowCoordinators = flowCoordinators,
                )

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        this@CheckoutModule.checkoutComponents =
                            CheckoutComponentsFactory(config = configuration).create()

                        val flowComponent = checkoutComponents.create(ComponentName.Flow)

                        withContext(Dispatchers.Main) {
                            val dialog = BottomSheetDialog(context)
                            bottomSheetDialog = dialog

                            val containerView = FrameLayout(context).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundResource(R.drawable.rounded_top_background)
                                fitsSystemWindows = true
                            }

                            dialog.setContentView(containerView)

                            dialog.window?.apply {
                                setWindowAnimations(R.style.CustomBottomSheetDialog)
                                setBackgroundDrawableResource(android.R.color.transparent)
                                setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                            }

                            val bottomSheet =
                                dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                            bottomSheet?.apply {
                                setBackgroundResource(R.drawable.rounded_top_background)
                                clipToOutline = true
                            }

                            dialog.behavior.apply {
                                state = BottomSheetBehavior.STATE_EXPANDED
                                skipCollapsed = true
                                isFitToContents = true
                                peekHeight = 0
                            }

                            val view = flowComponent.provideView(containerView)

                            if (view is ComposeView) {
                                val lifecycleOwner = activity as? LifecycleOwner
                                val viewModelStoreOwner = activity as? ViewModelStoreOwner
                                val savedStateRegistryOwner = activity as? SavedStateRegistryOwner

                                view.setViewTreeLifecycleOwner(lifecycleOwner)
                                view.setViewTreeViewModelStoreOwner(viewModelStoreOwner)
                                view.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
                            }

                            if (view != null) {
                                val wrappedView = FrameLayout(context).apply {
                                    layoutParams = FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.WRAP_CONTENT
                                    ).apply {
                                        topMargin = 100
                                        bottomMargin = 60
                                    }
                                    addView(view)
                                }

                                ViewCompat.setOnApplyWindowInsetsListener(wrappedView) { v, insets ->
                                    val bottomInset =
                                        insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                                    v.updatePadding(bottom = bottomInset)
                                    insets
                                }

                                containerView.addView(wrappedView)
                                dialog.show()
                            } else {
                                val fallbackTextView = TextView(context).apply {
                                    text = "Flow component failed to render."
                                    textSize = 20f
                                    setTextColor(Color.RED)
                                }
                                containerView.addView(fallbackTextView)
                            }
                        }
                    } catch (checkoutError: CheckoutError) {
                        Log.e("FlowModule", "Error creating Flow component", checkoutError)
                        checkoutError.printStackTrace()
                    }
                }
            }
        }
    }
}
