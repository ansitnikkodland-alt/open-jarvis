package com.openjarvis.agent

import java.text.Normalizer
import java.util.Locale

/**
 * Deterministic fail-closed guard for financial side effects.
 * Read-only app launch/navigation is intentionally allowed.
 */
internal object BankingSafetyPolicy {
    const val BLOCKED_TRANSACTION = "RULE_BANKING_TRANSACTION_BLOCKED"
    const val BLOCKED_CREDENTIAL = "RULE_BANKING_CREDENTIAL_BLOCKED"

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFC)
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()

    private val explicitTransactionIntent = Regex(
        "(?<![\\p{L}\\p{N}])(" +
            "send money|send cash|make payment|pay now|pay bill|pay karo|payment karo|" +
            "upi payment|upi transfer|bank transfer|money transfer|withdraw money|" +
            "paisa bhejo|paise bhejo|paisa transfer|paise transfer|paise bhej|paisa bhej|" +
            "bhugtan karo|bhugtaan karo|bill bharo|recharge karo|mobile recharge|top up karo|" +
            "पैसे भेजो|पैसा भेजो|पैसे ट्रांसफर|पैसा ट्रांसफर|भुगतान करो|भुगतान करें|" +
            "यूपीआई पेमेंट|यूपीआई ट्रांसफर|बिल भरो|" +
            "paise pathav|paise pathva|paise transfer kara|payment kara|" +
            "पैसे पाठवा|पैसे पाठव|रक्कम पाठवा|पेमेंट करा|बिल भरा" +
        ")(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE
    )

    private val transactionVerb = Regex(
        "(?<![\\p{L}\\p{N}])(pay|payment|transfer|send|withdraw|recharge|bhejo|bhej|पाठवा|पाठव|भेजो|भेज)(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE
    )

    private val amountOrRecipient = Regex(
        "(?<![\\p{L}\\p{N}])(\\d+(?:\\.\\d{1,2})?|to\\s+[\\p{L}]+|ko\\s+[\\p{L}]+|को\\s*[\\p{L}]+)(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE
    )

    private val credentialTerms = Regex(
        "(?<![\\p{L}\\p{N}])(upi pin|pin|otp|one time password|cvv|cvc|card number|" +
            "debit card|credit card|bank password|net banking password|account number|" +
            "ओटीपी|यूपीआई पिन|पिन|सीवीवी|कार्ड नंबर|खाता नंबर|बैंक पासवर्ड|" +
            "otp code|verification code)(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE
    )

    private val credentialSubmission = Regex(
        "(?<![\\p{L}\\p{N}])(enter|type|input|submit|share|tell|send|paste|fill| डालो|डालें|लिखो|भेजो|बताओ|शेयर करो|" +
            "tak|dalo|likho|bhejo|batao|share karo|enter karo|type karo| भरें|टाइप करो|दाखल करा|टाका)(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE
    )

    private val financialPackages = setOf(
        "com.google.android.apps.wallet",
        "com.google.android.apps.nbu.paisa.user",
        "com.paytm",
        "com.phonepe",
        "in.org.npci.upiapp",
    )

    fun isFinancialPackage(packageName: String?): Boolean {
        val pkg = packageName?.lowercase(Locale.ROOT).orEmpty()
        return pkg in financialPackages ||
            listOf("bank", "upi", "wallet", "paytm", "phonepe", "paisa").any(pkg::contains)
    }

    fun knownFinancialPackages(): Set<String> = financialPackages

    private val financialControl = Regex(
        "(?<![\\p{L}\\p{N}])(pay|payment|transfer|send|proceed|confirm|withdraw|" +
            "recharge|upi|भुगतान|भेजें|भेजो|ट्रांसफर|पैसे भेजो|पेमेंट|पाठवा)(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE
    )

    private val informationalRequest = Regex(
        "^(how|what|why|explain|describe|meaning|kaise|kyun|kya|samjhao|samjha|क्यों|कैसे|क्या|समझाओ|काय|कसे)\\\\b",
        RegexOption.IGNORE_CASE
    )

    fun isBlockedCommand(command: String): Boolean {
        val s = normalize(command)
        if (s.isBlank() || informationalRequest.containsMatchIn(s)) return false
        if (explicitTransactionIntent.containsMatchIn(s)) return true
        if (transactionVerb.containsMatchIn(s) && amountOrRecipient.containsMatchIn(s)) return true
        return credentialTerms.containsMatchIn(s) && credentialSubmission.containsMatchIn(s)
    }

    fun failureRule(command: String, actions: List<Action>): String? {
        val s = normalize(command)
        if (isBlockedCommand(s)) {
            return if (credentialTerms.containsMatchIn(s) && credentialSubmission.containsMatchIn(s))
                BLOCKED_CREDENTIAL else BLOCKED_TRANSACTION
        }
        if (actions.any(::isBlockedFinancialAction)) {
            return if (actions.any { action ->
                listOfNotNull(action.text, action.hint, action.value, action.label)
                    .any { credentialTerms.containsMatchIn(normalize(it)) }
            }) BLOCKED_CREDENTIAL else BLOCKED_TRANSACTION
        }
        return null
    }

    fun blocksFinancialActions(actions: List<Action>): Boolean =
        actions.any(::isBlockedFinancialAction)

    private fun isBlockedFinancialAction(action: Action): Boolean {
        val pkg = action.packageName?.lowercase(Locale.ROOT).orEmpty()
        val financialApp = isFinancialPackage(pkg)
        val text = listOfNotNull(action.text, action.hint, action.value, action.label)
            .joinToString(" ")
        val credentialMentioned = credentialTerms.containsMatchIn(normalize(text))
        if (credentialMentioned && action.action in setOf(Action.TYPE, Action.TYPE_TEXT, Action.CLICK, Action.TAP)) {
            return true
        }
        if (!financialApp) return false
        return when (action.action) {
            Action.TYPE, Action.TYPE_TEXT -> true
            Action.CLICK, Action.TAP -> financialControl.containsMatchIn(normalize(text))
            else -> false
        }
    }

    fun userGuidance(rule: String): String = when (rule) {
        BLOCKED_CREDENTIAL ->
            "Banking safety: OTP, UPI PIN, CVV, password ya card/account details Jarvis enter/share nahi karega. Ye step khud secure app mein karo."
        BLOCKED_TRANSACTION ->
            "Banking safety: Jarvis payment, money transfer, withdrawal ya recharge execute nahi karega. Transaction khud official app mein verify karke karo."
        else -> "Banking action safety policy ne is action ko block kiya."
    }
}
